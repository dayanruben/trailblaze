package xyz.block.trailblaze.ui.utils

import xyz.block.trailblaze.logs.client.TrailblazeLog
import xyz.block.trailblaze.logs.model.HasLlmResponseId
import xyz.block.trailblaze.ui.tabs.session.models.GroupedLog

object LogUtils {


  fun logIndentLevel(log: TrailblazeLog): Int = when (log) {
    is TrailblazeLog.MaestroCommandLog -> 1
    is TrailblazeLog.MaestroDriverLog -> 2
    is TrailblazeLog.TrailblazeLlmRequestLog -> 1
    is TrailblazeLog.TrailblazeToolLog -> 1
    is TrailblazeLog.DelegatingTrailblazeToolLog -> 2
    else -> 0
  }

  /**
   * Groups logs by llmResponseId while maintaining timestamp ordering.
   * Logs with the same non-null llmResponseId are grouped together.
   * The resulting list is sorted by the earliest timestamp in each group/single log.
   */
  fun groupLogsByLlmResponseId(logs: List<TrailblazeLog>): List<GroupedLog> {
    val sortedLogs = logs.sortedBy { it.timestamp }

    // First pass: assign llmResponseId to MaestroDriverLog from most recent MaestroCommandLog
    val logsWithAssignedIds = mutableListOf<Pair<TrailblazeLog, String?>>()
    var currentMaestroCommandResponseId: String? = null

    for (log in sortedLogs) {
      val responseId = when {
        log is HasLlmResponseId -> {
          // Update the current response ID if this is a MaestroCommandLog
          if (log is TrailblazeLog.MaestroCommandLog) {
            currentMaestroCommandResponseId = log.llmResponseId
          }
          log.llmResponseId
        }

        log is TrailblazeLog.MaestroDriverLog -> {
          // Inherit from the most recent MaestroCommandLog
          currentMaestroCommandResponseId
        }

        else -> null
      }

      logsWithAssignedIds.add(log to responseId)
    }

    // Group logs by the assigned response ID
    val groupedByResponseId = mutableMapOf<String?, MutableList<TrailblazeLog>>()

    for ((log, responseId) in logsWithAssignedIds) {
      groupedByResponseId.getOrPut(responseId) { mutableListOf() }.add(log)
    }

    val result = mutableListOf<GroupedLog>()

    // Process logs with llmResponseId = null (single logs)
    groupedByResponseId[null]?.forEach { log ->
      result.add(GroupedLog.Single(log))
    }

    // Process logs with non-null llmResponseId
    groupedByResponseId.entries
      .filter { it.key != null }
      .forEach { (responseId, logsInGroup) ->
        if (logsInGroup.size == 1) {
          result.add(GroupedLog.Single(logsInGroup.first()))
        } else {
          result.add(GroupedLog.Group(responseId!!, logsInGroup))
        }
      }

    // Sort the final result by timestamp (earliest timestamp in each group)
    return result.sortedBy { it.timestamp }
  }
}