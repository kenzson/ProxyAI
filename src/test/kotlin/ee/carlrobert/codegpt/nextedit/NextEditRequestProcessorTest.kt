package ee.carlrobert.codegpt.nextedit

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class NextEditRequestProcessorTest {

    @Test
    fun shouldReplaceCodeToEditRegionWithCursor() {
        val originalContent = """
            <|current_file_content|>
            current_file_path: Test.java
            <|code_to_edit|>
            old content<|cursor|> here
            <|/code_to_edit|>
            <|/current_file_content|>
            """.trimIndent()

        val response = """
            ```
            new content
            ```
            """.trimIndent()

        val actual = NextEditRequestProcessor.replaceCodeToEditRegion(originalContent, response)

        assertThat(actual).isEqualTo("""
            <|current_file_content|>
            current_file_path: Test.java
            new content
            <|/current_file_content|>
            """.trimIndent())
    }

    @Test
    fun shouldHandleMissingTags() {
        val originalContent = """
            public class BookDto {
                private String title;
            }
            """.trimIndent()

        val response = """
            ```
            new content
            ```
            """.trimIndent()

        val actual = NextEditRequestProcessor.replaceCodeToEditRegion(originalContent, response)
        assertThat(actual).isEqualTo(originalContent)
    }

    @Test
    fun shouldHandleEmptyResponse() {
        val originalContent = """
            <|current_file_content|>
            current_file_path: Test.java
            <|code_to_edit|>
            original content
            <|/code_to_edit|>
            <|/current_file_content|>
            """.trimIndent()

        val actual = NextEditRequestProcessor.replaceCodeToEditRegion(originalContent, "")
        assertThat(actual).isEqualTo(originalContent)
    }

    @Test
    fun shouldExtractFromMultipleBacktickBlocks() {
        val originalContent = """
            <|current_file_content|>
            current_file_path: Test.java
            <|code_to_edit|>
            original content
            <|/code_to_edit|>
            <|/current_file_content|>
            """.trimIndent()

        val response = """
            Some text before
            ```
            first block
            ```
            Some text in between
            ```
            second block
            ```
            Some text after
            """.trimIndent()

        val actual = NextEditRequestProcessor.replaceCodeToEditRegion(originalContent, response)

        // Should extract from the first backtick block
        val expected = """
            <|current_file_content|>
            current_file_path: Test.java
            first block
            <|/current_file_content|>
            """.trimIndent()

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun shouldPreserveLineEndingsWithPrefixEndingInNewline() {
        val originalContent = """
            <|current_file_content|>
            current_file_path: Test.java
            prefix line
            <|code_to_edit|>
            content to replace
            <|/code_to_edit|>
            suffix line
            <|/current_file_content|>
            """.trimIndent()

        val response = """
            ```
            new content
            ```
            """.trimIndent()

        val expected = """
            <|current_file_content|>
            current_file_path: Test.java
            prefix line
            new content
            suffix line
            <|/current_file_content|>
            """.trimIndent()

        val actual = NextEditRequestProcessor.replaceCodeToEditRegion(originalContent, response)
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun shouldHandlePrefixWithoutNewline() {
        val originalContent = """<|current_file_content|>
current_file_path: Test.java
prefix line<|code_to_edit|>
content to replace
<|/code_to_edit|>
suffix line
<|/current_file_content|>"""

        val response = """
            ```
            new content
            ```
            """.trimIndent()

        val expected = """<|current_file_content|>
current_file_path: Test.java
prefix line
new content
suffix line
<|/current_file_content|>"""

        val actual = NextEditRequestProcessor.replaceCodeToEditRegion(originalContent, response)
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun shouldHandleSuffixStartingWithNewline() {
        val originalContent = """
            <|current_file_content|>
            current_file_path: Test.java
            prefix line
            <|code_to_edit|>
            content to replace
            <|/code_to_edit|>
            suffix line
            <|/current_file_content|>
        """.trimIndent()

        val aiResponse = "```\nnew content\n```"

        val expected = """
            <|current_file_content|>
            current_file_path: Test.java
            prefix line
            new content
            suffix line
            <|/current_file_content|>
        """.trimIndent()

        val actual = NextEditRequestProcessor.replaceCodeToEditRegion(originalContent, aiResponse)
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun shouldHandleContentWithoutBackticks() {
        val originalContent = """
            <|current_file_content|>
            current_file_path: Test.java
            <|code_to_edit|>
            original content
            <|/code_to_edit|>
            <|/current_file_content|>
        """.trimIndent()

        val aiResponse = "new content without backticks"

        val actual = NextEditRequestProcessor.replaceCodeToEditRegion(originalContent, aiResponse)

        // Should return original content since no backticks found
        assertThat(actual).isEqualTo(originalContent)
    }

    @Test
    fun shouldHandleCursorMarkerInDifferentPositions() {
        val originalContent = """
            <|current_file_content|>
            current_file_path: Test.java
            <|code_to_edit|>
            line1
            li<|cursor|>ne2
            line3
            <|/code_to_edit|>
            <|/current_file_content|>
        """.trimIndent()

        val aiResponse = """
            ```
            line1
            modified line2
            line3
            ```
        """.trimIndent()

        val expected = """
            <|current_file_content|>
            current_file_path: Test.java
            line1
            modified line2
            line3
            <|/current_file_content|>
        """.trimIndent()

        val actual = NextEditRequestProcessor.replaceCodeToEditRegion(originalContent, aiResponse)
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun shouldHandleLargeContentReplacement() {
        val originalContent = """<|current_file_content|>
current_file_path: LargeFile.java
<|code_to_edit|>
line1
line2
line3
<|/code_to_edit|>
<|/current_file_content|>"""

        val aiResponse = """
            ```
            modified line1
            modified line2
            modified line3
            ```
        """.trimIndent()

        val expected = """<|current_file_content|>
current_file_path: LargeFile.java
modified line1
modified line2
modified line3
<|/current_file_content|>"""

        val actual = NextEditRequestProcessor.replaceCodeToEditRegion(originalContent, aiResponse)
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun shouldHandleBackticksWithLanguageSpecifier() {
        val originalContent = """
            <|current_file_content|>
            current_file_path: Test.java
            <|code_to_edit|>
            original content
            <|/code_to_edit|>
            <|/current_file_content|>
        """.trimIndent()

        val aiResponse = """
            ```java
            public class Test {
                // modified content
            }
            ```
        """.trimIndent()

        val expected = """
            <|current_file_content|>
            current_file_path: Test.java
            public class Test {
                // modified content
            }
            <|/current_file_content|>
        """.trimIndent()

        val actual = NextEditRequestProcessor.replaceCodeToEditRegion(originalContent, aiResponse)
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun shouldHandleEmptyCodeToEditRegion() {
        val originalContent = """
            <|current_file_content|>
            current_file_path: Test.java
            prefix content
            <|code_to_edit|>
            <|/code_to_edit|>
            suffix content
            <|/current_file_content|>
        """.trimIndent()

        val aiResponse = "```\nnew content\n```"

        val expected = """
            <|current_file_content|>
            current_file_path: Test.java
            prefix content
            new content
            suffix content
            <|/current_file_content|>
        """.trimIndent()

        val actual = NextEditRequestProcessor.replaceCodeToEditRegion(originalContent, aiResponse)
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun shouldHandleMalformedTags() {
        val originalContent = """
            <|current_file_content|>
            current_file_path: Test.java
            <|code_to_edit|>
            content
            <|/current_file_content|>
        """.trimIndent()

        val aiResponse = "```\nnew content\n```"

        // Missing closing tag, should return original
        val actual = NextEditRequestProcessor.replaceCodeToEditRegion(originalContent, aiResponse)
        assertThat(actual).isEqualTo(originalContent)
    }
}