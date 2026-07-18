package com.docdroid.agent

import com.docdroid.model.DocumentFile
import com.docdroid.model.ToolResult
import com.docdroid.model.ToolStatus
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AgentLoopTest {

    private lateinit var registry: ToolRegistry
    private lateinit var dispatcher: ToolDispatcher
    private lateinit var pythonCodeGenerator: PythonCodeGenerator

    @Before
    fun setup() {
        registry = ToolRegistry()
        dispatcher = ToolDispatcher(registry)
        pythonCodeGenerator = PythonCodeGenerator()
    }

    private fun createLoop(needle: NeedleEngine): AgentLoop =
        AgentLoop(needle, dispatcher, pythonCodeGenerator)

    @Test
    fun `processMessage emits Error when needle not initialized`() = runTest {
        val needle = FakeNeedleEngine(false)
        val loop = createLoop(needle)

        val events = loop.processMessage("hello").toList()

        assertTrue(events.any { it is AgentEvent.Thinking })
        val errors = events.filterIsInstance<AgentEvent.Error>()
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("not initialized"))
    }

    @Test
    fun `processMessage executes tool and emits Response`() = runTest {
        registry.register("merge_pdfs") { args ->
            ToolResult("merge_pdfs", ToolStatus.SUCCESS, result = "merged ${args["input_paths"]}")
        }

        val needle = FakeNeedleEngine(
            initialized = true,
            responseJson = """[{"name":"merge_pdfs","arguments":{"input_paths":"/a.pdf,/b.pdf","output_path":"/out.pdf"}}]"""
        )
        val loop = createLoop(needle)

        val events = loop.processMessage("merge these PDFs").toList()

        assertTrue(events.any { it is AgentEvent.Thinking })
        assertTrue(events.any { it is AgentEvent.ToolCallStarted })
        assertTrue(events.any { it is AgentEvent.ToolCallCompleted })
        val responses = events.filterIsInstance<AgentEvent.Response>()
        assertEquals(1, responses.size)
        assertTrue(responses[0].message.contains("1/1"))
    }

    @Test
    fun `processMessage with no tool calls emits suggestion Response`() = runTest {
        val needle = FakeNeedleEngine(
            initialized = true,
            responseJson = "I don't know what to do with that."
        )
        val loop = createLoop(needle)

        val events = loop.processMessage("random text").toList()

        val responses = events.filterIsInstance<AgentEvent.Response>()
        assertEquals(1, responses.size)
        assertTrue(responses[0].message.contains("couldn't determine"))
    }

    @Test
    fun `processMessage with failed tool triggers code generation`() = runTest {
        registry.register("compress_pdf") { _ ->
            ToolResult("compress_pdf", ToolStatus.FAILED, error = "Not implemented")
        }

        val needle = FakeNeedleEngine(
            initialized = true,
            responseJson = """[{"name":"compress_pdf","arguments":{"input_path":"/doc.pdf","quality":"60","output_path":"/compressed.pdf"}}]"""
        )
        val loop = createLoop(needle)

        val events = loop.processMessage("compress this PDF").toList()

        assertTrue(events.any { it is AgentEvent.CodeGenerated })
    }

    @Test
    fun `processMessage with files includes file info in query`() = runTest {
        var capturedQuery = ""
        registry.register("extract_text") { args ->
            ToolResult("extract_text", ToolStatus.SUCCESS, result = "text from ${args["input_path"]}")
        }

        val needle = FakeNeedleEngine(
            initialized = true,
            responseJson = """[{"name":"extract_text","arguments":{"input_path":"/tmp/doc.pdf"}}]"""
        )
        val loop = createLoop(needle)

        val files = listOf(
            DocumentFile(name = "doc.pdf", path = "/tmp/doc.pdf", mimeType = "application/pdf", size = 1024)
        )

        val events = loop.processMessage("extract text", files).toList()

        capturedQuery = needle.lastQuery
        assertTrue(capturedQuery.contains("doc.pdf"))
        assertTrue(capturedQuery.contains("/tmp/doc.pdf"))
    }

    @Test
    fun `processMessage with multiple tool calls executes all`() = runTest {
        registry.register("tool_a") { _ -> ToolResult("tool_a", ToolStatus.SUCCESS, result = "a done") }
        registry.register("tool_b") { _ -> ToolResult("tool_b", ToolStatus.SUCCESS, result = "b done") }

        val needle = FakeNeedleEngine(
            initialized = true,
            responseJson = """[{"name":"tool_a","arguments":{}},{"name":"tool_b","arguments":{}}]"""
        )
        val loop = createLoop(needle)

        val events = loop.processMessage("do both").toList()

        val completed = events.filterIsInstance<AgentEvent.ToolCallCompleted>()
        assertEquals(2, completed.size)
    }

    @Test
    fun `processMessage error from needle emits Error event`() = runTest {
        val needle = FakeNeedleEngine(
            initialized = true,
            shouldFail = true
        )
        val loop = createLoop(needle)

        val events = loop.processMessage("fail please").toList()

        val errors = events.filterIsInstance<AgentEvent.Error>()
        assertEquals(1, errors.size)
    }
}

class FakeNeedleEngine(
    private val initialized: Boolean = true,
    private val responseJson: String = "[]",
    private val shouldFail: Boolean = false
) : NeedleEngine {

    var lastQuery: String = ""
        private set

    override fun isInitialized(): Boolean = initialized

    override fun getInitError(): String? = if (!initialized) "Not initialized" else null

    override suspend fun query(query: String, toolsJson: String): Result<List<ToolCall>> {
        lastQuery = query
        if (!initialized) {
            return Result.failure(Exception("Needle model not initialized"))
        }
        if (shouldFail) {
            return Result.failure(Exception("Simulated failure"))
        }
        return try {
            Result.success(parseFakeResponse(responseJson))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseFakeResponse(json: String): List<ToolCall> {
        val trimmed = json.trim()
        if (!trimmed.startsWith("[")) return emptyList()

        val arr = kotlinx.serialization.json.Json.parseToJsonElement(trimmed)
            as kotlinx.serialization.json.JsonArray
        val calls = mutableListOf<ToolCall>()
        for (element in arr) {
            val obj = element as kotlinx.serialization.json.JsonObject
            val name = (obj["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
            if (name.isEmpty()) continue
            val args = mutableMapOf<String, String>()
            val argsObj = obj["arguments"] as? kotlinx.serialization.json.JsonObject
            argsObj?.entries?.forEach { (key, value) ->
                args[key] = (value as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
            }
            calls.add(ToolCall(name = name, arguments = args))
        }
        return calls
    }
}
