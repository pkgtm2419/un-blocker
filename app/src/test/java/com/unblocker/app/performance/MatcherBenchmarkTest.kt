package com.unblocker.app.performance

import com.unblocker.app.logic.rules.*
import java.io.File
import org.junit.Test

/** Opt-in diagnostic, no wall-time CI assertions. Deterministic inputs, environment-specific output. */
class MatcherBenchmarkTest {
    @Test fun benchmark() {
        if(System.getenv("UB_BLOCKER_BENCHMARK")!="1") return
        val report=StringBuilder("rules,matcher,parse_ms,construct_ms,retained_index_bytes,p50_ns,p95_ns,p99_ns,allocated_bytes_per_query,tsv_bytes\n")
        for(count in listOf(1000,25000,100000,250000)) {
            val start=System.nanoTime()
            val rules=CompiledRuleSet((0 until count).map { RuleParser.parse("||ads$it.example.test^")!! })
            val parseMs=(System.nanoTime()-start)/1_000_000.0
            val tsvBytes=rules.rules.sumOf { "${it.action}\t${it.kind}\t${it.value}\tAD\t1\n".toByteArray().size }
            for(kind in listOf("hash","packed")) {
                gc()
                val before=heap()
                val construct=System.nanoTime()
                val match: (String)->DnsRule? = if(kind=="hash") DnsRuleEngine(rules)::match else rules.toRuleSet()::match
                val constructMs=(System.nanoTime()-construct)/1_000_000.0
                gc()
                val retained=(heap()-before).coerceAtLeast(0)
                val queries=(0 until 20000).map { if(it%2==0) "sub.ads${(it*7919)%count}.example.test" else "cdn${it}.benign.test" }
                repeat(3) { queries.forEach { match(it) } }
                val allocations=allocationReader()
                val allocatedBefore=allocations()
                val times=LongArray(queries.size)
                queries.forEachIndexed { index,query -> val t=System.nanoTime(); match(query); times[index]=System.nanoTime()-t }
                val allocatedAfter=allocations()
                times.sort()
                val allocation=if(allocatedBefore<0) -1.0 else (allocatedAfter-allocatedBefore).toDouble()/queries.size
                report.append("$count,$kind,$parseMs,$constructMs,$retained,${times[10000]},${times[19000]},${times[19800]},$allocation,$tsvBytes\n")
            }
        }
        val output=File(System.getenv("UB_BLOCKER_BENCHMARK_OUTPUT") ?: "build/matcher-benchmark.csv")
        output.parentFile?.mkdirs()
        output.writeText(report.toString())
        println(report)
    }
    private fun gc() { System.gc(); Thread.sleep(100) }
    private fun heap()=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()
    private fun allocationReader():()->Long = runCatching {
        val bean=Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
        val method=Class.forName("com.sun.management.ThreadMXBean").getMethod("getThreadAllocatedBytes",java.lang.Long.TYPE)
        val id=Thread.currentThread().id
        val reader:()->Long = { (method.invoke(bean,id) as Long) }
        reader
    }.getOrElse { { -1L } }
}
