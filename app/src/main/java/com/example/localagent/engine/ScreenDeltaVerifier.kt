package com.example.localagent.engine

import android.view.accessibility.AccessibilityNodeInfo
import java.util.zip.CRC32

object ScreenDeltaVerifier {
    fun computeWindowHash(root: AccessibilityNodeInfo?): Long {
        if (root == null) return 0L
        val builder = StringBuilder()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0

        while (queue.isNotEmpty() && count < 40) {
            val node = queue.removeFirst()
            count++
            builder.append(node.className ?: "")
                .append(node.viewIdResourceName ?: "")
                .append(node.text ?: "")

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    queue.add(child)
                }
            }
        }
        val crc = CRC32()
        crc.update(builder.toString().toByteArray())
        return crc.value
    }
}
