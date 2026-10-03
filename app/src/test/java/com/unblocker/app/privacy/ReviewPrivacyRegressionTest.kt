package com.unblocker.app.privacy

import com.unblocker.app.logic.analysis.*
import java.io.File
import java.io.Reader
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReviewPrivacyRegressionTest {
    @get:Rule val temp=TemporaryFolder()
    @Test fun obsoleteLearningIsRemovedEvenWhenKeystoreFailsButRulesSurvive() {
        val files=temp.newFolder("files"); val privateFiles=temp.newFolder("private")
        File(files,"learned_trackers.txt").writeText("sensitive.example:0.9")
        for(name in listOf("learning-v1","learning-v1.tmp")) File(privateFiles,name).writeText("obsolete")
        val rule=File(privateFiles,"allowlist-v1").apply {writeText("preserved")}
        val store=EvidenceStoreInitializer.create(files,privateFiles,{throw IllegalStateException("key unavailable")},DeviceLearning::memoryEvidenceStore)
        assertEquals(0,store.size())
        assertFalse(File(files,"learned_trackers.txt").exists())
        assertFalse(File(privateFiles,"learning-v1").exists())
        assertFalse(File(privateFiles,"learning-v1.tmp").exists())
        assertEquals("preserved",rule.readText())
    }
    @Test fun oversizedUnterminatedHeaderAndRecordStopReadingPromptly() {
        for(limit in listOf(68,256)) {
            var readCount=0
            val reader=object:Reader() {
                override fun read(buffer:CharArray,offset:Int,length:Int):Int {
                    readCount++
                    if(readCount>limit+2) fail("Reader consumed unbounded corrupt input")
                    buffer[offset]='x';return 1
                }
                override fun close() {}
            }
            assertThrows(IllegalArgumentException::class.java) { BoundedEvidenceInput.line(reader,limit) }
            assertTrue(readCount<=limit+1)
        }
    }
    @Test fun totalSnapshotLimitRejectsOversizedFileWithoutRestoringRecords() {
        val file=File(temp.root,"snapshot")
        val key=SecretKeySpec(ByteArray(32){7},"HmacSHA256")
        val store=PrivateEvidenceStore(key,file,1,{100})
        store.put("weak.test",ReputationEvidence(.4f,ReputationState.OBSERVING,1,1,UserFeedback.NONE,100))
        store.flush()
        val valid=file.readText()
        file.writeText(valid+"x".repeat(2000))
        assertEquals(0,PrivateEvidenceStore(key,file,1,{100}).size())
    }
}
