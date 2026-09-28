package com.example.localagent.serializer

import com.example.localagent.NodeData
import org.json.JSONArray
import org.json.JSONObject

object ScreenSerializer {

    fun serializeScreen(nodes: List<NodeData>): String {
        val jsonArray = JSONArray()
        var indexCounter = 0

        for (node in nodes) {
            // Filter out non-actionable layout containers, invisible nodes, and empty nodes
            if (node.text.isNull_or_blank() && node.contentDescription.isNull_or_blank() && !node.hasActions) {
                continue
            }

            val simpleClassName = node.className?.substringAfterLast('.') ?: "View"
            val jsonObject = JSONObject().apply {
                put("index", indexCounter++)
                put("type", simpleClassName)
                put("class", node.className ?: "")
                if (!node.text.isNull_or_blank()) {
                    put("text", node.text)
                }
                if (!node.contentDescription.isNull_or_blank()) {
                    put("desc", node.contentDescription)
                }
                val boundsObj = JSONObject().apply {
                    put("l", node.boundsInScreen.left)
                    put("t", node.boundsInScreen.top)
                    put("r", node.boundsInScreen.right)
                    put("b", node.boundsInScreen.bottom)
                }
                put("bounds", boundsObj)
            }
            jsonArray.put(jsonObject)
        }

        val fullOutput = jsonArray.toString()
        return if (fullOutput.length > 4000) {
            fullOutput.substring(0, 4000)
        } else {
            fullOutput
        }
    }

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.trim().isEmpty()
    }
}
