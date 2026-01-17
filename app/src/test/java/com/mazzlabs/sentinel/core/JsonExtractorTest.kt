package com.mazzlabs.sentinel.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonExtractorTest {

    @Test
    fun `extract parses direct object`() {
        val result = JsonExtractor.extract("{\"action\":\"NONE\"}")
        val json = (result as JsonExtractor.ExtractionResult.Success).json
        assertEquals("NONE", json.getString("action"))
    }

    @Test
    fun `extract parses markdown code block`() {
        val text = """
            ```json
            {"action":"CLICK","target":"ok"}
            ```
        """.trimIndent()
        val result = JsonExtractor.extract(text)
        val json = (result as JsonExtractor.ExtractionResult.Success).json
        assertEquals("CLICK", json.getString("action"))
    }

    @Test
    fun `extract parses balanced JSON in text`() {
        val text = "Here you go: {\"action\":\"BACK\"} thanks!"
        val result = JsonExtractor.extract(text)
        val json = (result as JsonExtractor.ExtractionResult.Success).json
        assertEquals("BACK", json.getString("action"))
    }

    @Test
    fun `extract repairs common issues`() {
        val text = "{action:'TYPE', text:'hello',}"
        val result = JsonExtractor.extract(text)
        val json = (result as JsonExtractor.ExtractionResult.Success).json
        assertEquals("TYPE", json.getString("action"))
        assertEquals("hello", json.getString("text"))
    }

    @Test
    fun `extract returns failure when no JSON`() {
        val result = JsonExtractor.extract("not json")
        assertTrue(result is JsonExtractor.ExtractionResult.Failure)
        val failure = result as JsonExtractor.ExtractionResult.Failure
        assertTrue(failure.attempts.contains("direct_parse_failed"))
    }
}
