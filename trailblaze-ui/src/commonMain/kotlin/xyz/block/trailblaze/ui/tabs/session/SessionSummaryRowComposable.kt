package xyz.block.trailblaze.ui.tabs.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.block.trailblaze.logs.model.SessionStatus
import xyz.block.trailblaze.ui.composables.SelectableText
import xyz.block.trailblaze.ui.composables.StatusBadge
import xyz.block.trailblaze.ui.utils.FormattingUtils.formatDuration

@Composable
fun SessionSummaryRow(
  status: SessionStatus?,
  deviceName: String?,
  deviceType: String?,
  totalDurationMs: Long? = null,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      horizontalArrangement = Arrangement.Start,
      verticalAlignment = Alignment.CenterVertically
    ) {
      status?.let {
        StatusBadge(status = status)
      }
      Spacer(modifier = Modifier.width(16.dp))
      SelectableText(
        text = "Device: ${deviceName ?: "Unknown"}${deviceType?.let { " ($it)" } ?: ""}",
        style = MaterialTheme.typography.bodyMedium
      )
      totalDurationMs?.let {
        Spacer(modifier = Modifier.width(16.dp))
        val durationText = "Total: ${formatDuration(it)}"
        SelectableText(
          text = durationText,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium
        )
      }
    }
  }
}
