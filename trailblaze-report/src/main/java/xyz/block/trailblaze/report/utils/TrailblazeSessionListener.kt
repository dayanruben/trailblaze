package xyz.block.trailblaze.report.utils
/**
 * This is an abstraction on top of the logging system that will notify listeners of various states.
 */
interface TrailblazeSessionListener {
  val trailblazeSessionId: String
  fun onSessionStarted()
  fun onUpdate(message: String)
  fun onSessionEnded()
}

/**
 * Listener for changes to the session list (when sessions are added or removed).
 */
interface SessionListListener {
  fun onSessionAdded(sessionId: String)
  fun onSessionRemoved(sessionId: String)
}
