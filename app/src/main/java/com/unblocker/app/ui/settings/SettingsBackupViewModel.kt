package com.unblocker.app.ui.settings

import androidx.lifecycle.ViewModel
import com.unblocker.app.data.export.BackupRestoreSummary
import com.unblocker.app.data.export.SettingsBackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/**
 * ViewModel exposing user-controlled SAF settings backup and restore operations (WP-22 / F31).
 */
@HiltViewModel
class SettingsBackupViewModel
    @Inject
    constructor(
        private val backupManager: SettingsBackupManager,
    ) : ViewModel() {
        /** Writes the non-secret settings and rules JSON backup to [outputStream]. */
        suspend fun exportBackup(outputStream: OutputStream) = backupManager.exportBackup(outputStream)

        /** Validates and restores settings, extensions, and readable rules from [inputStream]. */
        suspend fun restoreBackup(inputStream: InputStream): BackupRestoreSummary =
            backupManager.restoreBackup(inputStream)
    }
