package com.example.localagent

import com.example.localagent.inventory.AppCategory
import com.example.localagent.memory.OperationalMetric
import com.example.localagent.memory.SelfReflectionEngine
import com.example.localagent.safety.PermissionManager
import com.example.localagent.skills.CalculatorSkill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class SkillsAndReflectionTest {

    private lateinit var tempFile: File

    @Before
    fun setUp() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_reflection_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        tempFile = File(tempDir, "test_metrics.json")
    }

    @Test
    fun testCalculatorSkill_sanitizeExpression() {
        val raw = "calculate 45 * 8 in calculator"
        val sanitized = CalculatorSkill.sanitizeExpression(raw)
        assertEquals("45*8", sanitized)
    }

    @Test
    fun testPermissionManagerGroupA() {
        val perms = PermissionManager.GROUP_A_PERMISSIONS
        assertTrue(perms.contains(android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
        assertTrue(perms.contains(android.Manifest.permission.READ_EXTERNAL_STORAGE))
    }

    @Test
    fun testSelfReflectionEngine_recordsAndGeneratesAdvice() {
        val engine = SelfReflectionEngine(org.mockito.Mockito.mock(android.content.Context::class.java))
        engine.recordMetric(
            OperationalMetric(
                executionDurationMs = 1500L,
                ramSpikeMb = 120L,
                obstacleDismissalCount = 1,
                stepCountToSuccess = 4
            )
        )

        val advice = engine.generateSystemAdvice()
        assertNotNull(advice)
        assertTrue(advice.isNotEmpty())
    }

    @Test
    fun testAppCategoryEnum() {
        assertEquals("NEWS", AppCategory.NEWS.name)
        assertEquals("UTILITY", AppCategory.UTILITY.name)
        assertEquals("COMMUNICATION", AppCategory.COMMUNICATION.name)
    }
}
