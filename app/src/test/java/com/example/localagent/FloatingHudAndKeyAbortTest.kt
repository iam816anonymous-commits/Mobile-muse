package com.example.localagent

import android.view.KeyEvent
import com.example.localagent.memory.MemoryLedger
import com.example.localagent.state.AgentStatus
import com.example.localagent.state.TaskGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class FloatingHudAndKeyAbortTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testOnKeyEvent_doublePressVolumeDownTriggersAbort() {
        val service = LocalAgentService()
        val ledgerFile = tempFolder.newFile("test_key_abort_ledger.json")
        service.memoryLedger = MemoryLedger(ledgerFile)

        service.stateManager.startTask(TaskGoal("test", "Test Goal"))
        assertEquals(AgentStatus.RUNNING, service.stateManager.getCurrentState().status)

        val keyEvent1 = mock(KeyEvent::class.java)
        `when`(keyEvent1.keyCode).thenReturn(KeyEvent.KEYCODE_VOLUME_DOWN)
        `when`(keyEvent1.action).thenReturn(KeyEvent.ACTION_DOWN)

        // First press: records time, does not abort
        val handled1 = service.onKeyEvent(keyEvent1)
        assertFalse(handled1)
        assertEquals(AgentStatus.RUNNING, service.stateManager.getCurrentState().status)

        // Second press immediately after: triggers abort and returns true
        val keyEvent2 = mock(KeyEvent::class.java)
        `when`(keyEvent2.keyCode).thenReturn(KeyEvent.KEYCODE_VOLUME_DOWN)
        `when`(keyEvent2.action).thenReturn(KeyEvent.ACTION_DOWN)

        val handled2 = service.onKeyEvent(keyEvent2)
        assertTrue(handled2)
        assertEquals(AgentStatus.IDLE, service.stateManager.getCurrentState().status)
    }

    @Test
    fun testOnKeyEvent_singlePressDoesNotAbort() {
        val service = LocalAgentService()
        val ledgerFile = tempFolder.newFile("test_key_single_ledger.json")
        service.memoryLedger = MemoryLedger(ledgerFile)

        service.stateManager.startTask(TaskGoal("test", "Test Goal"))

        val keyEvent = mock(KeyEvent::class.java)
        `when`(keyEvent.keyCode).thenReturn(KeyEvent.KEYCODE_VOLUME_DOWN)
        `when`(keyEvent.action).thenReturn(KeyEvent.ACTION_DOWN)

        val handled = service.onKeyEvent(keyEvent)
        assertFalse(handled)
        assertEquals(AgentStatus.RUNNING, service.stateManager.getCurrentState().status)
    }
}
