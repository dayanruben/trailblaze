package xyz.block.trailblaze.toolcalls.commands

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable
import maestro.orchestra.Command
import maestro.orchestra.ElementSelector
import maestro.orchestra.TapOnElementCommand
import xyz.block.trailblaze.AgentMemory
import xyz.block.trailblaze.toolcalls.MapsToMaestroCommands
import xyz.block.trailblaze.toolcalls.TrailblazeToolClass
import xyz.block.trailblaze.toolcalls.TrailblazeTools.REQUIRED_ACCESSIBILITY_TEXT_DESCRIPTION

@Serializable
@TrailblazeToolClass("tapOnElementWithAccessibilityText")
@LLMDescription(
  """
Invoking this function will trigger a tap on a view with the provided accessibility text.
This will commonly be used when tapping on visual elements on the screen that do not have
text to identify it. This includes images or avatars with text, prefer to use the
accessibility text for these views because the text for an image or avatar will not resolve.
Ensure that you provide the entire accessibilityText string to this function to streamline
finding the corresponding view.

The text argument is required. Only provide additional fields if the accessibility text
provided exactly matches elsewhere on the screen. In this case the additional fields will be
used to identify the specific view to tap on.
      """,
)
data class TapOnElementWithAccessiblityTextTrailblazeTool(
  @LLMDescription(REQUIRED_ACCESSIBILITY_TEXT_DESCRIPTION) val accessibilityText: String,
  val id: String? = null,
  val index: String? = null,
  val enabled: Boolean? = null,
  val selected: Boolean? = null,
) : MapsToMaestroCommands() {
  override fun toMaestroCommands(memory: AgentMemory): List<Command> = listOf(
    TapOnElementCommand(
      selector = ElementSelector(
        textRegex = accessibilityText,
        idRegex = id,
        index = index,
        enabled = enabled,
        selected = selected,
      ),
    ),
  )
}
