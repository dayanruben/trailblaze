package xyz.block.trailblaze.report.models

import kotlinx.serialization.Serializable
import maestro.orchestra.ApplyConfigurationCommand
import xyz.block.trailblaze.agent.model.AgentTaskStatus
import xyz.block.trailblaze.api.AgentActionType
import xyz.block.trailblaze.api.HasClickCoordinates
import xyz.block.trailblaze.api.ViewHierarchyTreeNode
import xyz.block.trailblaze.llm.LlmUsageAndCostExt.computeUsageSummary
import xyz.block.trailblaze.llm.TrailblazeLlmModel
import xyz.block.trailblaze.logs.client.TrailblazeJsonInstance
import xyz.block.trailblaze.logs.client.TrailblazeLog
import xyz.block.trailblaze.logs.model.HasAgentTaskStatus
import xyz.block.trailblaze.maestro.MaestroYamlSerializer
import xyz.block.trailblaze.utils.Ext.asMaestroCommand

@Serializable
data class SessionSummary(
  val sessionId: String,
  val outcome: String?,
  val trailblazeLlmModel: TrailblazeLlmModel?,
  val llmCallCount: Int,
  val totalCostInUsDollars: Double,
  val screenshotCount: Int,
  val screenshots: List<String>,
  val sessionStartTimestampMs: Long,
  val sessionDurationSeconds: Double,
  val agentTasks: List<TaskIdAndPrompt>,
  val eventGroups: List<PromptEventGroup>,
) {
  companion object {

    private const val HTTP_PORT: Int = 52525 // Default port for the report server

    fun screenshotUrl(sessionId: String, screenshotFile: String?, isStandaloneFileReport: Boolean): String? = if (screenshotFile == null) {
      null
    } else if (isStandaloneFileReport) {
      "$sessionId/$screenshotFile"
    } else {
      "http://localhost:$HTTP_PORT/static/$sessionId/$screenshotFile"
    }

    fun fromLogs(sessionId: String, logs: List<TrailblazeLog>, isStandaloneFileReport: Boolean): SessionSummary {
      val sortedLogs = logs.filterNot {
        it is TrailblazeLog.MaestroCommandLog && it.maestroCommandJsonObj.asMaestroCommand() is ApplyConfigurationCommand
      }
        .map {
          when (it) {
            is TrailblazeLog.MaestroDriverLog -> {
              it.copy(
                viewHierarchy = ViewHierarchyTreeNode(),
                screenshotFile = screenshotUrl(sessionId, it.screenshotFile, isStandaloneFileReport),
              )
            }

            is TrailblazeLog.TrailblazeLlmRequestLog -> {
              it.copy(
                viewHierarchy = ViewHierarchyTreeNode(),
                llmMessages = emptyList(),
                screenshotFile = screenshotUrl(sessionId, it.screenshotFile, isStandaloneFileReport),
              )
            }

            is TrailblazeLog.TrailblazeAgentTaskStatusChangeLog,
            is TrailblazeLog.MaestroCommandLog,
            is TrailblazeLog.TrailblazeToolLog,
            is TrailblazeLog.DelegatingTrailblazeToolLog,
            is TrailblazeLog.TrailblazeSessionStatusChangeLog,
            is TrailblazeLog.ObjectiveStartLog,
            is TrailblazeLog.ObjectiveCompleteLog,
            -> it
          }
        }.sortedBy { it.timestamp }

      val sessionStartTimestamp = sortedLogs.first().timestamp
      val screenshotUrls: List<String> = sortedLogs.mapNotNull { log ->
        when (log) {
          is TrailblazeLog.TrailblazeLlmRequestLog -> log.screenshotFile
          is TrailblazeLog.MaestroDriverLog -> {
            if (listOf(
                AgentActionType.CLEAR_APP_STATE,
                AgentActionType.KILL_APP,
                AgentActionType.LAUNCH_APP,
                AgentActionType.GRANT_PERMISSIONS,
              ).none { actionType ->
                log.action.type == actionType
              }
            ) {
              log.screenshotFile
            } else {
              null
            }
          }

          else -> null
        }
      }.distinct()

      val finalStatus = sortedLogs.filterIsInstance<HasAgentTaskStatus>().lastOrNull()

      val groups = mutableSetOf<PromptLogGroup>()

      var currGroup = PromptLogGroup()

      fun currPrompt(): String? = currGroup.logs.filterIsInstance<HasAgentTaskStatus>()
        .firstOrNull()?.agentTaskStatus?.statusData?.prompt

      fun addToCurrentGroup(log: TrailblazeLog) {
        currGroup.logs.add(log)
      }

      fun createNewGroupAndAddToThatNewGroup(newPromptLogGroup: PromptLogGroup) {
        if (currGroup.logs.isNotEmpty()) {
          groups.add(currGroup.sorted())
        }
        currGroup = newPromptLogGroup
      }

      sortedLogs.forEach { log ->
        if (log is HasAgentTaskStatus) {
          val newPrompt = log.agentTaskStatus.statusData.prompt
          if (newPrompt != currPrompt()) {
            createNewGroupAndAddToThatNewGroup(PromptLogGroup(newPrompt, mutableSetOf(log)))
          } else {
            when (log.agentTaskStatus) {
              is AgentTaskStatus.InProgress -> addToCurrentGroup(log)
              is AgentTaskStatus.Failure,
              is AgentTaskStatus.Success,
              -> addToCurrentGroup(log)
            }
          }
        } else {
          addToCurrentGroup(log)
        }
      }

      if (currGroup.logs.isNotEmpty()) {
        if (currGroup != groups.lastOrNull()) {
          groups.add(currGroup.sorted())
        }
      }

      val mappedToEvents = groups.map { logsInGroup: PromptLogGroup ->
        val events: List<SessionEvent> = logsInGroup.logs.mapNotNull { log ->
          when (log) {
            is TrailblazeLog.TrailblazeLlmRequestLog -> SessionEvent.LlmRequest(
              timestamp = log.timestamp,
              screenshotFile = log.screenshotFile,
              deviceWidth = log.deviceWidth,
              deviceHeight = log.deviceHeight,
              durationMs = log.durationMs,
              elapsedTimeMs = log.timestamp.toEpochMilliseconds() - sessionStartTimestamp.toEpochMilliseconds(),
            )

            is TrailblazeLog.MaestroDriverLog -> {
              val clickCoordinates: HasClickCoordinates? = if (log.action is HasClickCoordinates) {
                (log.action as HasClickCoordinates)
              } else {
                null
              }

              SessionEvent.MaestroDriver(
                timestamp = log.timestamp,
                screenshotFile = log.screenshotFile,
                deviceWidth = log.deviceWidth,
                deviceHeight = log.deviceHeight,
                durationMs = log.durationMs,
                elapsedTimeMs = log.timestamp.toEpochMilliseconds() - sessionStartTimestamp.toEpochMilliseconds(),
                code = TrailblazeJsonInstance.encodeToString(log.action),
                x = clickCoordinates?.x,
                y = clickCoordinates?.y,
              )
            }

            is TrailblazeLog.MaestroCommandLog -> SessionEvent.MaestroCommand(
              timestamp = log.timestamp,
              durationMs = log.durationMs,
              code = MaestroYamlSerializer.toYaml(listOf(log.maestroCommandJsonObj.asMaestroCommand()!!), false),
              elapsedTimeMs = log.timestamp.toEpochMilliseconds() - sessionStartTimestamp.toEpochMilliseconds(),
            )

            is TrailblazeLog.TrailblazeToolLog -> SessionEvent.TrailblazeTool(
              code = TrailblazeJsonInstance.encodeToString(log.trailblazeTool),
              timestamp = log.timestamp,
              durationMs = log.durationMs,
              elapsedTimeMs = log.timestamp.toEpochMilliseconds() - sessionStartTimestamp.toEpochMilliseconds(),
            )

            is TrailblazeLog.TrailblazeAgentTaskStatusChangeLog -> SessionEvent.AgentStatusChanged(
              details = log.agentTaskStatus::class.java.simpleName,
              prompt = log.agentTaskStatus.statusData.prompt,
              timestamp = log.timestamp,
              elapsedTimeMs = log.timestamp.toEpochMilliseconds() - sessionStartTimestamp.toEpochMilliseconds(),
            )

            is TrailblazeLog.TrailblazeSessionStatusChangeLog -> SessionEvent.SessionStatusChanged(
              details = log.sessionStatus::class.java.simpleName,
              timestamp = log.timestamp,
              elapsedTimeMs = log.timestamp.toEpochMilliseconds() - sessionStartTimestamp.toEpochMilliseconds(),
            )

            is TrailblazeLog.ObjectiveStartLog,
            is TrailblazeLog.ObjectiveCompleteLog,
            is TrailblazeLog.DelegatingTrailblazeToolLog,
            -> null
          }
        }

        val commands = logsInGroup.logs
          .filterIsInstance<TrailblazeLog.MaestroCommandLog>()
          .map { it.maestroCommandJsonObj.asMaestroCommand()!! }
        val maestroYaml = MaestroYamlSerializer.toYaml(
          commands = commands,
          includeConfiguration = false,
        )

        PromptEventGroup(
          prompt = logsInGroup.prompt,
          kotlin = null,
          yaml = maestroYaml,
          events = events,
        )
      }

      return SessionSummary(
        sessionId = sessionId,
        outcome = finalStatus?.let { it.agentTaskStatus::class.java.simpleName },
        llmCallCount = sortedLogs.filterIsInstance<TrailblazeLog.TrailblazeLlmRequestLog>().size,
        sessionStartTimestampMs = sessionStartTimestamp.toEpochMilliseconds(),
        screenshots = screenshotUrls,
        agentTasks = sortedLogs.filterIsInstance<HasAgentTaskStatus>()
          .sortedBy { (it as TrailblazeLog).timestamp }
          .distinctBy { it.agentTaskStatus.statusData.taskId }
          .map { TaskIdAndPrompt(it.agentTaskStatus.statusData.taskId, it.agentTaskStatus.statusData.prompt) },
        totalCostInUsDollars = sortedLogs.computeUsageSummary()?.totalCostInUsDollars ?: 0.0,
        screenshotCount = screenshotUrls.size,
        trailblazeLlmModel = sortedLogs.filterIsInstance<TrailblazeLog.TrailblazeLlmRequestLog>()
          .firstOrNull()?.trailblazeLlmModel,
        sessionDurationSeconds = (sortedLogs.last().timestamp.toEpochMilliseconds() - sessionStartTimestamp.toEpochMilliseconds()) / 1000.0,
        eventGroups = mappedToEvents,
      )
    }
  }
}
