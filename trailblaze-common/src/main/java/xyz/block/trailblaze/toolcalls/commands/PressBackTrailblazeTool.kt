package xyz.block.trailblaze.toolcalls.commands

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable
import maestro.orchestra.BackPressCommand
import maestro.orchestra.Command
import xyz.block.trailblaze.AgentMemory
import xyz.block.trailblaze.toolcalls.MapsToMaestroCommands
import xyz.block.trailblaze.toolcalls.TrailblazeToolClass

@Serializable
@TrailblazeToolClass("pressBack")
@LLMDescription(
  """
Presses the virtual back button on an Android device.
Navigates to the previous page or state.
Consider using `wait` command if the app is loading a new screen.
    """,
)
data object PressBackTrailblazeTool : MapsToMaestroCommands() {
  override fun toMaestroCommands(memory: AgentMemory): List<Command> = listOf(
    BackPressCommand(),
  )
}
