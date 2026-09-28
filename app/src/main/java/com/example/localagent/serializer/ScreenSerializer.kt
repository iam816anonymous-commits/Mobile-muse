package com.example.localagent.serializer

import com.example.localagent.NodeData
import org.json.JSONArray
import org.json.JSONObject

object ScreenSerializer {

    fun serializeScreen(nodes: List<NodeData>): String {
        val jsonArray = JSONArray()
        for (node in nodes) {
            val jsonObject = JSONObject()
            if (!node.text.isNull_or_blank()) {
                jsonObject.put("text", node.text)
            }
            if (!node.contentDescription.isNull_or_blank()) {
                jsonObject.put("desc", node.contentDescription)
            }
            if (!node.className.isNull_or_blank()) {
                jsonObject.put("class", node.className)
            }
            val boundsObj = JSONObject().apply {
                put("l", node.boundsInScreen.left)
                put("t", node.boundsInScreen.top)
                put("r", node.boundsInScreen.right)
                put("b", node.boundsInScreen.bottom)
            }
            jsonObject.put("bounds", boundsObj)
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString()
    }

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.trim().isEmpty()
    }
}
