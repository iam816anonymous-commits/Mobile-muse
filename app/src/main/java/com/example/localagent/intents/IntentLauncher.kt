package com.example.localagent.intents

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.util.Log

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
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "Launched Chrome successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Chrome", e)
            false
        }
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
