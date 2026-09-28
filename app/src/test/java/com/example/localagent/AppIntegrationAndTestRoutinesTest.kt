package com.example.localagent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.localagent.intents.IntentLauncher
import com.example.localagent.memory.MemoryLedger
import com.example.localagent.routines.TestRoutines
import com.example.localagent.state.AgentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class AppIntegrationAndTestRoutinesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testIntentLauncher_chromeLaunch() {
        val mockContext = mock(Context::class.java)
        val mockPm = mock(PackageManager::class.java)
        `when`(mockContext.packageManager).thenReturn(mockPm)

        val result = IntentLauncher.launchChrome(mockContext)
        assertTrue(result)

        val captor = ArgumentCaptor.forClass(Intent::class.java)
        verify(mockContext).startActivity(captor.capture())
        assertNotNull(captor.value)
    }

    @Test
    fun testIntentLauncher_cameraLaunch() {
        val mockContext = mock(Context::class.java)

        val result = IntentLauncher.launchCamera(mockContext)
        assertTrue(result)

        val captor = ArgumentCaptor.forClass(Intent::class.java)
        verify(mockContext).startActivity(captor.capture())
        assertNotNull(captor.value)
    }

    @Test
    fun testTestRoutines_chromeSearchWorkflow() {
        val mockService = mock(LocalAgentService::class.java)
        val mockPm = mock(PackageManager::class.java)

        val stateManager = com.example.localagent.state.TaskStateManager()
        val ledgerFile = tempFolder.newFile("test_chrome_ledger.json")
        val memoryLedger = MemoryLedger(ledgerFile)

        `when`(mockService.stateManager).thenReturn(stateManager)
        `when`(mockService.memoryLedger).thenReturn(memoryLedger)
        `when`(mockService.packageManager).thenReturn(mockPm)

        val result = TestRoutines.runChromeSearchTest(mockService, "Test Query")
        assertTrue(result)

        assertEquals(AgentStatus.COMPLETED, stateManager.getCurrentState().status)
        val entries = memoryLedger.getEntries()
        assertTrue(entries.isNotEmpty())
        assertEquals("LAUNCH_CHROME", entries[0].action)
        assertTrue(entries[0].success)
    }

    @Test
    fun testTestRoutines_cameraRecordingWorkflow() {
        val mockService = mock(LocalAgentService::class.java)

        val stateManager = com.example.localagent.state.TaskStateManager()
        val ledgerFile = tempFolder.newFile("test_camera_ledger.json")
        val memoryLedger = MemoryLedger(ledgerFile)

        `when`(mockService.stateManager).thenReturn(stateManager)
        `when`(mockService.memoryLedger).thenReturn(memoryLedger)

        val result = TestRoutines.runCameraRecordingTest(mockService)
        assertTrue(result)

        assertEquals(AgentStatus.COMPLETED, stateManager.getCurrentState().status)
        val entries = memoryLedger.getEntries()
        assertTrue(entries.isNotEmpty())
        assertEquals("LAUNCH_CAMERA", entries[0].action)
        assertTrue(entries[0].success)
    }

    @Test
    fun testTestRoutines_youtubePlaybackWorkflow() {
        val mockService = mock(LocalAgentService::class.java)
        val mockPm = mock(PackageManager::class.java)

        val stateManager = com.example.localagent.state.TaskStateManager()
        val ledgerFile = tempFolder.newFile("test_youtube_ledger.json")
        val memoryLedger = MemoryLedger(ledgerFile)

        `when`(mockService.stateManager).thenReturn(stateManager)
        `when`(mockService.memoryLedger).thenReturn(memoryLedger)
        `when`(mockService.packageManager).thenReturn(mockPm)

        val result = TestRoutines.runYouTubePlaybackTest(mockService, "Music Video")
        assertTrue(result)

        assertEquals(AgentStatus.COMPLETED, stateManager.getCurrentState().status)
        val entries = memoryLedger.getEntries()
        assertTrue(entries.isNotEmpty())
        assertEquals("LAUNCH_YOUTUBE", entries[0].action)
        assertTrue(entries[0].success)
    }
}
