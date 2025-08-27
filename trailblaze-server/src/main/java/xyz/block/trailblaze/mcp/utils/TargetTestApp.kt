package xyz.block.trailblaze.mcp.utils

/**
 * The test app that will be used for running the tests.
 * It will start an instrumentation process and block the thread with a server running.
 */
data class TargetTestApp(
  val testAppId: String,
  val fqTestName: String,
  val gradleInstallAndroidTestCommand: String,
) {
  /** Empty Companion object to allow extension values */
  companion object {
    val DEFAULT = TargetTestApp(
      testAppId = "xyz.block.trailblaze.runner",
      fqTestName = "xyz.block.trailblaze.AndroidOnDeviceMcpServerTest",
      gradleInstallAndroidTestCommand = ":trailblaze-android-ondevice-mcp:installDebugAndroidTest",
    )
  }
}
