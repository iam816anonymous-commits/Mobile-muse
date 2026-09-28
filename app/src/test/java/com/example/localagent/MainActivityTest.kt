package com.example.localagent

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic

class MainActivityTest {

    @Test
    fun testIsAccessibilityServiceEnabled_disabledWhenSettingNull() {
        val mockContext = mock(Context::class.java)
        val mockResolver = mock(ContentResolver::class.java)
        `when`(mockContext.contentResolver).thenReturn(mockResolver)
        `when`(mockContext.packageName).thenReturn("com.example.localagent")

        val enabled = MainActivity.isAccessibilityServiceEnabled(mockContext, LocalAgentService::class.java)
        assertFalse(enabled)
    }

    @Test
    fun testIsAccessibilityServiceEnabled_enabledWhenSettingMatches() {
        val mockContext = mock(Context::class.java)
        val mockResolver = mock(ContentResolver::class.java)
        `when`(mockContext.contentResolver).thenReturn(mockResolver)
        `when`(mockContext.packageName).thenReturn("com.example.localagent")

        mockStatic(Settings.Secure::class.java).use { settingsMock ->
            settingsMock.`when`<String> {
                Settings.Secure.getString(
                    mockResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            }.thenReturn("com.example.localagent/.LocalAgentService:other.pkg/.OtherService")

            val enabled = MainActivity.isAccessibilityServiceEnabled(mockContext, LocalAgentService::class.java)
            assertTrue(enabled)
        }
    }
}
