package com.example.localagent

import com.example.localagent.engine.ObstacleDetector
import com.example.localagent.memory.ActionRule
import com.example.localagent.memory.ActionType
import com.example.localagent.memory.KnowledgeLedger
import com.example.localagent.memory.RuleLedger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class LowRamAndVoiceOptimizationsTest {

    private lateinit var rulesFile: File
    private lateinit var knowledgeFile: File

    @Before
    fun setUp() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_localagent_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        rulesFile = File(tempDir, "test_rules.json")
        knowledgeFile = File(tempDir, "test_knowledge.json")
    }

    @Test
    fun testRuleLedgerFifoLimit() {
        val ledger = RuleLedger(rulesFile)
        for (i in 1..350) {
            ledger.addTransition(
                screenFingerprint = "fp_$i",
                userGoal = "goal_$i",
                actionRule = ActionRule(ActionType.CLICK)
            )
        }

        // Action rule for fp_350 should exist
        val found = ledger.getActionRule("fp_350", "goal_350")
        assertTrue("Latest entry should be retained", found != null)

        // Oldest action rule fp_1 should have been dropped due to FIFO cap of 300
        val dropped = ledger.getActionRule("fp_1", "goal_1")
        assertTrue("First entry (fp_1) should be dropped by FIFO cap", dropped == null)
    }

    @Test
    fun testKnowledgeLedgerFifoLimit() {
        val ledger = KnowledgeLedger(knowledgeFile)
        for (i in 1..250) {
            ledger.addKnowledge("id_$i", "query_$i", "App_$i", "Answer_$i")
        }

        val entries = ledger.getEntries()
        assertEquals("Knowledge ledger FIFO should cap at 200 facts", 200, entries.size)
        assertEquals("First entry should be query_51 due to FIFO eviction", "query_51", entries[0].query)
        assertEquals("Last entry should be query_250", "query_250", entries.last().query)
    }

    @Test
    fun testObstacleDetectorDismissableDetection() {
        assertTrue("ObstacleDetector should match 'allow'", ObstacleDetector.isObstacleNode("Allow permissions", null))
        assertTrue("ObstacleDetector should match 'cancel'", ObstacleDetector.isObstacleNode("Cancel", null))
        assertFalse("ObstacleDetector should not match normal text", ObstacleDetector.isObstacleNode("Search Google", null))
    }
}
