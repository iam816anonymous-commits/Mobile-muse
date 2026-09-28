package com.example.localagent.intents

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.LocalAgentService

object IntentLauncher {

    private const val TAG = "IntentLauncher"
    const val PACKAGE_CHROME = "com.android.chrome"
    const val PACKAGE_YOUTUBE = "com.google.android.youtube"

    fun launchChrome(context: Context): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE_CHROME)
                ?: Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(PACKAGE_CHROME)
                }
            intent.putExtra("com.android.chrome.prefer_new", true)
            intent.putExtra("create_new_tab", true)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            context.startActivity(intent)
            Log.d(TAG, "Launched Chrome successfully with force new tab flags")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Chrome", e)
            false
        }
    }

    fun openChromeNewTabFallback(service: LocalAgentService, searchQuery: String) {
        val root = service.getActiveWindowRoot()
        if (root != null) {
            try {
                val newTabNode = findNewTabButton(root)
                if (newTabNode != null) {
                    try {
                        service.performClickWithFallback(newTabNode)
                    } finally {
                        newTabNode.recycle()
                    }
                } else {
                    val moreOptionsNode = findMoreOptionsButton(root)
                    if (moreOptionsNode != null) {
                        try {
                            service.performClickWithFallback(moreOptionsNode)
                        } finally {
                            moreOptionsNode.recycle()
                        }
                    }
                }

                // Focus search bar omnibox and set text
                val omniboxNode = findOmniboxNode(root)
                if (omniboxNode != null) {
                    try {
                        val args = Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, searchQuery)
                        }
                        omniboxNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    } finally {
                        omniboxNode.recycle()
                    }
                }
            } finally {
                root.recycle()
            }
        }
    }

    private fun findNewTabButton(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        if (desc.contains("new tab") || text.contains("new tab")) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findNewTabButton(child)
            child.recycle()
            if (match != null) return match
        }
        return null
    }

    private fun findMoreOptionsButton(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (desc.contains("more options") || desc.contains("more_options") || desc.contains("menu")) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findMoreOptionsButton(child)
            child.recycle()
            if (match != null) return match
        }
        return null
    }

    private fun findOmniboxNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val id = node.viewIdResourceName?.lowercase() ?: ""
        if (id.contains("url_bar") || id.contains("search_box") || desc.contains("search or type url") || text.contains("search or type url")) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findOmniboxNode(child)
            child.recycle()
            if (match != null) return match
        }
        return null
    }

    fun launchYouTube(context: Context): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE_YOUTUBE)
                ?: Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(PACKAGE_YOUTUBE)
                }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "Launched YouTube successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch YouTube", e)
            false
        }
    }

    fun launchCamera(context: Context): Boolean {
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Launched Camera successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Camera", e)
            false
        }
    }
}
