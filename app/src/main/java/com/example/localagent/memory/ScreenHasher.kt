package com.example.localagent.memory

import com.example.localagent.NodeData
import java.security.MessageDigest

object ScreenHasher {

    fun computeFingerprint(packageName: String?, nodes: List<NodeData>): String {
        val pkg = packageName?.trim() ?: "unknown"

        val clickableTokens = nodes
            .filter { it.hasActions || !it.text.isNull_or_blank() || !it.contentDescription.isNull_or_blank() }
            .mapNotNull { node ->
                val label = node.text ?: node.contentDescription
                val cls = node.className
                if (label != null) "$label:$cls" else cls
            }
            .sorted()

        val rawSignature = "$pkg|${clickableTokens.joinToString(",")}"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(rawSignature.toByteArray(Charsets.UTF_8))

        // Convert first 8 bytes of SHA-256 digest into 64-bit hex string
        val sb = StringBuilder()
        for (i in 0 until 8) {
            sb.append(String.format("%02x", digest[i]))
        }
        return sb.toString()
    }

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.trim().isEmpty()
    }
}
