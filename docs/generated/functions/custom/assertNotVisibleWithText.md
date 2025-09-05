## Tool `assertNotVisibleWithText`

## Description
Asserts that an element with the provided text is NOT visible on the screen. The text argument is required.
Only provide additional fields if the text provided exactly matches elsewhere on the screen.
In this case, the additional fields will be used to identify the specific view to assert visibility for.

NOTE:
- This will wait for the item to appear if it is not visible yet.
- You may need to scroll down the page or close the keyboard if it is not visible in the screenshot.
- Use this tool whenever an objective begins with the word expect, verify, confirm, or assert (case-insensitive).

### Command Class
`xyz.block.trailblaze.toolcalls.commands.AssertNotVisibleWithTextTrailblazeTool`

### Registered `AssertNotVisibleWithTextTrailblazeTool` in `ToolRegistry`
### Required Parameters
- `text`: `String`
  The text to match on. This is required.
NOTE:
- The text can be a regular expression.
- If more than one view matches the text, other optional properties are required to disambiguate.

### Optional Parameters
- `index`: `Integer`
  0-based index of the view to select among those that match all other criteria.
- `id`: `String`
  Regex for selecting the view by id. This is helpful to disambiguate when multiple views have the same text.
- `enabled`: `Boolean`
- `selected`: `Boolean`



<hr/>

**NOTE**: THIS IS GENERATED DOCUMENTATION