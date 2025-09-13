package xyz.block.trailblaze.agent

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.ToolResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.message.Attachment
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.params.LLMParams
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import xyz.block.trailblaze.agent.model.PromptStepStatus
import xyz.block.trailblaze.api.ScreenState
import xyz.block.trailblaze.api.TrailblazeAgent
import xyz.block.trailblaze.api.ViewHierarchyTreeNode
import xyz.block.trailblaze.llm.TrailblazeLlmModel
import xyz.block.trailblaze.logs.client.TrailblazeJsonInstance
import xyz.block.trailblaze.toolcalls.TrailblazeTool
import xyz.block.trailblaze.toolcalls.TrailblazeToolResult
import xyz.block.trailblaze.toolcalls.commands.ObjectiveStatusTrailblazeTool
import xyz.block.trailblaze.util.TemplatingUtil
import xyz.block.trailblaze.yaml.PromptStep

class TrailblazeKoogLlmClientHelper(
  var systemPromptTemplate: String,
  val userObjectiveTemplate: String,
  val userMessageTemplate: String,
  val trailblazeLlmModel: TrailblazeLlmModel,
  val llmClient: LLMClient,
  val elementComparator: TrailblazeElementComparator,
) {

  // This field will be used to determine whether or not our next request to the LLM should require
  // a tool call. This occasionally happens when the LLM returns the tool call in the message
  // instead of actually triggering the tool. In this case we already have the model's reasoning
  // so we can just force it to call a tool next.
  private var shouldForceToolCall = false

  fun setShouldForceToolCall(force: Boolean) {
    shouldForceToolCall = force
  }

  fun getShouldForceToolCall(): Boolean = shouldForceToolCall

  private var forceStepStatusUpdate = false

  fun setForceStepStatusUpdate(force: Boolean) {
    forceStepStatusUpdate = force
  }

  fun getForceStepStatusUpdate(): Boolean = forceStepStatusUpdate

  fun handleTrailblazeToolForPrompt(
    trailblazeTool: TrailblazeTool,
    llmResponseId: String?,
    step: PromptStepStatus,
    screenStateForLlmRequest: ScreenState,
    agent: TrailblazeAgent,
  ): TrailblazeToolResult = when (trailblazeTool) {
    is ObjectiveStatusTrailblazeTool -> {
      setForceStepStatusUpdate(false)
      when (trailblazeTool.status) {
        "in_progress" -> TrailblazeToolResult.Success
        "failed" -> {
          step.markAsFailed()
          // Using objective to determine when we're done, not the tool result
          TrailblazeToolResult.Success
        }

        "completed" -> {
          step.markAsComplete()
          TrailblazeToolResult.Success
        }

        else -> TrailblazeToolResult.Error.UnknownTrailblazeTool(trailblazeTool)
      }
    }

    else -> {
      val (updatedTools, trailblazeToolResult) = agent.runTrailblazeTools(
        tools = listOf(trailblazeTool),
        llmResponseId = llmResponseId,
        screenState = screenStateForLlmRequest,
        elementComparator = elementComparator,
      )
      setForceStepStatusUpdate(true)
      println("\u001B[33m\n[ACTION_TAKEN] Tool executed: ${trailblazeTool.javaClass.simpleName}\u001B[0m")
      trailblazeToolResult
    }
  }

  fun ToolRegistry.getTrailblazeToolFromToolRegistry(toolName: String, toolArgs: JsonObject): TrailblazeTool {
    val toolRegistry = this

    @Suppress("UNCHECKED_CAST")
    val koogTool: Tool<TrailblazeTool, ToolResult> =
      toolRegistry.getTool(toolName) as Tool<TrailblazeTool, ToolResult>
    val trailblazeTool: TrailblazeTool = TrailblazeJsonInstance.decodeFromJsonElement(
      deserializer = koogTool.argsSerializer,
      element = toolArgs,
    )
    return trailblazeTool
  }

  fun handleLlmResponse(
    toolRegistry: ToolRegistry,
    llmMessage: String?,
    toolName: String,
    toolArgs: JsonObject,
    llmResponseId: String?,
    step: PromptStepStatus,
    screenStateForLlmRequest: ScreenState,
    agent: TrailblazeAgent,
  ) {
    val trailblazeToolResult = try {
      val trailblazeTool = toolRegistry.getTrailblazeToolFromToolRegistry(
        toolName = toolName,
        toolArgs = toolArgs,
      )
      handleTrailblazeToolForPrompt(
        trailblazeTool = trailblazeTool,
        llmResponseId = llmResponseId,
        step = step,
        screenStateForLlmRequest = screenStateForLlmRequest,
        agent = agent,
      )
    } catch (e: Exception) {
      TrailblazeToolResult.Error.ExceptionThrown.fromThrowable(
        throwable = e,
        trailblazeTool = null,
      )
    }

    step.addCompletedToolCallToChatHistory(
      llmResponseContent = llmMessage,
      toolName = toolName,
      toolArgs = toolArgs,
      commandResult = trailblazeToolResult,
    )
  }

  suspend fun callLlm(
    llmRequestData: KoogLlmRequestData,
  ): List<Message.Response> {
    var lastException: Exception? = null
    val maxRetries = 3

    for (attempt in 1..maxRetries) {
      try {
        val koogLlmResponse: List<Message.Response> = llmClient.execute(
          prompt = Prompt(
            messages = llmRequestData.messages,
            id = llmRequestData.callId,
            params = LLMParams(
              temperature = null,
              speculation = null,
              schema = null,
              toolChoice = llmRequestData.toolChoice,
            ),
          ),
          model = trailblazeLlmModel.toKoogLlmModel(),
          tools = llmRequestData.toolDescriptors,
        )
        return koogLlmResponse
      } catch (e: Exception) {
        lastException = e
        if (attempt < maxRetries) {
          val baseDelayMs = 1000L // 1 second base delay
          val delayMs = baseDelayMs + (attempt - 1) * 3000L // Add 3 seconds per retry
          println("[RETRY] Server error (attempt $attempt/$maxRetries), retrying in ${delayMs}ms...")
          delay(delayMs)
        } else {
          // exhausted retries
          throw e
        }
      }
    }
    throw lastException ?: RuntimeException("Unexpected error in retry logic")
  }

  fun createNextChatRequestKoog(
    screenState: ScreenState,
    step: PromptStep,
    forceStepStatusUpdate: Boolean,
    limitedHistory: List<Message>,
  ) = buildList {
    add(
      Message.System(
        content = systemPromptTemplate,
        metaInfo = RequestMetaInfo.create(Clock.System),
      ),
    )
    add(
      Message.User(
        content = TemplatingUtil.renderTemplate(
          template = userObjectiveTemplate,
          values = mapOf(
            "objective" to step.prompt,
          ),
        ),
        metaInfo = RequestMetaInfo.create(Clock.System),
      ),
    )
    add(
      Message.User(
        content = TrailblazeAiRunnerMessages.getReminderMessage(step, forceStepStatusUpdate),
        metaInfo = RequestMetaInfo.create(Clock.System),
      ),
    )

    // Add previous LLM responses
    addAll(limitedHistory)

    val viewHierarchyJson = Json.encodeToString(ViewHierarchyTreeNode.serializer(), screenState.viewHierarchy)
    add(
      Message.User(
        content = TemplatingUtil.renderTemplate(
          template = userMessageTemplate,
          values = mapOf(
            "view_hierarchy" to viewHierarchyJson,
          ),
        ),
        attachments = buildList {
          val screenshotBytes = screenState.screenshotBytes
          if (screenshotBytes != null &&
            screenshotBytes.isNotEmpty() &&
            trailblazeLlmModel.capabilityIds.contains(
              LLMCapability.Vision.Image.id,
            )
          ) {
            add(
              Attachment.Image(
                AttachmentContent.Binary.Bytes(screenshotBytes),
                format = "png",
              ),
            )
          }
        },
        metaInfo = RequestMetaInfo.create(Clock.System),
      ),
    )
  }
}
