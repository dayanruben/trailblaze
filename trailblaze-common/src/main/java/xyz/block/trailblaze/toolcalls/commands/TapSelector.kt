package xyz.block.trailblaze.toolcalls.commands

import xyz.block.trailblaze.api.ViewHierarchyTreeNode
import xyz.block.trailblaze.toolcalls.ExecutableTrailblazeTool

/**
 * Given the root of the view hierarchy and a target node, find the best TrailblazeTool for tapping.
 * Prefers unique text, then unique accessibility text, then falls back to center point.
 */
fun findBestTapTrailblazeToolForNode(
  root: ViewHierarchyTreeNode,
  target: ViewHierarchyTreeNode,
  longPress: Boolean = false,
): ExecutableTrailblazeTool {
  val allNodes = root.aggregate().distinctBy { System.identityHashCode(it) }
  val targetAndDescendants = target.aggregate()

  // Try text in target or any descendant, prioritizing first found unique
  for (descendant in targetAndDescendants) {
    val text = descendant.text
    if (!text.isNullOrBlank()) {
      val nodesWithText = allNodes.filter { it.text == text }.distinctBy { System.identityHashCode(it) }
      if (nodesWithText.size == 1) {
        return if (longPress) {
          LongPressOnElementWithTextTrailblazeTool(text)
        } else {
          TapOnElementWithTextTrailblazeTool(text)
        }
      }
    }
  }

  // Try accessibility text in target or any descendant, prioritizing first found unique
  for (descendant in targetAndDescendants) {
    val accessibilityText = descendant.accessibilityText
    if (!accessibilityText.isNullOrBlank()) {
      val nodesWithAccText = allNodes.filter { it.accessibilityText == accessibilityText }
        .distinctBy { System.identityHashCode(it) }
      if (nodesWithAccText.size == 1) {
        return if (longPress) {
          LongPressElementWithAccessibilityTextTrailblazeTool(accessibilityText)
        } else {
          TapOnElementWithAccessiblityTextTrailblazeTool(accessibilityText)
        }
      }
    }
  }

  // Fallback to center point of the target node
  val bounds = target.bounds
  val (x, y) = if (bounds != null) bounds.centerX to bounds.centerY else 0 to 0
  return if (longPress) {
    LongPressOnPointTrailblazeTool(x, y)
  } else {
    TapOnPointTrailblazeTool(x, y)
  }
}
