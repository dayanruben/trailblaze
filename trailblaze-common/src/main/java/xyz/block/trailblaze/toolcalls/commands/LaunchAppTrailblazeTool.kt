package xyz.block.trailblaze.toolcalls.commands

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable
import maestro.orchestra.Command
import maestro.orchestra.LaunchAppCommand
import xyz.block.trailblaze.AgentMemory
import xyz.block.trailblaze.toolcalls.MapsToMaestroCommands
import xyz.block.trailblaze.toolcalls.TrailblazeToolClass

@Serializable
@TrailblazeToolClass("launchApp")
@LLMDescription(
  "Use this to open an app on the device as if a user tapped on the app icon in the launcher.",
)
data class LaunchAppTrailblazeTool(
  @LLMDescription("The package name of the app to launch. Example: 'com.example.app'")
  val appId: String,
  @LLMDescription(
    """
Available App Launch Modes:
- "REINSTALL" (Default if unspecified) will launch the app as if it was just installed and never run on the device before.
- "RESUME" will launch the app like you would from the apps launcher.  If the app was in memory, it'll pick up where it left off.
- "FORCE_RESTART" will force stop the application and then launch the app like you would from the app launcher.
    """,
  )
  val launchMode: LaunchMode = LaunchMode.REINSTALL,
) : MapsToMaestroCommands() {
  override fun toMaestroCommands(memory: AgentMemory): List<Command> = listOf(
    LaunchAppCommand(
      appId = appId,
      clearState = when (launchMode) {
        LaunchMode.REINSTALL -> true
        LaunchMode.RESUME,
        LaunchMode.FORCE_RESTART,
        -> false
      },
      stopApp = when (launchMode) {
        LaunchMode.RESUME -> false
        LaunchMode.FORCE_RESTART,
        LaunchMode.REINSTALL,
        -> true
      },
    ),
  )

  enum class LaunchMode {
    /**
     * Launch the app in a clean state, like when the app is initially installed.
     */
    REINSTALL,

    /**
     * Resume the app without clearing its state.
     */
    RESUME,

    /**
     * Stop the application and then restart it without clearing its state.
     */
    FORCE_RESTART,

    ;

    companion object {
      fun fromString(value: String?): LaunchMode = LaunchMode.entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: REINSTALL
    }
  }
}
