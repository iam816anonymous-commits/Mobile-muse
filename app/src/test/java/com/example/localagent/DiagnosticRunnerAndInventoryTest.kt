package com.example.localagent

import android.content.Context
import android.content.pm.PackageManager
import com.example.localagent.inventory.AppInventoryManager
import com.example.localagent.inventory.AppProfile
import com.example.localagent.receiver.GoalDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class DiagnosticRunnerAndInventoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testAppProfile_jsonSerialization() {
        val profile = AppProfile(
            appName = "Google Chrome",
            packageName = "com.android.chrome",
            launchable = true,
            hasEditableInput = true,
            supportsScroll = true
        )

        val json = profile.toJsonObject()
        val deserialized = AppProfile.fromJsonObject(json)

        assertEquals("Google Chrome", deserialized.appName)
        assertEquals("com.android.chrome", deserialized.packageName)
        assertTrue(deserialized.launchable)
        assertTrue(deserialized.hasEditableInput)
        assertTrue(deserialized.supportsScroll)
    }

    @Test
    fun testAppInventoryManager_scannerAndPersistence() {
        val mockContext = mock(Context::class.java)
        val mockPm = mock(PackageManager::class.java)

        `when`(mockContext.filesDir).thenReturn(tempFolder.newFolder("test_files"))
        `when`(mockContext.packageName).thenReturn("com.example.localagent")
        `when`(mockContext.packageManager).thenReturn(mockPm)

        val inventoryManager = AppInventoryManager(mockContext)
        val profile = AppProfile("Test App", "com.test.app", launchable = true)

        inventoryManager.updateProfile(profile)
        val loaded = inventoryManager.getProfiles()

        assertEquals(1, loaded.size)
        assertEquals("com.test.app", loaded[0].packageName)
    }

    @Test
    fun testGoalDispatcher_auditActionConstants() {
        assertEquals("com.localagent.RUN_DIAGNOSTIC", GoalDispatcher.ACTION_RUN_DIAGNOSTIC)
        assertEquals("com.localagent.RUN_APP_AUDIT", GoalDispatcher.ACTION_RUN_APP_AUDIT)
    }
}
