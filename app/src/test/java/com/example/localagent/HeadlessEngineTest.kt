package com.example.localagent

import com.example.localagent.skills.HeadlessMathEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadlessEngineTest {

    @Test
    fun testIsMathQuery() {
        assertTrue(HeadlessMathEngine.isMathQuery("calculate 25^5"))
        assertTrue(HeadlessMathEngine.isMathQuery("45 * 2"))
        assertTrue(HeadlessMathEngine.isMathQuery("compute (12 + 8) / 4"))
        assertTrue(HeadlessMathEngine.isMathQuery("what is 100 % 7"))
        assertFalse(HeadlessMathEngine.isMathQuery("take a photo"))
        assertFalse(HeadlessMathEngine.isMathQuery("open chrome"))
    }

    @Test
    fun testExtractExpression() {
        assertEquals("25 ^ 5", HeadlessMathEngine.extractExpression("calculate 25^5"))
        assertEquals("45 * 2", HeadlessMathEngine.extractExpression("45 * 2"))
        assertEquals("(12 + 8) / 4", HeadlessMathEngine.extractExpression("compute (12 + 8) / 4"))
        assertEquals("100 % 7", HeadlessMathEngine.extractExpression("what is 100 % 7"))
    }

    @Test
    fun testEvaluateExpression() {
        assertEquals(31250000.0, HeadlessMathEngine.evaluateExpression("25 ^ 5"), 0.001)
        assertEquals(90.0, HeadlessMathEngine.evaluateExpression("45 * 2"), 0.001)
        assertEquals(5.0, HeadlessMathEngine.evaluateExpression("(12 + 8) / 4"), 0.001)
        assertEquals(2.0, HeadlessMathEngine.evaluateExpression("100 % 7"), 0.001)
        assertEquals(15.5, HeadlessMathEngine.evaluateExpression("10 + 5.5"), 0.001)
    }
}
