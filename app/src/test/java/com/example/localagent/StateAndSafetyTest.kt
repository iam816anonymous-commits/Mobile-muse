package com.example.localagent

import com.example.localagent.memory.MemoryLedger
import com.example.localagent.safety.KillSwitchReceiver
import com.example.localagent.state.AgentStatus
import com.example.localagent.state.TaskGoal
import com.example.localagent.state.TaskStateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StateAndSafetyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testTaskStateManager_lifecycleAndCircuitBreaker() {
        val manager = TaskStateManager(maxStepsLimit = 15)
        assertEquals(AgentStatus.IDLE, manager.getCurrentState().status)

        val goal = TaskGoal(id = "1", description = "Test Goal", maxSteps = 15)
        manager.startTask(goal)

        assertEquals(AgentStatus.RUNNING, manager.getCurrentState().status)
        assertEquals(0, manager.getCurrentState().currentStepIndex)
        assertTrue(manager.getCurrentState().activeStateFlags["isProcessing"] == true)

        // Increment up to step index 14
        for (i in 1 until 15) {
            val canContinue = manager.incrementStep()
            assertTrue("Step $i should be allowed", canContinue)
            assertEquals(i, manager.getCurrentState().currentStepIndex)
            assertEquals(AgentStatus.RUNNING, manager.getCurrentState().status)
        }

        // 15th step increment (reaching index 15 >= maxSteps 15) triggers circuit breaker
        val canContinue15 = manager.incrementStep()
        assertFalse("Circuit breaker should halt on 15th action", canContinue15)
        assertEquals(AgentStatus.HALTED, manager.getCurrentState().status)
        assertNotNull(manager.getCurrentState().failureReason)
        assertTrue(manager.getCurrentState().failureReason!!.contains("Circuit Breaker"))
    }

    @Test
    fun testMemoryLedger_recordingAndPersistence() {
        val storageFile = tempFolder.newFile("test_ledger.json")
        val ledger = MemoryLedger(storageFile)

        ledger.recordStep(0, "CLICK_BUTTON", true)
        ledger.recordStep(1, "SUBMIT_FORM", false, "TIMEOUT")

        assertEquals(2, ledger.getEntries().size)
        assertTrue(ledger.hasFailedRecently("SUBMIT_FORM", "TIMEOUT"))
        assertFalse(ledger.hasFailedRecently("CLICK_BUTTON", null))

        // Test persistence reload
        val reloadedLedger = MemoryLedger(storageFile)
        assertEquals(2, reloadedLedger.getEntries().size)
        assertEquals("SUBMIT_FORM", reloadedLedger.getEntries()[1].action)
        assertEquals("TIMEOUT", reloadedLedger.getEntries()[1].failureCode)
    }

    @Test
    fun testKillSwitchReceiver_triggering() {
        var killTriggered = false
        val receiver = KillSwitchReceiver {
            killTriggered = true
        }

        val mockIntent = org.mockito.Mockito.mock(android.content.Intent::class.java)
        org.mockito.Mockito.`when`(mockIntent.action).thenReturn(KillSwitchReceiver.ACTION_KILL_SWITCH)

        receiver.onReceive(null, mockIntent)
        assertTrue("Kill switch trigger callback should be invoked", killTriggered)
    }
}
