package com.example.localagent

import android.graphics.Rect
import com.example.localagent.engine.AutonomousEngine
import com.example.localagent.network.AiBridgeClient
import com.example.localagent.serializer.ScreenSerializer
import org.json.JSONObject
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
