package com.example.localagent

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.gestures.ActionExecutor
import com.example.localagent.gestures.GestureExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class GestureAndStabilizationTest {

    @Test
    fun testPerformClickWithFallback_directActionSucceeds() {
        val mockNode = mock(AccessibilityNodeInfo::class.java)
        val mockGestureExecutor = mock(GestureExecutor::class.java)

        `when`(mockNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)).thenReturn(true)

        val result = ActionExecutor.performClickWithFallback(mockNode, mockGestureExecutor)
        assertTrue(result)

        verify(mockGestureExecutor, never()).tap(
            anyFloat(),
            anyFloat(),
            any()
        )
    }

    @Test
    fun testPerformClickWithFallback_triggersCoordinateTapOnFailure() {
        val mockNode = mock(AccessibilityNodeInfo::class.java)
        val mockGestureExecutor = mock(GestureExecutor::class.java)

        `when`(mockNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)).thenReturn(false)
        `when`(
            mockGestureExecutor.tap(
                anyFloat(),
                anyFloat(),
                any()
            )
        ).thenReturn(true)

        val result = ActionExecutor.performClickWithFallback(mockNode, mockGestureExecutor)
        assertTrue(result)

        verify(mockGestureExecutor).tap(
            anyFloat(),
            anyFloat(),
            any()
        )
    }

    @Test
    fun testWaitForNodeOrTimeout_timeoutReturnsNull() {
        val service = object : LocalAgentService() {
            override fun getActiveWindowRoot(): AccessibilityNodeInfo? = null
        }

        var callbackResult: AccessibilityNodeInfo? = mock(AccessibilityNodeInfo::class.java)
        var callbackInvoked = false

        val lock = Object()

        service.waitForNodeOrTimeout({ false }, 250L) { resultNode ->
            synchronized(lock) {
                callbackResult = resultNode
                callbackInvoked = true
                lock.notifyAll()
            }
        }

        synchronized(lock) {
            if (!callbackInvoked) {
                lock.wait(1000L)
            }
        }

        assertTrue(callbackInvoked)
        assertNull(callbackResult)
    }
}
