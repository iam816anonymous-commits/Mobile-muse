package com.example.localagent.engine

object QueryPayloadSanitizer {

    private val SEARCH_PREFIX_REGEX = Regex(
        """(?i)^(?:open\s+(?:google\s+)?chrome\s+(?:and\s+)?(?:search\s+(?:for\s+)?|find\s+|look\s+up\s+)?|search\s+(?:for\s+)?|google\s+|look\s+up\s+|find\s+)"""
    )

    private val SEARCH_SUFFIX_REGEX = Regex(
        """(?i)\s+(?:on\s+chrome|in\s+chrome|on\s+google|using\s+chrome|in\s+browser)$"""
    )

    fun extractSearchQuery(rawGoal: String): String {
        var query = rawGoal.trim()
        query = SEARCH_PREFIX_REGEX.replace(query, "")
        query = SEARCH_SUFFIX_REGEX.replace(query, "")
        return query.trim('"', '\'', ' ', '\t', '\n')
    }
}
