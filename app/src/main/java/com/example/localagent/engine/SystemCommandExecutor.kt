package com.example.localagent.engine

import android.app.SearchManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Environment
import android.provider.AlarmClock
import android.provider.MediaStore
import android.util.Log
import com.example.localagent.LocalAgentService
import com.example.localagent.memory.StorageManager
import com.example.localagent.skills.HeadlessMathEngine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SystemCommandExecutor {

    private const val TAG = "SystemCommandExecutor"
    private var activeRecorder: MediaRecorder? = null
    private var activeRecordingFile: File? = null

    fun evaluateMath(service: LocalAgentService, rawGoal: String): Boolean {
        return HeadlessMathEngine.processMathGoal(service, rawGoal, service.voiceSynthesizer)
    }

    fun setTimer(context: Context, seconds: Int, label: String = "LocalAgent Timer"): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            if (context is LocalAgentService) {
                context.broadcastTelemetryLog("CLOCK", "Timer set for $seconds sec")
                context.broadcastGoalCompleted("set timer", "SUCCESS", "Timer set for $seconds sec")
                context.voiceSynthesizer?.speak("Setting timer for $seconds seconds.")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set timer", e)
            false
        }
    }

    fun setAlarm(context: Context, hour: Int, minute: Int, message: String = "LocalAgent Alarm"): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            if (context is LocalAgentService) {
                context.broadcastTelemetryLog("CLOCK", "Alarm set for $hour:$minute")
                context.broadcastGoalCompleted("set alarm", "SUCCESS", "Alarm set for $hour:$minute")
                context.voiceSynthesizer?.speak("Setting alarm for $hour:$minute.")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set alarm", e)
            false
        }
    }

    fun appendNote(context: Context, noteContent: String): Boolean {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val localAgentDir = File(downloadsDir, "LocalAgent")
            if (!localAgentDir.exists()) {
                localAgentDir.mkdirs()
            }
            val notesFile = File(localAgentDir, "notes.txt")
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            val formattedEntry = "[$timestamp] $noteContent\n"

            notesFile.appendText(formattedEntry)
            Log.i(TAG, "Appended note to ${notesFile.absolutePath}")

            if (context is LocalAgentService) {
                context.broadcastTelemetryLog("NOTE", "Note saved to Download/LocalAgent/notes.txt")
                context.broadcastGoalCompleted("save note", "SUCCESS", "Note appended: $noteContent")
                context.voiceSynthesizer?.speak("Note saved successfully.")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to append note", e)
            false
        }
    }

    fun startHeadlessAudioRecording(context: Context): Boolean {
        return try {
            stopHeadlessAudioRecording(context)
            val storageDir = StorageManager.getPersistentStorageDir(context)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outFile = File(storageDir, "LocalAgent_Rec_$timestamp.m4a")

            val recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }
            activeRecorder = recorder
            activeRecordingFile = outFile

            if (context is LocalAgentService) {
                context.broadcastTelemetryLog("RECORD", "Headless recording started -> ${outFile.name}")
                context.broadcastGoalCompleted("record audio", "SUCCESS", "Recording to ${outFile.name}")
                context.voiceSynthesizer?.speak("Audio recording started.")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting headless audio recording", e)
            activeRecorder = null
            activeRecordingFile = null
            false
        }
    }

    fun stopHeadlessAudioRecording(context: Context): File? {
        val recorder = activeRecorder ?: return null
        val file = activeRecordingFile
        return try {
            recorder.stop()
            recorder.release()
            activeRecorder = null
            activeRecordingFile = null
            if (context is LocalAgentService) {
                context.broadcastTelemetryLog("RECORD", "Headless recording stopped")
                context.voiceSynthesizer?.speak("Audio recording stopped.")
            }
            file
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping headless audio recorder", e)
            activeRecorder = null
            activeRecordingFile = null
            null
        }
    }

    fun getLatestPhotoSummary(context: Context): String {
        return try {
            val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            var count = 0
            var latestName = "Unknown"
            cursor?.use {
                count = it.count
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        latestName = it.getString(nameIndex) ?: "Unknown"
                    }
                }
            }
            val resultSummary = "Total photos: $count. Latest photo: $latestName"
            if (context is LocalAgentService) {
                context.broadcastTelemetryLog("MEDIASTORE", resultSummary)
                context.broadcastGoalCompleted("photo summary", "SUCCESS", resultSummary)
                context.voiceSynthesizer?.speak(resultSummary)
            }
            resultSummary
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore photos", e)
            "Unable to query photo library"
        }
    }

    fun deleteLatestPhoto(context: Context): Boolean {
        return try {
            val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            var photoId: Long = -1L
            var photoName = ""
            cursor?.use {
                if (it.moveToFirst()) {
                    val idIndex = it.getColumnIndex(MediaStore.Images.Media._ID)
                    val nameIndex = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    if (idIndex != -1) photoId = it.getLong(idIndex)
                    if (nameIndex != -1) photoName = it.getString(nameIndex) ?: ""
                }
            }

            if (photoId != -1L) {
                val deleteUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, photoId)
                val rowsDeleted = context.contentResolver.delete(deleteUri, null, null)
                val success = rowsDeleted > 0
                if (context is LocalAgentService) {
                    context.broadcastTelemetryLog("MEDIASTORE", "Delete photo '$photoName': $success")
                    context.broadcastGoalCompleted("delete photo", "SUCCESS", "Deleted $photoName")
                    context.voiceSynthesizer?.speak("Deleted latest photo $photoName")
                }
                success
            } else {
                if (context is LocalAgentService) {
                    context.broadcastTelemetryLog("MEDIASTORE", "No photos found to delete")
                    context.voiceSynthesizer?.speak("No photos found to delete.")
                }
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete latest photo", e)
            false
        }
    }

    fun performWebSearch(context: Context, cleanQuery: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, cleanQuery)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            if (context is LocalAgentService) {
                context.broadcastTelemetryLog("SEARCH", "Dispatched native web search for '$cleanQuery'")
                context.broadcastGoalCompleted("web search", "SUCCESS", cleanQuery)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch web search for '$cleanQuery'", e)
            false
        }
    }
}
