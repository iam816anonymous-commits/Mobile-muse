package com.example.localagent

import android.content.ComponentCallbacks2
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.memory.MemoryLedger
import com.example.localagent.serializer.ScreenSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class MemoryOptimizationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testOnTrimMemory_clearsRuleCache() {
        val file = tempFolder.newFile("test_trim_ledger.json")
        val service = LocalAgentService()
        service.memoryLedger = MemoryLedger(file)

        service.memoryLedger.cacheRule("[{\"text\":\"Button\"}]", "CLICK")
        assertEquals("CLICK", service.memoryLedger.getCachedRule("[{\"text\":\"Button\"}]"))

        service.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        assertNull(service.memoryLedger.getCachedRule("[{\"text\":\"Button\"}]"))
    }

    @Test
    fun testTraverseAndExtractNode_filtersInvisibleAndCapsDepthAt7() {
        val service = LocalAgentService()
        val visibleNode = mock(AccessibilityNodeInfo::class.java)
        val invisibleNode = mock(AccessibilityNodeInfo::class.java)

        `when`(visibleNode.isVisibleToUser).thenReturn(true)
        `when`(visibleNode.text).thenReturn("Visible")
        `when`(visibleNode.childCount).thenReturn(0)

        `when`(invisibleNode.isVisibleToUser).thenReturn(false)

        val resultVisible = mutableListOf<NodeData>()
        service.traverseAndExtractNode(visibleNode, resultVisible)
        assertEquals(1, resultVisible.size)

        val resultInvisible = mutableListOf<NodeData>()
        service.traverseAndExtractNode(invisibleNode, resultInvisible)
        assertEquals(0, resultInvisible.size)

        val depthNode = mock(AccessibilityNodeInfo::class.java)
        `when`(depthNode.isVisibleToUser).thenReturn(true)
        val resultDepth = mutableListOf<NodeData>()
        service.traverseAndExtractNode(depthNode, resultDepth, currentDepth = 7)
        assertEquals(0, resultDepth.size)
    }

    @Test
    fun testScreenSerializer_skipsEmptyContainers() {
        val nodes = listOf(
            NodeData(
                text = null,
                contentDescription = null,
                className = "android.widget.LinearLayout",
                boundsInScreen = Rect(0, 0, 100, 100),
                hasActions = false
            ),
            NodeData(
                text = "Clickable Text",
                contentDescription = null,
                className = "android.widget.TextView",
                boundsInScreen = Rect(10, 10, 50, 50),
                hasActions = true
            )
        )

        val serialized = ScreenSerializer.serializeScreen(nodes)
        val jsonArray = org.json.JSONArray(serialized)

        // Only the TextView with text/actions should be included in serialized JSON
        assertEquals(1, jsonArray.length())
        assertEquals("Clickable Text", jsonArray.getJSONObject(0).getString("text"))
    }
}
