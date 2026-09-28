package com.example.localagent.engine

object QueryFormulator {

    fun formulateQuery(context: StallContext): String {
        val appLabel = context.packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        val targetElement = context.visibleTexts.firstOrNull() ?: "button"
        return if (context.userGoal.isNotBlank()) {
            "How to ${context.userGoal} in $appLabel Android"
        } else {
            "Where is $targetElement in $appLabel"
        }
    }
}
