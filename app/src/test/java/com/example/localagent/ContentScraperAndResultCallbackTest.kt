package com.example.localagent

import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.scraper.ContentScraper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class ContentScraperAndResultCallbackTest {

    @Test
    fun testContentScraper_collectLeafText() {
        val rootNode = mock(AccessibilityNodeInfo::class.java)
        val childNode = mock(AccessibilityNodeInfo::class.java)

        `when`(rootNode.childCount).thenReturn(1)
        `when`(rootNode.getChild(0)).thenReturn(childNode)
        `when`(rootNode.text).thenReturn("Root Header")

        `when`(childNode.text).thenReturn("Child Body Response")
        `when`(childNode.childCount).thenReturn(0)

        val collectedText = ContentScraper.collectLeafText(rootNode)
        assertTrue(collectedText.contains("Child Body Response"))
    }

    @Test
    fun testBroadcastGoalCompleted_constructsCorrectIntent() {
        val service = mock(LocalAgentService::class.java)

        val captor = ArgumentCaptor.forClass(Intent::class.java)
        org.mockito.Mockito.doCallRealMethod().`when`(service).broadcastGoalCompleted(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString()
        )

        service.broadcastGoalCompleted("Query Quantum", "SUCCESS", "Quantum result text")

        verify(service).sendBroadcast(captor.capture())
        val intent = captor.value
        assertNotNull(intent)
    }
}
