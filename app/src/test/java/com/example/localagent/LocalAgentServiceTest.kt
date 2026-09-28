package com.example.localagent

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class LocalAgentServiceTest {

    @Test
    fun testTraverseAndExtractNode_singleNodeRecycled() {
        val service = LocalAgentService()
        val mockNode = mock(AccessibilityNodeInfo::class.java)

        `when`(mockNode.isVisibleToUser).thenReturn(true)
        `when`(mockNode.text).thenReturn("Click Me")
        `when`(mockNode.contentDescription).thenReturn("Button Description")
        `when`(mockNode.className).thenReturn("android.widget.Button")
        `when`(mockNode.childCount).thenReturn(0)

        val result = mutableListOf<NodeData>()
        service.traverseAndExtractNode(mockNode, result)

        assertEquals(1, result.size)
        assertEquals("Click Me", result[0].text)
        assertEquals("Button Description", result[0].contentDescription)
        assertEquals("android.widget.Button", result[0].className)

        // Root node passed into traverseAndExtractNode is NOT recycled inside traverseAndExtractNode itself
        // (the caller recycles the root node).
        verify(mockNode, never()).recycle()
    }

    @Test
    fun testTraverseAndExtractNode_childrenRecycled() {
        val service = LocalAgentService()
        val rootNode = mock(AccessibilityNodeInfo::class.java)
        val childNode = mock(AccessibilityNodeInfo::class.java)

        `when`(rootNode.isVisibleToUser).thenReturn(true)
        `when`(rootNode.childCount).thenReturn(1)
        `when`(rootNode.getChild(0)).thenReturn(childNode)

        `when`(childNode.isVisibleToUser).thenReturn(true)
        `when`(childNode.text).thenReturn("Child Text")
        `when`(childNode.childCount).thenReturn(0)

        val result = mutableListOf<NodeData>()
        service.traverseAndExtractNode(rootNode, result)

        assertEquals(2, result.size)
        assertEquals("Child Text", result[1].text)

        // Child node fetched via getChild(i) MUST be explicitly recycled!
        verify(childNode).recycle()
    }
}
