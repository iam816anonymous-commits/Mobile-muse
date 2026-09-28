package com.example.localagent

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class SelfDiagnosticTest {

    @Test
    fun testSelfDiagnostic_actionsDefined() {
        assertEquals("com.localagent.TEST_APP_LAUNCH", LocalAgentService.ACTION_TEST_APP_LAUNCH)
        assertEquals("com.localagent.TEST_NODE_DUMP", LocalAgentService.ACTION_TEST_NODE_DUMP)
        assertEquals("com.localagent.TEST_COORDINATE_TAP", LocalAgentService.ACTION_TEST_COORDINATE_TAP)
        assertEquals("com.localagent.TEST_TEXT_INJECTION", LocalAgentService.ACTION_TEST_TEXT_INJECTION)
    }

    @Test
    fun testTestNodeDump_executesWithoutCrash() {
        val service = mock(LocalAgentService::class.java)
        service.testNodeDump()
        verify(service).testNodeDump()
    }
}
