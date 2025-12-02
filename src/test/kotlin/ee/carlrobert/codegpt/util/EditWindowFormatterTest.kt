package ee.carlrobert.codegpt.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class EditWindowFormatterTest {

    private fun cursorOffsetFor(content: String, token: String): Int {
        val idx = content.indexOf(token)
        return if (idx < 0) -1 else idx + token.length
    }

    @Test
    fun shouldFormatMiddleCursorWithSmallConfig() {
        val cursorOffset = cursorOffsetFor(PY_SAMPLE_CONTENT, "def add(")
        val cfg = EditWindowConfig(0, 1, 1, 2)

        val result = EditWindowFormatter.formatWithIndices(PY_SAMPLE_CONTENT, "solver.py", cursorOffset, cfg)

        assertThat(result.formattedContent()).isEqualTo(
            """
                <|current_file_content|>
                current_file_path: solver.py
                b=2
                <|code_to_edit|>
                def add(<|cursor|>x, y):
                    return x+y
                <|/code_to_edit|>
                print(add(a,b))
                <|/current_file_content|>""".trimIndent()
        )
        assertThat(PY_SAMPLE_CONTENT.substring(result.editStartIndex, result.editEndIndex))
            .isEqualTo(
                """
                def add(x, y):
                    return x+y""".trimIndent()
            )
    }

    @Test
    fun shouldFormatStartCursorWithSmallConfig() {
        val cursorOffset = 0
        val cfg = EditWindowConfig(0, 1, 0, 2)

        val result = EditWindowFormatter.formatWithIndices(PY_SAMPLE_CONTENT, "solver.py", cursorOffset, cfg)

        assertThat(result.formattedContent()).isEqualTo(
            """
                <|current_file_content|>
                current_file_path: solver.py
                <|code_to_edit|>
                <|cursor|>a=1
                b=2
                <|/code_to_edit|>
                def add(x, y):
                <|/current_file_content|>""".trimIndent()
        )
        assertThat(PY_SAMPLE_CONTENT.substring(result.editStartIndex, result.editEndIndex))
            .isEqualTo(
                """
                a=1
                b=2""".trimIndent()
            )
    }

    @Test
    fun shouldFormatEndCursorWithSmallConfig() {
        val content = "a=1\nb=2\nc=3"
        val cursorOffset = content.length
        val cfg = EditWindowConfig(1, 0, 2, 0)

        val result = EditWindowFormatter.formatWithIndices(content, "solver.py", cursorOffset, cfg)

        assertThat(result.formattedContent()).isEqualTo(
            """
                <|current_file_content|>
                current_file_path: solver.py
                a=1
                <|code_to_edit|>
                b=2
                c=3<|cursor|>
                <|/code_to_edit|>
                <|/current_file_content|>""".trimIndent()
        )
        assertThat(content.substring(result.editStartIndex, result.editEndIndex))
            .isEqualTo(
                """
                b=2
                c=3""".trimIndent()
            )
    }

    @Test
    fun shouldFormatEmptyFileWithZeroWindowsAtStartCursor() {
        val content = ""
        val cursorOffset = 0
        val cfg = EditWindowConfig(0, 0, 0, 0)

        val result = EditWindowFormatter.formatWithIndices(content, "solver.py", cursorOffset, cfg)

        assertThat(result.formattedContent()).isEqualTo(
            """
                <|current_file_content|>
                current_file_path: solver.py
                <|code_to_edit|>
                <|cursor|>
                <|/code_to_edit|>
                <|/current_file_content|>""".trimIndent()
        )
        assertThat(content.substring(result.editStartIndex, result.editEndIndex)).isEmpty()
    }

    @Test
    fun shouldNormalizeCrlfAndPlaceCursorInMiddle() {
        val content = "a=1\r\nb=2\r\nc=3"
        val cursorOffset = content.indexOf("b=2")
        val cfg = EditWindowConfig(0, 0, 1, 1)

        val result = EditWindowFormatter.formatWithIndices(content, "solver.py", cursorOffset, cfg)

        assertThat(result.formattedContent()).isEqualTo(
            """
                <|current_file_content|>
                current_file_path: solver.py
                a=1
                <|code_to_edit|>
                b<|cursor|>=2
                <|/code_to_edit|>
                c=3
                <|/current_file_content|>""".trimIndent()
        )
        val normalizedContent = content.replace("\r\n", "\n").replace('\r', '\n')
        assertThat(normalizedContent.substring(result.editStartIndex, result.editEndIndex))
            .isEqualTo("b=2")
    }

    @Test
    fun shouldClampCursorOffsetBeyondEnd() {
        val content = "abc"
        val cursorOffset = 9999
        val cfg = EditWindowConfig(0, 0, 0, 0)

        val result = EditWindowFormatter.formatWithIndices(content, "solver.py", cursorOffset, cfg)

        assertThat(result.formattedContent()).isEqualTo(
            """
                <|current_file_content|>
                current_file_path: solver.py
                <|code_to_edit|>
                abc<|cursor|>
                <|/code_to_edit|>
                <|/current_file_content|>""".trimIndent()
        )
        assertThat(content.substring(result.editStartIndex, result.editEndIndex))
            .isEqualTo("abc")
    }

    @Test
    fun shouldFormatLargeSequentialLinesWithDefaults() {
        val content = generateLines(500)
        val cursorOffset = content.indexOf("LINE250")

        val result = EditWindowFormatter.formatWithIndices(content, "solver.py", cursorOffset)

        assertThat(result.formattedContent()).isEqualTo(buildExpectedLinesOutput(total = 500, cursorLine = 250))
    }

    private fun generateLines(n: Int): String {
        val sb = StringBuilder()
        for (i in 1..n) {
            sb.append("LINE").append(i)
            if (i < n) sb.append('\n')
        }
        return sb.toString()
    }

    private fun buildExpectedLinesOutput(total: Int, cursorLine: Int): String {
        val fullStart = maxOf(1, cursorLine - 150)
        val fullEnd = minOf(total, cursorLine + 250)
        val editStart = maxOf(1, cursorLine - 15)
        val editEnd = minOf(total, cursorLine + 25)

        val sb = StringBuilder()
        sb.append("<|current_file_content|>\n")
        sb.append("current_file_path: solver.py\n")
        for (ln in fullStart..fullEnd) {
            if (ln == editStart) sb.append("<|code_to_edit|>\n")
            if (ln == cursorLine) sb.append("<|cursor|>")
            sb.append("LINE").append(ln)
            if (ln == editEnd) {
                if (ln < fullEnd) sb.append('\n').append("<|/code_to_edit|>").append('\n')
                else sb.append('\n').append("<|/code_to_edit|>")
            } else if (ln < fullEnd) {
                sb.append('\n')
            }
        }
        sb.append('\n').append("<|/current_file_content|>")
        return sb.toString()
    }

    companion object {
        private val PY_SAMPLE_CONTENT = """
                a=1
                b=2
                def add(x, y):
                    return x+y
                print(add(a,b))
                end""".trimIndent()
    }
}
