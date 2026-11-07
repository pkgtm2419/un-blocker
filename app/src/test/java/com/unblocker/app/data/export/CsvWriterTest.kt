package com.unblocker.app.data.export

import com.unblocker.data.db.entity.DomainStatEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.StringWriter

class CsvWriterTest {

    @Test
    fun testEscapeCellStandard() {
        assertEquals("google.com", CsvWriter.escapeCell("google.com"))
        assertEquals("\"google,com\"", CsvWriter.escapeCell("google,com"))
        assertEquals("\"quotes\"\"test\"", CsvWriter.escapeCell("quotes\"test"))
    }

    @Test
    fun testFormulaInjectionGuarded() {
        // Cells starting with =, +, -, @ must be prefixed with single quote
        assertEquals("'=1+1", CsvWriter.escapeCell("=1+1"))
        assertEquals("'+cmd", CsvWriter.escapeCell("+cmd"))
        assertEquals("'-calc", CsvWriter.escapeCell("-calc"))
        assertEquals("'@SUM(A1:A10)", CsvWriter.escapeCell("@SUM(A1:A10)"))
    }

    @Test
    fun testDomainExporterOutputsValidCsv() {
        val stats = listOf(
            DomainStatEntity(
                domain = "example.com",
                callCount = 10,
                blockedCount = 0,
                allowedCount = 10,
                firstSeen = 1700000000000L,
                lastSeen = 1700001000000L,
                lastDecision = 0,
                lastReason = 0
            ),
            DomainStatEntity(
                domain = "tracker.com",
                callCount = 5,
                blockedCount = 5,
                allowedCount = 0,
                firstSeen = 1700000000000L,
                lastSeen = 1700002000000L,
                lastDecision = 1,
                lastReason = 1
            )
        )

        val output = ByteArrayOutputStream()
        val count = DomainExporter.exportToCsv(stats, output)
        assertEquals(2, count)

        val text = output.toString("UTF-8")
        assertTrue(text.contains("Domain,LastDecision,TotalQueries"))
        assertTrue(text.contains("example.com,ALLOWED,10,0,10"))
        assertTrue(text.contains("tracker.com,BLOCKED,5,5,0"))
    }
}
