package com.unblocker.app.quality

import com.unblocker.app.logic.AdDetector
import com.unblocker.app.logic.AdultContentDetector
import com.unblocker.app.logic.analysis.AdaptiveBlockingEngine
import com.unblocker.app.logic.analysis.LocalNetworkLearner
import com.unblocker.app.domain.usecase.DecideBlockingUseCase
import com.unblocker.app.logic.rules.CompiledRuleSet
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class CompiledCorpusTest {
    @Test fun productionRulesMeetLabeledCorpusFalsePositiveBaseline() {
        val rules=File("build/generated/dns-assets/dns-rules.tsv").reader().use { CompiledRuleSet.fromTsv(it) }
        val detector=AdDetector(compiledRules=rules)
        val adult=AdultContentDetector().apply {
            rules.rules.filter { it.category==com.unblocker.app.domain.model.BlockingCategory.ADULT_CONTENT }
                .forEach { addDomain(it.value) }
        }
        val pipeline=DecideBlockingUseCase(detector,adult,AdaptiveBlockingEngine(null,LocalNetworkLearner()))
        val rows=javaClass.getResourceAsStream("/classifier-corpus.tsv")!!.bufferedReader().use { it.readLines() }
        var tp=0; var fp=0; var tn=0; var fn=0
        rows.filter { !it.startsWith("#") && it.isNotBlank() }.forEach {
            val fields=it.split('\t'); val expected=fields[1]=="BLOCK"
            val actual=pipeline(fields[0]).isBlocked
            if(actual && expected) tp++ else if(actual) fp++ else if(expected) fn++ else tn++
        }
        val precision=tp.toDouble()/(tp+fp).coerceAtLeast(1)
        val recall=tp.toDouble()/(tp+fn).coerceAtLeast(1)
        val fpr=fp.toDouble()/(fp+tn).coerceAtLeast(1)
        val fnr=fn.toDouble()/(fn+tp).coerceAtLeast(1)
        File("build/reports/corpus").mkdirs()
        File("build/reports/corpus/metrics.txt").writeText("TP=$tp FP=$fp TN=$tn FN=$fn precision=$precision recall=$recall FPR=$fpr FNR=$fnr\n")
        assertTrue("Corpus must contain both classes",tp+fn>=10 && fp+tn>=10)
        assertEquals("False positives exceed checked-in baseline",0,fp)
        assertEquals("Curated known rules regressed",0,fn)
    }
    @Test fun invalidCompiledFieldsFailClosed() {
        for(row in listOf("BLOCK\tSUFFIX\tbad..test\tAD\t1", "BLOCK\tSUFFIX\ttest.com\tAD\t-1", "BLOCK\tWHAT\ttest.com\tAD\t1")) {
            try { CompiledRuleSet.fromTsv(row.reader()); fail("Accepted invalid asset") }
            catch(expected:IllegalArgumentException) {}
        }
    }
}
