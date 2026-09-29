package com.example.localagent.memory

import android.view.accessibility.AccessibilityNodeInfo
import java.util.LinkedList
import java.util.Queue
import java.util.zip.CRC32

object ScreenFingerprinter {

    fun computeFingerprint(root: AccessibilityNodeInfo?, currentPackage: String): String {
        if (root == null) return "$currentPackage:empty"
        val structureBuilder = StringBuilder(currentPackage).append(":")

        // Collect stable structural elements (resource-ids / class names, exclude dynamic text)
        val queue: Queue<AccessibilityNodeInfo> = LinkedList()
        queue.add(root)
        var count = 0

        while (!queue.isEmpty() && count < 30) {
            val node = queue.poll() ?: continue
            count++
            node.viewIdResourceName?.let { structureBuilder.append(it).append(";") }
                ?: node.className?.let { structureBuilder.append(it).append(";") }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        val crc = CRC32()
        crc.update(structureBuilder.toString().toByteArray())
        return "$currentPackage:${crc.value}"
    }
}
