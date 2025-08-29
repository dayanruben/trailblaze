package xyz.block.trailblaze.host.mcp.host.newtools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import xyz.block.trailblaze.MaestroTrailblazeAgent
import xyz.block.trailblaze.agent.TrailblazeRunner
import xyz.block.trailblaze.logs.client.TrailblazeLogger
import xyz.block.trailblaze.maestro.MaestroYamlParser
import xyz.block.trailblaze.mcp.TrailblazeMcpSseSessionContext
import xyz.block.trailblaze.report.utils.LogsRepo
import xyz.block.trailblaze.report.utils.TrailblazeSessionListener
import java.io.File

@Suppress("unused")
class McpTestCasesToolSet(
  private val sessionContext: TrailblazeMcpSseSessionContext?,
  private val logsRepo: LogsRepo,
  private val openAiRunnerProvider: () -> TrailblazeRunner,
  private val typesafeYamlExecutor: (String) -> Unit,
) : ToolSet {

  val testCasesDirectory: String = File("../").canonicalPath

  @LLMDescription("Lists all available Trailblaze test cases.")
  @Tool
  fun listTestCases(): List<TestCase> {
    println("Listing test cases in directory: $testCasesDirectory")
    val dir = File(testCasesDirectory).also { it.mkdirs() }

    return listAllTestCases(dir)
  }

  private fun listAllTestCases(dir: File): List<TestCase> {
    val trailFiles = dir.walkTopDown().filter { it.name.endsWith(".trail.yaml") }.toList()
    return trailFiles.map {
      TestCase(
        name = it.name.replace(".trail.yaml", ""),
        filePath = it.relativeTo(dir).path,
      )
    }
  }

  val ioScope = CoroutineScope(Dispatchers.IO)

  @LLMDescription("Retrieves the details of a specific Trailblaze test case by name.")
  @Tool
  fun runTestCase(
    @LLMDescription("The absolute path of the Trailblaze test case file.")
    filePath: String,
  ): String {
    val yamlFile = File(testCasesDirectory, filePath)
    if (!yamlFile.exists() || !yamlFile.isFile) {
      return "Test case file not found: $filePath"
    }

    val yaml = yamlFile.readText()

    // Send progress notifications if we have a session context with progress service
    sessionContext?.let { sessionContext ->
      val mcpSseSessionId = sessionContext.mcpSseSessionId
      println("Executing prompt for session: $mcpSseSessionId")
      ioScope.launch {
        logsRepo.startWatchingTrailblazeSession(object : TrailblazeSessionListener {
          override val trailblazeSessionId: String = TrailblazeLogger.getCurrentSessionId()

          var progress = 0
          override fun onSessionStarted() {
            sessionContext.sendIndeterminateProgressMessage(progress++, "Session Started for session $mcpSseSessionId")
          }

          override fun onUpdate(message: String) {
            sessionContext.sendIndeterminateProgressMessage(progress++, message)
          }

          override fun onSessionEnded() {
            sessionContext.sendIndeterminateProgressMessage(progress++, "Session Ended for session $mcpSseSessionId")
            println("Session $mcpSseSessionId ended. Stopping progress updates.")
            this@launch.cancel()
            logsRepo.stopWatching(this.trailblazeSessionId)
          }
        })
      }
    }

    // Try parsing as Maestro YAML first, then fallback to Typesafe Trailblaze Yaml
    try {
      val commands = MaestroYamlParser.parseYaml(yaml)
      // Is Valid Maestro Yaml
      val maestroAgent = openAiRunnerProvider().agent as MaestroTrailblazeAgent
      ioScope.launch {
        maestroAgent.runMaestroCommands(
          maestroCommands = commands,
          llmResponseId = null,
        )
      }
      return "Test Case Execution started with Maestro"
    } catch (e: Exception) {
      // Not Maestro Yaml, run as Typesafe Trailblaze Yaml
      try {
        typesafeYamlExecutor(yaml)
        return "Test Case Executed with Typesafe Trailblaze Yaml"
      } catch (e: Exception) {
        return "Failed to execute test case: ${e.message ?: "Unknown error"}"
      }
    }
  }

  @Serializable
  data class TestCase(
    val name: String,
    val filePath: String,
  )
}
