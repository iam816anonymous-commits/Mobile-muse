package com.example.localagent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.localagent.engine.AppResolver
import com.example.localagent.engine.AutonomousEngine
import com.example.localagent.memory.ActionType
import com.example.localagent.receiver.GoalBroadcastReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class AutonomousEngineAndAppResolverTest {

    @Test
    fun testAppResolver_packageResolution() {
        val mockContext = mock(Context::class.java)
        val mockPm = mock(PackageManager::class.java)
        `when`(mockContext.packageManager).thenReturn(mockPm)

        `when`(mockPm.getLaunchIntentForPackage(AppResolver.PKG_GEMINI)).thenReturn(mock(Intent::class.java))
        val geminiResult = AppResolver.resolveAndLaunch(mockContext, "Ask Gemini quantum physics")
        assertTrue(geminiResult)

        `when`(mockPm.getLaunchIntentForPackage(AppResolver.PKG_CHROME)).thenReturn(mock(Intent::class.java))
        val chromeResult = AppResolver.resolveAndLaunch(mockContext, "Search news on Chrome")
        assertTrue(chromeResult)
    }

    @Test
    fun testGoalBroadcastReceiver_triggersCallback() {
        val mockService = mock(LocalAgentService::class.java)
        `when`(mockService.stateManager).thenReturn(com.example.localagent.state.TaskStateManager())

        var receivedGoal: String? = null
        val receiver = GoalBroadcastReceiver(mockService) { goal ->
            receivedGoal = goal
        }

        val intent = mock(Intent::class.java)
        `when`(intent.action).thenReturn(GoalBroadcastReceiver.ACTION_EXECUTE_GOAL)
        `when`(intent.getStringExtra(GoalBroadcastReceiver.EXTRA_GOAL_TEXT)).thenReturn("Ask ChatGPT a question")

        receiver.onReceive(mockService, intent)

        assertEquals("Ask ChatGPT a question", receivedGoal)
    }

    @Test
    fun testAutonomousEngine_parseAiActionResponseJson() {
        val clickJson = "{\"action\":\"CLICK\", \"target_text\":\"Submit\"}"
        val clickRule = AutonomousEngine.parseAiActionResponse(clickJson)
        assertNotNull(clickRule)
        assertEquals(ActionType.CLICK, clickRule?.type)
        assertEquals("Submit", clickRule?.textPayload)

        val inputJson = "{\"action\":\"INPUT_TEXT\", \"input_payload\":\"quantum mechanics\"}"
        val inputRule = AutonomousEngine.parseAiActionResponse(inputJson)
        assertNotNull(inputRule)
        assertEquals(ActionType.INPUT, inputRule?.type)
        assertEquals("quantum mechanics", inputRule?.textPayload)
    }
}
