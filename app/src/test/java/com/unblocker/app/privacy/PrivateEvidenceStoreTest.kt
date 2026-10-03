package com.unblocker.app.privacy

import com.unblocker.app.logic.analysis.*
import java.io.File
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrivateEvidenceStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private fun key(n:Byte=7) = SecretKeySpec(ByteArray(32){n}, "HmacSHA256")
    @Test fun reloadIsOpaqueBoundedAndRejectsDifferentKeyOrOldVersion() {
        val file = File(temp.root,"evidence")
        val s = PrivateEvidenceStore(key(),file,8,{100})
        repeat(20) { s.put("ads$it.test", ReputationEvidence(.8f,ReputationState.SUSPECT,3,1,UserFeedback.NONE,100)) }
        assertEquals(8,s.size())
        assertFalse(file.readText().contains("ads"))
        assertEquals(8,PrivateEvidenceStore(key(),file,8,{100}).size())
        assertEquals(0,PrivateEvidenceStore(key(8),file).size())
        file.writeText(file.readText().replaceFirst("v3:","v2:"))
        assertEquals(0,PrivateEvidenceStore(key(),file).size())
    }
    @Test fun malformedFieldsAndWeakStaleRecordsAreDiscarded() {
        val file = File(temp.root,"evidence")
        val s=PrivateEvidenceStore(key(),file,8,{100})
        s.put("weak.test",ReputationEvidence(.4f,ReputationState.OBSERVING,1,1,UserFeedback.NONE,100))
        val header=file.readLines().first()
        val id=file.readLines()[1].substringBefore(':')
        for (row in listOf("$id:NaN:SUSPECT:1:1:0:100", "$id:0.8:BAD:1:1:0:100",
            "$id:0.8:SUSPECT:999:1:0:100", "$id:0.8:SUSPECT:1:999:0:100")) {
            file.writeText("$header\n$row\n")
            assertEquals(0,PrivateEvidenceStore(key(),file,8,{100}).size())
        }
        s.put("weak.test",ReputationEvidence(.4f,ReputationState.OBSERVING,1,1,UserFeedback.NONE,100))
        assertEquals(0,PrivateEvidenceStore(key(),file,8,{108}).size())
    }
}
