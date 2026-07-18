package com.docdroid.data

import com.docdroid.model.DocumentFile
import com.docdroid.model.Role
import com.docdroid.model.ToolCallResult
import com.docdroid.model.ToolStatus
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ChatRepositoryTest {

    private lateinit var repository: ChatRepository

    @Before
    fun setup() {
        repository = ChatRepository()
    }

    @Test
    fun `initial state is empty`() = runTest {
        repository.messages.test {
            assertEquals(emptyList<com.docdroid.model.ChatMessage>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addUserMessage appends to messages`() = runTest {
        repository.addUserMessage("Hello", emptyList())

        repository.messages.test {
            val messages = awaitItem()
            assertEquals(1, messages.size)
            assertEquals(Role.USER, messages[0].role)
            assertEquals("Hello", messages[0].content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addUserMessage with attachments`() = runTest {
        val doc = DocumentFile(name = "test.pdf", path = "/tmp/test.pdf", mimeType = "application/pdf", size = 1024)
        repository.addUserMessage("Process this", listOf(doc))

        repository.messages.test {
            val messages = awaitItem()
            assertEquals(1, messages.size)
            assertEquals(1, messages[0].attachments.size)
            assertEquals("test.pdf", messages[0].attachments[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addAssistantMessage appends`() = runTest {
        repository.addAssistantMessage("Here is the result")

        repository.messages.test {
            val messages = awaitItem()
            assertEquals(1, messages.size)
            assertEquals(Role.ASSISTANT, messages[0].role)
            assertEquals("Here is the result", messages[0].content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addAssistantMessage with tool calls`() = runTest {
        val toolResult = ToolCallResult(
            toolName = "merge_pdfs",
            status = ToolStatus.SUCCESS,
            result = "Merged 2 PDFs"
        )
        repository.addAssistantMessage("Done!", listOf(toolResult))

        repository.messages.test {
            val messages = awaitItem()
            assertEquals(1, messages.size)
            assertEquals(1, messages[0].toolCalls.size)
            assertEquals("merge_pdfs", messages[0].toolCalls[0].toolName)
            assertEquals(ToolStatus.SUCCESS, messages[0].toolCalls[0].status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addSystemMessage appends`() = runTest {
        repository.addSystemMessage("Analyzing...")

        repository.messages.test {
            val messages = awaitItem()
            assertEquals(1, messages.size)
            assertEquals(Role.SYSTEM, messages[0].role)
            assertEquals("Analyzing...", messages[0].content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clear removes all messages`() = runTest {
        repository.addUserMessage("msg1")
        repository.addAssistantMessage("msg2")
        repository.addSystemMessage("msg3")

        repository.clear()

        repository.messages.test {
            val messages = awaitItem()
            assertTrue(messages.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `multiple messages maintain order`() = runTest {
        repository.addUserMessage("first")
        repository.addAssistantMessage("second")
        repository.addUserMessage("third")

        repository.messages.test {
            val messages = awaitItem()
            assertEquals(3, messages.size)
            assertEquals("first", messages[0].content)
            assertEquals("second", messages[1].content)
            assertEquals("third", messages[2].content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `each message has unique id`() = runTest {
        repository.addUserMessage("msg1")
        repository.addUserMessage("msg2")

        repository.messages.test {
            val messages = awaitItem()
            assertNotEquals(messages[0].id, messages[1].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `messages have timestamps`() = runTest {
        val before = System.currentTimeMillis()
        repository.addUserMessage("timestamped")
        val after = System.currentTimeMillis()

        repository.messages.test {
            val messages = awaitItem()
            assertTrue(messages[0].timestamp in before..after)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
