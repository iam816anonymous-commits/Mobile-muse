package com.example.localagent.engine

data class StallContext(
    val packageName: String,
    val userGoal: String,
    val visibleTexts: List<String>,
    val failureCount: Int
)

object StallDetector {

    private var consecutiveFailures = 0
    private var lastPackage = ""

    fun recordFailure() {
        consecutiveFailures++
    }

    fun reset() {
        consecutiveFailures = 0
    }

    fun isStalled(hasTargetIndex: Boolean): Boolean {
        return consecutiveFailures >= 2 || !hasTargetIndex
    }

    fun buildContext(packageName: String, goal: String, labels: List<String>): StallContext {
        lastPackage = packageName
        return StallContext(
            packageName = packageName,
            userGoal = goal,
            visibleTexts = labels.take(5),
            failureCount = consecutiveFailures
        )
    }
}
