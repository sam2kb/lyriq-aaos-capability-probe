package com.sam2kb.lyriqprobe.core.car

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyConfig
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.sam2kb.lyriqprobe.core.model.DrivingStateSnapshot

/**
 * Owns one Car connection for a scan session. All operations are read-only.
 *
 * Car.createCar() is called from a background dispatcher by the caller. The class must
 * not be used to write vehicle properties; no setter methods are exposed.
 */
class CarAccess private constructor(
    val context: Context,
    val car: Car?,
    val creationError: String?,
) {
    val connected: Boolean
        get() = car?.isConnected == true

    val carApiVersion: String?
        get() = runCatching { Car.getCarVersion()?.toString() }.getOrNull()

    val platformVersion: String?
        get() = runCatching { Car.getPlatformVersion()?.toString() }.getOrNull()

    fun propertyManager(): CarPropertyManager? = manager(Car.PROPERTY_SERVICE)

    fun uxRestrictionManager(): CarUxRestrictionsManager? = manager(Car.CAR_UX_RESTRICTION_SERVICE)

    private inline fun <reified T : Any> manager(serviceName: String): T? = try {
        car?.getCarManager(serviceName) as? T
    } catch (throwable: Throwable) {
        Log.w(TAG, "Car manager $serviceName unavailable: ${throwable.javaClass.simpleName}")
        null
    }

    fun currentDrivingState(): DrivingStateSnapshot {
        val manager = uxRestrictionManager()
            ?: return DrivingStateSnapshot(
                available = false,
                requiresDistractionOptimization = null,
                activeRestrictions = null,
                restrictionNames = emptyList(),
                maxContentDepth = null,
                maxCumulativeContentItems = null,
                maxRestrictedStringLength = null,
                error = "CarUxRestrictionsManager is unavailable.",
                note = "Advanced tests remain available only because restrictions could not be read; verify parked state manually.",
            )
        return try {
            val restrictions = manager.currentCarUxRestrictions
                ?: return unavailableDrivingState("CarUxRestrictionsManager returned null.")
            restrictions.toSnapshot()
        } catch (throwable: Throwable) {
            unavailableDrivingState(
                "${throwable.javaClass.simpleName}: ${throwable.message ?: "no message"}",
            )
        }
    }

    fun registerUxListener(listener: (DrivingStateSnapshot) -> Unit): Boolean {
        val manager = uxRestrictionManager() ?: return false
        return try {
            manager.registerListener { restrictions -> listener(restrictions.toSnapshot()) }
            true
        } catch (throwable: Throwable) {
            Log.w(TAG, "Unable to register UX listener: ${throwable.javaClass.simpleName}")
            false
        }
    }

    fun unregisterUxListener() {
        runCatching { uxRestrictionManager()?.unregisterListener() }
    }

    fun close() {
        unregisterUxListener()
        runCatching { car?.disconnect() }
    }

    companion object {
        private const val TAG = "CarAccess"

        fun open(context: Context): CarAccess {
            if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
                return CarAccess(context, null, "android.hardware.type.automotive is not present.")
            }
            return try {
                CarAccess(context, Car.createCar(context), null)
            } catch (throwable: Throwable) {
                CarAccess(
                    context,
                    null,
                    "${throwable.javaClass.name}: ${throwable.message ?: "no message"}",
                )
            }
        }
    }
}

fun CarUxRestrictions.toSnapshot(): DrivingStateSnapshot {
    val names = buildList {
        if (activeRestrictions and CarUxRestrictions.UX_RESTRICTIONS_NO_DIALPAD != 0) add("NO_DIALPAD")
        if (activeRestrictions and CarUxRestrictions.UX_RESTRICTIONS_NO_FILTERING != 0) add("NO_FILTERING")
        if (activeRestrictions and CarUxRestrictions.UX_RESTRICTIONS_LIMIT_CONTENT != 0) add("LIMIT_CONTENT")
        if (activeRestrictions and CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD != 0) add("NO_KEYBOARD")
        if (activeRestrictions and CarUxRestrictions.UX_RESTRICTIONS_NO_VOICE_TRANSCRIPTION != 0) add("NO_VOICE_TRANSCRIPTION")
        if (activeRestrictions and CarUxRestrictions.UX_RESTRICTIONS_LIMIT_STRING_LENGTH != 0) add("LIMIT_STRING_LENGTH")
        if (activeRestrictions and CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP != 0) add("NO_SETUP")
        if (activeRestrictions and CarUxRestrictions.UX_RESTRICTIONS_NO_TEXT_MESSAGE != 0) add("NO_TEXT_MESSAGE")
        if (activeRestrictions and CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO != 0) add("NO_VIDEO")
        if (activeRestrictions and CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED != 0) add("FULLY_RESTRICTED")
    }
    return DrivingStateSnapshot(
        available = true,
        requiresDistractionOptimization = isRequiresDistractionOptimization,
        activeRestrictions = activeRestrictions,
        restrictionNames = names,
        maxContentDepth = maxContentDepth,
        maxCumulativeContentItems = maxCumulativeContentItems,
        maxRestrictedStringLength = maxRestrictedStringLength,
        error = null,
        note = if (isRequiresDistractionOptimization) {
            "AAOS requires distraction optimization; interactive diagnostics are disabled."
        } else {
            "No distraction-optimization requirement is active."
        },
    )
}

private fun unavailableDrivingState(error: String) = DrivingStateSnapshot(
    available = false,
    requiresDistractionOptimization = null,
    activeRestrictions = null,
    restrictionNames = emptyList(),
    maxContentDepth = null,
    maxCumulativeContentItems = null,
    maxRestrictedStringLength = null,
    error = error,
    note = "Advanced tests remain available only because restrictions could not be read; verify parked state manually.",
)

/**
 * Snapshot of the managers available from this Car connection, used by the system probe.
 */
data class CarManagerPresence(
    val propertyService: Boolean,
    val uxRestrictionService: Boolean,
    val infoService: Boolean,
)

fun CarAccess.managerPresence(): CarManagerPresence = CarManagerPresence(
    propertyService = propertyManager() != null,
    uxRestrictionService = uxRestrictionManager() != null,
    infoService = runCatching { car?.getCarManager(Car.INFO_SERVICE) != null }.getOrDefault(false),
)

/**
 * Exposed only for diagnostics. The probe never calls any setter on this list.
 */
fun CarPropertyConfig<*>.readableAccessLabel(): String = when (access) {
    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ -> "READ"
    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE -> "READ_WRITE"
    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE -> "WRITE"
    else -> "NONE"
}
