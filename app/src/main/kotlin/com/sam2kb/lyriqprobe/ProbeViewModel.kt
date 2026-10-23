package com.sam2kb.lyriqprobe

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sam2kb.lyriqprobe.core.car.CarAccess
import com.sam2kb.lyriqprobe.core.model.AppSnapshot
import com.sam2kb.lyriqprobe.core.model.CarAppLibrarySnapshot
import com.sam2kb.lyriqprobe.core.model.DrivingStateSnapshot
import com.sam2kb.lyriqprobe.core.model.ProbeData
import com.sam2kb.lyriqprobe.core.model.ProbeModule
import com.sam2kb.lyriqprobe.core.model.ProbeReport
import com.sam2kb.lyriqprobe.core.model.ProbeSection
import com.sam2kb.lyriqprobe.core.model.ResultStatus
import com.sam2kb.lyriqprobe.core.model.TestOutcome
import com.sam2kb.lyriqprobe.core.model.TestResult
import com.sam2kb.lyriqprobe.core.model.VpnCapabilitySnapshot
import com.sam2kb.lyriqprobe.core.probe.CameraProbe
import com.sam2kb.lyriqprobe.core.probe.ConnectivityProbe
import com.sam2kb.lyriqprobe.core.probe.PermissionProbe
import com.sam2kb.lyriqprobe.core.probe.SensorProbe
import com.sam2kb.lyriqprobe.core.probe.StorageProbe
import com.sam2kb.lyriqprobe.core.probe.SystemProbe
import com.sam2kb.lyriqprobe.core.probe.VehicleProbe
import com.sam2kb.lyriqprobe.core.report.ReportFactory
import com.sam2kb.lyriqprobe.core.report.ReportJson
import com.sam2kb.lyriqprobe.core.runner.TestHarness
import com.sam2kb.lyriqprobe.core.runner.activeMeta
import com.sam2kb.lyriqprobe.core.runner.discoveryMeta
import com.sam2kb.lyriqprobe.report.ReportStore
import com.sam2kb.lyriqprobe.report.SavedReport
import com.sam2kb.lyriqprobe.vpn.VpnEstablishOutcome
import com.sam2kb.lyriqprobe.vpn.VpnProbe
import com.sam2kb.lyriqprobe.vpn.VpnProbeBroker
import com.sam2kb.lyriqprobe.vpn.VpnProbeService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

enum class ScanPhase {
    IDLE,
    RUNNING,
    COMPLETE,
    FAILED,
}

data class ProbeUiState(
    val app: AppSnapshot? = null,
    val report: ProbeReport? = null,
    val savedReports: List<SavedReport> = emptyList(),
    val scanPhase: ScanPhase = ScanPhase.IDLE,
    val scanStep: String = "Ready",
    val scanCompleted: Int = 0,
    val scanTotal: Int = 9,
    val driving: DrivingStateSnapshot? = null,
    val automotiveFeature: Boolean = false,
    val parkedAttested: Boolean = false,
    val activeBusy: String? = null,
    val lastActiveResult: TestResult? = null,
    val message: String? = null,
) {
    val restricted: Boolean
        get() = driving?.requiresDistractionOptimization == true

    val needsAttestation: Boolean
        get() = automotiveFeature && driving?.available != true

    val parkedPermitted: Boolean
        get() = !restricted && (!needsAttestation || parkedAttested)

    val scanRunning: Boolean
        get() = scanPhase == ScanPhase.RUNNING
}

private data class ActiveMutation(
    val result: TestResult,
    val update: (ProbeData) -> ProbeData = { it },
)

class ProbeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val harness = TestHarness()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val systemProbe = SystemProbe(app)
    private val permissionProbe = PermissionProbe(app)
    private val cameraProbe = CameraProbe(app)
    private val vehicleProbe = VehicleProbe()
    private val sensorProbe = SensorProbe(app)
    private val storageProbe = StorageProbe(app)
    private val connectivityProbe = ConnectivityProbe(app)
    private val vpnProbe = VpnProbe(app)
    private val reportFactory = ReportFactory(BuildConfig.VERSION_NAME)
    private val reportStore = ReportStore(app)
    private val appInfo = runCatching { systemProbe.readAppInfo() }.getOrNull()
    private val automotiveFeature = app.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)

    private val _state = MutableStateFlow(
        ProbeUiState(
            app = appInfo,
            automotiveFeature = automotiveFeature,
            scanStep = "Ready for Quick Scan",
        ),
    )
    val state: StateFlow<ProbeUiState> = _state.asStateFlow()

    private val carReady = CompletableDeferred<CarAccess?>()
    private var carAccess: CarAccess? = null
    private var scanJob: Job? = null
    private var activeJob: Job? = null
    private var vpnInFlight = false

    init {
        viewModelScope.launch {
            VpnProbeBroker.events.collect { outcome -> onVpnEstablished(outcome) }
        }
        viewModelScope.launch {
            val reports = withContext(Dispatchers.IO) {
                runCatching { reportStore.list() }.getOrDefault(emptyList())
            }
            _state.update { it.copy(savedReports = reports) }

            val access = withContext(Dispatchers.IO) { CarAccess.open(app) }
            carAccess = access
            withContext(Dispatchers.IO) {
                access.registerUxListener { snapshot ->
                    mainHandler.post { onDrivingChanged(snapshot) }
                }
            }
            val current = withContext(Dispatchers.IO) { access.currentDrivingState() }
            _state.update {
                it.copy(
                    driving = current,
                    message = if (current.requiresDistractionOptimization == true) {
                        "Driving restriction active. Interactive diagnostics are disabled."
                    } else {
                        it.message
                    },
                )
            }
            carReady.complete(access)
        }
    }

    fun runQuickScan() {
        if (scanJob?.isActive == true) return
        val guard = scanGuard()
        if (guard != null) {
            showMessage(guard)
            return
        }
        scanJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    scanPhase = ScanPhase.RUNNING,
                    scanStep = "Preparing probes",
                    scanCompleted = 0,
                    scanTotal = 9,
                    activeBusy = null,
                    lastActiveResult = null,
                    message = null,
                    report = null,
                )
            }
            try {
                val access = carReady.await()
                var data = ProbeData(
                    app = _state.value.app ?: appInfo,
                    driving = _state.value.driving ?: unavailableDriving("Driving state not yet read."),
                    carAppLibrary = carAppNotTested(),
                )
                publish(data)
                fun advance(completed: Int, step: String) {
                    _state.update { it.copy(scanCompleted = completed, scanStep = step) }
                }

                val system = safeSection(
                    ProbeModule.SYSTEM,
                    "system.module",
                    "System inventory module",
                    "PackageManager / Build",
                ) { withContext(Dispatchers.IO) { systemProbe.scan(access) } }
                data = data.copy(
                    app = system.snapshot?.app ?: data.app,
                    device = system.snapshot?.device,
                    android = system.snapshot?.android,
                    automotive = system.snapshot?.automotive,
                    results = data.results + system.results,
                )
                publish(data)
                advance(1, "Permissions")

                val permissions = safeSection(
                    ProbeModule.PERMISSIONS,
                    "permissions.module",
                    "Permission matrix module",
                    "PackageManager",
                ) { withContext(Dispatchers.IO) { permissionProbe.scan() } }
                data = data.copy(
                    permissions = permissions.snapshot,
                    results = data.results + permissions.results,
                )
                publish(data)
                advance(2, "Cameras")

                val cameras = safeSection(
                    ProbeModule.CAMERAS,
                    "camera.module",
                    "Camera2 inventory module",
                    "Camera2",
                ) { withContext(Dispatchers.IO) { cameraProbe.scan() } }
                data = data.copy(
                    cameras = cameras.snapshot,
                    results = data.results + cameras.results,
                )
                publish(data)
                advance(3, "Vehicle")

                val vehicle = safeSection(
                    ProbeModule.VEHICLE,
                    "vehicle.module",
                    "Vehicle property module",
                    "CarPropertyManager",
                ) { withContext(Dispatchers.IO) { vehicleProbe.scanProperties(access) } }
                data = data.copy(
                    vehicle = vehicle.snapshot,
                    results = data.results + vehicle.results,
                )
                publish(data)
                advance(4, "Sensors")

                val sensors = safeSection(
                    ProbeModule.SENSORS,
                    "sensors.module",
                    "Android sensor module",
                    "SensorManager",
                ) { withContext(Dispatchers.IO) { sensorProbe.scan() } }
                data = data.copy(
                    sensors = sensors.snapshot,
                    results = data.results + sensors.results,
                )
                publish(data)
                advance(5, "Storage")

                val storage = safeSection(
                    ProbeModule.STORAGE,
                    "storage.module",
                    "Storage module",
                    "StorageManager / SAF",
                ) { withContext(Dispatchers.IO) { storageProbe.scan() } }
                data = data.copy(
                    storage = storage.snapshot,
                    results = data.results + storage.results,
                )
                publish(data)
                advance(6, "Connectivity")

                val connectivity = safeSection(
                    ProbeModule.CONNECTIVITY,
                    "connectivity.module",
                    "Connectivity and hardware module",
                    "ConnectivityManager / UsbManager / LocationManager",
                ) { withContext(Dispatchers.IO) { connectivityProbe.scan() } }
                data = data.copy(
                    connectivity = connectivity.snapshot,
                    results = data.results + connectivity.results,
                )
                publish(data)
                advance(7, "VPN")

                val vpn = safeSection(
                    ProbeModule.VPN,
                    "vpn.module",
                    "VPN capability module",
                    "VpnService",
                ) { withContext(Dispatchers.IO) { vpnProbe.scan() } }
                data = data.copy(
                    vpn = vpn.snapshot,
                    results = data.results + vpn.results,
                )
                publish(data)
                advance(8, "Driving state")

                val drivingNow = withContext(Dispatchers.IO) {
                    access?.currentDrivingState()
                        ?: unavailableDriving("No Car UX restrictions service is available.")
                }
                _state.update { it.copy(driving = drivingNow) }
                val drivingResult = harness.run(
                    discoveryMeta(
                        "driving.restrictions",
                        ProbeModule.DRIVING,
                        "AAOS driving restrictions",
                        "CarUxRestrictionsManager",
                    ),
                ) {
                    TestOutcome(
                        status = if (drivingNow.available) ResultStatus.AVAILABLE else ResultStatus.UNAVAILABLE,
                        summary = if (drivingNow.requiresDistractionOptimization == true) {
                            "Driving restrictions require distraction optimization; interactive diagnostics are locked."
                        } else if (drivingNow.available) {
                            "No distraction-optimization requirement at scan time."
                        } else {
                            "Driving restrictions unavailable: ${drivingNow.error ?: "unknown"}"
                        },
                        details = buildString {
                            appendLine("Restrictions=${drivingNow.restrictionNames}")
                            appendLine("maxContentDepth=${drivingNow.maxContentDepth}")
                            appendLine("maxCumulativeContentItems=${drivingNow.maxCumulativeContentItems}")
                            appendLine(drivingNow.note)
                        },
                        exceptionClass = if (drivingNow.available) null else "DrivingStateUnavailable",
                        exceptionMessage = drivingNow.error,
                    )
                }
                val carApp = carAppNotTested()
                val carAppResult = harness.run(
                    discoveryMeta(
                        "carapp.library",
                        ProbeModule.VEHICLE,
                        "Car App Library hardware path",
                        "androidx.car.app (not integrated)",
                    ),
                ) {
                    TestOutcome(
                        status = ResultStatus.NOT_TESTED,
                        summary = carApp.reason,
                        details = "A Car App Library probe requires a host-bound CarAppService and a template surface. " +
                            "This build uses direct read-only VHAL access and does not claim CarInfo or CarSensors results.",
                    )
                }
                data = data.copy(
                    driving = drivingNow,
                    carAppLibrary = carApp,
                    results = data.results + drivingResult + carAppResult,
                )
                val report = publish(data)
                advance(9, "Saving report")
                val saved = withContext(Dispatchers.IO) {
                    runCatching { reportStore.save(report) }
                }
                val savedReports = withContext(Dispatchers.IO) {
                    runCatching { reportStore.list() }.getOrDefault(_state.value.savedReports)
                }
                _state.update {
                    it.copy(
                        scanPhase = ScanPhase.COMPLETE,
                        scanStep = "Scan complete",
                        scanCompleted = 9,
                        report = report,
                        savedReports = savedReports,
                        message = saved.fold(
                            onSuccess = { s -> "Quick Scan complete. Saved ${s.displayName}." },
                            onFailure = { t -> "Quick Scan complete, but saving failed: ${t.javaClass.simpleName}: ${t.message}" },
                        ),
                    )
                }
            } catch (cancelled: CancellationException) {
                _state.update {
                    if (it.scanPhase == ScanPhase.RUNNING) {
                        it.copy(
                            scanPhase = ScanPhase.IDLE,
                            scanStep = "Stopped",
                            message = it.message ?: "Quick Scan stopped.",
                        )
                    } else {
                        it
                    }
                }
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(
                        scanPhase = ScanPhase.FAILED,
                        scanStep = "Failed",
                        message = "Quick Scan failed: ${throwable.javaClass.simpleName}: ${throwable.message}",
                    )
                }
            } finally {
                scanJob = null
            }
        }
    }

    fun openCamera(cameraId: String) {
        runActive(
            id = "camera.open.$cameraId",
            label = "Opening camera $cameraId",
            module = ProbeModule.CAMERAS,
        ) {
            ActiveMutation(cameraProbe.openClose(cameraId))
        }
    }

    fun captureFrame(cameraId: String) {
        runActive(
            id = "camera.frame.$cameraId",
            label = "Capturing one frame from camera $cameraId",
            module = ProbeModule.CAMERAS,
        ) {
            ActiveMutation(cameraProbe.captureSingleFrame(cameraId))
        }
    }

    fun readVehicleValues() {
        runActive(
            id = "vehicle.read_values",
            label = "Reading allowlisted vehicle values",
            module = ProbeModule.VEHICLE,
        ) {
            val result = vehicleProbe.readValues(carAccess)
            ActiveMutation(result.result) { data ->
                data.copy(vehicle = data.vehicle?.copy(readings = result.readings))
            }
        }
    }

    fun sampleSensor(sensorType: Int, sensorName: String) {
        runActive(
            id = "sensors.sample.$sensorType",
            label = "Sampling $sensorName for 10 seconds",
            module = ProbeModule.SENSORS,
        ) {
            val result = sensorProbe.sample(sensorType) { isActiveAllowedNow() }
            ActiveMutation(result.result) { data ->
                data.copy(sensors = data.sensors?.copy(lastSample = result.sample))
            }
        }
    }

    fun testStorage() {
        val uri = storageProbe.selectedTreeUri()
        if (uri == null) {
            showMessage("Select a writable folder with the system picker first.")
            return
        }
        runActive(
            id = "storage.write_read_delete",
            label = "Creating, reading and deleting a test file",
            module = ProbeModule.STORAGE,
        ) {
            val result = storageProbe.testTree(uri) { isActiveAllowedNow() }
            val tree = storageProbe.inspectSelectedTree(uri)
            ActiveMutation(result.result) { data ->
                data.copy(
                    storage = data.storage?.copy(
                        testRecord = result.record,
                        selectedTree = tree,
                    ),
                )
            }
        }
    }

    fun onTreeSelected(uri: Uri) {
        viewModelScope.launch {
            val permissionError = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }.exceptionOrNull()
            }
            storageProbe.saveSelectedTree(uri)
            val report = _state.value.report
            val storage = report?.storage
            if (report != null && storage != null) {
                val tree = withContext(Dispatchers.IO) { storageProbe.inspectSelectedTree(uri) }
                publish(report.toProbeData().copy(storage = storage.copy(selectedTree = tree)))
            }
            showMessage(
                if (permissionError != null) {
                    "Folder selected for this session, but persistent permission failed: " +
                        "${permissionError.javaClass.simpleName}: ${permissionError.message}"
                } else {
                    "Folder selected with persistent read/write access."
                },
            )
        }
    }

    fun clearSelectedTree() {
        viewModelScope.launch {
            val uri = storageProbe.selectedTreeUri()
            if (uri != null) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        app.contentResolver.releasePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                    }
                }
            }
            storageProbe.clearSelectedTree()
            val report = _state.value.report
            val storage = report?.storage
            if (report != null && storage != null) {
                publish(report.toProbeData().copy(storage = storage.copy(selectedTree = null)))
            }
            showMessage("Persisted folder selection cleared.")
        }
    }

    fun onPermissionResult(permission: String, granted: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(message = null) }
            try {
                val requestResult = permissionProbe.recordRequestResult(permission, granted)
                val refreshed = withContext(Dispatchers.IO) { permissionProbe.scan() }
                val report = _state.value.report
                if (report == null) {
                    _state.update { it.copy(lastActiveResult = requestResult, message = requestResult.summary) }
                    return@launch
                }
                val base = report.toProbeData()
                val nonPermissionDiscovery = base.results.filterNot {
                    it.module == ProbeModule.PERMISSIONS && it.kind == com.sam2kb.lyriqprobe.core.model.TestKind.DISCOVERY
                }
                val updated = base.copy(
                    permissions = refreshed.snapshot ?: base.permissions,
                    results = nonPermissionDiscovery + refreshed.results + requestResult,
                )
                publish(updated)
                _state.update {
                    it.copy(
                        lastActiveResult = requestResult,
                        message = requestResult.summary,
                    )
                }
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(message = "Permission refresh failed: ${throwable.javaClass.simpleName}: ${throwable.message}")
                }
            }
        }
    }

    fun setParkedAttested(value: Boolean) {
        if (_state.value.restricted) {
            showMessage("Driving restrictions are active; parked confirmation cannot override them.")
            return
        }
        _state.update { it.copy(parkedAttested = value) }
    }

    fun saveCurrentReport() {
        val report = _state.value.report ?: run {
            showMessage("Run Quick Scan before saving a report.")
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { reportStore.save(report) } }
            result.onSuccess { saved ->
                val reports = withContext(Dispatchers.IO) {
                    runCatching { reportStore.list() }.getOrDefault(_state.value.savedReports)
                }
                _state.update {
                    it.copy(
                        savedReports = reports,
                        message = "Saved ${saved.displayName}.",
                    )
                }
            }.onFailure { throwable ->
                showMessage("Save failed: ${throwable.javaClass.simpleName}: ${throwable.message}")
            }
        }
    }

    fun currentJson(): String? = _state.value.report?.let { ReportJson.encode(it) }

    fun savedJson(saved: SavedReport): String? = runCatching { reportStore.readJson(saved) }.getOrNull()

    fun currentFileBaseName(): String {
        val timestamp = _state.value.report?.timestamp ?: Instant.now().toString()
        return "lyriq-probe-${timestamp.replace(":", "").replace("-", "").take(15)}"
    }

    fun createShareUri(json: String, fileName: String): Uri? = runCatching {
        val file = reportStore.makeShareFile(json, fileName)
        FileProvider.getUriForFile(app, "${app.packageName}.reports", file)
    }.getOrNull()

    fun writeExport(uri: Uri, json: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
                        it.write(json)
                    } ?: error("The document provider returned no output stream.")
                }
            }
            result.onSuccess {
                showMessage("Report exported.")
            }.onFailure {
                showMessage("Export failed: ${it.javaClass.simpleName}: ${it.message}")
            }
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    override fun onCleared() {
        scanJob?.cancel()
        activeJob?.cancel()
        carAccess?.close()
        mainHandler.removeCallbacksAndMessages(null)
        super.onCleared()
    }

    private fun runActive(
        id: String,
        label: String,
        module: ProbeModule,
        operation: suspend () -> ActiveMutation,
    ) {
        val guard = activeGuard()
        if (guard != null) {
            showMessage(guard)
            return
        }
        activeJob = viewModelScope.launch {
            _state.update { it.copy(activeBusy = label, lastActiveResult = null, message = null) }
            try {
                val mutation = withContext(Dispatchers.IO) { operation() }
                val report = _state.value.report
                if (report == null) {
                    showMessage("Run Quick Scan before an active test.")
                    return@launch
                }
                val data = report.toProbeData()
                val updated = mutation.update(data).copy(results = data.results + mutation.result)
                publish(updated)
                _state.update {
                    it.copy(
                        lastActiveResult = mutation.result,
                        message = mutation.result.summary,
                    )
                }
            } catch (cancelled: CancellationException) {
                _state.update {
                    it.copy(
                        message = "Active test stopped because the parked/driving state changed.",
                    )
                }
                throw cancelled
            } catch (throwable: Throwable) {
                val failure = harness.run(
                    activeMeta(
                        id = "active.$id",
                        module = module,
                        name = label,
                        apiUsed = "active test wrapper",
                    ),
                ) { throw throwable }
                val report = _state.value.report
                if (report != null) {
                    val data = report.toProbeData()
                    publish(data.copy(results = data.results + failure))
                }
                _state.update {
                    it.copy(
                        lastActiveResult = failure,
                        message = failure.summary,
                    )
                }
            } finally {
                activeJob = null
                _state.update {
                    if (it.activeBusy == label) it.copy(activeBusy = null) else it
                }
            }
        }
    }

    private fun scanGuard(): String? {
        val snapshot = _state.value
        if (scanJob?.isActive == true) return "Quick Scan is already running."
        if (activeJob?.isActive == true) return "An active test is running."
        if (snapshot.activeBusy != null || vpnInFlight) return "An active test is running."
        if (snapshot.restricted) {
            return "Vehicle reports a restricted driving state. Interactive diagnostics are disabled."
        }
        if (snapshot.needsAttestation && !snapshot.parkedAttested) {
            return "Parked state could not be read. Confirm the vehicle is parked before running diagnostics."
        }
        return null
    }

    private fun activeGuard(): String? {
        val snapshot = _state.value
        if (snapshot.report == null) return "Run Quick Scan first."
        if (snapshot.scanRunning) return "Quick Scan is running."
        if (snapshot.activeBusy != null) return "Another active test is running."
        if (snapshot.restricted) {
            return "Vehicle reports a restricted driving state. Active diagnostics are disabled."
        }
        if (snapshot.needsAttestation && !snapshot.parkedAttested) {
            return "Parked state could not be read. Confirm the vehicle is parked before active tests."
        }
        return null
    }

    private fun isActiveAllowedNow(): Boolean {
        val snapshot = _state.value
        if (snapshot.restricted) return false
        if (snapshot.needsAttestation && !snapshot.parkedAttested) return false
        return true
    }

    private fun onDrivingChanged(snapshot: DrivingStateSnapshot) {
        val restricted = snapshot.requiresDistractionOptimization == true
        _state.update {
            it.copy(
                driving = snapshot,
                parkedAttested = if (restricted) false else it.parkedAttested,
                message = if (restricted) {
                    "Driving restriction active. Interactive diagnostics are disabled."
                } else {
                    it.message
                },
            )
        }
        if (restricted) {
            activeJob?.cancel()
            scanJob?.cancel()
            if (vpnInFlight) {
                vpnInFlight = false
                app.stopService(Intent(app, VpnProbeService::class.java))
                _state.update { it.copy(activeBusy = null) }
            }
        }
        val report = _state.value.report ?: return
        publish(report.toProbeData().copy(driving = snapshot))
    }

    private fun publish(data: ProbeData): ProbeReport {
        val report = reportFactory.build(data)
        _state.update { it.copy(report = report) }
        return report
    }

    private suspend fun <T> safeSection(
        module: ProbeModule,
        id: String,
        name: String,
        api: String,
        block: suspend () -> ProbeSection<T>,
    ): ProbeSection<T> {
        return try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            val result = harness.run(discoveryMeta(id, module, name, api)) { throw throwable }
            ProbeSection(null, listOf(result), "${throwable.javaClass.simpleName}: ${throwable.message}")
        }
    }

    private fun showMessage(message: String) {
        _state.update { it.copy(message = message) }
    }

    private fun ProbeReport.toProbeData(): ProbeData = ProbeData(
        app = app,
        device = device,
        android = android,
        automotive = automotive,
        permissions = permissions,
        cameras = cameras,
        vehicle = vehicleProperties,
        sensors = androidSensors,
        storage = storage,
        connectivity = connectivity,
        vpn = vpn,
        driving = drivingState,
        carAppLibrary = carAppLibrary,
        results = testResults,
        errors = emptyList(),
    )

    fun startVpnTest(): Intent? {
        val guard = activeGuard()
        if (guard != null) {
            showMessage(guard)
            return null
        }
        val base = _state.value.report?.vpn
        vpnInFlight = true
        _state.update { it.copy(activeBusy = "Checking VPN authorization", message = null) }
        val consentIntent = try {
            vpnProbe.authorizationIntent()
        } catch (throwable: Throwable) {
            vpnInFlight = false
            _state.update { it.copy(activeBusy = null) }
            viewModelScope.launch { recordVpnFailure("VpnService.prepare failed", throwable) }
            return null
        }
        if (consentIntent == null) {
            val snapshot = vpnProbe.snapshotForPrepare(base, "ALREADY_AUTHORIZED", false)
            _state.update { it.copy(activeBusy = "Establishing temporary VPN interface") }
            publishVpn(snapshot)
            startVpnService()
            return null
        }
        publishVpn(vpnProbe.snapshotForPrepare(base, "CONSENT_REQUIRED", true))
        _state.update { it.copy(activeBusy = "Waiting for VPN authorization") }
        return consentIntent
    }

    fun onVpnConsentResult(granted: Boolean) {
        if (!vpnInFlight) return
        if (_state.value.restricted) {
            vpnInFlight = false
            _state.update {
                it.copy(
                    activeBusy = null,
                    message = "VPN test aborted: driving restrictions became active before the service started.",
                )
            }
            return
        }
        val report = _state.value.report ?: return
        val base = report.vpn
        if (!granted) {
            vpnInFlight = false
            val snapshot = vpnProbe.snapshotForConsent(base, granted = false, dialogRequested = true)
            publishVpn(snapshot)
            _state.update { it.copy(activeBusy = null) }
            viewModelScope.launch {
                val denied = vpnProbe.deniedResult("the system authorization dialog")
                appendVpnResult(denied, snapshot)
            }
            return
        }
        publishVpn(vpnProbe.snapshotForConsent(base, granted = true, dialogRequested = true))
        _state.update { it.copy(activeBusy = "Establishing temporary VPN interface") }
        startVpnService()
    }

    private fun startVpnService() {
        try {
            val component = app.startService(
                Intent(app, VpnProbeService::class.java).setAction(VpnProbeService.ACTION_ESTABLISH),
            )
            if (component == null) {
                throw IllegalStateException("startService returned null; the VPN service was not started.")
            }
        } catch (throwable: Throwable) {
            vpnInFlight = false
            _state.update { it.copy(activeBusy = null) }
            viewModelScope.launch { recordVpnFailure("Starting the VPN probe service failed", throwable) }
        }
    }

    private suspend fun onVpnEstablished(outcome: VpnEstablishOutcome) {
        val report = _state.value.report ?: return
        val denied = outcome.exceptionClass?.contains("SecurityException") == true
        val authorizationState = when {
            denied -> "DENIED"
            outcome.established -> "AUTHORIZED"
            else -> report.vpn?.authorizationState ?: "ERROR"
        }
        val snapshot = vpnProbe.snapshotForOutcome(report.vpn, outcome, authorizationState)
        val status = when {
            denied -> ResultStatus.DENIED
            outcome.established && outcome.closed == true -> ResultStatus.SUCCESS
            else -> ResultStatus.ERROR
        }
        val result = harness.run(
            activeMeta(
                id = "vpn.establish",
                module = ProbeModule.VPN,
                name = "Establish and close a temporary TUN interface",
                apiUsed = "VpnService.Builder.establish",
                timeoutMs = 10_000,
            ),
        ) {
            TestOutcome(
                status = status,
                summary = when {
                    denied -> "AAOS denied the temporary TUN interface even after authorization."
                    outcome.established && outcome.closed == true ->
                        "Temporary TUN interface established and closed in ${outcome.durationMs} ms; no routes, DNS, or app rules were configured."
                    outcome.established ->
                        "TUN interface was established, but close status was ${outcome.closed}."
                    else ->
                        "TUN interface was not established: ${outcome.exceptionClass ?: "unknown"} ${outcome.exceptionMessage ?: ""}".trim()
                },
                details = buildString {
                    appendLine("Authorization state: $authorizationState")
                    appendLine("Addresses: ${outcome.addressesAdded}")
                    appendLine("Routes: ${outcome.routesAdded} (intentionally empty)")
                    appendLine("DNS: ${outcome.dnsServers} (intentionally empty)")
                    appendLine("Allowed apps: ${outcome.allowedApplications} (intentionally empty)")
                    appendLine("Session: ${VpnProbeService.SESSION_NAME}")
                },
                exceptionClass = outcome.exceptionClass,
                exceptionMessage = outcome.exceptionMessage,
                measurements = mapOf(
                    "established" to outcome.established.toString(),
                    "closed" to (outcome.closed?.toString() ?: "unknown"),
                    "duration_ms" to outcome.durationMs.toString(),
                ),
            )
        }
        vpnInFlight = false
        _state.update { it.copy(activeBusy = null) }
        appendVpnResult(result, snapshot)
    }

    private suspend fun recordVpnFailure(stage: String, throwable: Throwable) {
        val snapshot = (_state.value.report?.vpn ?: vpnProbe.emptySnapshot()).copy(
            error = "${throwable.javaClass.simpleName}: ${throwable.message}",
        )
        val result = vpnProbe.failureResult(stage, throwable)
        appendVpnResult(result, snapshot)
    }

    private fun appendVpnResult(result: TestResult, snapshot: VpnCapabilitySnapshot) {
        val report = _state.value.report ?: return
        val data = report.toProbeData()
        publish(
            data.copy(
                vpn = snapshot,
                results = data.results + result,
            ),
        )
        _state.update { it.copy(lastActiveResult = result, message = result.summary) }
    }

    private fun publishVpn(snapshot: VpnCapabilitySnapshot) {
        val report = _state.value.report ?: return
        publish(report.toProbeData().copy(vpn = snapshot))
    }

    private fun carAppNotTested(): CarAppLibrarySnapshot = CarAppLibrarySnapshot(
        integrated = false,
        status = ResultStatus.NOT_TESTED,
        reason = "The Car App Library hardware path is not integrated in this build. " +
            "CarInfo and CarSensors require a host-bound CarAppService and template surface; " +
            "direct read-only VHAL access is the implemented path.",
        libraryVersion = null,
        observedAt = Instant.now().toString(),
    )

    private fun unavailableDriving(reason: String): DrivingStateSnapshot = DrivingStateSnapshot(
        available = false,
        requiresDistractionOptimization = null,
        activeRestrictions = null,
        restrictionNames = emptyList(),
        maxContentDepth = null,
        maxCumulativeContentItems = null,
        maxRestrictedStringLength = null,
        error = reason,
        note = "Active tests require an explicit parked confirmation because restrictions could not be read. " +
            "The app is not distraction-optimized.",
    )
}
