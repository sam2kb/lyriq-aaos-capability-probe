package com.sam2kb.lyriqprobe.core.probe

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.documentfile.provider.DocumentFile
import com.sam2kb.lyriqprobe.core.model.AppExternalDirInfo
import com.sam2kb.lyriqprobe.core.model.ProbeModule
import com.sam2kb.lyriqprobe.core.model.ProbeSection
import com.sam2kb.lyriqprobe.core.model.ResultStatus
import com.sam2kb.lyriqprobe.core.model.SelectedTreeInfo
import com.sam2kb.lyriqprobe.core.model.StorageSnapshot
import com.sam2kb.lyriqprobe.core.model.StorageStep
import com.sam2kb.lyriqprobe.core.model.StorageTestRecord
import com.sam2kb.lyriqprobe.core.model.StorageVolumeInfo
import com.sam2kb.lyriqprobe.core.model.TestOutcome
import com.sam2kb.lyriqprobe.core.model.TestResult
import com.sam2kb.lyriqprobe.core.runner.TestHarness
import com.sam2kb.lyriqprobe.core.runner.activeMeta
import com.sam2kb.lyriqprobe.core.runner.discoveryMeta
import com.sam2kb.lyriqprobe.core.storage.SelectedTreeStore
import com.sam2kb.lyriqprobe.core.util.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

data class StorageTestResult(
    val record: StorageTestRecord?,
    val result: TestResult,
)

/**
 * Storage discovery plus a user-initiated SAF create/write/read/delete proof.
 *
 * Because the test only ever deletes the file it created, no user file is touched. The
 * selected tree URI is persisted in app-private preferences, never copied into the report.
 */
