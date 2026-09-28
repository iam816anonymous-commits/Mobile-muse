package com.example.localagent

import android.graphics.Rect
import com.example.localagent.memory.MemoryLedger
import com.example.localagent.network.AiBridgeClient
import com.example.localagent.serializer.ScreenSerializer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class AiBridgeAndSerializerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testScreenSerializer_compactJsonFormatting() {
        val rect1 = mock(Rect::class.java)
        rect1.left = 10
        rect1.top = 20
        rect1.right = 100
        rect1.bottom = 200

        val rect2 = mock(Rect::class.java)
        rect2.left = 0
        rect2.top = 0
        rect2.right = 50
        rect2.bottom = 50

        val nodes = listOf(
            NodeData(
                text = "Login",
                contentDescription = null,
                className = "android.widget.Button",
                boundsInScreen = rect1
            ),
            NodeData(
                text = null,
                contentDescription = "User Avatar",
                className = "android.widget.ImageView",
                boundsInScreen = rect2
            )
        )

        val serialized = ScreenSerializer.serializeScreen(nodes)
        val jsonArray = JSONArray(serialized)

        assertEquals(2, jsonArray.length())

        val obj0 = jsonArray.getJSONObject(0)
        assertEquals("Login", obj0.getString("text"))
        assertEquals("android.widget.Button", obj0.getString("class"))
        assertFalse(obj0.has("desc"))
        val bounds0 = obj0.getJSONObject("bounds")
        assertEquals(10, bounds0.getInt("l"))
        assertEquals(20, bounds0.getInt("t"))

        val obj1 = jsonArray.getJSONObject(1)
        assertFalse(obj1.has("text"))
        assertEquals("User Avatar", obj1.getString("desc"))
    }

    @Test
    fun testMemoryLedger_ruleCachingAndOfflineLookup() {
        val file = tempFolder.newFile("rules_ledger.json")
        val ledger = MemoryLedger(file)

        val screenStateSig = "[{\"text\":\"Submit\",\"class\":\"android.widget.Button\"}]"
        assertNull(ledger.getCachedRule(screenStateSig))

        ledger.cacheRule(screenStateSig, "CLICK_NODE_AT_BOUNDS")

        assertEquals("CLICK_NODE_AT_BOUNDS", ledger.getCachedRule(screenStateSig))

        // Reload from file
        val reloadedLedger = MemoryLedger(file)
        assertEquals("CLICK_NODE_AT_BOUNDS", reloadedLedger.getCachedRule(screenStateSig))
    }

    @Test
    fun testAiBridgeClient_bodyFormatting() {
        val client = AiBridgeClient(apiKey = "test_key")
        val requestBody = client.buildGeminiRequestBody("[{\"text\":\"Hello\"}]", "Find Search Bar")

        val rootObj = JSONObject(requestBody)
        assertTrue(rootObj.has("contents"))
        val contentsArray = rootObj.getJSONArray("contents")
        assertEquals(1, contentsArray.length())
        val partsArray = contentsArray.getJSONObject(0).getJSONArray("parts")
        val promptText = partsArray.getJSONObject(0).getString("text")

        assertTrue(promptText.contains("Find Search Bar"))
        assertTrue(promptText.contains("[{\"text\":\"Hello\"}]"))
    }
}
