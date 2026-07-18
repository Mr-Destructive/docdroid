package com.docdroid.agent

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NeedleAgentInitTest {

    @Test
    fun `new NeedleAgent is not initialized`() {
        val agent = NeedleAgent()
        assertFalse(agent.isInitialized())
    }

    @Test
    fun `new NeedleAgent has no init error`() {
        val agent = NeedleAgent()
        assertNull(agent.getInitError())
    }

    @Test
    fun `query fails when not initialized`() = runTest {
        val agent = NeedleAgent()
        val result = agent.query("hello", "[]")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not initialized") == true)
    }

    @Test
    fun `implements NeedleEngine interface`() {
        val agent: NeedleEngine = NeedleAgent()
        assertFalse(agent.isInitialized())
    }
}
