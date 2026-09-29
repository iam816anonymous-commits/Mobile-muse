package com.example.localagent.skills

import android.content.Context
import android.media.MediaRecorder
import android.os.Environment
import android.util.Log
import com.example.localagent.LocalAgentService
import com.example.localagent.memory.StorageManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HeadlessMediaEngine {

    private const val TAG = "HeadlessMediaEngine"
    private var activeRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null

    fun startHeadlessRecording(context: Context, outputFile: File): Boolean {
        return try {
            stopHeadlessRecording()
            val recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            activeRecorder = recorder
            currentOutputFile = outputFile
            Log.i(TAG, "Headless recording started: ${outputFile.absolutePath}")
            if (context is LocalAgentService) {
                context.broadcastTelemetryLog("RECORD", "Headless background recording started -> ${outputFile.name}")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start headless recording", e)
            if (context is LocalAgentService) {
                context.broadcastTelemetryLog("RECORD", "Failed to start headless recording: ${e.message}")
            }
            activeRecorder = null
            currentOutputFile = null
            false
        }
    }

    fun stopHeadlessRecording(): File? {
        val recorder = activeRecorder ?: return null
        val file = currentOutputFile
        return try {
            recorder.stop()
            recorder.release()
            Log.i(TAG, "Headless recording stopped successfully")
            activeRecorder = null
            currentOutputFile = null
            file
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping headless recorder", e)
            activeRecorder = null
            currentOutputFile = null
            null
        }
    }

    fun processRecordAudioGoal(service: LocalAgentService): Boolean {
        val storageDir = StorageManager.getPersistentStorageDir(service)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(storageDir, "LocalAgent_Rec_$timestamp.m4a")

        service.broadcastTelemetryLog("RECORD", "Initiating headless background audio recording...")
        val success = startHeadlessRecording(service, outFile)
        if (success) {
            service.broadcastGoalCompleted("record audio", "SUCCESS", "Recording to ${outFile.name}")
            service.voiceSynthesizer?.speak("Started background audio recording.")
        }
        return success
    }
}
