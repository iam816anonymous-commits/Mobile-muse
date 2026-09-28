package com.example.localagent

import android.graphics.Rect
import com.example.localagent.engine.AutonomousEngine
import com.example.localagent.engine.QueryFormulator
import com.example.localagent.engine.SelfHealingResolver
import com.example.localagent.engine.StallDetector
import com.example.localagent.intents.CapabilityDomain
import com.example.localagent.network.AiBridgeClient
import com.example.localagent.serializer.ScreenSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutonomousEngineAndAppResolverTest {

    @Test
    fun testScreenSerializer_indexedOutput() {
        val node1 = NodeData(
            text = "Search or type URL",
            contentDescription = "",
            className = "android.widget.EditText",
            boundsInScreen = Rect(54, 90, 980, 210),
            hasActions = true
        )
        val serialized = ScreenSerializer.serializeScreen(listOf(node1))

        assertTrue(serialized.contains("\"index\":0"))
        assertTrue(serialized.contains("\"type\":\"EditText\""))
    }

    @Test
    fun testReActResponseParsing() {
        val json = """{"thought": "Need to click search", "action": "CLICK", "target_index": 0, "is_complete": false}"""
        val rule = AutonomousEngine.parseAiActionResponse(json)

        assertNotNull(rule)
    }

    @Test
    fun testReActCompletionParsing() {
        val json = """{"thought": "Task finished", "action": "TERMINATE", "is_complete": true, "extracted_result": "42"}"""
        val rule = AutonomousEngine.parseAiActionResponse(json)

        assertNotNull(rule)
        assertEquals("42", rule?.textPayload)
    }

    @Test
    fun testStallDetectorAndQueryFormulator() {
        StallDetector.reset()
        StallDetector.recordFailure()
        StallDetector.recordFailure()

        assertTrue(StallDetector.isStalled(hasTargetIndex = true))

        val ctx = StallDetector.buildContext("com.google.android.keep", "Record memo", listOf("Record", "Settings"))
        val query = QueryFormulator.formulateQuery(ctx)

        assertTrue(query.contains("How to Record memo in Keep Android"))
    }

    @Test
    fun testSelfHealingResolver_parseResponse() {
        val json = """{"target_keyword": "More options", "action": "CLICK"}"""
        val (keyword, rule) = SelfHealingResolver.parseHealingResponse(json)

        assertEquals("More options", keyword)
        assertEquals("More options", rule.textPayload)
    }

    @Test
    fun testCapabilityDomains() {
        assertEquals("DOMAIN_NOTES", CapabilityDomain.DOMAIN_NOTES.name)
        assertEquals("DOMAIN_CLOCK", CapabilityDomain.DOMAIN_CLOCK.name)
        assertEquals("DOMAIN_VOICE_RECORDER", CapabilityDomain.DOMAIN_VOICE_RECORDER.name)
        assertEquals("DOMAIN_LOCAL_VIDEO", CapabilityDomain.DOMAIN_LOCAL_VIDEO.name)
        assertEquals("DOMAIN_ONLINE_VIDEO", CapabilityDomain.DOMAIN_ONLINE_VIDEO.name)
    }

    @Test
    fun testAiBridgeClient_parseAgentAction() {
        val client = AiBridgeClient()
        val jsonInput = """{"action": "CLICK", "target_index": 1, "input_text": null}"""
        val action = client.parseAgentAction(jsonInput)

        assertNotNull(action)
        assertEquals("CLICK", action?.action)
        assertEquals(1, action?.targetIndex)
    }

    @Test
    fun testParseAiActionResponse() {
        val rule = AutonomousEngine.parseAiActionResponse("""{"action": "INPUT", "input_text": "Ask Gemini"}""")
        assertNotNull(rule)
        assertEquals("Ask Gemini", rule?.textPayload)
    }
}