class StorageProbe(
    private val context: Context,
    private val harness: TestHarness = TestHarness(),
) {
    private val storageManager: StorageManager? = context.getSystemService(StorageManager::class.java)
    private val treeStore = SelectedTreeStore(context)
    private var lastTest: StorageTestRecord? = null

    suspend fun scan(): ProbeSection<StorageSnapshot> {
        val volumes = try {
            storageManager?.storageVolumes.orEmpty().map(::toVolumeInfo)
        } catch (throwable: Throwable) {
            emptyList()
        }
        val externalDirs = context.getExternalFilesDirs(null)
            .filterNotNull()
            .mapIndexedNotNull { index, dir -> toExternalDir(index, dir) }
        val selected = treeStore.load()?.let(::inspectTree)
        val snapshot = StorageSnapshot(
            volumes = volumes,
            appExternalDirs = externalDirs,
            externalStorageState = Environment.getExternalStorageState(),
            selectedTree = selected,
            storageAccessFrameworkNote = "Storage Access Framework access is user-selected and independent of " +
                "getExternalFilesDirs(). A mounted USB mass-storage device may appear as a StorageVolume while " +
                "being absent from UsbManager, or vice versa.",
            testRecord = lastTest,
        )
        val result = harness.run(
            discoveryMeta(
                "storage.volumes",
                ProbeModule.STORAGE,
                "Storage volume discovery",
                "StorageManager.getStorageVolumes + getExternalFilesDirs",
            ),
        ) {
            TestOutcome(
                status = if (volumes.isEmpty() && externalDirs.isEmpty()) {
                    ResultStatus.NOT_EXPOSED
                } else {
                    ResultStatus.AVAILABLE
                },
                summary = "${volumes.size} volume(s), ${externalDirs.size} app-specific external dir(s), " +
                    "external state ${snapshot.externalStorageState}.",
                details = buildString {
                    appendLine("Storage volumes:")
                    volumes.forEach {
                        appendLine("- ${it.description}: state=${it.state}, removable=${it.removable}, emulated=${it.emulated}, path=${it.path}")
                    }
                    appendLine("App-specific external directories (writable without broad storage permission):")
                    externalDirs.forEach {
                        appendLine("- ${it.path}: write=${it.canWrite}, free=${formatBytes(it.availableBytes)}")
                    }
                    appendLine("SAF-selected tree: ${selected?.displayName ?: "(none)"}")
                },
                measurements = mapOf(
                    "volume_count" to volumes.size.toString(),
                    "app_external_dir_count" to externalDirs.size.toString(),
                ),
            )
        }
        return ProbeSection(snapshot, listOf(result))
    }

    suspend fun testTree(uri: Uri, canContinue: () -> Boolean = { true }): StorageTestResult = withContext(Dispatchers.IO) {
        var record: StorageTestRecord? = null
        val result = harness.run(
            activeMeta(
                id = "storage.write_read_delete",
                module = ProbeModule.STORAGE,
                name = "Create, write, read and delete a test file",
                apiUsed = "Storage Access Framework + DocumentFile",
                timeoutMs = 30_000,
            ),
        ) {
            if (!canContinue()) {
                return@run TestOutcome(
                    ResultStatus.NOT_TESTED,
                    "Storage test not started because the vehicle is no longer in a permitted state.",
                )
            }
            val started = Instant.now()
            val steps = mutableListOf<StorageStep>()
            var created: DocumentFile? = null
            var treeName = "selected folder"
            val fileName = "lyriq-probe-${fileTimestamp(started)}-${UUID.randomUUID().toString().take(8)}.txt"
            val payload = "LYRIQ_AAOS_CAPABILITY_PROBE\n" +
                "created=$started\n" +
                "nonce=${UUID.randomUUID()}\n" +
                "This file is temporary and is deleted by the probe.\n"
            var failure: Throwable? = null
            try {
                val tree = DocumentFile.fromTreeUri(context, uri)
                    ?: throw IllegalStateException("DocumentFile.fromTreeUri returned null.")
                treeName = tree.name ?: treeName
                steps += StorageStep("Resolve selected tree", true, "Resolved $treeName.")
                if (!tree.canWrite()) {
                    steps += StorageStep("Check write access", false, "The selected tree is not writable.")
                    throw SecurityException("Selected tree is not writable.")
                }
                steps += StorageStep("Check write access", true, "The selected tree is writable.")
                if (tree.findFile(fileName) != null) {
                    throw IllegalStateException("Refusing to continue: a file named $fileName already exists.")
                }
                created = tree.createFile("text/plain", fileName)
                    ?: throw IllegalStateException("DocumentFile.createFile returned null.")
                steps += StorageStep(
                    "Create uniquely named test file",
                    true,
                    "Created $fileName (${created.uri}).",
                )
                val output = context.contentResolver.openOutputStream(created.uri, "wt")
                    ?: throw IllegalStateException("openOutputStream returned null.")
                output.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(payload)
                    writer.flush()
                }
                steps += StorageStep("Write and flush known bytes", true, "Wrote ${payload.toByteArray().size} bytes.")
                val readBack = context.contentResolver.openInputStream(created.uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    ?: throw IllegalStateException("openInputStream returned null.")
                val hashesMatch = sha256(payload) == sha256(readBack)
                steps += StorageStep(
                    "Read back and verify content",
                    readBack == payload && hashesMatch,
                    "read=${readBack.toByteArray().size} bytes, sha256_match=$hashesMatch.",
                )
                if (readBack != payload || !hashesMatch) {
                    throw IllegalStateException("Read-back content did not match the written content.")
                }
                val deleted = created.delete()
                steps += StorageStep(
                    "Delete the test file",
                    deleted,
                    if (deleted) "Deleted $fileName." else "DocumentFile.delete returned false for $fileName.",
                )
                if (!deleted) throw IllegalStateException("Test file could not be deleted.")
                created = null
                TestOutcome(
                    status = ResultStatus.SUCCESS,
                    summary = "SAF create/write/read/delete verification passed in $treeName.",
                    details = steps.joinToString("\n") { "${if (it.success) "PASS" else "FAIL"} ${it.name}: ${it.detail}" },
                    measurements = mapOf(
                        "file_name" to fileName,
                        "bytes" to payload.toByteArray().size.toString(),
                        "sha256_match" to "true",
                    ),
                )
            } catch (throwable: Throwable) {
                failure = throwable
                val status = if (throwable is SecurityException) ResultStatus.DENIED else ResultStatus.ERROR
                TestOutcome(
                    status = status,
                    summary = "Storage test failed on $treeName: ${throwable.javaClass.simpleName}: ${throwable.message}",
                    details = steps.joinToString("\n") { "${if (it.success) "PASS" else "FAIL"} ${it.name}: ${it.detail}" },
                    exceptionClass = throwable.javaClass.name,
                    exceptionMessage = throwable.message,
                )
            } finally {
                created?.let { file ->
                    val cleanup = runCatching { file.delete() }.getOrDefault(false)
                    steps += StorageStep(
                        "Cleanup attempt for the probe's own test file",
                        cleanup,
                        if (cleanup) "Removed the temporary test file." else "Cleanup failed; the leftover file is named $fileName and was created by this probe.",
                    )
                }
                record = StorageTestRecord(
                    displayName = treeName,
                    success = failure == null && steps.all { it.success },
                    steps = steps.toList(),
                    startedAt = started.toString(),
                    completedAt = Instant.now().toString(),
                    exceptionClass = failure?.javaClass?.name,
                    exceptionMessage = failure?.message,
                )
            }
        }
        lastTest = record
        StorageTestResult(record, result)
    }

    fun clearSelectedTree() {
        treeStore.clear()
    }

    fun saveSelectedTree(uri: Uri) {
        treeStore.save(uri)
    }

    fun selectedTreeUri(): Uri? = treeStore.load()

    fun inspectSelectedTree(uri: Uri): SelectedTreeInfo = inspectTree(uri)

    private fun inspectTree(uri: Uri): SelectedTreeInfo {
        val tree = runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull()
        val persisted = context.contentResolver.persistedUriPermissions
        val read = persisted.any { it.uri == uri && it.isReadPermission }
        val write = persisted.any { it.uri == uri && it.isWritePermission }
        return SelectedTreeInfo(
            displayName = tree?.name ?: "(unnamed tree)",
            canRead = tree?.canRead() == true,
            canWrite = tree?.canWrite() == true,
            readPersisted = read,
            writePersisted = write,
            childCount = runCatching { tree?.listFiles()?.size }.getOrNull(),
            error = if (tree == null) "The persisted tree URI could not be reopened." else null,
        )
    }

    private fun toVolumeInfo(volume: StorageVolume): StorageVolumeInfo {
        val directory = if (Build.VERSION.SDK_INT >= 30) {
            runCatching { volume.directory }.getOrNull()
        } else {
            null
        }
        val stat = directory?.let { runCatching { StatFs(it.absolutePath) }.getOrNull() }
        return StorageVolumeInfo(
            description = runCatching { volume.getDescription(context) }.getOrDefault("Storage volume"),
            state = volume.state,
            mounted = volume.state == Environment.MEDIA_MOUNTED,
            removable = volume.isRemovable,
            emulated = volume.isEmulated,
            primary = volume.isPrimary,
            mediaStoreVolumeName = volume.mediaStoreVolumeName?.let(::sanitizeVolumeToken),
            path = directory?.absolutePath?.let(::sanitizePath),
            totalBytes = stat?.let { it.blockCountLong * it.blockSizeLong },
            availableBytes = stat?.let { it.availableBlocksLong * it.blockSizeLong },
            accessError = if (directory == null) "StorageVolume.getDirectory() unavailable or denied." else null,
        )
    }

    private fun toExternalDir(index: Int, dir: File): AppExternalDirInfo? {
        if (!dir.exists()) return null
        val stat = runCatching { StatFs(dir.absolutePath) }.getOrNull()
        return AppExternalDirInfo(
            label = if (index == 0) "Primary external app dir" else "External app dir ${index + 1}",
            path = sanitizePath(dir.absolutePath),
            canRead = dir.canRead(),
            canWrite = dir.canWrite(),
            removable = runCatching { Environment.isExternalStorageRemovable(dir) }.getOrDefault(false),
            emulated = runCatching { Environment.isExternalStorageEmulated(dir) }.getOrDefault(false),
            totalBytes = stat?.let { it.blockCountLong * it.blockSizeLong },
            availableBytes = stat?.let { it.availableBlocksLong * it.blockSizeLong },
        )
    }

    private fun sanitizePath(path: String): String =
        SANITIZE_VOLUME.replace(path, "/[VOLUME]")

    private fun sanitizeVolumeToken(value: String): String =
        if (VOLUME_PATTERN.matches(value)) "[VOLUME]" else value

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun fileTimestamp(instant: Instant): String =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(instant)

    private companion object {
        val VOLUME_PATTERN = Regex("^[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}$")
        val SANITIZE_VOLUME = Regex("(?i)/[0-9a-f]{4}-[0-9a-f]{4}(?=/|$)")
    }
}
