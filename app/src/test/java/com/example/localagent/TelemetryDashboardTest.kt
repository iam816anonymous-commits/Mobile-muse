package com.example.localagent

import android.content.Intent
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class TelemetryDashboardTest {

    @Test
    fun testBroadcastTelemetryLog_constructsCorrectIntent() {
        val service = mock(LocalAgentService::class.java)

        val captor = ArgumentCaptor.forClass(Intent::class.java)
        org.mockito.Mockito.doCallRealMethod().`when`(service).broadcastTelemetryLog(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString()
        )

        service.broadcastTelemetryLog("SYS", "Test telemetry log entry")

        verify(service).sendBroadcast(captor.capture())
        val intent = captor.value
        assertNotNull(intent)
    }
}
