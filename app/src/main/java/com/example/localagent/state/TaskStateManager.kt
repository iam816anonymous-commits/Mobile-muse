package com.example.localagent.state

enum class AgentStatus {
    IDLE,
    RUNNING,
    HALTED,
    COMPLETED,
    FAILED
}

data class TaskGoal(
    val id: String,
    val description: String,
    val maxSteps: Int = 15
)

data class TaskState(
    val goal: TaskGoal?,
    val currentStepIndex: Int = 0,
    val status: AgentStatus = AgentStatus.IDLE,
    val activeStateFlags: Map<String, Boolean> = emptyMap(),
    val failureReason: String? = null
)

class TaskStateManager(private val maxStepsLimit: Int = 15) {

    private var currentState: TaskState = TaskState(goal = null)
    private var listener: ((TaskState) -> Unit)? = null

    fun setListener(listener: ((TaskState) -> Unit)?) {
        this.listener = listener
    }

    fun getCurrentState(): TaskState = currentState

    fun startTask(goal: TaskGoal) {
        val effectiveMaxSteps = if (goal.maxSteps in 1..maxStepsLimit) goal.maxSteps else maxStepsLimit
        val adjustedGoal = goal.copy(maxSteps = effectiveMaxSteps)
        currentState = TaskState(
            goal = adjustedGoal,
            currentStepIndex = 0,
            status = AgentStatus.RUNNING,
            activeStateFlags = mapOf("isProcessing" to true)
        )
        notifyStateChange()
    }

    fun incrementStep(): Boolean {
        val goal = currentState.goal ?: return false
        if (currentState.status != AgentStatus.RUNNING) return false

        val nextIndex = currentState.currentStepIndex + 1
        return if (nextIndex >= goal.maxSteps) {
            currentState = currentState.copy(
                currentStepIndex = nextIndex,
                status = AgentStatus.HALTED,
                failureReason = "Circuit Breaker: Maximum step limit (${goal.maxSteps}) reached",
                activeStateFlags = currentState.activeStateFlags + ("isProcessing" to false)
            )
            notifyStateChange()
            false
        } else {
            currentState = currentState.copy(
                currentStepIndex = nextIndex
            )
            notifyStateChange()
            true
        }
    }

    fun updateFlag(key: String, value: Boolean) {
        currentState = currentState.copy(
            activeStateFlags = currentState.activeStateFlags + (key to value)
        )
        notifyStateChange()
    }

    fun completeTask() {
        currentState = currentState.copy(
            status = AgentStatus.COMPLETED,
            activeStateFlags = currentState.activeStateFlags + ("isProcessing" to false)
        )
        notifyStateChange()
    }

    fun haltTask(reason: String) {
        currentState = currentState.copy(
            status = AgentStatus.HALTED,
            failureReason = reason,
            activeStateFlags = currentState.activeStateFlags + ("isProcessing" to false)
        )
        notifyStateChange()
    }

    fun reset() {
        currentState = TaskState(goal = null)
        notifyStateChange()
    }

    private fun notifyStateChange() {
        listener?.invoke(currentState)
    }
}
