package com.limelight.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.preference.PreferenceManager
import com.limelight.binding.input.advance_setting.config.PageConfigController
import com.limelight.binding.input.advance_setting.sqlite.SuperConfigDatabaseHelper
import com.limelight.computers.ComputerDatabaseManager
import com.limelight.preferences.BackgroundSource
import com.limelight.nvstream.http.ComputerDetails
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.security.KeyStore
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Arrays
import java.util.Base64 as JavaBase64
import java.util.TreeSet
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class ConfigurationSyncManager(private val context: Context) {

    data class PackagePreview(
        val schemaVersion: Int,
        val appVersionCode: Long,
        val appVersionName: String,
        val exportedAt: Long,
        val defaultPreferenceCount: Int,
        val appLastSettingsCount: Int,
        val customResolutionsCount: Int,
        val sceneConfigsCount: Int,
        val appViewPreferenceCount: Int,
        val hiddenAppsCount: Int,
        val crownProfilesCount: Int,
        val pairedComputersCount: Int,
        val hasPairingIdentity: Boolean,
        val isEncrypted: Boolean = false
    ) {
        val totalItems: Int
            get() = defaultPreferenceCount +
                    appLastSettingsCount +
                    customResolutionsCount +
                    sceneConfigsCount +
                    appViewPreferenceCount +
                    hiddenAppsCount +
                    crownProfilesCount +
                    pairedComputersCount +
                    if (hasPairingIdentity) 1 else 0
    }

    data class ImportResult(
        val defaultPreferencesImported: Int,
        val appLastSettingsImported: Int,
        val customResolutionsImported: Int,
        val sceneConfigsImported: Int,
        val appViewPreferencesImported: Int,
        val hiddenAppsImported: Int,
        val crownProfilesImported: Int,
        val crownProfilesFailed: Int,
        val pairingItemsImported: Int,
        val pairingItemsFailed: Int
    ) {
        val totalImported: Int
            get() = defaultPreferencesImported +
                    appLastSettingsImported +
                    customResolutionsImported +
                    sceneConfigsImported +
                    appViewPreferencesImported +
                    hiddenAppsImported +
                    crownProfilesImported +
                    pairingItemsImported
    }

    data class LocalSnapshotInfo(
        val file: File,
        val exists: Boolean,
        val updatedAt: Long,
        val sizeBytes: Long
    )

    data class ExternalSnapshotInfo(
        val exists: Boolean,
        val updatedAt: Long,
        val sizeBytes: Long,
        val displayName: String
    )

    data class SnapshotWriteResult(
        val local: LocalSnapshotInfo,
        val external: ExternalSnapshotInfo?
    )

    data class AutoSyncResult(
        val enabled: Boolean,
        val readExternal: Boolean,
        val appliedMergedPackage: Boolean,
        val wroteExternal: Boolean,
        val contentHash: String,
        val errorMessage: String? = null,
        val pairingItemsImported: Int = 0,
        val pairingItemsFailed: Int = 0
    )

    data class SyncStatusInfo(
        val completedAt: Long,
        val success: Boolean,
        val readExternal: Boolean,
        val appliedMergedPackage: Boolean,
        val wroteExternal: Boolean,
        val errorMessage: String
    ) {
        val hasCompletedSync: Boolean
            get() = completedAt > 0L
    }

    private data class ExternalDocument(
        val uri: Uri,
        val displayName: String
    )

    private data class ExternalSyncPackageRecord(
        val syncPackage: String,
        val requiresEncryptionRewrite: Boolean
    )

    private data class PairingImportResult(
        val imported: Int,
        val failed: Int
    )

    private data class ProcessSyncLock(
        private val randomAccessFile: RandomAccessFile,
        private val lock: FileLock
    ) : Closeable {
        override fun close() {
            runCatching { lock.release() }
            runCatching { randomAccessFile.close() }
        }
    }

    @Throws(JSONException::class)
    fun exportSyncPackage(): String {
        val sections = JSONObject()
            .put(
                SECTION_DEFAULT_PREFERENCES,
                JSONObject().put(
                    KEY_VALUES,
                    encodePreferences(
                        SECTION_DEFAULT_PREFERENCES,
                        PreferenceManager.getDefaultSharedPreferences(context),
                        PORTABLE_DEFAULT_PREF_KEYS
                    )
                )
            )
            .put(
                SECTION_APP_LAST_SETTINGS,
                JSONObject().put(
                    KEY_VALUES,
                    encodePreferences(
                        SECTION_APP_LAST_SETTINGS,
                        context.getSharedPreferences(APP_LAST_SETTINGS_PREFS, Context.MODE_PRIVATE),
                        null
                    )
                )
            )
            .put(
                SECTION_CUSTOM_RESOLUTIONS,
                JSONObject().put(
                    KEY_VALUES,
                    encodePreferences(
                        SECTION_CUSTOM_RESOLUTIONS,
                        context.getSharedPreferences(CUSTOM_RESOLUTIONS_PREFS, Context.MODE_PRIVATE),
                        null
                    )
                )
            )
            .put(
                SECTION_SCENE_CONFIGS,
                JSONObject().put(
                    KEY_VALUES,
                    encodePreferences(
                        SECTION_SCENE_CONFIGS,
                        context.getSharedPreferences(SCENE_CONFIGS_PREFS, Context.MODE_PRIVATE),
                        null
                    )
                )
            )
            .put(
                SECTION_APP_VIEW_PREFERENCES,
                JSONObject().put(
                    KEY_VALUES,
                    encodePreferences(
                        SECTION_APP_VIEW_PREFERENCES,
                        context.getSharedPreferences(APP_VIEW_PREFS, Context.MODE_PRIVATE),
                        APP_VIEW_PREF_KEYS
                    )
                )
            )
            .put(
                SECTION_HIDDEN_APPS,
                JSONObject().put(
                    KEY_VALUES,
                    encodePreferences(
                        SECTION_HIDDEN_APPS,
                        context.getSharedPreferences(HIDDEN_APPS_PREFS, Context.MODE_PRIVATE),
                        null
                    )
                )
            )
            .put(SECTION_CROWN_PROFILES, exportCrownProfiles())
            .put(SECTION_PAIRING, exportPairingState())

        return buildSyncPackage(sections)
    }

    @Throws(JSONException::class)
    fun exportEncryptedSyncPackage(password: String): String {
        return encryptSyncPackageCore(exportSyncPackage(), password)
    }

    @Throws(JSONException::class)
    fun exportEncryptedSyncPackageWithSavedPassword(): String? {
        val password = loadExternalSyncPassword(context) ?: return null
        return exportEncryptedSyncPackage(password)
    }

    private fun buildSyncPackage(sections: JSONObject): String {
        return buildSyncPackageCore(sections, currentSyncPackageMetadata())
    }

    private fun currentSyncPackageMetadata(): SyncPackageMetadata {
        return SyncPackageMetadata(
            deviceId = deviceId(),
            backupDeviceKey = backupDeviceKey(),
            packageName = context.packageName,
            appVersionCode = appVersionCode(),
            appVersionName = appVersionName()
        )
    }

    @Throws(JSONException::class)
    fun previewSyncPackage(syncPackage: String, password: String? = null): PackagePreview {
        val encrypted = isEncryptedSyncPackage(syncPackage)
        val root = parseAndValidateRoot(resolvePlainSyncPackage(syncPackage, password))
        val sections = root.optJSONObject(KEY_SECTIONS)
            ?: throw JSONException("Missing sections")

        return PackagePreview(
            schemaVersion = root.optInt(KEY_SCHEMA_VERSION, 0),
            appVersionCode = root.optLong(KEY_APP_VERSION_CODE, 0L),
            appVersionName = root.optString(KEY_APP_VERSION_NAME, ""),
            exportedAt = root.optLong(KEY_EXPORTED_AT, 0L),
            defaultPreferenceCount = countValues(
                valuesFromSection(sections.optJSONObject(SECTION_DEFAULT_PREFERENCES)),
                PORTABLE_DEFAULT_PREF_KEYS
            ),
            appLastSettingsCount = countValues(
                valuesFromSection(sections.optJSONObject(SECTION_APP_LAST_SETTINGS)),
                null
            ),
            customResolutionsCount = countValues(
                valuesFromSection(sections.optJSONObject(SECTION_CUSTOM_RESOLUTIONS)),
                null
            ),
            sceneConfigsCount = countValues(
                valuesFromSection(sections.optJSONObject(SECTION_SCENE_CONFIGS)),
                null
            ),
            appViewPreferenceCount = countValues(
                valuesFromSection(sections.optJSONObject(SECTION_APP_VIEW_PREFERENCES)),
                APP_VIEW_PREF_KEYS
            ),
            hiddenAppsCount = countValues(
                valuesFromSection(sections.optJSONObject(SECTION_HIDDEN_APPS)),
                null
            ),
            crownProfilesCount = sections.optJSONArray(SECTION_CROWN_PROFILES)?.length() ?: 0,
            pairedComputersCount = countPairedComputers(sections.optJSONObject(SECTION_PAIRING)),
            hasPairingIdentity = hasPairingIdentity(sections.optJSONObject(SECTION_PAIRING)),
            isEncrypted = encrypted
        )
    }

    @Throws(JSONException::class)
    fun importSyncPackage(syncPackage: String, password: String? = null): ImportResult {
        val root = parseAndValidateRoot(resolvePlainSyncPackage(syncPackage, password))
        ensureBackupMatchesThisDevice(root)
        val sections = root.optJSONObject(KEY_SECTIONS)
            ?: throw JSONException("Missing sections")

        val defaultPreferencesImported = applyPreferences(
            SECTION_DEFAULT_PREFERENCES,
            PreferenceManager.getDefaultSharedPreferences(context),
            valuesFromSection(sections.optJSONObject(SECTION_DEFAULT_PREFERENCES)),
            PORTABLE_DEFAULT_PREF_KEYS
        )
        val appLastSettingsImported = applyPreferences(
            SECTION_APP_LAST_SETTINGS,
            context.getSharedPreferences(APP_LAST_SETTINGS_PREFS, Context.MODE_PRIVATE),
            valuesFromSection(sections.optJSONObject(SECTION_APP_LAST_SETTINGS)),
            null
        )
        val customResolutionsImported = applyPreferences(
            SECTION_CUSTOM_RESOLUTIONS,
            context.getSharedPreferences(CUSTOM_RESOLUTIONS_PREFS, Context.MODE_PRIVATE),
            valuesFromSection(sections.optJSONObject(SECTION_CUSTOM_RESOLUTIONS)),
            null
        )
        val sceneConfigsImported = applyPreferences(
            SECTION_SCENE_CONFIGS,
            context.getSharedPreferences(SCENE_CONFIGS_PREFS, Context.MODE_PRIVATE),
            valuesFromSection(sections.optJSONObject(SECTION_SCENE_CONFIGS)),
            null
        )
        val appViewPreferencesImported = applyPreferences(
            SECTION_APP_VIEW_PREFERENCES,
            context.getSharedPreferences(APP_VIEW_PREFS, Context.MODE_PRIVATE),
            valuesFromSection(sections.optJSONObject(SECTION_APP_VIEW_PREFERENCES)),
            APP_VIEW_PREF_KEYS
        )
        val hiddenAppsImported = applyPreferences(
            SECTION_HIDDEN_APPS,
            context.getSharedPreferences(HIDDEN_APPS_PREFS, Context.MODE_PRIVATE),
            valuesFromSection(sections.optJSONObject(SECTION_HIDDEN_APPS)),
            null
        )

        val crownResult = importCrownProfiles(sections.optJSONArray(SECTION_CROWN_PROFILES))
        val pairingResult = importPairingState(sections.optJSONObject(SECTION_PAIRING))

        if (defaultPreferencesImported > 0) {
            context.sendBroadcast(Intent(BackgroundSource.ACTION_REFRESH))
        }

        return ImportResult(
            defaultPreferencesImported = defaultPreferencesImported,
            appLastSettingsImported = appLastSettingsImported,
            customResolutionsImported = customResolutionsImported,
            sceneConfigsImported = sceneConfigsImported,
            appViewPreferencesImported = appViewPreferencesImported,
            hiddenAppsImported = hiddenAppsImported,
            crownProfilesImported = crownResult.first,
            crownProfilesFailed = crownResult.second,
            pairingItemsImported = pairingResult.imported,
            pairingItemsFailed = pairingResult.failed
        )
    }

    @Throws(JSONException::class)
    fun decryptEncryptedSyncPackage(syncPackage: String, password: String): String {
        return decryptSyncPackageCore(syncPackage, password)
    }

    @Throws(JSONException::class)
    fun decryptEncryptedSyncPackageWithSavedPassword(syncPackage: String): String? {
        val password = loadExternalSyncPassword(context) ?: return null
        return decryptEncryptedSyncPackage(syncPackage, password)
    }

    fun saveExternalSyncPassword(password: String): Boolean {
        return saveExternalSyncPassword(context, password)
    }

    fun hasExternalSyncPassword(): Boolean {
        return hasExternalSyncPassword(context)
    }

    @Throws(JSONException::class)
    fun mergeSyncPackages(localPackage: String, externalPackage: String): String {
        return mergeSyncPackagePairCore(localPackage, externalPackage, currentSyncPackageMetadata())
    }

    @Throws(JSONException::class)
    fun syncContentHash(syncPackage: String): String {
        return syncContentHashCore(syncPackage)
    }

    @Throws(JSONException::class)
    fun mergeSyncPackages(syncPackages: List<String>): String {
        return mergeSyncPackageListCore(syncPackages, currentSyncPackageMetadata())
    }

    fun synchronizeWithExternalSnapshot(): AutoSyncResult {
        return trySynchronizeWithExternalSnapshot()
            ?: AutoSyncResult(
                enabled = true,
                readExternal = false,
                appliedMergedPackage = false,
                wroteExternal = false,
                contentHash = "",
                errorMessage = "Another configuration sync is already running"
            )
    }

    internal fun trySynchronizeWithExternalSnapshot(): AutoSyncResult? {
        if (!isExternalSnapshotEnabled(context)) {
            return AutoSyncResult(
                enabled = false,
                readExternal = false,
                appliedMergedPackage = false,
                wroteExternal = false,
                contentHash = ""
            )
        }

        val processLock = try {
            acquireProcessSyncLock()
        } catch (e: Exception) {
            return AutoSyncResult(
                enabled = true,
                readExternal = false,
                appliedMergedPackage = false,
                wroteExternal = false,
                contentHash = "",
                errorMessage = e.message ?: e.javaClass.simpleName
            )
        } ?: return null

        processLock.use {
            return synchronizeWithExternalSnapshotLocked()
        }
    }

    private fun synchronizeWithExternalSnapshotLocked(): AutoSyncResult {
        return try {
            val localPackage = exportSyncPackage()
            val localHash = syncContentHash(localPackage)
            val externalPackages = readExternalSyncPackageRecords()

            if (externalPackages.isEmpty()) {
                writeLocalSnapshot(localPackage)
                writeExternalSnapshot(localPackage)
                rememberExternalSync(localPackage, localHash)
                return AutoSyncResult(
                    enabled = true,
                    readExternal = false,
                    appliedMergedPackage = false,
                    wroteExternal = true,
                    contentHash = localHash
                )
            }

            val externalRequiresEncryptionRewrite = externalPackages.any { it.requiresEncryptionRewrite }
            val externalPackage = mergeSyncPackages(externalPackages.map { it.syncPackage })
            val externalHash = syncContentHash(externalPackage)
            if (!hasKnownExternalSyncBaseline()) {
                val importResult = importSyncPackage(externalPackage)
                val adoptedPackage = exportSyncPackage()
                val adoptedHash = syncContentHash(adoptedPackage)
                writeLocalSnapshot(adoptedPackage)
                writeExternalSnapshot(adoptedPackage)
                rememberExternalSync(adoptedPackage, adoptedHash)
                return AutoSyncResult(
                    enabled = true,
                    readExternal = true,
                    appliedMergedPackage = true,
                    wroteExternal = true,
                    contentHash = adoptedHash,
                    pairingItemsImported = importResult.pairingItemsImported,
                    pairingItemsFailed = importResult.pairingItemsFailed
                )
            }

            if (externalHash == localHash) {
                writeLocalSnapshot(localPackage)
                if (externalRequiresEncryptionRewrite) {
                    writeExternalSnapshot(localPackage)
                }
                rememberExternalSync(localPackage, localHash)
                return AutoSyncResult(
                    enabled = true,
                    readExternal = true,
                    appliedMergedPackage = false,
                    wroteExternal = externalRequiresEncryptionRewrite,
                    contentHash = localHash
                )
            }

            val mergedPackage = mergeSyncPackages(localPackage, externalPackage)
            val mergedHash = syncContentHash(mergedPackage)
            val applyMerged = mergedHash != localHash
            var pairingItemsImported = 0
            var pairingItemsFailed = 0
            if (applyMerged) {
                val importResult = importSyncPackage(mergedPackage)
                pairingItemsImported = importResult.pairingItemsImported
                pairingItemsFailed = importResult.pairingItemsFailed
            }
            writeLocalSnapshot(mergedPackage)

            val writeExternal = shouldWriteExternalSnapshotCore(
                mergedHash,
                externalHash,
                externalRequiresEncryptionRewrite
            )
            if (writeExternal) {
                writeExternalSnapshot(mergedPackage)
            }
            rememberExternalSync(mergedPackage, mergedHash)

            AutoSyncResult(
                enabled = true,
                readExternal = true,
                appliedMergedPackage = applyMerged,
                wroteExternal = writeExternal,
                contentHash = mergedHash,
                pairingItemsImported = pairingItemsImported,
                pairingItemsFailed = pairingItemsFailed
            )
        } catch (e: Exception) {
            AutoSyncResult(
                enabled = true,
                readExternal = false,
                appliedMergedPackage = false,
                wroteExternal = false,
                contentHash = "",
                errorMessage = e.message ?: e.javaClass.simpleName
            )
        }
    }

    private fun acquireProcessSyncLock(): ProcessSyncLock? {
        val lockDirectory = File(context.applicationContext.filesDir, SYNC_LOCK_DIRECTORY)
        if (!lockDirectory.exists() && !lockDirectory.mkdirs()) {
            throw IOException("Unable to create config sync lock directory: ${lockDirectory.absolutePath}")
        }

        val randomAccessFile = RandomAccessFile(File(lockDirectory, SYNC_LOCK_FILE_NAME), "rw")
        val lock = try {
            randomAccessFile.channel.tryLock()
        } catch (_: OverlappingFileLockException) {
            null
        }

        if (lock == null) {
            randomAccessFile.close()
            return null
        }
        return ProcessSyncLock(randomAccessFile, lock)
    }

    fun rememberAutoSyncResult(result: AutoSyncResult) {
        val errorMessage = result.errorMessage.orEmpty()
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putLong(PREF_LAST_BACKGROUND_SYNC_AT, System.currentTimeMillis())
            .putBoolean(PREF_LAST_BACKGROUND_SYNC_SUCCESS, errorMessage.isBlank())
            .putBoolean(PREF_LAST_BACKGROUND_SYNC_READ_EXTERNAL, result.readExternal)
            .putBoolean(PREF_LAST_BACKGROUND_SYNC_APPLIED, result.appliedMergedPackage)
            .putBoolean(PREF_LAST_BACKGROUND_SYNC_WROTE_EXTERNAL, result.wroteExternal)
            .putString(PREF_LAST_BACKGROUND_SYNC_ERROR, errorMessage)
            .apply()
    }

    fun rememberAutoSyncFailure(errorMessage: String) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putLong(PREF_LAST_BACKGROUND_SYNC_AT, System.currentTimeMillis())
            .putBoolean(PREF_LAST_BACKGROUND_SYNC_SUCCESS, false)
            .putBoolean(PREF_LAST_BACKGROUND_SYNC_READ_EXTERNAL, false)
            .putBoolean(PREF_LAST_BACKGROUND_SYNC_APPLIED, false)
            .putBoolean(PREF_LAST_BACKGROUND_SYNC_WROTE_EXTERNAL, false)
            .putString(PREF_LAST_BACKGROUND_SYNC_ERROR, errorMessage)
            .apply()
    }

    fun syncStatusInfo(): SyncStatusInfo {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return SyncStatusInfo(
            completedAt = prefs.getLong(PREF_LAST_BACKGROUND_SYNC_AT, 0L),
            success = prefs.getBoolean(PREF_LAST_BACKGROUND_SYNC_SUCCESS, false),
            readExternal = prefs.getBoolean(PREF_LAST_BACKGROUND_SYNC_READ_EXTERNAL, false),
            appliedMergedPackage = prefs.getBoolean(PREF_LAST_BACKGROUND_SYNC_APPLIED, false),
            wroteExternal = prefs.getBoolean(PREF_LAST_BACKGROUND_SYNC_WROTE_EXTERNAL, false),
            errorMessage = prefs.getString(PREF_LAST_BACKGROUND_SYNC_ERROR, "") ?: ""
        )
    }

    fun localSnapshotFile(): File {
        return File(File(context.filesDir, LOCAL_SNAPSHOT_DIRECTORY), LOCAL_SNAPSHOT_FILE_NAME)
    }

    fun localSnapshotInfo(): LocalSnapshotInfo {
        val file = localSnapshotFile()
        return LocalSnapshotInfo(
            file = file,
            exists = file.exists(),
            updatedAt = if (file.exists()) file.lastModified() else 0L,
            sizeBytes = if (file.exists()) file.length() else 0L
        )
    }

    @Throws(JSONException::class, IOException::class)
    fun writeLocalSnapshot(): LocalSnapshotInfo {
        return writeLocalSnapshot(exportSyncPackage())
    }

    @Throws(JSONException::class, IOException::class)
    fun writeConfiguredSnapshots(): SnapshotWriteResult {
        val syncPackage = exportSyncPackage()
        val contentHash = syncContentHash(syncPackage)
        val localSnapshot = writeLocalSnapshot(syncPackage)
        val externalSnapshot = if (isExternalSnapshotEnabled(context)) {
            writeExternalSnapshot(syncPackage).also {
                rememberExternalSync(syncPackage, contentHash)
            }
        } else {
            null
        }
        return SnapshotWriteResult(localSnapshot, externalSnapshot)
    }

    @Throws(IOException::class)
    fun writeExternalSnapshot(syncPackage: String): ExternalSnapshotInfo {
        val treeUri = externalSyncTreeUri(context)
            ?: throw IOException("No external sync directory selected")
        val externalSyncPackage = encryptedExternalSyncPackage(syncPackage)
        val treeDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        val snapshotUri = openOrCreateExternalDocument(
            treeUri,
            treeDocumentUri,
            DEFAULT_FILE_NAME
        )

        writeExternalDocumentText(snapshotUri, externalSyncPackage)

        val deviceSnapshotUri = openOrCreateExternalDocument(
            treeUri,
            treeDocumentUri,
            externalDeviceSnapshotFileName()
        )
        writeExternalDocumentText(deviceSnapshotUri, externalSyncPackage)

        val writtenAt = System.currentTimeMillis()
        val snapshotInfo = queryExternalSnapshotInfo(snapshotUri)
        return snapshotInfo.copy(
            exists = true,
            updatedAt = snapshotInfo.updatedAt.takeIf { it > 0L } ?: writtenAt
        )
    }

    fun externalSnapshotInfo(): ExternalSnapshotInfo {
        val treeUri = externalSyncTreeUri(context)
            ?: return ExternalSnapshotInfo(false, 0L, 0L, DEFAULT_FILE_NAME)
        val snapshotUri = findExternalSnapshotDocument(treeUri, DEFAULT_FILE_NAME)
            ?: return ExternalSnapshotInfo(false, 0L, 0L, DEFAULT_FILE_NAME)
        return queryExternalSnapshotInfo(snapshotUri)
    }

    @Throws(IOException::class)
    fun readExternalSnapshot(): String {
        return readExternalSyncPackages().firstOrNull()
            ?: throw IOException("No same-device external backup exists")
    }

    @Throws(IOException::class)
    fun readExternalSyncPackages(): List<String> {
        return readExternalSyncPackageRecords().map { it.syncPackage }
    }

    @Throws(IOException::class)
    private fun readExternalSyncPackageRecords(): List<ExternalSyncPackageRecord> {
        val treeUri = externalSyncTreeUri(context)
            ?: throw IOException("No external sync directory selected")
        val documents = queryExternalSnapshotDocuments(treeUri)
        if (documents.isEmpty()) return emptyList()

        val packages = LinkedHashMap<String, ExternalSyncPackageRecord>()
        val externalPassword = loadExternalSyncPassword(context)
        for (document in documents.sortedBy { it.displayName }) {
            val rawSyncPackage = runCatching { readExternalDocumentText(document.uri) }.getOrNull()
                ?: continue
            val encrypted = isEncryptedSyncPackage(rawSyncPackage)
            val syncPackage = try {
                resolvePlainSyncPackage(rawSyncPackage, externalPassword)
            } catch (e: Exception) {
                if (encrypted) {
                    throw IOException("Backup password is required to read encrypted external backup", e)
                }
                continue
            }
            if (!isBackupPackageForThisDevice(syncPackage)) continue
            val contentHash = runCatching { syncContentHash(syncPackage) }.getOrNull()
                ?: continue
            val existing = packages[contentHash]
            packages[contentHash] = ExternalSyncPackageRecord(
                syncPackage = syncPackage,
                requiresEncryptionRewrite = existing?.requiresEncryptionRewrite == true || !encrypted
            )
        }

        return packages.values
            .sortedWith(
                compareByDescending<ExternalSyncPackageRecord> { exportedAt(it.syncPackage) }
                    .thenByDescending { syncId(it.syncPackage) }
            )
            .take(1)
    }

    private fun encryptedExternalSyncPackage(syncPackage: String): String {
        val password = loadExternalSyncPassword(context)
            ?: throw IOException("Backup password is required to write encrypted external backup")
        return encryptSyncPackageCore(syncPackage, password)
    }

    @Throws(JSONException::class, IOException::class)
    private fun writeLocalSnapshot(syncPackage: String): LocalSnapshotInfo {
        val file = localSnapshotFile()
        val directory = file.parentFile
            ?: throw IOException("Missing local snapshot directory")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Unable to create local snapshot directory: ${directory.absolutePath}")
        }

        val tempFile = File(directory, "${file.name}.tmp")
        try {
            tempFile.writeText(syncPackage, Charsets.UTF_8)
            if (file.exists() && !file.delete()) {
                throw IOException("Unable to replace local snapshot: ${file.absolutePath}")
            }
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }

            val writtenAt = System.currentTimeMillis()
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putLong(PREF_LAST_LOCAL_SNAPSHOT_AT, writtenAt)
                .apply()

            return LocalSnapshotInfo(
                file = file,
                exists = true,
                updatedAt = file.lastModified().takeIf { it > 0L } ?: writtenAt,
                sizeBytes = file.length()
            )
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    private fun openOrCreateExternalDocument(
        treeUri: Uri,
        treeDocumentUri: Uri,
        displayName: String
    ): Uri {
        return findExternalSnapshotDocument(treeUri, displayName)
            ?: DocumentsContract.createDocument(
                context.contentResolver,
                treeDocumentUri,
                "application/json",
                displayName
            )
            ?: throw IOException("Unable to create external sync snapshot: $displayName")
    }

    private fun readExternalDocumentText(uri: Uri): String {
        return context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: throw IOException("Unable to read external sync snapshot")
    }

    private fun writeExternalDocumentText(uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri, "wt")
            ?.bufferedWriter(Charsets.UTF_8)
            ?.use { it.write(text) }
            ?: throw IOException("Unable to write external sync snapshot")
    }

    private fun findExternalSnapshotDocument(treeUri: Uri, displayName: String): Uri? {
        return queryExternalSnapshotDocuments(treeUri)
            .firstOrNull { it.displayName == displayName }
            ?.uri
    }

    private fun queryExternalSnapshotDocuments(treeUri: Uri): List<ExternalDocument> {
        return try {
            val childDocumentsUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )

            val documents = mutableListOf<ExternalDocument>()
            context.contentResolver.query(childDocumentsUri, projection, null, null, null)?.use { cursor ->
                val documentIdColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val displayNameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (displayNameColumn < 0 || documentIdColumn < 0) continue
                    val displayName = cursor.getString(displayNameColumn) ?: continue
                    if (isExternalSyncPackageFile(displayName)) {
                        documents += ExternalDocument(
                            uri = DocumentsContract.buildDocumentUriUsingTree(
                            treeUri,
                            cursor.getString(documentIdColumn)
                            ),
                            displayName = displayName
                        )
                    }
                }
            }

            documents
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun queryExternalSnapshotInfo(snapshotUri: Uri): ExternalSnapshotInfo {
        return try {
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE
            )

            context.contentResolver.query(snapshotUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val lastModifiedColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    val sizeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    return ExternalSnapshotInfo(
                        exists = true,
                        updatedAt = if (lastModifiedColumn >= 0) cursor.getLong(lastModifiedColumn) else 0L,
                        sizeBytes = if (sizeColumn >= 0) cursor.getLong(sizeColumn) else 0L,
                        displayName = if (displayNameColumn >= 0) {
                            cursor.getString(displayNameColumn) ?: DEFAULT_FILE_NAME
                        } else {
                            DEFAULT_FILE_NAME
                        }
                    )
                }
            }

            ExternalSnapshotInfo(false, 0L, 0L, DEFAULT_FILE_NAME)
        } catch (_: Exception) {
            ExternalSnapshotInfo(false, 0L, 0L, DEFAULT_FILE_NAME)
        }
    }

    private fun resolvePlainSyncPackage(syncPackage: String, password: String?): String {
        if (!isEncryptedSyncPackage(syncPackage)) return syncPackage
        val backupPassword = password?.takeIf { it.isNotBlank() }
            ?: throw JSONException("Backup password is required")
        return decryptSyncPackageCore(syncPackage, backupPassword)
    }

    private fun parseAndValidateRoot(syncPackage: String): JSONObject {
        val root = JSONObject(syncPackage)
        val schemaVersion = root.optInt(KEY_SCHEMA_VERSION, 0)

        if (schemaVersion < 1) {
            throw JSONException("Missing or invalid schema version")
        }
        if (schemaVersion > SUPPORTED_SCHEMA_VERSION) {
            throw JSONException("Unsupported schema version: $schemaVersion")
        }
        if (!root.has(KEY_SECTIONS)) {
            throw JSONException("Missing sections")
        }

        return root
    }

    private fun appVersionCode(): Long {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } catch (_: Exception) {
            0L
        }
    }

    private fun appVersionName(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun deviceId(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val existing = prefs.getString(PREF_DEVICE_ID, null)
            ?.takeIf { it.isNotBlank() }
        if (existing != null) return existing

        val generated = UUID.randomUUID().toString()
        prefs.edit()
            .putString(PREF_DEVICE_ID, generated)
            .apply()
        return generated
    }

    private fun backupDeviceKey(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
        val source = androidId ?: deviceId()
        return sha256Hex("$BACKUP_DEVICE_KEY_VERSION|${context.packageName}|$source".toByteArray(Charsets.UTF_8))
    }

    private fun externalDeviceSnapshotFileName(): String {
        return "$DEVICE_BACKUP_PREFIX${backupDeviceKey()}$DEVICE_SNAPSHOT_SUFFIX"
    }

    private fun isExternalSyncPackageFile(displayName: String): Boolean {
        return displayName == DEFAULT_FILE_NAME ||
                (displayName.startsWith(DEVICE_BACKUP_PREFIX) &&
                        displayName.endsWith(DEVICE_SNAPSHOT_SUFFIX)) ||
                (displayName.startsWith(DEVICE_SNAPSHOT_PREFIX) &&
                        displayName.endsWith(DEVICE_SNAPSHOT_SUFFIX))
    }

    private fun ensureBackupMatchesThisDevice(root: JSONObject) {
        val backupDeviceKey = root.optString(KEY_BACKUP_DEVICE_KEY).takeIf { it.isNotBlank() }
            ?: return
        if (backupDeviceKey != backupDeviceKey()) {
            throw JSONException("Backup belongs to a different device")
        }
    }

    private fun isBackupPackageForThisDevice(syncPackage: String): Boolean {
        val root = runCatching { JSONObject(syncPackage) }.getOrNull() ?: return false
        val packageBackupDeviceKey = root.optString(KEY_BACKUP_DEVICE_KEY).takeIf { it.isNotBlank() }
        if (packageBackupDeviceKey != null) {
            return packageBackupDeviceKey == backupDeviceKey()
        }

        val packageDeviceId = root.optString(KEY_DEVICE_ID).takeIf { it.isNotBlank() }
        return packageDeviceId == deviceId()
    }

    private fun exportedAt(syncPackage: String): Long {
        return runCatching { JSONObject(syncPackage).optLong(KEY_EXPORTED_AT, 0L) }.getOrDefault(0L)
    }

    private fun syncId(syncPackage: String): String {
        return runCatching { JSONObject(syncPackage).optString(KEY_SYNC_ID, "") }.getOrDefault("")
    }

    private fun rememberExternalSync(syncPackage: String, contentHash: String) {
        val root = runCatching { JSONObject(syncPackage) }.getOrNull()
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(PREF_LAST_EXTERNAL_SYNC_ID, root?.optString(KEY_SYNC_ID, "") ?: "")
            .putString(PREF_LAST_EXTERNAL_CONTENT_HASH, contentHash)
            .putLong(PREF_LAST_EXTERNAL_SYNC_AT, System.currentTimeMillis())
            .apply()
    }

    private fun hasKnownExternalSyncBaseline(): Boolean {
        return !PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_LAST_EXTERNAL_CONTENT_HASH, null)
            .isNullOrBlank()
    }

    private fun payloadHash(payload: String): String {
        return sha256Hex(payload.toByteArray(Charsets.UTF_8))
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun encodePreferences(
        sectionName: String,
        prefs: SharedPreferences,
        allowedKeys: Set<String>?
    ): JSONObject {
        val output = JSONObject()
        val currentValues = prefs.all
        val statePrefs = context.getSharedPreferences(SYNC_VALUE_STATE_PREFS, Context.MODE_PRIVATE)
        val stateEditor = statePrefs.edit()
        val now = System.currentTimeMillis()
        val localDeviceId = deviceId()
        val trackedKeysStateKey = trackedKeysStateKey(sectionName)
        val trackedKeys = TreeSet(
            statePrefs.getStringSet(trackedKeysStateKey, emptySet()).orEmpty()
        )
        val keys = TreeSet<String>()

        for (key in currentValues.keys) {
            if (allowedKeys == null || key in allowedKeys) {
                keys.add(key)
            }
        }
        for (key in trackedKeys) {
            if (allowedKeys == null || key in allowedKeys) {
                keys.add(key)
            }
        }

        for (key in keys) {
            if (allowedKeys != null && key !in allowedKeys) continue

            val encodedValue = if (currentValues.containsKey(key)) {
                encodePreferenceValue(currentValues[key])
            } else {
                deletedPreferenceValue(
                    sectionName,
                    key,
                    statePrefs,
                    stateEditor,
                    now,
                    localDeviceId
                )
            } ?: continue

            output.put(
                key,
                stampEncodedValue(
                    sectionName,
                    key,
                    encodedValue,
                    statePrefs,
                    stateEditor,
                    now,
                    localDeviceId
                )
            )
            trackedKeys.add(key)
        }
        stateEditor.putStringSet(trackedKeysStateKey, trackedKeys)
        stateEditor.apply()
        return output
    }

    private fun encodePreferenceValue(value: Any?): JSONObject? {
        return when (value) {
            is String -> typedValue(TYPE_STRING, value)
            is Boolean -> typedValue(TYPE_BOOLEAN, value)
            is Int -> typedValue(TYPE_INT, value)
            is Long -> typedValue(TYPE_LONG, value)
            is Float -> typedValue(TYPE_FLOAT, value.toDouble())
            is Set<*> -> {
                val values = value.filterIsInstance<String>().sorted()
                typedValue(TYPE_STRING_SET, JSONArray(values))
            }
            else -> null
        }
    }

    private fun typedValue(type: String, value: Any): JSONObject {
        return JSONObject()
            .put(KEY_TYPE, type)
            .put(KEY_VALUE, value)
    }

    private fun deletedPreferenceValue(
        sectionName: String,
        key: String,
        statePrefs: SharedPreferences,
        stateEditor: SharedPreferences.Editor,
        now: Long,
        localDeviceId: String
    ): JSONObject? {
        val statePrefix = valueStatePrefix(sectionName, key)
        val oldValueHash = statePrefs.getString("$statePrefix.hash", null) ?: return null
        val deletedValue = typedValue(TYPE_DELETED, JSONObject.NULL)
        val deletedHash = encodedValueHash(deletedValue)
        val updatedAt: Long
        val updatedBy: String

        if (oldValueHash == deletedHash) {
            updatedAt = statePrefs.getLong("$statePrefix.updatedAt", now)
            updatedBy = statePrefs.getString("$statePrefix.updatedBy", localDeviceId) ?: localDeviceId
        } else {
            updatedAt = now
            updatedBy = localDeviceId
            stateEditor.putString("$statePrefix.hash", deletedHash)
            stateEditor.putLong("$statePrefix.updatedAt", updatedAt)
            stateEditor.putString("$statePrefix.updatedBy", updatedBy)
        }

        return deletedValue
            .put(KEY_UPDATED_AT, updatedAt)
            .put(KEY_UPDATED_BY, updatedBy)
    }

    private fun stampEncodedValue(
        sectionName: String,
        key: String,
        encodedValue: JSONObject,
        statePrefs: SharedPreferences,
        stateEditor: SharedPreferences.Editor,
        now: Long,
        localDeviceId: String
    ): JSONObject {
        val valueHash = encodedValueHash(encodedValue)
        val statePrefix = valueStatePrefix(sectionName, key)
        val oldValueHash = statePrefs.getString("$statePrefix.hash", null)
        val updatedAt: Long
        val updatedBy: String

        if (oldValueHash == valueHash) {
            updatedAt = statePrefs.getLong("$statePrefix.updatedAt", now)
            updatedBy = statePrefs.getString("$statePrefix.updatedBy", localDeviceId) ?: localDeviceId
        } else {
            updatedAt = now
            updatedBy = localDeviceId
            stateEditor.putString("$statePrefix.hash", valueHash)
            stateEditor.putLong("$statePrefix.updatedAt", updatedAt)
            stateEditor.putString("$statePrefix.updatedBy", updatedBy)
        }

        return copyEncodedValue(encodedValue)
            .put(KEY_UPDATED_AT, updatedAt)
            .put(KEY_UPDATED_BY, updatedBy)
    }

    private fun rememberImportedValueState(sectionName: String, key: String, encodedValue: JSONObject) {
        val statePrefs = context.getSharedPreferences(SYNC_VALUE_STATE_PREFS, Context.MODE_PRIVATE)
        val updatedAt = encodedValue.optLong(KEY_UPDATED_AT, System.currentTimeMillis())
        val updatedBy = encodedValue.optString(KEY_UPDATED_BY, deviceId()).takeIf { it.isNotBlank() }
            ?: deviceId()
        val valueHash = encodedValueHash(encodedValue)
        val statePrefix = valueStatePrefix(sectionName, key)
        val trackedKeysStateKey = trackedKeysStateKey(sectionName)
        val trackedKeys = trackedKeysAfterImport(
            statePrefs.getStringSet(trackedKeysStateKey, emptySet()).orEmpty(),
            key
        )

        statePrefs.edit()
            .putString("$statePrefix.hash", valueHash)
            .putLong("$statePrefix.updatedAt", updatedAt)
            .putString("$statePrefix.updatedBy", updatedBy)
            .putStringSet(trackedKeysStateKey, trackedKeys)
            .apply()
    }

    private fun valueStatePrefix(sectionName: String, key: String): String {
        return "${sectionName}:${sha256Hex(key.toByteArray(Charsets.UTF_8))}"
    }

    private fun trackedKeysStateKey(sectionName: String): String {
        return "$sectionName.trackedKeys"
    }

    private fun copyEncodedValue(encodedValue: JSONObject): JSONObject {
        return JSONObject(encodedValue.toString())
    }

    private fun encodedValueHash(encodedValue: JSONObject): String {
        return sha256Hex(
            JSONObject()
                .put(KEY_TYPE, encodedValue.optString(KEY_TYPE))
                .put(KEY_VALUE, encodedValue.opt(KEY_VALUE))
                .toString()
                .toByteArray(Charsets.UTF_8)
        )
    }

    private fun valuesFromSection(section: JSONObject?): JSONObject? {
        return section?.optJSONObject(KEY_VALUES)
    }

    private fun normalizedSectionsForHash(sections: JSONObject): JSONObject {
        return JSONObject()
            .put(
                SECTION_DEFAULT_PREFERENCES,
                JSONObject().put(
                    KEY_VALUES,
                    mergeEncodedValues(
                        valuesFromSection(sections.optJSONObject(SECTION_DEFAULT_PREFERENCES)),
                        null,
                        PORTABLE_DEFAULT_PREF_KEYS
                    )
                )
            )
            .put(
                SECTION_APP_LAST_SETTINGS,
                JSONObject().put(
                    KEY_VALUES,
                    mergeEncodedValues(
                        valuesFromSection(sections.optJSONObject(SECTION_APP_LAST_SETTINGS)),
                        null,
                        null
                    )
                )
            )
            .put(
                SECTION_CUSTOM_RESOLUTIONS,
                JSONObject().put(
                    KEY_VALUES,
                    mergeEncodedValues(
                        valuesFromSection(sections.optJSONObject(SECTION_CUSTOM_RESOLUTIONS)),
                        null,
                        null
                    )
                )
            )
            .put(
                SECTION_SCENE_CONFIGS,
                JSONObject().put(
                    KEY_VALUES,
                    mergeEncodedValues(
                        valuesFromSection(sections.optJSONObject(SECTION_SCENE_CONFIGS)),
                        null,
                        null
                    )
                )
            )
            .put(
                SECTION_APP_VIEW_PREFERENCES,
                JSONObject().put(
                    KEY_VALUES,
                    mergeEncodedValues(
                        valuesFromSection(sections.optJSONObject(SECTION_APP_VIEW_PREFERENCES)),
                        null,
                        APP_VIEW_PREF_KEYS
                    )
                )
            )
            .put(
                SECTION_HIDDEN_APPS,
                JSONObject().put(
                    KEY_VALUES,
                    mergeEncodedValues(
                        valuesFromSection(sections.optJSONObject(SECTION_HIDDEN_APPS)),
                        null,
                        null
                    )
                )
            )
            .put(
                SECTION_CROWN_PROFILES,
                mergeCrownProfiles(sections.optJSONArray(SECTION_CROWN_PROFILES), null)
            )
    }

    private fun countValues(encodedValues: JSONObject?, allowedKeys: Set<String>?): Int {
        if (encodedValues == null) return 0

        var count = 0
        val keys = encodedValues.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (allowedKeys != null && key !in allowedKeys) continue
            if (encodedValues.optJSONObject(key) != null) {
                count++
            }
        }
        return count
    }

    private fun countPairedComputers(pairingSection: JSONObject?): Int {
        return pairingSection?.optJSONArray(KEY_PAIRING_COMPUTERS)?.length() ?: 0
    }

    private fun hasPairingIdentity(pairingSection: JSONObject?): Boolean {
        if (pairingSection == null) return false
        return pairingSection.optString(KEY_PAIRING_UNIQUE_ID).isNotBlank() ||
                pairingSection.optString(KEY_PAIRING_CLIENT_CERTIFICATE).isNotBlank() ||
                pairingSection.optString(KEY_PAIRING_CLIENT_PRIVATE_KEY).isNotBlank()
    }

    private fun mergeEncodedValues(
        externalValues: JSONObject?,
        localValues: JSONObject?,
        allowedKeys: Set<String>?
    ): JSONObject {
        val merged = JSONObject()
        val keys = TreeSet<String>()
        externalValues?.keys()?.forEach { keys.add(it) }
        localValues?.keys()?.forEach { keys.add(it) }

        for (key in keys) {
            if (allowedKeys != null && key !in allowedKeys) continue

            val externalValue = externalValues?.optJSONObject(key)
            val localValue = localValues?.optJSONObject(key)
            val mergedValue = mergeTypedValue(externalValue, localValue) ?: continue
            merged.put(key, mergedValue)
        }

        return merged
    }

    private fun mergeTypedValue(externalValue: JSONObject?, localValue: JSONObject?): JSONObject? {
        if (externalValue == null) return localValue
        if (localValue == null) return externalValue

        val externalType = externalValue.optString(KEY_TYPE)
        val localType = localValue.optString(KEY_TYPE)
        if (externalType == TYPE_STRING_SET && localType == TYPE_STRING_SET) {
            val merged = typedValue(
                    TYPE_STRING_SET,
                    mergeStringSetValues(
                            externalValue.optJSONArray(KEY_VALUE),
                            localValue.optJSONArray(KEY_VALUE)
                    )
            )
            val winner = chooseNewerEncodedValue(externalValue, localValue)
            return merged
                    .put(KEY_UPDATED_AT, maxOf(updatedAt(externalValue), updatedAt(localValue)))
                    .put(KEY_UPDATED_BY, winner.optString(KEY_UPDATED_BY, deviceId()))
        }

        return chooseNewerEncodedValue(externalValue, localValue)
    }

    private fun mergeStringSetValues(externalValues: JSONArray?, localValues: JSONArray?): JSONArray {
        val merged = TreeSet<String>()
        if (externalValues != null) {
            for (i in 0 until externalValues.length()) {
                merged.add(externalValues.optString(i))
            }
        }
        if (localValues != null) {
            for (i in 0 until localValues.length()) {
                merged.add(localValues.optString(i))
            }
        }
        return JSONArray(merged)
    }

    private fun chooseNewerEncodedValue(externalValue: JSONObject, localValue: JSONObject): JSONObject {
        val externalUpdatedAt = updatedAt(externalValue)
        val localUpdatedAt = updatedAt(localValue)
        return when {
            externalUpdatedAt > localUpdatedAt -> externalValue
            localUpdatedAt > externalUpdatedAt -> localValue
            externalValue.optString(KEY_UPDATED_BY) > localValue.optString(KEY_UPDATED_BY) -> externalValue
            externalValue.optString(KEY_UPDATED_BY) < localValue.optString(KEY_UPDATED_BY) -> localValue
            encodedValueHash(externalValue) > encodedValueHash(localValue) -> externalValue
            else -> localValue
        }
    }

    private fun updatedAt(encodedValue: JSONObject): Long {
        return encodedValue.optLong(KEY_UPDATED_AT, 0L)
    }

    private fun applyPreferences(
        sectionName: String,
        prefs: SharedPreferences,
        encodedValues: JSONObject?,
        allowedKeys: Set<String>?
    ): Int {
        if (encodedValues == null) return 0

        val editor = prefs.edit()
        var applied = 0
        val keys = encodedValues.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (allowedKeys != null && key !in allowedKeys) continue

            val encodedValue = encodedValues.optJSONObject(key) ?: continue
            if (putTypedValue(editor, key, encodedValue)) {
                rememberImportedValueState(sectionName, key, encodedValue)
                applied++
            }
        }
        editor.apply()
        return applied
    }

    private fun putTypedValue(
        editor: SharedPreferences.Editor,
        key: String,
        encodedValue: JSONObject
    ): Boolean {
        return when (encodedValue.optString(KEY_TYPE)) {
            TYPE_STRING -> {
                editor.putString(key, encodedValue.optString(KEY_VALUE, ""))
                true
            }
            TYPE_BOOLEAN -> {
                editor.putBoolean(key, encodedValue.optBoolean(KEY_VALUE, false))
                true
            }
            TYPE_INT -> {
                editor.putInt(key, encodedValue.optInt(KEY_VALUE, 0))
                true
            }
            TYPE_LONG -> {
                editor.putLong(key, encodedValue.optLong(KEY_VALUE, 0L))
                true
            }
            TYPE_FLOAT -> {
                editor.putFloat(key, encodedValue.optDouble(KEY_VALUE, 0.0).toFloat())
                true
            }
            TYPE_STRING_SET -> {
                val values = LinkedHashSet<String>()
                val array = encodedValue.optJSONArray(KEY_VALUE) ?: JSONArray()
                for (i in 0 until array.length()) {
                    values.add(array.optString(i))
                }
                editor.putStringSet(key, values)
                true
            }
            TYPE_DELETED -> {
                editor.remove(key)
                true
            }
            else -> false
        }
    }

    private fun exportCrownProfiles(): JSONArray {
        val profiles = mutableListOf<JSONObject>()
        val helper = SuperConfigDatabaseHelper(context)
        val statePrefs = context.getSharedPreferences(SYNC_VALUE_STATE_PREFS, Context.MODE_PRIVATE)
        val stateEditor = statePrefs.edit()
        val now = System.currentTimeMillis()
        val localDeviceId = deviceId()
        val trackedProfileIds = TreeSet(
            statePrefs.getStringSet(CROWN_PROFILE_TRACKED_IDS, emptySet()).orEmpty()
        )
        val seenProfileIds = mutableSetOf<String>()

        for (configId in helper.queryAllConfigIds()) {
            val name = helper.queryConfigAttribute(
                configId,
                PageConfigController.COLUMN_STRING_CONFIG_NAME,
                "default"
            ) as? String ?: "default"
            val payload = runCatching { helper.exportConfig(configId) }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: continue
            val profileId = crownProfileIdForConfig(statePrefs, stateEditor, configId, payload)

            profiles.add(
                stampCrownProfile(
                    profileId = profileId,
                    sourceConfigId = configId,
                    name = name,
                    payload = payload,
                    deleted = false,
                    statePrefs = statePrefs,
                    stateEditor = stateEditor,
                    now = now,
                    localDeviceId = localDeviceId
                )
            )
            trackedProfileIds.add(profileId)
            seenProfileIds.add(profileId)
            stateEditor.putLong(crownProfileConfigIdKey(profileId), configId)
            stateEditor.putString(crownProfileIdByConfigKey(configId), profileId)
        }

        for (profileId in trackedProfileIds) {
            if (profileId in seenProfileIds) continue
            val tombstone = deletedCrownProfile(
                profileId = profileId,
                statePrefs = statePrefs,
                stateEditor = stateEditor,
                now = now,
                localDeviceId = localDeviceId
            )
            if (tombstone != null) {
                profiles.add(tombstone)
            }
        }

        stateEditor.putStringSet(CROWN_PROFILE_TRACKED_IDS, trackedProfileIds)
        stateEditor.apply()

        profiles.sortWith(compareBy({ it.optString(KEY_CROWN_PROFILE_NAME) }, { it.optString(KEY_CROWN_PROFILE_ID) }))
        return JSONArray(profiles)
    }

    private fun mergeCrownProfiles(externalProfiles: JSONArray?, localProfiles: JSONArray?): JSONArray {
        val profilesByHash = LinkedHashMap<String, JSONObject>()

        fun addProfiles(profiles: JSONArray?) {
            if (profiles == null) return
            for (i in 0 until profiles.length()) {
                val profile = profiles.optJSONObject(i) ?: continue
                val payload = profile.optString("payload").takeIf { it.isNotBlank() } ?: continue
                profilesByHash[payloadHash(payload)] = JSONObject()
                    .put("sourceConfigId", 0L)
                    .put("name", profile.optString("name", "default"))
                    .put("payload", payload)
            }
        }

        addProfiles(externalProfiles)
        addProfiles(localProfiles)

        val sortedProfiles = profilesByHash.values.sortedWith(
            compareBy({ it.optString("name") }, { payloadHash(it.optString("payload")) })
        )
        return JSONArray(sortedProfiles)
    }

    private fun importCrownProfiles(profiles: JSONArray?): Pair<Int, Int> {
        if (profiles == null) return 0 to 0

        val helper = SuperConfigDatabaseHelper(context)
        val statePrefs = context.getSharedPreferences(SYNC_VALUE_STATE_PREFS, Context.MODE_PRIVATE)
        val stateEditor = statePrefs.edit()
        val trackedProfileIds = TreeSet(
            statePrefs.getStringSet(CROWN_PROFILE_TRACKED_IDS, emptySet()).orEmpty()
        )
        val existingProfileHashes = mutableMapOf<String, Long>()
        val existingConfigIds = helper.queryAllConfigIds().toMutableSet()
        for (configId in helper.queryAllConfigIds()) {
            val payload = runCatching { helper.exportConfig(configId) }.getOrNull()
            val name = helper.queryConfigAttribute(
                configId,
                PageConfigController.COLUMN_STRING_CONFIG_NAME,
                "default"
            ) as? String ?: "default"
            if (!payload.isNullOrBlank()) {
                existingProfileHashes[crownProfileContentHash(name, payload, false)] = configId
            }
        }

        var imported = 0
        var failed = 0

        for (i in 0 until profiles.length()) {
            val profile = profiles.optJSONObject(i)
                ?.let { normalizedCrownProfile(it, deviceId()) }

            if (profile == null) {
                failed++
                continue
            }

            val profileId = profile.optString(KEY_CROWN_PROFILE_ID).takeIf { it.isNotBlank() }
            if (profileId == null) {
                failed++
                continue
            }

            trackedProfileIds.add(profileId)
            if (profile.optBoolean(KEY_CROWN_PROFILE_DELETED, false)) {
                val configId = mappedCrownProfileConfigId(statePrefs, profileId, existingConfigIds)
                if (configId != null) {
                    helper.deleteConfig(configId)
                    existingConfigIds.remove(configId)
                    replaceSelectedCrownConfig(configId, null)
                    stateEditor.remove(crownProfileIdByConfigKey(configId))
                    imported++
                }
                rememberCrownProfileState(stateEditor, trackedProfileIds, profileId, null, profile)
                continue
            }

            val payload = profile.optString(KEY_CROWN_PROFILE_PAYLOAD).takeIf { it.isNotBlank() }
            if (payload == null) {
                failed++
                continue
            }

            val name = profile.optString(KEY_CROWN_PROFILE_NAME, "default")
            val profileHash = crownProfileContentHash(name, payload, false)
            val mappedConfigId = mappedCrownProfileConfigId(statePrefs, profileId, existingConfigIds)
            var replacedConfigId: Long? = null
            if (mappedConfigId != null) {
                val localPayload = runCatching { helper.exportConfig(mappedConfigId) }.getOrNull()
                val localName = helper.queryConfigAttribute(
                    mappedConfigId,
                    PageConfigController.COLUMN_STRING_CONFIG_NAME,
                    "default"
                ) as? String ?: "default"
                if (localPayload != null && crownProfileContentHash(localName, localPayload, false) == profileHash) {
                    rememberCrownProfileState(stateEditor, trackedProfileIds, profileId, mappedConfigId, profile)
                    continue
                }

                replacedConfigId = mappedConfigId
            } else {
                val existingConfigId = existingProfileHashes[profileHash]
                if (existingConfigId != null && existingConfigId in existingConfigIds) {
                    rememberCrownProfileState(stateEditor, trackedProfileIds, profileId, existingConfigId, profile)
                    continue
                }
            }

            val importedConfigId = importCrownProfileAndFindId(helper, payload, existingConfigIds)
            if (importedConfigId != null) {
                if (replacedConfigId != null) {
                    helper.deleteConfig(replacedConfigId)
                    existingConfigIds.remove(replacedConfigId)
                    replaceSelectedCrownConfig(replacedConfigId, importedConfigId)
                    stateEditor.remove(crownProfileIdByConfigKey(replacedConfigId))
                }
                existingConfigIds.add(importedConfigId)
                existingProfileHashes[profileHash] = importedConfigId
                rememberCrownProfileState(stateEditor, trackedProfileIds, profileId, importedConfigId, profile)
                imported++
            } else {
                failed++
            }
        }

        stateEditor.putStringSet(CROWN_PROFILE_TRACKED_IDS, trackedProfileIds)
        stateEditor.apply()
        return imported to failed
    }

    private fun exportPairingState(): JSONObject {
        val pairing = JSONObject()
        val computers = JSONArray()
        val pairNames = context.getSharedPreferences(PAIR_NAME_PREFS, Context.MODE_PRIVATE)

        val manager = ComputerDatabaseManager(context)
        try {
            manager.getAllComputers()
                .sortedBy { it.uuid.orEmpty() }
                .forEach { details ->
                    val uuid = details.uuid?.takeIf { it.isNotBlank() } ?: return@forEach
                    computers.put(encodeComputer(details, pairNames.getString(uuid, "") ?: ""))
                }
        } finally {
            manager.close()
        }

        readPrivateFileText(UNIQUE_ID_FILE_NAME)?.let {
            pairing.put(KEY_PAIRING_UNIQUE_ID, it)
        }
        readPrivateFileBase64(CLIENT_CERTIFICATE_FILE_NAME)?.let {
            pairing.put(KEY_PAIRING_CLIENT_CERTIFICATE, it)
        }
        readPrivateFileBase64(CLIENT_PRIVATE_KEY_FILE_NAME)?.let {
            pairing.put(KEY_PAIRING_CLIENT_PRIVATE_KEY, it)
        }

        return pairing
            .put(KEY_UPDATED_AT, pairingUpdatedAt())
            .put(KEY_PAIRING_COMPUTERS, computers)
    }

    private fun importPairingState(pairing: JSONObject?): PairingImportResult {
        if (pairing == null) return PairingImportResult(0, 0)

        var imported = 0
        var failed = 0
        val uniqueId = pairing.optString(KEY_PAIRING_UNIQUE_ID).takeIf { isValidUniqueId(it) }
        if (uniqueId != null) {
            writePrivateFileText(UNIQUE_ID_FILE_NAME, uniqueId)
            imported++
        } else if (pairing.has(KEY_PAIRING_UNIQUE_ID)) {
            failed++
        }

        importIdentityFile(pairing, KEY_PAIRING_CLIENT_CERTIFICATE, CLIENT_CERTIFICATE_FILE_NAME).also {
            imported += it.imported
            failed += it.failed
        }
        importIdentityFile(pairing, KEY_PAIRING_CLIENT_PRIVATE_KEY, CLIENT_PRIVATE_KEY_FILE_NAME).also {
            imported += it.imported
            failed += it.failed
        }

        val pairNameEditor = context.getSharedPreferences(PAIR_NAME_PREFS, Context.MODE_PRIVATE).edit()
        val manager = ComputerDatabaseManager(context)
        try {
            val computers = pairing.optJSONArray(KEY_PAIRING_COMPUTERS) ?: JSONArray()
            for (i in 0 until computers.length()) {
                val encoded = computers.optJSONObject(i)
                val decoded = encoded?.let { decodeComputer(it) }
                if (decoded == null) {
                    failed++
                    continue
                }
                if (manager.updateComputer(decoded)) {
                    encoded.optString(KEY_PAIRING_PAIR_NAME).takeIf { it.isNotBlank() }?.let {
                        pairNameEditor.putString(decoded.uuid, it)
                    }
                    imported++
                } else {
                    failed++
                }
            }
        } finally {
            manager.close()
        }
        pairNameEditor.apply()

        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putLong(PREF_PAIRING_STATE_UPDATED_AT, pairing.optLong(KEY_UPDATED_AT, System.currentTimeMillis()))
            .apply()

        return PairingImportResult(imported, failed)
    }

    private fun importIdentityFile(pairing: JSONObject, key: String, fileName: String): PairingImportResult {
        if (!pairing.has(key)) return PairingImportResult(0, 0)
        val encoded = pairing.optString(key).takeIf { it.isNotBlank() }
            ?: return PairingImportResult(0, 1)
        val decoded = decodeIdentityFileBytes(encoded, fileName)
            ?: return PairingImportResult(0, 1)
        if (!isValidIdentityFile(fileName, decoded)) {
            return PairingImportResult(0, 1)
        }
        writePrivateFileBytes(fileName, decoded)
        return PairingImportResult(1, 0)
    }

    private fun decodeIdentityFileBytes(value: String, fileName: String): ByteArray? {
        val trimmed = value.trim()
        if (trimmed.startsWith(PEM_BEGIN_PREFIX)) {
            return normalizeIdentityFileBytes(fileName, trimmed.toByteArray(Charsets.UTF_8))
        }

        val decoded = runCatching { Base64.decode(trimmed, Base64.DEFAULT) }.getOrNull()
            ?: return null
        return normalizeIdentityFileBytes(fileName, decoded)
    }

    private fun normalizeIdentityFileBytes(fileName: String, bytes: ByteArray): ByteArray? {
        val text = runCatching { String(bytes, Charsets.UTF_8).trim() }.getOrNull()
        if (text != null && text.startsWith(PEM_BEGIN_PREFIX)) {
            return if (fileName == CLIENT_PRIVATE_KEY_FILE_NAME) {
                decodePemBody(text)
            } else {
                text.replace("\r", "").toByteArray(Charsets.UTF_8)
            }
        }
        return bytes
    }

    private fun decodePemBody(pem: String): ByteArray? {
        val body = pem.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith(PEM_BEGIN_PREFIX) && !it.startsWith(PEM_END_PREFIX) }
            .joinToString(separator = "")
        if (body.isBlank()) return null
        return runCatching { Base64.decode(body, Base64.DEFAULT) }.getOrNull()
    }

    private fun isValidIdentityFile(fileName: String, bytes: ByteArray): Boolean {
        return when (fileName) {
            CLIENT_CERTIFICATE_FILE_NAME -> runCatching {
                CertificateFactory.getInstance("X.509")
                    .generateCertificate(ByteArrayInputStream(bytes))
            }.isSuccess

            CLIENT_PRIVATE_KEY_FILE_NAME -> runCatching {
                KeyFactory.getInstance("RSA")
                    .generatePrivate(PKCS8EncodedKeySpec(bytes))
            }.isSuccess

            else -> bytes.isNotEmpty()
        }
    }

    private fun encodeComputer(details: ComputerDetails, pairName: String): JSONObject {
        val output = JSONObject()
            .put(KEY_PAIRING_COMPUTER_UUID, details.uuid)
            .put(KEY_PAIRING_COMPUTER_NAME, details.name ?: "")
            .put(KEY_PAIRING_LOCAL_ADDRESS, encodeAddress(details.localAddress))
            .put(KEY_PAIRING_REMOTE_ADDRESS, encodeAddress(details.remoteAddress))
            .put(KEY_PAIRING_MANUAL_ADDRESS, encodeAddress(details.manualAddress))
            .put(KEY_PAIRING_IPV6_ADDRESS, encodeAddress(details.ipv6Address))
            .put(KEY_PAIRING_IPV6_DISABLED, details.ipv6Disabled)
            .put(KEY_PAIRING_ACTIVE_ADDRESS, encodeAddress(details.activeAddress))
            .put(KEY_PAIRING_HTTPS_PORT, details.httpsPort)
            .put(KEY_PAIRING_MAC_ADDRESS, details.macAddress ?: "")
            .put(KEY_PAIRING_PAIR_NAME, pairName)

        details.serverCert?.let {
            output.put(KEY_PAIRING_SERVER_CERTIFICATE, Base64.encodeToString(it.encoded, Base64.NO_WRAP))
        }
        return output
    }

    private fun decodeComputer(encoded: JSONObject): ComputerDetails? {
        val uuid = encoded.optString(KEY_PAIRING_COMPUTER_UUID).takeIf { it.isNotBlank() }
            ?: return null
        val name = encoded.optString(KEY_PAIRING_COMPUTER_NAME).takeIf { it.isNotBlank() }
            ?: return null

        return ComputerDetails().apply {
            this.uuid = uuid
            this.name = name
            localAddress = decodeAddress(encoded.optJSONObject(KEY_PAIRING_LOCAL_ADDRESS))
            remoteAddress = decodeAddress(encoded.optJSONObject(KEY_PAIRING_REMOTE_ADDRESS))
            manualAddress = decodeAddress(encoded.optJSONObject(KEY_PAIRING_MANUAL_ADDRESS))
            ipv6Address = decodeAddress(encoded.optJSONObject(KEY_PAIRING_IPV6_ADDRESS))
            ipv6Disabled = encoded.optBoolean(KEY_PAIRING_IPV6_DISABLED, false)
            activeAddress = decodeAddress(encoded.optJSONObject(KEY_PAIRING_ACTIVE_ADDRESS))
            httpsPort = encoded.optInt(KEY_PAIRING_HTTPS_PORT, 0)
            macAddress = encoded.optString(KEY_PAIRING_MAC_ADDRESS).takeIf { it.isNotBlank() }
            serverCert = decodeCertificate(encoded.optString(KEY_PAIRING_SERVER_CERTIFICATE))
        }
    }

    private fun encodeAddress(address: ComputerDetails.AddressTuple?): Any {
        return if (address == null) {
            JSONObject.NULL
        } else {
            JSONObject()
                .put(KEY_PAIRING_ADDRESS, address.address)
                .put(KEY_PAIRING_PORT, address.port)
        }
    }

    private fun decodeAddress(encoded: JSONObject?): ComputerDetails.AddressTuple? {
        if (encoded == null) return null
        val address = encoded.optString(KEY_PAIRING_ADDRESS).takeIf { it.isNotBlank() } ?: return null
        val port = encoded.optInt(KEY_PAIRING_PORT, 0).takeIf { it > 0 } ?: return null
        return runCatching { ComputerDetails.AddressTuple(address, port) }.getOrNull()
    }

    private fun decodeCertificate(encoded: String): X509Certificate? {
        if (encoded.isBlank()) return null
        val bytes = runCatching { Base64.decode(encoded, Base64.NO_WRAP) }.getOrNull() ?: return null
        return runCatching {
            CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
        }.getOrNull()
    }

    private fun pairingUpdatedAt(): Long {
        val stored = PreferenceManager.getDefaultSharedPreferences(context)
            .getLong(PREF_PAIRING_STATE_UPDATED_AT, 0L)
        if (stored > 0L) return stored

        return listOf(
            File(context.filesDir, UNIQUE_ID_FILE_NAME),
            File(context.filesDir, CLIENT_CERTIFICATE_FILE_NAME),
            File(context.filesDir, CLIENT_PRIVATE_KEY_FILE_NAME),
            context.getDatabasePath(COMPUTERS_DATABASE_FILE_NAME)
        )
            .filter { it.exists() }
            .maxOfOrNull { it.lastModified() }
            ?: 0L
    }

    private fun readPrivateFileText(fileName: String): String? {
        return runCatching { File(context.filesDir, fileName).readText(Charsets.UTF_8).trim() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun readPrivateFileBase64(fileName: String): String? {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return null
        return runCatching { Base64.encodeToString(file.readBytes(), Base64.NO_WRAP) }.getOrNull()
    }

    private fun writePrivateFileText(fileName: String, text: String) {
        writePrivateFileBytes(fileName, text.toByteArray(Charsets.UTF_8))
    }

    private fun writePrivateFileBytes(fileName: String, bytes: ByteArray) {
        val file = File(context.filesDir, fileName)
        val tempFile = File(context.filesDir, "$fileName.tmp")
        tempFile.writeBytes(bytes)
        if (file.exists() && !file.delete()) {
            throw IOException("Unable to replace private file: $fileName")
        }
        if (!tempFile.renameTo(file)) {
            tempFile.copyTo(file, overwrite = true)
            tempFile.delete()
        }
    }

    private fun isValidUniqueId(uniqueId: String): Boolean {
        return uniqueId.length == UNIQUE_ID_LENGTH && uniqueId.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }

    private fun crownProfileIdForConfig(
        statePrefs: SharedPreferences,
        stateEditor: SharedPreferences.Editor,
        configId: Long,
        payload: String
    ): String {
        val stateKey = crownProfileIdByConfigKey(configId)
        val existing = statePrefs.getString(stateKey, null)?.takeIf { it.isNotBlank() }
        if (existing != null) return existing

        val generated = "payload-${payloadHash(payload)}"
        stateEditor.putString(stateKey, generated)
        return generated
    }

    private fun stampCrownProfile(
        profileId: String,
        sourceConfigId: Long,
        name: String,
        payload: String,
        deleted: Boolean,
        statePrefs: SharedPreferences,
        stateEditor: SharedPreferences.Editor,
        now: Long,
        localDeviceId: String
    ): JSONObject {
        val contentHash = crownProfileContentHash(name, payload, deleted)
        val oldHash = statePrefs.getString(crownProfileHashKey(profileId), null)
        val updatedAt: Long
        val updatedBy: String

        if (oldHash == contentHash) {
            updatedAt = statePrefs.getLong(crownProfileUpdatedAtKey(profileId), now)
            updatedBy = statePrefs.getString(crownProfileUpdatedByKey(profileId), localDeviceId) ?: localDeviceId
        } else {
            updatedAt = now
            updatedBy = localDeviceId
            stateEditor.putString(crownProfileHashKey(profileId), contentHash)
            stateEditor.putLong(crownProfileUpdatedAtKey(profileId), updatedAt)
            stateEditor.putString(crownProfileUpdatedByKey(profileId), updatedBy)
        }
        stateEditor.putString(crownProfileNameKey(profileId), name)

        return JSONObject()
            .put(KEY_CROWN_PROFILE_ID, profileId)
            .put(KEY_CROWN_PROFILE_SOURCE_CONFIG_ID, sourceConfigId)
            .put(KEY_CROWN_PROFILE_NAME, name)
            .put(KEY_CROWN_PROFILE_PAYLOAD, payload)
            .put(KEY_CROWN_PROFILE_DELETED, deleted)
            .put(KEY_UPDATED_AT, updatedAt)
            .put(KEY_UPDATED_BY, updatedBy)
    }

    private fun deletedCrownProfile(
        profileId: String,
        statePrefs: SharedPreferences,
        stateEditor: SharedPreferences.Editor,
        now: Long,
        localDeviceId: String
    ): JSONObject? {
        if (statePrefs.getString(crownProfileHashKey(profileId), null).isNullOrBlank()) return null
        val name = statePrefs.getString(crownProfileNameKey(profileId), "default") ?: "default"
        return stampCrownProfile(
            profileId = profileId,
            sourceConfigId = 0L,
            name = name,
            payload = "",
            deleted = true,
            statePrefs = statePrefs,
            stateEditor = stateEditor,
            now = now,
            localDeviceId = localDeviceId
        )
    }

    private fun mappedCrownProfileConfigId(
        statePrefs: SharedPreferences,
        profileId: String,
        existingConfigIds: Set<Long>
    ): Long? {
        val configId = statePrefs.getLong(crownProfileConfigIdKey(profileId), Long.MIN_VALUE)
        return configId.takeIf { it in existingConfigIds }
    }

    private fun importCrownProfileAndFindId(
        helper: SuperConfigDatabaseHelper,
        payload: String,
        existingConfigIds: Set<Long>
    ): Long? {
        if (helper.importConfig(payload) != 0) return null
        return helper.queryAllConfigIds()
            .filter { it !in existingConfigIds }
            .maxOrNull()
    }

    private fun rememberCrownProfileState(
        stateEditor: SharedPreferences.Editor,
        trackedProfileIds: MutableSet<String>,
        profileId: String,
        configId: Long?,
        profile: JSONObject
    ) {
        trackedProfileIds.add(profileId)
        stateEditor.putString(crownProfileHashKey(profileId), crownProfileContentHash(profile))
        stateEditor.putLong(crownProfileUpdatedAtKey(profileId), profile.optLong(KEY_UPDATED_AT, System.currentTimeMillis()))
        stateEditor.putString(crownProfileUpdatedByKey(profileId), profile.optString(KEY_UPDATED_BY, deviceId()))
        stateEditor.putString(crownProfileNameKey(profileId), profile.optString(KEY_CROWN_PROFILE_NAME, "default"))
        if (configId != null) {
            stateEditor.putLong(crownProfileConfigIdKey(profileId), configId)
            stateEditor.putString(crownProfileIdByConfigKey(configId), profileId)
        } else {
            stateEditor.remove(crownProfileConfigIdKey(profileId))
        }
    }

    private fun replaceSelectedCrownConfig(oldConfigId: Long, newConfigId: Long?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getLong(CURRENT_CROWN_CONFIG_ID_KEY, 0L) != oldConfigId) return
        prefs.edit()
            .putLong(CURRENT_CROWN_CONFIG_ID_KEY, newConfigId ?: 0L)
            .apply()
    }

    private fun normalizedCrownProfile(profile: JSONObject, fallbackDeviceId: String): JSONObject? {
        return normalizedCrownProfileCore(profile, fallbackDeviceId)
    }

    private fun crownProfileContentHash(profile: JSONObject): String {
        return crownProfileContentHash(
            profile.optString(KEY_CROWN_PROFILE_NAME, "default"),
            profile.optString(KEY_CROWN_PROFILE_PAYLOAD, ""),
            profile.optBoolean(KEY_CROWN_PROFILE_DELETED, false)
        )
    }

    private fun crownProfileContentHash(name: String, payload: String, deleted: Boolean): String {
        return sha256Hex(
            JSONObject()
                .put(KEY_CROWN_PROFILE_NAME, name)
                .put(KEY_CROWN_PROFILE_PAYLOAD, if (deleted) "" else payload)
                .put(KEY_CROWN_PROFILE_DELETED, deleted)
                .toString()
                .toByteArray(Charsets.UTF_8)
        )
    }

    private fun crownProfileIdByConfigKey(configId: Long): String {
        return "crownProfiles.config.$configId.profileId"
    }

    private fun crownProfileConfigIdKey(profileId: String): String {
        return "crownProfiles.profile.${payloadHash(profileId)}.configId"
    }

    private fun crownProfileHashKey(profileId: String): String {
        return "crownProfiles.profile.${payloadHash(profileId)}.hash"
    }

    private fun crownProfileUpdatedAtKey(profileId: String): String {
        return "crownProfiles.profile.${payloadHash(profileId)}.updatedAt"
    }

    private fun crownProfileUpdatedByKey(profileId: String): String {
        return "crownProfiles.profile.${payloadHash(profileId)}.updatedBy"
    }

    private fun crownProfileNameKey(profileId: String): String {
        return "crownProfiles.profile.${payloadHash(profileId)}.name"
    }

    companion object {
        private data class SyncPackageMetadata(
            val deviceId: String,
            val backupDeviceKey: String,
            val packageName: String,
            val appVersionCode: Long,
            val appVersionName: String
        )

        internal fun mergeSyncPackagesForTest(syncPackages: List<String>): String {
            return mergeSyncPackageListCore(
                syncPackages,
                SyncPackageMetadata(
                    deviceId = "test-device",
                    backupDeviceKey = "test-backup-device",
                    packageName = "com.limelight.test",
                    appVersionCode = 1L,
                    appVersionName = "test"
                )
            )
        }

        internal fun trackedKeysAfterImportForTest(existingKeys: Set<String>, importedKey: String): Set<String> {
            return trackedKeysAfterImport(existingKeys, importedKey)
        }

        internal fun encryptSyncPackageForTest(syncPackage: String, password: String): String {
            return encryptSyncPackageCore(syncPackage, password)
        }

        internal fun decryptSyncPackageForTest(syncPackage: String, password: String): String {
            return decryptSyncPackageCore(syncPackage, password)
        }

        internal fun shouldWriteExternalSnapshotForTest(
            mergedHash: String,
            externalHash: String,
            externalRequiresEncryptionRewrite: Boolean
        ): Boolean {
            return shouldWriteExternalSnapshotCore(
                mergedHash,
                externalHash,
                externalRequiresEncryptionRewrite
            )
        }

        fun isEncryptedSyncPackage(syncPackage: String): Boolean {
            return runCatching { isEncryptedSyncPackageCore(syncPackage) }.getOrDefault(false)
        }

        fun canStoreExternalSyncPassword(): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        }

        fun hasExternalSyncPassword(context: Context): Boolean {
            return loadExternalSyncPassword(context) != null
        }

        private fun saveExternalSyncPassword(context: Context, password: String): Boolean {
            if (password.isBlank() || !canStoreExternalSyncPassword()) return false
            return runCatching {
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateExternalPasswordKey())
                val ciphertext = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
                val iv = cipher.iv
                context.applicationContext
                    .getSharedPreferences(CONFIG_SYNC_SECRET_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(PREF_EXTERNAL_PASSWORD_IV, encodeEnvelopeBase64(iv))
                    .putString(PREF_EXTERNAL_PASSWORD_CIPHERTEXT, encodeEnvelopeBase64(ciphertext))
                    .apply()
                true
            }.getOrDefault(false)
        }

        private fun loadExternalSyncPassword(context: Context): String? {
            if (!canStoreExternalSyncPassword()) return null
            return runCatching {
                val prefs = context.applicationContext
                    .getSharedPreferences(CONFIG_SYNC_SECRET_PREFS, Context.MODE_PRIVATE)
                val iv = decodeEnvelopeBase64(prefs.getString(PREF_EXTERNAL_PASSWORD_IV, "") ?: "")
                val ciphertext = decodeEnvelopeBase64(prefs.getString(PREF_EXTERNAL_PASSWORD_CIPHERTEXT, "") ?: "")
                if (iv.isEmpty() || ciphertext.isEmpty()) return null

                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateExternalPasswordKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
                String(cipher.doFinal(ciphertext), Charsets.UTF_8).takeIf { it.isNotBlank() }
            }.getOrNull()
        }

        private fun buildSyncPackageCore(sections: JSONObject, metadata: SyncPackageMetadata): String {
            return JSONObject()
                .put(KEY_SCHEMA_VERSION, SUPPORTED_SCHEMA_VERSION)
                .put(KEY_SYNC_ID, UUID.randomUUID().toString())
                .put(KEY_DEVICE_ID, metadata.deviceId)
                .put(KEY_BACKUP_DEVICE_KEY, metadata.backupDeviceKey)
                .put(KEY_BACKUP_TARGET, BACKUP_TARGET_SAME_DEVICE)
                .put(KEY_PACKAGE_NAME, metadata.packageName)
                .put(KEY_APP_VERSION_CODE, metadata.appVersionCode)
                .put(KEY_APP_VERSION_NAME, metadata.appVersionName)
                .put(KEY_EXPORTED_AT, System.currentTimeMillis())
                .put(KEY_SECTIONS, sections)
                .toString(2)
        }

        private fun trackedKeysAfterImport(existingKeys: Set<String>, importedKey: String): Set<String> {
            return TreeSet(existingKeys).apply { add(importedKey) }
        }

        private fun mergeSyncPackageListCore(
            syncPackages: List<String>,
            metadata: SyncPackageMetadata
        ): String {
            if (syncPackages.isEmpty()) {
                throw JSONException("No sync packages to merge")
            }

            val validPackages = syncPackages
                .filter { it.isNotBlank() }
                .distinctBy { syncContentHashCore(it) }

            if (validPackages.isEmpty()) {
                throw JSONException("No valid sync packages to merge")
            }
            if (validPackages.size == 1) {
                return normalizedSyncPackageCore(validPackages.first(), metadata)
            }

            var mergedPackage = validPackages.first()
            for (i in 1 until validPackages.size) {
                mergedPackage = mergeSyncPackagePairCore(mergedPackage, validPackages[i], metadata)
            }
            return mergedPackage
        }

        private fun normalizedSyncPackageCore(
            syncPackage: String,
            metadata: SyncPackageMetadata
        ): String {
            val root = parseAndValidateRootCore(syncPackage)
            val sections = root.optJSONObject(KEY_SECTIONS)
                ?: throw JSONException("Missing sections")
            return buildSyncPackageCore(normalizedSectionsForHashCore(sections), metadata)
        }

        private fun mergeSyncPackagePairCore(
            localPackage: String,
            externalPackage: String,
            metadata: SyncPackageMetadata
        ): String {
            val localRoot = parseAndValidateRootCore(localPackage)
            val externalRoot = parseAndValidateRootCore(externalPackage)
            val localSections = localRoot.optJSONObject(KEY_SECTIONS)
                ?: throw JSONException("Missing local sections")
            val externalSections = externalRoot.optJSONObject(KEY_SECTIONS)
                ?: throw JSONException("Missing external sections")

            val mergedSections = JSONObject()
                .put(
                    SECTION_DEFAULT_PREFERENCES,
                    mergedPreferenceSectionCore(
                        externalSections.optJSONObject(SECTION_DEFAULT_PREFERENCES),
                        localSections.optJSONObject(SECTION_DEFAULT_PREFERENCES),
                        PORTABLE_DEFAULT_PREF_KEYS,
                        metadata.deviceId
                    )
                )
                .put(
                    SECTION_APP_LAST_SETTINGS,
                    mergedPreferenceSectionCore(
                        externalSections.optJSONObject(SECTION_APP_LAST_SETTINGS),
                        localSections.optJSONObject(SECTION_APP_LAST_SETTINGS),
                        null,
                        metadata.deviceId
                    )
                )
                .put(
                    SECTION_CUSTOM_RESOLUTIONS,
                    mergedPreferenceSectionCore(
                        externalSections.optJSONObject(SECTION_CUSTOM_RESOLUTIONS),
                        localSections.optJSONObject(SECTION_CUSTOM_RESOLUTIONS),
                        null,
                        metadata.deviceId
                    )
                )
                .put(
                    SECTION_SCENE_CONFIGS,
                    mergedPreferenceSectionCore(
                        externalSections.optJSONObject(SECTION_SCENE_CONFIGS),
                        localSections.optJSONObject(SECTION_SCENE_CONFIGS),
                        null,
                        metadata.deviceId
                    )
                )
                .put(
                    SECTION_APP_VIEW_PREFERENCES,
                    mergedPreferenceSectionCore(
                        externalSections.optJSONObject(SECTION_APP_VIEW_PREFERENCES),
                        localSections.optJSONObject(SECTION_APP_VIEW_PREFERENCES),
                        APP_VIEW_PREF_KEYS,
                        metadata.deviceId
                    )
                )
                .put(
                    SECTION_HIDDEN_APPS,
                    mergedPreferenceSectionCore(
                        externalSections.optJSONObject(SECTION_HIDDEN_APPS),
                        localSections.optJSONObject(SECTION_HIDDEN_APPS),
                        null,
                        metadata.deviceId
                    )
                )
                .put(
                    SECTION_CROWN_PROFILES,
                    mergeCrownProfilesCore(
                        externalSections.optJSONArray(SECTION_CROWN_PROFILES),
                        localSections.optJSONArray(SECTION_CROWN_PROFILES),
                        metadata.deviceId
                    )
                )
                .put(
                    SECTION_PAIRING,
                    mergePairingSectionCore(
                        externalSections.optJSONObject(SECTION_PAIRING),
                        localSections.optJSONObject(SECTION_PAIRING)
                    )
                )

            return buildSyncPackageCore(mergedSections, metadata)
        }

        private fun syncContentHashCore(syncPackage: String): String {
            val root = parseAndValidateRootCore(syncPackage)
            val sections = root.optJSONObject(KEY_SECTIONS)
                ?: throw JSONException("Missing sections")
            return sha256HexCore(
                normalizedSectionsForHashCore(sections)
                    .toString()
                    .toByteArray(Charsets.UTF_8)
            )
        }

        private fun shouldWriteExternalSnapshotCore(
            mergedHash: String,
            externalHash: String,
            externalRequiresEncryptionRewrite: Boolean
        ): Boolean {
            return mergedHash != externalHash || externalRequiresEncryptionRewrite
        }

        private fun normalizedSectionsForHashCore(sections: JSONObject): JSONObject {
            return JSONObject()
                .put(
                    SECTION_DEFAULT_PREFERENCES,
                    mergedPreferenceSectionCore(
                        sections.optJSONObject(SECTION_DEFAULT_PREFERENCES),
                        null,
                        PORTABLE_DEFAULT_PREF_KEYS,
                        "hash"
                    )
                )
                .put(
                    SECTION_APP_LAST_SETTINGS,
                    mergedPreferenceSectionCore(
                        sections.optJSONObject(SECTION_APP_LAST_SETTINGS),
                        null,
                        null,
                        "hash"
                    )
                )
                .put(
                    SECTION_CUSTOM_RESOLUTIONS,
                    mergedPreferenceSectionCore(
                        sections.optJSONObject(SECTION_CUSTOM_RESOLUTIONS),
                        null,
                        null,
                        "hash"
                    )
                )
                .put(
                    SECTION_SCENE_CONFIGS,
                    mergedPreferenceSectionCore(
                        sections.optJSONObject(SECTION_SCENE_CONFIGS),
                        null,
                        null,
                        "hash"
                    )
                )
                .put(
                    SECTION_APP_VIEW_PREFERENCES,
                    mergedPreferenceSectionCore(
                        sections.optJSONObject(SECTION_APP_VIEW_PREFERENCES),
                        null,
                        APP_VIEW_PREF_KEYS,
                        "hash"
                    )
                )
                .put(
                    SECTION_HIDDEN_APPS,
                    mergedPreferenceSectionCore(
                        sections.optJSONObject(SECTION_HIDDEN_APPS),
                        null,
                        null,
                        "hash"
                    )
                )
                .put(
                    SECTION_CROWN_PROFILES,
                    mergeCrownProfilesCore(sections.optJSONArray(SECTION_CROWN_PROFILES), null, "hash")
                )
                .put(
                    SECTION_PAIRING,
                    normalizedPairingSectionCore(sections.optJSONObject(SECTION_PAIRING))
                )
        }

        private fun mergedPreferenceSectionCore(
            externalSection: JSONObject?,
            localSection: JSONObject?,
            allowedKeys: Set<String>?,
            fallbackDeviceId: String
        ): JSONObject {
            return JSONObject().put(
                KEY_VALUES,
                mergeEncodedValuesCore(
                    valuesFromSectionCore(externalSection),
                    valuesFromSectionCore(localSection),
                    allowedKeys,
                    fallbackDeviceId
                )
            )
        }

        private fun valuesFromSectionCore(section: JSONObject?): JSONObject? {
            return section?.optJSONObject(KEY_VALUES)
        }

        private fun mergeEncodedValuesCore(
            externalValues: JSONObject?,
            localValues: JSONObject?,
            allowedKeys: Set<String>?,
            fallbackDeviceId: String
        ): JSONObject {
            val merged = JSONObject()
            val keys = TreeSet<String>()
            externalValues?.keys()?.forEach { keys.add(it) }
            localValues?.keys()?.forEach { keys.add(it) }

            for (key in keys) {
                if (allowedKeys != null && key !in allowedKeys) continue

                val externalValue = externalValues?.optJSONObject(key)
                val localValue = localValues?.optJSONObject(key)
                val mergedValue = mergeTypedValueCore(externalValue, localValue, fallbackDeviceId) ?: continue
                merged.put(key, mergedValue)
            }

            return merged
        }

        private fun mergeTypedValueCore(
            externalValue: JSONObject?,
            localValue: JSONObject?,
            fallbackDeviceId: String
        ): JSONObject? {
            if (externalValue == null) return localValue
            if (localValue == null) return externalValue

            val externalType = externalValue.optString(KEY_TYPE)
            val localType = localValue.optString(KEY_TYPE)
            if (externalType == TYPE_STRING_SET && localType == TYPE_STRING_SET) {
                val merged = typedValueCore(
                    TYPE_STRING_SET,
                    mergeStringSetValuesCore(
                        externalValue.optJSONArray(KEY_VALUE),
                        localValue.optJSONArray(KEY_VALUE)
                    )
                )
                val winner = chooseNewerEncodedValueCore(externalValue, localValue)
                return merged
                    .put(KEY_UPDATED_AT, maxOf(updatedAtCore(externalValue), updatedAtCore(localValue)))
                    .put(KEY_UPDATED_BY, winner.optString(KEY_UPDATED_BY, fallbackDeviceId))
            }

            return chooseNewerEncodedValueCore(externalValue, localValue)
        }

        private fun mergeStringSetValuesCore(externalValues: JSONArray?, localValues: JSONArray?): JSONArray {
            val merged = TreeSet<String>()
            if (externalValues != null) {
                for (i in 0 until externalValues.length()) {
                    merged.add(externalValues.optString(i))
                }
            }
            if (localValues != null) {
                for (i in 0 until localValues.length()) {
                    merged.add(localValues.optString(i))
                }
            }
            return JSONArray(merged)
        }

        private fun chooseNewerEncodedValueCore(externalValue: JSONObject, localValue: JSONObject): JSONObject {
            val externalUpdatedAt = updatedAtCore(externalValue)
            val localUpdatedAt = updatedAtCore(localValue)
            return when {
                externalUpdatedAt > localUpdatedAt -> externalValue
                localUpdatedAt > externalUpdatedAt -> localValue
                externalValue.optString(KEY_UPDATED_BY) > localValue.optString(KEY_UPDATED_BY) -> externalValue
                externalValue.optString(KEY_UPDATED_BY) < localValue.optString(KEY_UPDATED_BY) -> localValue
                encodedValueHashCore(externalValue) > encodedValueHashCore(localValue) -> externalValue
                else -> localValue
            }
        }

        private fun updatedAtCore(encodedValue: JSONObject): Long {
            return encodedValue.optLong(KEY_UPDATED_AT, 0L)
        }

        private fun encodedValueHashCore(encodedValue: JSONObject): String {
            return sha256HexCore(
                JSONObject()
                    .put(KEY_TYPE, encodedValue.optString(KEY_TYPE))
                    .put(KEY_VALUE, encodedValue.opt(KEY_VALUE))
                    .toString()
                    .toByteArray(Charsets.UTF_8)
            )
        }

        private fun typedValueCore(type: String, value: Any): JSONObject {
            return JSONObject()
                .put(KEY_TYPE, type)
                .put(KEY_VALUE, value)
        }

        private fun mergeCrownProfilesCore(
            externalProfiles: JSONArray?,
            localProfiles: JSONArray?,
            fallbackDeviceId: String
        ): JSONArray {
            val profilesById = LinkedHashMap<String, JSONObject>()

            fun addProfiles(profiles: JSONArray?) {
                if (profiles == null) return
                for (i in 0 until profiles.length()) {
                    val profile = profiles.optJSONObject(i) ?: continue
                    val normalized = normalizedCrownProfileCore(profile, fallbackDeviceId) ?: continue
                    val profileId = normalized.optString(KEY_CROWN_PROFILE_ID)
                    val existing = profilesById[profileId]
                    profilesById[profileId] = if (existing == null) {
                        normalized
                    } else {
                        chooseNewerCrownProfileCore(existing, normalized)
                    }
                }
            }

            addProfiles(externalProfiles)
            addProfiles(localProfiles)

            val sortedProfiles = profilesById.values.sortedWith(
                compareBy({ it.optString(KEY_CROWN_PROFILE_NAME) }, { it.optString(KEY_CROWN_PROFILE_ID) })
            )
            return JSONArray(sortedProfiles)
        }

        private fun normalizedCrownProfileCore(profile: JSONObject, fallbackDeviceId: String): JSONObject? {
            val deleted = profile.optBoolean(KEY_CROWN_PROFILE_DELETED, false)
            val payload = profile.optString(KEY_CROWN_PROFILE_PAYLOAD)
            val profileId = profile.optString(KEY_CROWN_PROFILE_ID)
                .takeIf { it.isNotBlank() }
                ?: payload
                    .takeIf { it.isNotBlank() }
                    ?.let { "payload-${payloadHashCore(it)}" }
                ?: return null

            if (!deleted && payload.isBlank()) return null

            return JSONObject()
                .put(KEY_CROWN_PROFILE_ID, profileId)
                .put(KEY_CROWN_PROFILE_SOURCE_CONFIG_ID, profile.optLong(KEY_CROWN_PROFILE_SOURCE_CONFIG_ID, 0L))
                .put(KEY_CROWN_PROFILE_NAME, profile.optString(KEY_CROWN_PROFILE_NAME, "default"))
                .put(KEY_CROWN_PROFILE_PAYLOAD, if (deleted) "" else payload)
                .put(KEY_CROWN_PROFILE_DELETED, deleted)
                .put(KEY_UPDATED_AT, profile.optLong(KEY_UPDATED_AT, 0L))
                .put(
                    KEY_UPDATED_BY,
                    profile.optString(KEY_UPDATED_BY, fallbackDeviceId).takeIf { it.isNotBlank() }
                        ?: fallbackDeviceId
                )
        }

        private fun chooseNewerCrownProfileCore(existingProfile: JSONObject, candidateProfile: JSONObject): JSONObject {
            val existingUpdatedAt = existingProfile.optLong(KEY_UPDATED_AT, 0L)
            val candidateUpdatedAt = candidateProfile.optLong(KEY_UPDATED_AT, 0L)
            return when {
                existingUpdatedAt > candidateUpdatedAt -> existingProfile
                candidateUpdatedAt > existingUpdatedAt -> candidateProfile
                existingProfile.optString(KEY_UPDATED_BY) > candidateProfile.optString(KEY_UPDATED_BY) -> existingProfile
                existingProfile.optString(KEY_UPDATED_BY) < candidateProfile.optString(KEY_UPDATED_BY) -> candidateProfile
                crownProfileContentHashCore(existingProfile) > crownProfileContentHashCore(candidateProfile) -> existingProfile
                else -> candidateProfile
            }
        }

        private fun crownProfileContentHashCore(profile: JSONObject): String {
            return sha256HexCore(
                JSONObject()
                    .put(KEY_CROWN_PROFILE_NAME, profile.optString(KEY_CROWN_PROFILE_NAME, "default"))
                    .put(
                        KEY_CROWN_PROFILE_PAYLOAD,
                        if (profile.optBoolean(KEY_CROWN_PROFILE_DELETED, false)) {
                            ""
                        } else {
                            profile.optString(KEY_CROWN_PROFILE_PAYLOAD)
                        }
                    )
                    .put(KEY_CROWN_PROFILE_DELETED, profile.optBoolean(KEY_CROWN_PROFILE_DELETED, false))
                    .toString()
                    .toByteArray(Charsets.UTF_8)
            )
        }

        private fun mergePairingSectionCore(externalSection: JSONObject?, localSection: JSONObject?): JSONObject {
            val externalPairing = normalizedPairingSectionCore(externalSection)
            val localPairing = normalizedPairingSectionCore(localSection)
            if (externalSection == null) return localPairing
            if (localSection == null) return externalPairing

            val externalUpdatedAt = externalPairing.optLong(KEY_UPDATED_AT, 0L)
            val localUpdatedAt = localPairing.optLong(KEY_UPDATED_AT, 0L)
            return when {
                externalUpdatedAt > localUpdatedAt -> externalPairing
                localUpdatedAt > externalUpdatedAt -> localPairing
                pairingSectionContentHashCore(externalPairing) > pairingSectionContentHashCore(localPairing) -> externalPairing
                else -> localPairing
            }
        }

        private fun normalizedPairingSectionCore(section: JSONObject?): JSONObject {
            val normalized = JSONObject()
                .put(KEY_UPDATED_AT, section?.optLong(KEY_UPDATED_AT, 0L) ?: 0L)
                .put(KEY_PAIRING_COMPUTERS, JSONArray())

            section?.optString(KEY_PAIRING_UNIQUE_ID)?.takeIf { it.isNotBlank() }?.let {
                normalized.put(KEY_PAIRING_UNIQUE_ID, it)
            }
            section?.optString(KEY_PAIRING_CLIENT_CERTIFICATE)?.takeIf { it.isNotBlank() }?.let {
                normalized.put(KEY_PAIRING_CLIENT_CERTIFICATE, it)
            }
            section?.optString(KEY_PAIRING_CLIENT_PRIVATE_KEY)?.takeIf { it.isNotBlank() }?.let {
                normalized.put(KEY_PAIRING_CLIENT_PRIVATE_KEY, it)
            }

            val computersByUuid = LinkedHashMap<String, JSONObject>()
            val computers = section?.optJSONArray(KEY_PAIRING_COMPUTERS)
            if (computers != null) {
                for (i in 0 until computers.length()) {
                    val computer = computers.optJSONObject(i) ?: continue
                    val normalizedComputer = normalizedPairingComputerCore(computer) ?: continue
                    computersByUuid[normalizedComputer.optString(KEY_PAIRING_COMPUTER_UUID)] = normalizedComputer
                }
            }

            val sortedComputers = JSONArray()
            for (uuid in computersByUuid.keys.sorted()) {
                sortedComputers.put(computersByUuid.getValue(uuid))
            }
            normalized.put(KEY_PAIRING_COMPUTERS, sortedComputers)
            return normalized
        }

        private fun normalizedPairingComputerCore(computer: JSONObject): JSONObject? {
            val uuid = computer.optString(KEY_PAIRING_COMPUTER_UUID).takeIf { it.isNotBlank() }
                ?: return null
            val name = computer.optString(KEY_PAIRING_COMPUTER_NAME).takeIf { it.isNotBlank() }
                ?: return null

            return JSONObject()
                .put(KEY_PAIRING_COMPUTER_UUID, uuid)
                .put(KEY_PAIRING_COMPUTER_NAME, name)
                .put(KEY_PAIRING_LOCAL_ADDRESS, normalizedPairingAddressCore(computer.optJSONObject(KEY_PAIRING_LOCAL_ADDRESS)))
                .put(KEY_PAIRING_REMOTE_ADDRESS, normalizedPairingAddressCore(computer.optJSONObject(KEY_PAIRING_REMOTE_ADDRESS)))
                .put(KEY_PAIRING_MANUAL_ADDRESS, normalizedPairingAddressCore(computer.optJSONObject(KEY_PAIRING_MANUAL_ADDRESS)))
                .put(KEY_PAIRING_IPV6_ADDRESS, normalizedPairingAddressCore(computer.optJSONObject(KEY_PAIRING_IPV6_ADDRESS)))
                .put(KEY_PAIRING_IPV6_DISABLED, computer.optBoolean(KEY_PAIRING_IPV6_DISABLED, false))
                .put(KEY_PAIRING_ACTIVE_ADDRESS, normalizedPairingAddressCore(computer.optJSONObject(KEY_PAIRING_ACTIVE_ADDRESS)))
                .put(KEY_PAIRING_HTTPS_PORT, computer.optInt(KEY_PAIRING_HTTPS_PORT, 0))
                .put(KEY_PAIRING_MAC_ADDRESS, computer.optString(KEY_PAIRING_MAC_ADDRESS, ""))
                .put(KEY_PAIRING_PAIR_NAME, computer.optString(KEY_PAIRING_PAIR_NAME, ""))
                .put(KEY_PAIRING_SERVER_CERTIFICATE, computer.optString(KEY_PAIRING_SERVER_CERTIFICATE, ""))
        }

        private fun normalizedPairingAddressCore(address: JSONObject?): Any {
            if (address == null) return JSONObject.NULL
            val host = address.optString(KEY_PAIRING_ADDRESS).takeIf { it.isNotBlank() }
                ?: return JSONObject.NULL
            val port = address.optInt(KEY_PAIRING_PORT, 0).takeIf { it > 0 }
                ?: return JSONObject.NULL
            return JSONObject()
                .put(KEY_PAIRING_ADDRESS, host)
                .put(KEY_PAIRING_PORT, port)
        }

        private fun pairingSectionContentHashCore(pairing: JSONObject): String {
            return sha256HexCore(pairing.toString().toByteArray(Charsets.UTF_8))
        }

        private fun payloadHashCore(payload: String): String {
            return sha256HexCore(payload.toByteArray(Charsets.UTF_8))
        }

        private fun encryptSyncPackageCore(syncPackage: String, password: String): String {
            if (password.isBlank()) {
                throw JSONException("Backup password is required")
            }

            val salt = randomBytes(PASSWORD_SALT_BYTES)
            val iv = randomBytes(GCM_IV_BYTES)
            val kdf = preferredKdfAlgorithm()
            val key = deriveBackupKey(password, salt, PASSWORD_KDF_ITERATIONS, kdf)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            val sealedPayload = cipher.doFinal(syncPackage.toByteArray(Charsets.UTF_8))
            val tagBytes = GCM_TAG_BITS / 8
            if (sealedPayload.size <= tagBytes) {
                throw JSONException("Encrypted backup payload is empty")
            }

            val ciphertext = sealedPayload.copyOfRange(0, sealedPayload.size - tagBytes)
            val tag = sealedPayload.copyOfRange(sealedPayload.size - tagBytes, sealedPayload.size)
            return JSONObject()
                .put(KEY_ENCRYPTION_FORMAT, ENCRYPTED_PACKAGE_FORMAT)
                .put(KEY_ENCRYPTION_VERSION, ENCRYPTED_PACKAGE_VERSION)
                .put(KEY_ENCRYPTION_KDF, kdf)
                .put(KEY_ENCRYPTION_ITERATIONS, PASSWORD_KDF_ITERATIONS)
                .put(KEY_ENCRYPTION_SALT, encodeEnvelopeBase64(salt))
                .put(KEY_ENCRYPTION_CIPHER, ENCRYPTION_CIPHER_NAME)
                .put(KEY_ENCRYPTION_IV, encodeEnvelopeBase64(iv))
                .put(KEY_ENCRYPTION_CIPHERTEXT, encodeEnvelopeBase64(ciphertext))
                .put(KEY_ENCRYPTION_TAG, encodeEnvelopeBase64(tag))
                .toString(2)
        }

        private fun decryptSyncPackageCore(syncPackage: String, password: String): String {
            if (password.isBlank()) {
                throw JSONException("Backup password is required")
            }

            val root = JSONObject(syncPackage)
            if (!isEncryptedSyncPackageCore(root)) {
                parseAndValidateRootCore(syncPackage)
                return syncPackage
            }

            val version = root.optInt(KEY_ENCRYPTION_VERSION, 0)
            if (version != ENCRYPTED_PACKAGE_VERSION) {
                throw JSONException("Unsupported encrypted backup version: $version")
            }
            if (root.optString(KEY_ENCRYPTION_CIPHER) != ENCRYPTION_CIPHER_NAME) {
                throw JSONException("Unsupported encrypted backup cipher")
            }

            val kdf = root.optString(KEY_ENCRYPTION_KDF)
            val iterations = root.optInt(KEY_ENCRYPTION_ITERATIONS, 0)
            if (iterations <= 0) {
                throw JSONException("Missing encrypted backup KDF iterations")
            }
            val salt = decodeRequiredBase64(root, KEY_ENCRYPTION_SALT)
            val iv = decodeRequiredBase64(root, KEY_ENCRYPTION_IV)
            val ciphertext = decodeRequiredBase64(root, KEY_ENCRYPTION_CIPHERTEXT)
            val tag = decodeRequiredBase64(root, KEY_ENCRYPTION_TAG)
            val sealedPayload = ciphertext + tag

            return try {
                val key = deriveBackupKey(password, salt, iterations, kdf)
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                val plainText = String(cipher.doFinal(sealedPayload), Charsets.UTF_8)
                parseAndValidateRootCore(plainText)
                plainText
            } catch (e: Exception) {
                throw JSONException("Unable to decrypt backup: ${e.message}")
            }
        }

        private fun decodeRequiredBase64(root: JSONObject, key: String): ByteArray {
            val value = root.optString(key).takeIf { it.isNotBlank() }
                ?: throw JSONException("Missing encrypted backup field: $key")
            return runCatching { decodeEnvelopeBase64(value) }
                .getOrElse { throw JSONException("Invalid encrypted backup field: $key") }
        }

        private fun deriveBackupKey(
            password: String,
            salt: ByteArray,
            iterations: Int,
            kdfAlgorithm: String
        ): SecretKeySpec {
            val chars = password.toCharArray()
            val spec = PBEKeySpec(chars, salt, iterations, AES_KEY_BITS)
            return try {
                val factory = SecretKeyFactory.getInstance(kdfAlgorithm)
                SecretKeySpec(factory.generateSecret(spec).encoded, KEY_ALGORITHM_AES)
            } finally {
                spec.clearPassword()
                Arrays.fill(chars, '\u0000')
            }
        }

        private fun preferredKdfAlgorithm(): String {
            return runCatching {
                SecretKeyFactory.getInstance(KDF_PBKDF2_SHA256)
                KDF_PBKDF2_SHA256
            }.getOrDefault(KDF_PBKDF2_SHA1)
        }

        private fun isEncryptedSyncPackageCore(syncPackage: String): Boolean {
            return isEncryptedSyncPackageCore(JSONObject(syncPackage))
        }

        private fun isEncryptedSyncPackageCore(root: JSONObject): Boolean {
            return root.optString(KEY_ENCRYPTION_FORMAT) == ENCRYPTED_PACKAGE_FORMAT
        }

        private fun randomBytes(size: Int): ByteArray {
            return ByteArray(size).also { SecureRandom().nextBytes(it) }
        }

        private fun encodeEnvelopeBase64(bytes: ByteArray): String {
            return JavaBase64.getEncoder().encodeToString(bytes)
        }

        private fun decodeEnvelopeBase64(value: String): ByteArray {
            return JavaBase64.getDecoder().decode(value)
        }

        @SuppressLint("NewApi")
        private fun getOrCreateExternalPasswordKey(): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
            (keyStore.getKey(EXTERNAL_PASSWORD_KEY_ALIAS, null) as? SecretKey)?.let { return it }

            val keyGenerator = KeyGenerator.getInstance(KEY_ALGORITHM_AES, ANDROID_KEYSTORE_PROVIDER)
            val keySpec = KeyGenParameterSpec.Builder(
                EXTERNAL_PASSWORD_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AES_KEY_BITS)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGenerator.init(keySpec)
            return keyGenerator.generateKey()
        }

        private fun sha256HexCore(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString(separator = "") { "%02x".format(it) }
        }

        private fun parseAndValidateRootCore(syncPackage: String): JSONObject {
            val root = JSONObject(syncPackage)
            val schemaVersion = root.optInt(KEY_SCHEMA_VERSION, 0)

            if (schemaVersion < 1) {
                throw JSONException("Missing or invalid schema version")
            }
            if (schemaVersion > SUPPORTED_SCHEMA_VERSION) {
                throw JSONException("Unsupported schema version: $schemaVersion")
            }
            if (!root.has(KEY_SECTIONS)) {
                throw JSONException("Missing sections")
            }

            return root
        }

        const val DEFAULT_FILE_NAME = "moonlight-vplus-config-sync.json"
        const val LOCAL_SNAPSHOT_FILE_NAME = "latest.json"
        const val PREF_AUTO_SNAPSHOT_ENABLED = "checkbox_config_sync_auto_snapshot"
        const val PREF_BACKGROUND_SYNC_ENABLED = "checkbox_config_sync_background_sync"
        const val PREF_EXTERNAL_SNAPSHOT_ENABLED = "checkbox_config_sync_external_snapshot"
        const val PREF_EXTERNAL_SNAPSHOT_IMPORT = "config_sync_import_external_snapshot"
        const val PREF_EXTERNAL_SYNC_DIRECTORY = "config_sync_select_directory"
        const val PREF_EXTERNAL_SYNC_TREE_URI = "config_sync_external_tree_uri"
        const val PREF_BACKUP_PASSWORD = "config_sync_backup_password"
        const val PREF_LAST_BACKGROUND_SYNC_APPLIED = "config_sync_last_background_sync_applied"
        const val PREF_LAST_BACKGROUND_SYNC_AT = "config_sync_last_background_sync_at"
        const val PREF_LAST_BACKGROUND_SYNC_ERROR = "config_sync_last_background_sync_error"
        const val PREF_LAST_BACKGROUND_SYNC_READ_EXTERNAL = "config_sync_last_background_sync_read_external"
        const val PREF_LAST_BACKGROUND_SYNC_SUCCESS = "config_sync_last_background_sync_success"
        const val PREF_LAST_BACKGROUND_SYNC_WROTE_EXTERNAL = "config_sync_last_background_sync_wrote_external"
        const val PREF_LAST_EXTERNAL_CONTENT_HASH = "config_sync_last_external_content_hash"
        const val PREF_LAST_EXTERNAL_SYNC_AT = "config_sync_last_external_sync_at"
        const val PREF_LAST_EXTERNAL_SYNC_ID = "config_sync_last_external_sync_id"
        const val PREF_LAST_LOCAL_SNAPSHOT_AT = "config_sync_last_local_snapshot_at"
        const val PREF_LOCAL_SNAPSHOT_NOW = "config_sync_snapshot_now"
        const val PREF_SYNC_STATUS = "config_sync_status"
        private const val PREF_DEVICE_ID = "config_sync_device_id"
        private const val PREF_PAIRING_STATE_UPDATED_AT = "config_sync_pairing_state_updated_at"

        private const val SUPPORTED_SCHEMA_VERSION = 2
        private const val BACKUP_DEVICE_KEY_VERSION = "same-device-v1"
        private const val BACKUP_TARGET_SAME_DEVICE = "sameDevice"
        private const val DEVICE_BACKUP_PREFIX = "moonlight-vplus-device-backup."
        private const val DEVICE_SNAPSHOT_PREFIX = "moonlight-vplus-config-sync."
        private const val DEVICE_SNAPSHOT_SUFFIX = ".json"
        private const val LOCAL_SNAPSHOT_DIRECTORY = "config-sync"
        private const val SYNC_LOCK_DIRECTORY = "config-sync"
        private const val SYNC_LOCK_FILE_NAME = "sync.lock"
        private const val CONFIG_SYNC_SECRET_PREFS = "config_sync_secrets"
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val EXTERNAL_PASSWORD_KEY_ALIAS = "moonlight_vplus_config_sync_external_password"
        private const val PREF_EXTERNAL_PASSWORD_IV = "external_password_iv"
        private const val PREF_EXTERNAL_PASSWORD_CIPHERTEXT = "external_password_ciphertext"

        private const val ENCRYPTED_PACKAGE_FORMAT = "moonlight-vplus-config-sync-encrypted"
        private const val ENCRYPTED_PACKAGE_VERSION = 1
        private const val ENCRYPTION_CIPHER_NAME = "AES-256-GCM"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM_AES = "AES"
        private const val KDF_PBKDF2_SHA256 = "PBKDF2WithHmacSHA256"
        private const val KDF_PBKDF2_SHA1 = "PBKDF2WithHmacSHA1"
        private const val AES_KEY_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val GCM_IV_BYTES = 12
        private const val PASSWORD_SALT_BYTES = 16
        private const val PASSWORD_KDF_ITERATIONS = 150_000

        private const val APP_LAST_SETTINGS_PREFS = "app_last_settings"
        private const val APP_VIEW_PREFS = "AppView"
        private const val CUSTOM_RESOLUTIONS_PREFS = "custom_resolutions"
        private const val CURRENT_CROWN_CONFIG_ID_KEY = "current_config_id"
        private const val HIDDEN_APPS_PREFS = "HiddenApps"
        private const val PAIR_NAME_PREFS = "pair_name_map"
        private const val SCENE_CONFIGS_PREFS = "SceneConfigs"
        private const val SYNC_VALUE_STATE_PREFS = "config_sync_value_state"

        private const val KEY_APP_VERSION_CODE = "appVersionCode"
        private const val KEY_APP_VERSION_NAME = "appVersionName"
        private const val KEY_BACKUP_DEVICE_KEY = "backupDeviceKey"
        private const val KEY_BACKUP_TARGET = "backupTarget"
        private const val KEY_DEVICE_ID = "deviceId"
        private const val KEY_EXPORTED_AT = "exportedAt"
        private const val KEY_PACKAGE_NAME = "packageName"
        private const val KEY_SCHEMA_VERSION = "schemaVersion"
        private const val KEY_SECTIONS = "sections"
        private const val KEY_SYNC_ID = "syncId"
        private const val KEY_TYPE = "type"
        private const val KEY_UPDATED_AT = "updatedAt"
        private const val KEY_UPDATED_BY = "updatedBy"
        private const val KEY_VALUE = "value"
        private const val KEY_VALUES = "values"
        private const val KEY_CROWN_PROFILE_DELETED = "deleted"
        private const val KEY_CROWN_PROFILE_ID = "profileId"
        private const val KEY_CROWN_PROFILE_NAME = "name"
        private const val KEY_CROWN_PROFILE_PAYLOAD = "payload"
        private const val KEY_CROWN_PROFILE_SOURCE_CONFIG_ID = "sourceConfigId"
        private const val KEY_ENCRYPTION_CIPHER = "cipher"
        private const val KEY_ENCRYPTION_CIPHERTEXT = "ciphertext"
        private const val KEY_ENCRYPTION_FORMAT = "format"
        private const val KEY_ENCRYPTION_ITERATIONS = "iterations"
        private const val KEY_ENCRYPTION_IV = "iv"
        private const val KEY_ENCRYPTION_KDF = "kdf"
        private const val KEY_ENCRYPTION_SALT = "salt"
        private const val KEY_ENCRYPTION_TAG = "tag"
        private const val KEY_ENCRYPTION_VERSION = "version"
        private const val KEY_PAIRING_ACTIVE_ADDRESS = "activeAddress"
        private const val KEY_PAIRING_ADDRESS = "address"
        private const val KEY_PAIRING_CLIENT_CERTIFICATE = "clientCertificatePem"
        private const val KEY_PAIRING_CLIENT_PRIVATE_KEY = "clientPrivateKeyPkcs8"
        private const val KEY_PAIRING_COMPUTER_NAME = "name"
        private const val KEY_PAIRING_COMPUTER_UUID = "uuid"
        private const val KEY_PAIRING_COMPUTERS = "computers"
        private const val KEY_PAIRING_HTTPS_PORT = "httpsPort"
        private const val KEY_PAIRING_IPV6_ADDRESS = "ipv6Address"
        private const val KEY_PAIRING_IPV6_DISABLED = "ipv6Disabled"
        private const val KEY_PAIRING_LOCAL_ADDRESS = "localAddress"
        private const val KEY_PAIRING_MAC_ADDRESS = "macAddress"
        private const val KEY_PAIRING_MANUAL_ADDRESS = "manualAddress"
        private const val KEY_PAIRING_PAIR_NAME = "pairName"
        private const val KEY_PAIRING_PORT = "port"
        private const val KEY_PAIRING_REMOTE_ADDRESS = "remoteAddress"
        private const val KEY_PAIRING_SERVER_CERTIFICATE = "serverCertificateDer"
        private const val KEY_PAIRING_UNIQUE_ID = "uniqueId"

        private const val SECTION_APP_LAST_SETTINGS = "appLastSettings"
        private const val SECTION_APP_VIEW_PREFERENCES = "appViewPreferences"
        private const val SECTION_CROWN_PROFILES = "crownProfiles"
        private const val SECTION_CUSTOM_RESOLUTIONS = "customResolutions"
        private const val SECTION_DEFAULT_PREFERENCES = "defaultPreferences"
        private const val SECTION_HIDDEN_APPS = "hiddenApps"
        private const val SECTION_PAIRING = "pairing"
        private const val SECTION_SCENE_CONFIGS = "sceneConfigs"

        private const val TYPE_BOOLEAN = "boolean"
        private const val TYPE_DELETED = "deleted"
        private const val TYPE_FLOAT = "float"
        private const val TYPE_INT = "int"
        private const val TYPE_LONG = "long"
        private const val TYPE_STRING = "string"
        private const val TYPE_STRING_SET = "stringSet"

        private const val CROWN_PROFILE_TRACKED_IDS = "crownProfiles.trackedProfileIds"
        private const val CLIENT_CERTIFICATE_FILE_NAME = "client.crt"
        private const val CLIENT_PRIVATE_KEY_FILE_NAME = "client.key"
        private const val COMPUTERS_DATABASE_FILE_NAME = "computers.db"
        private const val PEM_BEGIN_PREFIX = "-----BEGIN "
        private const val PEM_END_PREFIX = "-----END "
        private const val UNIQUE_ID_FILE_NAME = "uniqueid"
        private const val UNIQUE_ID_LENGTH = 16

        private val APP_VIEW_PREF_KEYS = setOf(
            "app_background_mode"
        )

        fun isAutoSnapshotEnabled(context: Context): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_AUTO_SNAPSHOT_ENABLED, false)
        }

        fun isExternalSnapshotEnabled(context: Context): Boolean {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getBoolean(PREF_EXTERNAL_SNAPSHOT_ENABLED, false) &&
                    !prefs.getString(PREF_EXTERNAL_SYNC_TREE_URI, null).isNullOrBlank() &&
                    hasExternalSyncPassword(context)
        }

        fun isBackgroundSyncEnabled(context: Context): Boolean {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getBoolean(PREF_BACKGROUND_SYNC_ENABLED, false) &&
                    isExternalSnapshotEnabled(context)
        }

        fun externalSyncTreeUri(context: Context): Uri? {
            val uriString = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_EXTERNAL_SYNC_TREE_URI, null)
                ?.takeIf { it.isNotBlank() }
                ?: return null
            return Uri.parse(uriString)
        }

        @JvmStatic
        fun recordPairingStateChanged(context: Context) {
            PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
                .edit()
                .putLong(PREF_PAIRING_STATE_UPDATED_AT, System.currentTimeMillis())
                .apply()
            ConfigurationSyncScheduler.requestSyncSoon(context.applicationContext)
        }

        fun isConfigSyncMetadataPreferenceKey(key: String?): Boolean {
            return key == PREF_DEVICE_ID ||
                    key == PREF_PAIRING_STATE_UPDATED_AT ||
                    key == PREF_AUTO_SNAPSHOT_ENABLED ||
                    key == PREF_BACKGROUND_SYNC_ENABLED ||
                    key == PREF_EXTERNAL_SNAPSHOT_ENABLED ||
                    key == PREF_EXTERNAL_SNAPSHOT_IMPORT ||
                    key == PREF_EXTERNAL_SYNC_DIRECTORY ||
                    key == PREF_EXTERNAL_SYNC_TREE_URI ||
                    key == PREF_BACKUP_PASSWORD ||
                    key == PREF_LAST_BACKGROUND_SYNC_APPLIED ||
                    key == PREF_LAST_BACKGROUND_SYNC_AT ||
                    key == PREF_LAST_BACKGROUND_SYNC_ERROR ||
                    key == PREF_LAST_BACKGROUND_SYNC_READ_EXTERNAL ||
                    key == PREF_LAST_BACKGROUND_SYNC_SUCCESS ||
                    key == PREF_LAST_BACKGROUND_SYNC_WROTE_EXTERNAL ||
                    key == PREF_LAST_EXTERNAL_CONTENT_HASH ||
                    key == PREF_LAST_EXTERNAL_SYNC_AT ||
                    key == PREF_LAST_EXTERNAL_SYNC_ID ||
                    key == PREF_LAST_LOCAL_SNAPSHOT_AT ||
                    key == PREF_LOCAL_SNAPSHOT_NOW ||
                    key == PREF_SYNC_STATUS
        }

        fun isPortableDefaultPreferenceKey(key: String): Boolean {
            return key in PORTABLE_DEFAULT_PREF_KEYS
        }

        fun portableSharedPreferenceNames(): List<String> {
            return PORTABLE_SHARED_PREFERENCE_NAMES
        }

        fun isPortableSharedPreferenceKey(sharedPreferencesName: String, key: String?): Boolean {
            if (key.isNullOrBlank()) return false
            return when (sharedPreferencesName) {
                APP_LAST_SETTINGS_PREFS,
                CUSTOM_RESOLUTIONS_PREFS,
                HIDDEN_APPS_PREFS,
                SCENE_CONFIGS_PREFS -> true
                APP_VIEW_PREFS -> key in APP_VIEW_PREF_KEYS
                else -> false
            }
        }

        private val PORTABLE_DEFAULT_PREF_KEYS = setOf(
            "analog_scrolling",
            "background_image_url",
            "background_source",
            "checkbox_adaptive_bitrate",
            "checkbox_absolute_mouse_mode",
            "checkbox_audio_vibration",
            "checkbox_background_audio",
            "checkbox_clipboard_sync_image",
            "checkbox_clipboard_sync_text",
            "checkbox_control_only",
            "checkbox_disable_warnings",
            "checkbox_enable_audio_passthrough",
            "checkbox_enable_audiofx",
            "checkbox_enable_analytics",
            "checkbox_enable_enhanced_touch",
            "checkbox_enable_esc_menu",
            "checkbox_enable_float_ball",
            "checkbox_enable_hdr",
            "checkbox_enable_hdr_high_brightness",
            "checkbox_hdr_brightness_override",
            "checkbox_enable_keyboard_toggle_in_native_touch",
            "checkbox_enable_mic",
            "checkbox_enable_native_mouse_pointer",
            "checkbox_enable_perf_overlay",
            "checkbox_enable_pip",
            "checkbox_enable_post_stream_toast",
            "checkbox_enable_sops",
            "checkbox_enable_spatializer",
            "checkbox_enable_start_key_menu",
            "checkbox_enable_stun",
            "checkbox_enhanced_touch_on_which_side",
            "checkbox_extreme_resume",
            "checkbox_fix_mouse_middle",
            "checkbox_fix_mouse_wheel",
            "checkbox_flip_face_buttons",
            "checkbox_framegen_present_real_first",
            "checkbox_full_range",
            "checkbox_gamepad_motion_fallback",
            "checkbox_gamepad_motion_sensors",
            "checkbox_gamepad_touchpad_as_mouse",
            "checkbox_half_height_osc_portrait",
            "checkbox_host_audio",
            "checkbox_lock_screen_after_disconnect",
            "checkbox_mic_balance",
            "checkbox_mic_gain",
            "checkbox_mic_voice_enhancement",
            "checkbox_mic_volume_processing",
            "checkbox_mouse_emulation",
            "checkbox_mouse_middle",
            "checkbox_mouse_nav_buttons",
            "checkbox_mouse_wheel",
            "checkbox_multi_controller",
            "checkbox_only_show_L3R3",
            "checkbox_reduce_refresh_rate",
            "checkbox_reverse_resolution",
            "checkbox_resume_stream",
            "checkbox_rotable_screen",
            "checkbox_screen_ds5_touchpad",
            "checkbox_show_QuickKeyCard",
            "checkbox_show_bitrate_card",
            "checkbox_show_guide_button",
            "checkbox_show_gyro_card",
            "checkbox_show_onscreen_controls",
            "checkbox_show_onscreen_keyboard",
            "checkbox_small_icon_mode",
            "checkbox_special_key_map",
            "checkbox_stretch_video",
            "checkbox_swap_quit_and_disconnect",
            "checkbox_sync_touch_event_with_display",
            "checkbox_touchscreen_trackpad",
            "checkbox_unlock_fps",
            "checkbox_vibrate_fallback",
            "checkbox_vibrate_osc",
            "enhanced_touch_zone_divider",
            "frame_pacing",
            "gyro_activation_key_code",
            "gyro_invert_x_axis",
            "gyro_invert_y_axis",
            "gyro_sensitivity_multiplier",
            "list_abr_mode",
            "list_audio_codec",
            "list_audio_config",
            "list_audio_passthrough_buffer",
            "list_audio_vibration_mode",
            "list_audio_vibration_scene",
            "list_esc_menu_key",
            "list_float_ball_double_click_action",
            "list_float_ball_long_click_action",
            "list_float_ball_single_click_action",
            "list_float_ball_swipe_down_action",
            "list_float_ball_swipe_left_action",
            "list_float_ball_swipe_right_action",
            "list_float_ball_swipe_up_action",
            "list_fps",
            "list_framegen_quality_preset",
            "list_hdr_mode",
            "list_languages",
            "list_mic_icon_color",
            "list_native_mouse_mode_preset",
            "list_perf_overlay_orientation",
            "list_perf_overlay_position",
            "list_resolution",
            "list_screen_combination_mode",
            "list_screen_position",
            "perf_overlay_display_items",
            "pointer_velocity_factor",
            "pref_enable_double_click_drag",
            "pref_enable_local_cursor_rendering",
            "seekbar_audio_vibration_strength",
            "seekbar_bitrate_kbps",
            "seekbar_deadzone",
            "seekbar_double_tap_time_threshold",
            "seekbar_flat_region_pixels",
            "seekbar_float_ball_auto_hide_delay",
            "seekbar_framegen_internal_width",
            "seekbar_framegen_slow_threshold_ms",
            "seekbar_hdr_peak_brightness_nits",
            "seekbar_keyboard_toggle_fingers_native_touch",
            "seekbar_mic_balance_target",
            "seekbar_mic_bitrate_kbps",
            "seekbar_mic_gain_db",
            "seekbar_osc_opacity",
            "seekbar_output_buffer_queue_limit",
            "seekbar_perf_overlay_bg_opacity",
            "seekbar_resolutions_scale",
            "seekbar_screen_offset_x",
            "seekbar_screen_offset_y",
            "seekbar_vibrate_fallback_strength",
            "video_format"
        )

        private val PORTABLE_SHARED_PREFERENCE_NAMES = listOf(
            APP_LAST_SETTINGS_PREFS,
            APP_VIEW_PREFS,
            CUSTOM_RESOLUTIONS_PREFS,
            HIDDEN_APPS_PREFS,
            SCENE_CONFIGS_PREFS
        )
    }
}
