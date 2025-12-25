package ee.carlrobert.codegpt.nextedit

import ee.carlrobert.codegpt.nextedit.NextEditPromptUtil

object NextEditRequestProcessor {
    const val EDITABLE_REGION_START = "<|editable_region_start|>"
    const val EDITABLE_REGION_END = "<|/editable_region_end|>"

    const val CODE_TO_EDIT_START = "<|code_to_edit|>"
    const val CODE_TO_EDIT_END = "<|/code_to_edit|>"
    const val CURSOR_MARKER = "<|cursor|>"

    fun replaceEditableRegionStrict(originalFileWithMarkers: String, newRegionContent: String): String {
        val startIdx = originalFileWithMarkers.indexOf(EDITABLE_REGION_START)
        val endIdx = originalFileWithMarkers.indexOf(EDITABLE_REGION_END)
        if (startIdx < 0 || endIdx < 0 || endIdx < startIdx) {
            return originalFileWithMarkers
        }

        val prefix = originalFileWithMarkers.substring(0, startIdx)
        val suffix = originalFileWithMarkers.substring(endIdx + EDITABLE_REGION_END.length)

        val result = StringBuilder(prefix)
        if (!prefix.endsWith("\n")) {
            result.append('\n')
        }
        result.append(newRegionContent)
        if (!newRegionContent.endsWith("\n")) {
            result.append('\n')
        }
        result.append(if (suffix.startsWith("\n")) suffix.substring(1) else suffix)
        return result.toString()
    }

    fun buildFileWithMarkers(
        fileContent: String,
        caretOffset: Int,
        linesBefore: Int,
        linesAfter: Int,
        maxLines: Int
    ): String {
        val (startPos, endPos) = NextEditPromptUtil.determineEditableRegionByLines(
            fileContent,
            caretOffset,
            linesBefore,
            linesAfter,
            maxLines
        )
        val prefix = fileContent.substring(0, startPos)
        val regionContent = fileContent.substring(startPos, endPos)
        val suffix = fileContent.substring(endPos)

        val sb = StringBuilder()
        sb.append(prefix)
        if (!prefix.endsWith("\n")) sb.append('\n')
        sb.append(EDITABLE_REGION_START).append('\n')
        sb.append(regionContent)
        if (!regionContent.endsWith("\n")) sb.append('\n')
        sb.append(EDITABLE_REGION_END).append('\n')
        sb.append(suffix)
        return sb.toString()
    }

    /**
     * Replaces content between <|code_to_edit|> and <|/code_to_edit|> markers with content extracted
     * from triple backticks in the AI response. Also removes any <|cursor|> markers from the extracted content.
     */
    fun replaceCodeToEditRegion(originalFileWithMarkers: String, aiResponse: String): String {
        val startIdx = originalFileWithMarkers.indexOf(CODE_TO_EDIT_START)
        val endIdx = originalFileWithMarkers.indexOf(CODE_TO_EDIT_END)
        if (startIdx < 0 || endIdx < 0 || endIdx < startIdx) {
            return originalFileWithMarkers
        }

        val extractedContent = extractCodeFromBackticks(aiResponse) ?: return originalFileWithMarkers
        val cleanedContent = removeCursorMarker(extractedContent)

        val prefix = originalFileWithMarkers.substring(0, startIdx)
        val suffix = originalFileWithMarkers.substring(endIdx + CODE_TO_EDIT_END.length)

        val result = StringBuilder(prefix)
        if (!prefix.endsWith("\n")) {
            result.append('\n')
        }
        result.append(cleanedContent)
        if (!cleanedContent.endsWith("\n")) {
            result.append('\n')
        }
        result.append(if (suffix.startsWith("\n")) suffix.substring(1) else suffix)
        return result.toString()
    }

    /**
     * Extracts content from the first triple backtick code block found in the AI response.
     * Returns null if no backticks are found.
     */
    fun extractCodeFromBackticks(message: String): String? {
        val fence = "```"
        val start = message.indexOf(fence)
        if (start == -1) return null

        var contentStart = start + fence.length
        if (contentStart < message.length && message[contentStart] == '\n') {
            contentStart += 1
        } else {
            val nl = message.indexOf('\n', contentStart)
            if (nl != -1) contentStart = nl + 1
        }

        // Find the end of the FIRST backtick block (not the last occurrence)
        val end = message.indexOf(fence, contentStart)
        if (end == -1 || end <= start) return null

        return message.substring(contentStart, end)
    }

    /**
     * Removes all occurrences of <|cursor|> markers from the content.
     */
    private fun removeCursorMarker(content: String): String {
        return content.replace(CURSOR_MARKER, "")
    }
}