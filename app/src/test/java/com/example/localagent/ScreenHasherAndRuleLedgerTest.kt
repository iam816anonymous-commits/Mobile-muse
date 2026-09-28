package com.example.localagent

import android.graphics.Rect
import com.example.localagent.memory.ActionRule
import com.example.localagent.memory.ActionType
import com.example.localagent.memory.RuleLedger
import com.example.localagent.memory.ScreenHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class ScreenHasherAndRuleLedgerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testScreenHasher_deterministic64BitHex() {
        val rect = mock(Rect::class.java)
        val nodes = listOf(
            NodeData(text = "Submit", contentDescription = null, className = "android.widget.Button", boundsInScreen = rect, hasActions = true),
            NodeData(text = "Search", contentDescription = null, className = "android.widget.EditText", boundsInScreen = rect, hasActions = true)
        )

        val hash1 = ScreenHasher.computeFingerprint("com.example.app", nodes)
        val hash2 = ScreenHasher.computeFingerprint("com.example.app", nodes.reversed())

        assertEquals(16, hash1.length) // 8 bytes = 16 hex chars (64-bit)
        assertEquals("Deterministic hashing should be order-independent for sorted node tokens", hash1, hash2)
    }

    @Test
    fun testRuleLedger_persistenceAndLookup() {
        val storageFile = tempFolder.newFile("local_rules.json")
        val ledger = RuleLedger(storageFile)

        val fingerprint = "a1b2c3d4e5f60718"
        val userGoal = "Open Settings"
        val rule = ActionRule(type = ActionType.CLICK, targetBounds = null, textPayload = "settings_btn")

        assertNull(ledger.getActionRule(fingerprint, userGoal))

        ledger.addTransition(fingerprint, userGoal, rule)

        val retrieved = ledger.getActionRule(fingerprint, userGoal)
        assertNotNull(retrieved)
        assertEquals(ActionType.CLICK, retrieved?.type)
        assertEquals("settings_btn", retrieved?.textPayload)

        // Verify file reload persistence
        val reloaded = RuleLedger(storageFile)
        val reloadedRule = reloaded.getActionRule(fingerprint, userGoal)
        assertNotNull(reloadedRule)
        assertEquals(ActionType.CLICK, reloadedRule?.type)
    }
}
