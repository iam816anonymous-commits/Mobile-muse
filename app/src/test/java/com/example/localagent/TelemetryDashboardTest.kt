package com.example.localagent

import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class TelemetryDashboardTest {

    @Test
    fun testBroadcastTelemetryLog_constructsCorrectIntent() {
        val service = mock(LocalAgentService::class.java)
        service.broadcastTelemetryLog("SYS", "Test telemetry log entry")
        verify(service).broadcastTelemetryLog("SYS", "Test telemetry log entry")
    }
}
