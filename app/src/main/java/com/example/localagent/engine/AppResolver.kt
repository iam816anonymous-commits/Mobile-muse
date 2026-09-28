package com.example.localagent.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

object AppResolver {

    private const val TAG = "AppResolver"

    const val PKG_GEMINI = "com.google.android.apps.bard"
    const val PKG_CHATGPT = "com.openai.chatgpt"
    const val PKG_CHROME = "com.android.chrome"
    const val PKG_YOUTUBE = "com.google.android.youtube"

    const val URL_GEMINI = "https://gemini.google.com"
    const val URL_CHATGPT = "https://chatgpt.com"

    fun resolveAndLaunch(context: Context, goalText: String): Boolean {
        val lowerGoal = goalText.lowercase()

        val (targetPkg, fallbackUrl) = when {
            lowerGoal.contains("gemini") -> PKG_GEMINI to URL_GEMINI
            lowerGoal.contains("chatgpt") -> PKG_CHATGPT to URL_CHATGPT
            lowerGoal.contains("youtube") -> PKG_YOUTUBE to null
            lowerGoal.contains("chrome") || lowerGoal.contains("search") || lowerGoal.contains("browse") -> PKG_CHROME to null
            else -> PKG_CHROME to null
        }

        return launchPackageOrFallbackUrl(context, targetPkg, fallbackUrl)
    }

    private fun launchPackageOrFallbackUrl(context: Context, packageName: String, fallbackUrl: String?): Boolean {
        return try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                Log.d(TAG, "Launched package: $packageName")
                true
            } else if (!fallbackUrl.isNull_or_blank()) {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                Log.d(TAG, "Package $packageName not installed. Opened fallback URL: $fallbackUrl")
                true
            } else {
                Log.w(TAG, "Package $packageName not found and no fallback URL provided.")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app/fallback for $packageName", e)
            false
        }
    }

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.trim().isEmpty()
    }
}
