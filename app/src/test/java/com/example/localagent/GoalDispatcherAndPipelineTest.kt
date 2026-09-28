package com.example.localagent

import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.engine.AutonomousEngine
import com.example.localagent.memory.ActionType
import com.example.localagent.receiver.GoalDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic

@OptIn(ExperimentalCoroutinesApi::class)
class GoalDispatcherAndPipelineTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun testGoalDispatcher_onReceiveDispatchesGoalOnCoroutine() = runTest(testDispatcher) {
        val mockService = mock(LocalAgentService::class.java)
        `when`(mockService.stateManager).thenReturn(com.example.localagent.state.TaskStateManager())

        var processedGoal: String? = null
        val dispatcher = GoalDispatcher(mockService, coroutineScope = testScope) { goal ->
            processedGoal = goal
        }

        val intent = mock(Intent::class.java)
        `when`(intent.action).thenReturn(GoalDispatcher.ACTION_EXECUTE_GOAL)
        `when`(intent.getStringExtra(GoalDispatcher.EXTRA_GOAL_TEXT)).thenReturn("Ask Gemini about quantum computing")

        dispatcher.onReceive(mockService, intent)

        assertEquals("Ask Gemini about quantum computing", processedGoal)
    }

    @Test
    fun testGoalDispatcher_dispatchesCalculatorSkill() = runTest(testDispatcher) {
        val mockService = mock(LocalAgentService::class.java)
        `when`(mockService.stateManager).thenReturn(com.example.localagent.state.TaskStateManager())

        var processedGoal: String? = null
        val dispatcher = GoalDispatcher(mockService, coroutineScope = testScope) { goal ->
            processedGoal = goal
        }

        val intent = mock(Intent::class.java)
        `when`(intent.action).thenReturn(GoalDispatcher.ACTION_EXECUTE_GOAL)
        `when`(intent.getStringExtra(GoalDispatcher.EXTRA_GOAL_TEXT)).thenReturn("calculate 45 * 8")

        dispatcher.onReceive(mockService, intent)

        assertEquals("calculate 45 * 8", processedGoal)
    }

    @Test
    fun testGoalDispatcher_dispatchesCameraSkill() = runTest(testDispatcher) {
        val mockService = mock(LocalAgentService::class.java)
        `when`(mockService.stateManager).thenReturn(com.example.localagent.state.TaskStateManager())

        var processedGoal: String? = null
        val dispatcher = GoalDispatcher(mockService, coroutineScope = testScope) { goal ->
            processedGoal = goal
        }

        val intent = mock(Intent::class.java)
        `when`(intent.action).thenReturn(GoalDispatcher.ACTION_EXECUTE_GOAL)
        `when`(intent.getStringExtra(GoalDispatcher.EXTRA_GOAL_TEXT)).thenReturn("take a photo with front camera")

        dispatcher.onReceive(mockService, intent)

        assertEquals("take a photo with front camera", processedGoal)
    }

    @Test
    fun testAutonomousEngine_parseExpandedActions() {
        val scrollJson = "{\"action\":\"SCROLL\", \"target_text\":\"down\"}"
        val scrollRule = AutonomousEngine.parseAiActionResponse(scrollJson)
        assertNotNull(scrollRule)
        assertEquals(ActionType.SCROLL, scrollRule?.type)

        val extractJson = "{\"action\":\"EXTRACT_RESULT\", \"target_text\":\"Quantum computers use qubits\"}"
        val extractRule = AutonomousEngine.parseAiActionResponse(extractJson)
        assertNotNull(extractRule)
        assertEquals(ActionType.EXTRACT_RESULT, extractRule?.type)
        assertEquals("Quantum computers use qubits", extractRule?.textPayload)
    }

    @Test
    fun testAutonomousEngine_findEditableNodeAndSendButton() {
        mockStatic(AccessibilityNodeInfo::class.java).use { staticMock ->
            staticMock.`when`<AccessibilityNodeInfo> { AccessibilityNodeInfo.obtain(any(AccessibilityNodeInfo::class.java)) }
                .thenAnswer { invocation -> invocation.getArgument(0) }

            val rootNode = mock(AccessibilityNodeInfo::class.java)
            val editableChild = mock(AccessibilityNodeInfo::class.java)
            val sendChild = mock(AccessibilityNodeInfo::class.java)

            `when`(rootNode.childCount).thenReturn(2)
            `when`(rootNode.getChild(0)).thenReturn(editableChild)
            `when`(rootNode.getChild(1)).thenReturn(sendChild)

            `when`(editableChild.isEditable).thenReturn(true)
            `when`(editableChild.childCount).thenReturn(0)

            `when`(sendChild.isClickable).thenReturn(true)
            `when`(sendChild.text).thenReturn("Send")
            `when`(sendChild.childCount).thenReturn(0)

            val foundEditable = AutonomousEngine.findEditableNode(rootNode)
            assertNotNull(foundEditable)

            val foundSend = AutonomousEngine.findSendOrSubmitButton(rootNode)
            assertNotNull(foundSend)
        }
    }
}
