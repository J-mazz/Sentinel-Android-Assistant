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
    fun `extract parses direct array`() {
        val result = JsonExtractor.extract("[{\"action\":\"BACK\"}]")
        val json = (result as JsonExtractor.ExtractionResult.ArraySuccess).json
        assertEquals("BACK", json.getJSONObject(0).getString("action"))
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

    @Test
    fun `extract handles escaped quotes in strings`() {
        val json = """{"action":"TYPE","text":"Say \"hello\" to them"}"""
        val result = JsonExtractor.extract(json)
        val parsed = (result as JsonExtractor.ExtractionResult.Success).json
        assertEquals("TYPE", parsed.getString("action"))
        assertTrue(parsed.getString("text").contains("hello"))
    }

    @Test
    fun `extract handles complex nested objects`() {
        val json = """{
            "action":"CLICK",
            "metadata":{
                "confidence":0.95,
                "targets":["btn1","btn2"]
            }
        }"""
        val result = JsonExtractor.extract(json)
        val parsed = (result as JsonExtractor.ExtractionResult.Success).json
        assertEquals("CLICK", parsed.getString("action"))
        assertEquals(0.95, parsed.getJSONObject("metadata").getDouble("confidence"), 0.01)
    }

    @Test
    fun `extract handles single quotes conversion`() {
        val json = "{'action':'SCROLL','direction':'DOWN'}"
        val result = JsonExtractor.extract(json)
        // Should successfully repair single quotes to double quotes
        assertEquals(JsonExtractor.ExtractionResult.Success::class.java, result::class.java)
    }

    @Test
    fun `extract handles unquoted JSON keys`() {
        val json = """{action:"TYPE", text:"hello", confidence: 0.8}"""
        val result = JsonExtractor.extract(json)
        val parsed = (result as JsonExtractor.ExtractionResult.Success).json
        assertEquals("TYPE", parsed.getString("action"))
        assertEquals("hello", parsed.getString("text"))
    }

    @Test
    fun `extract handles mixed markdown blocks`() {
        val text = """
            Here's the code:
            ```
            {"action":"HOME","reasoning":"return home"}
            ```
            Thanks!
        """.trimIndent()
        val result = JsonExtractor.extract(text)
        val parsed = (result as JsonExtractor.ExtractionResult.Success).json
        assertEquals("HOME", parsed.getString("action"))
    }

    @Test
    fun `extract handles array with multiple objects`() {
        val json = """[{"id":1,"name":"first"},{"id":2,"name":"second"}]"""
        val result = JsonExtractor.extract(json)
        val array = (result as JsonExtractor.ExtractionResult.ArraySuccess).json
        assertEquals(2, array.length())
        assertEquals("first", array.getJSONObject(0).getString("name"))
    }

    @Test
    fun `extract handles empty string gracefully`() {
        val result = JsonExtractor.extract("")
        assertTrue(result is JsonExtractor.ExtractionResult.Failure)
    }

    @Test
    fun `extract handles whitespace-heavy JSON`() {
        val json = """
            {
              "action"  :  "BACK"  ,
              "reasoning"  :  "User pressed back"
            }
        """.trimIndent()
        val result = JsonExtractor.extract(json)
        val parsed = (result as JsonExtractor.ExtractionResult.Success).json
        assertEquals("BACK", parsed.getString("action"))
    }

    @Test
    fun `extract handles JSON in wrapped response text`() {
        val text = """
            The system decided to:
            ```json
            {
              "action": "CLICK",
              "target": "confirm_button",
              "confidence": 0.92
            }
            ```
            This should trigger the confirmation.
        """.trimIndent()
        val result = JsonExtractor.extract(text)
        val parsed = (result as JsonExtractor.ExtractionResult.Success).json
        assertEquals("CLICK", parsed.getString("action"))
        assertEquals("confirm_button", parsed.getString("target"))
    }
}
