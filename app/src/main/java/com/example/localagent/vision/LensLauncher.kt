package com.example.localagent.vision

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

object LensLauncher {

    private const val TAG = "LensLauncher"
    const val PACKAGE_GOOGLE_LENS = "com.google.ar.lens"

    fun launchGoogleLens(context: Context, imageUri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            setPackage(PACKAGE_GOOGLE_LENS)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return try {
            context.startActivity(intent)
            Log.d(TAG, "Launched Google Lens intent successfully")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Google Lens package missing, launching generic image view chooser", e)
            val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(Intent.createChooser(fallbackIntent, "Identify Image").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                true
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to launch fallback image chooser", ex)
                false
            }
        }
    }
}
