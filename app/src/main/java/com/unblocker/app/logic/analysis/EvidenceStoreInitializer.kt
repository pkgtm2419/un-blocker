package com.unblocker.app.logic.analysis

import java.io.File
import java.io.IOException
import javax.crypto.SecretKey

/** Discards obsolete learning before opening v3; never touches user rule files or keys. */
internal object EvidenceStoreInitializer {
    fun create(filesDir:File,noBackupFilesDir:File,keyProvider:()->SecretKey,
               volatileFallback:()->PrivateEvidenceStore):PrivateEvidenceStore {
        val obsolete=listOf(File(filesDir,"learned_trackers.txt"),
            File(noBackupFilesDir,"learning-v1"),File(noBackupFilesDir,"learning-v1.tmp"))
        for(file in obsolete) {
            if(file.exists() && !file.delete()) throw IOException("Obsolete learning cleanup failed")
        }
        return try {
            PrivateEvidenceStore(keyProvider(),File(noBackupFilesDir,"learning-v3"))
        } catch (_:Exception) { volatileFallback() }
    }
}
