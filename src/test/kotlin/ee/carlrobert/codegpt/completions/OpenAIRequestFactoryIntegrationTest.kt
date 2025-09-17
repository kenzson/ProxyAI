package ee.carlrobert.codegpt.completions

import com.intellij.openapi.components.service
import ee.carlrobert.codegpt.ReferencedFile
import ee.carlrobert.codegpt.completions.factory.OpenAIRequestFactory
import ee.carlrobert.codegpt.conversations.Conversation
import ee.carlrobert.codegpt.conversations.ConversationService
import ee.carlrobert.codegpt.conversations.message.Message
import ee.carlrobert.codegpt.settings.configuration.ChatMode
import ee.carlrobert.codegpt.settings.prompts.PersonaPromptDetailsState
import ee.carlrobert.codegpt.settings.prompts.PersonasState
import ee.carlrobert.codegpt.settings.prompts.PromptsSettings
import ee.carlrobert.codegpt.settings.service.FeatureType
import ee.carlrobert.codegpt.util.file.FileUtil.getResourceContent
import ee.carlrobert.llm.client.openai.completion.OpenAIChatCompletionModel
import ee.carlrobert.llm.client.openai.completion.request.OpenAIChatCompletionStandardMessage
import org.assertj.core.api.Assertions.assertThat
import testsupport.IntegrationTest
import java.io.File

class OpenAIRequestFactoryIntegrationTest : IntegrationTest() {

    fun testDefaultPersonaUsesEditModePromptWhenEnabled() {
        useOpenAIService(OpenAIChatCompletionModel.GPT_4_O.code)
        service<PromptsSettings>().state.personas.selectedPersona = PersonasState.DEFAULT_PERSONA
        val conversation = ConversationService.getInstance().startConversation(project)
        val message = Message("Please refactor this code")
        val callParameters = ChatCompletionParameters
            .builder(conversation, message)
            .chatMode(ChatMode.EDIT)
            .build()

        val request = OpenAIRequestFactory().createChatRequest(callParameters)

        val systemMessages = request.messages
            .filterIsInstance<ee.carlrobert.llm.client.openai.completion.request.OpenAIChatCompletionStandardMessage>()
            .filter { it.role == "system" }
            .map { it.content }
        assertThat(systemMessages).isNotEmpty()
        val systemContent = systemMessages.first()
        assertThat(systemContent).isEqualTo(
            "You are an AI programming assistant integrated into a JetBrains IDE plugin. Your role is to answer coding questions, suggest new code, and perform refactoring or editing tasks. You have access to the following project information:\n" +
                    "\n" +
                    "Before we proceed with the main instructions, here is the content of relevant files in the project:\n" +
                    "\n" +
                    "<project_path>\n" +
                    "UNDEFINED\n" +
                    "</project_path>\n" +
                    "\n" +
                    "Instructions:\n" +
                    "\n" +
                    "1. Detect the intent behind the user's query:\n" +
                    "   - New code suggestion\n" +
                    "   - Technical explanation\n" +
                    "   - Code refactoring or editing\n" +
                    "\n" +
                    "2. For queries not related to the codebase or for new files, provide a standard code or text block response.\n" +
                    "\n" +
                    "3. For refactoring or editing an existing file, always generate a SEARCH/REPLACE block.\n" +
                    "\n" +
                    "4. For any code generation, refactoring, or editing task:\n" +
                    "   a. First, outline an implementation plan describing the steps to address the user's request.\n" +
                    "   b. As you generate code or SEARCH/REPLACE blocks, reference the relevant step(s) from your plan, explaining your approach for each change.\n" +
                    "   c. For complex tasks, break down the plan and code changes into smaller steps, presenting each with its rationale and code diff together.\n" +
                    "   d. If the user's intent is unclear, ask clarifying questions before proceeding.\n" +
                    "\n" +
                    "5. When generating SEARCH/REPLACE blocks:\n" +
                    "   a. Ensure each block represents an atomic, non-overlapping change that can be applied independently.\n" +
                    "   b. Provide sufficient context in the SEARCH part to uniquely locate the change.\n" +
                    "   c. Keep SEARCH blocks concise while including necessary surrounding lines.\n" +
                    "\n" +
                    "Formatting Guidelines:\n" +
                    "\n" +
                    "1. Begin with a brief, impersonal acknowledgment.\n" +
                    "\n" +
                    "2. Use the following format for code blocks:\n" +
                    "   ```[language]:[full_file_path]\n" +
                    "   [code content]\n" +
                    "   ```\n" +
                    "\n" +
                    "   Example:\n" +
                    "   ```java:/path/to/Main.java\n" +
                    "   public class Main {\n" +
                    "       public static void main(String[] args) {\n" +
                    "           System.out.println(\"Hello, World!\");\n" +
                    "       }\n" +
                    "   }\n" +
                    "   ```\n" +
                    "\n" +
                    "3. For new files, show the entire file content in a single code fence.\n" +
                    "\n" +
                    "4. For editing existing files, use this SEARCH/REPLACE structure:\n" +
                    "   ```[language]:[full_file_path]\n" +
                    "   <<<<<<< SEARCH\n" +
                    "   [exact lines from the file, including whitespace/comments]\n" +
                    "   =======\n" +
                    "   [replacement lines]\n" +
                    "   >>>>>>> REPLACE\n" +
                    "   ```\n" +
                    "\n" +
                    "   Example:\n" +
                    "   ```java:/path/to/Calculator.java\n" +
                    "   <<<<<<< SEARCH\n" +
                    "   public int add(int a, int b) {\n" +
                    "       return a + b;\n" +
                    "   }\n" +
                    "   =======\n" +
                    "   public int add(int a, int b) {\n" +
                    "       // Added input validation\n" +
                    "       if (a < 0 || b < 0) {\n" +
                    "           throw new IllegalArgumentException(\"Negative numbers not allowed\");\n" +
                    "       }\n" +
                    "       return a + b;\n" +
                    "   }\n" +
                    "   >>>>>>> REPLACE\n" +
                    "   ```\n" +
                    "\n" +
                    "5. Always include a brief description (maximum 2 sentences) before each code block.\n" +
                    "\n" +
                    "6. Do not provide an implementation plan for pure explanations or general questions.\n" +
                    "\n" +
                    "7. When refactoring an entire file, output multiple code blocks as needed, keeping changes concise unless a more extensive update is required.\n"
        )
    }

    fun testDefaultPersonaIsFilteredInAskMode() {
        useOpenAIService(OpenAIChatCompletionModel.GPT_4_O.code)
        service<PromptsSettings>().state.personas.selectedPersona = PersonasState.DEFAULT_PERSONA
        val conversation = ConversationService.getInstance().startConversation(project)
        val message = Message("Please refactor this code")
        val callParameters = ChatCompletionParameters
            .builder(conversation, message)
            .chatMode(ChatMode.ASK)
            .build()

        val request = OpenAIRequestFactory().createChatRequest(callParameters)

        val systemMessages = request.messages
            .filterIsInstance<ee.carlrobert.llm.client.openai.completion.request.OpenAIChatCompletionStandardMessage>()
            .filter { it.role == "system" }
            .map { it.content }
        assertThat(systemMessages).isNotEmpty()
        val systemContent = systemMessages.first()
        assertThat(systemContent).isEqualTo(
            "You are an AI programming assistant integrated into a JetBrains IDE plugin. Your role is to answer coding questions, suggest new code, and perform refactoring or editing tasks. You have access to the following project information:\n" +
                    "\n" +
                    "Before we proceed with the main instructions, here is the content of relevant files in the project:\n" +
                    "\n" +
                    "<project_path>\n" +
                    "UNDEFINED\n" +
                    "</project_path>\n" +
                    "\n" +
                    "Instructions:\n" +
                    "\n" +
                    "1. Detect the intent behind the user's query:\n" +
                    "   - New code suggestion\n" +
                    "   - Technical explanation\n" +
                    "   - Code refactoring or editing\n" +
                    "\n" +
                    "2. For queries not related to the codebase or for new files, provide a standard code or text block response.\n" +
                    "\n" +
                    "3. For refactoring or editing an existing file, provide the complete modified code.\n" +
                    "\n" +
                    "4. For any code generation, refactoring, or editing task:\n" +
                    "   a. First, outline an implementation plan describing the steps to address the user's request.\n" +
                    "   b. As you generate code, reference the relevant step(s) from your plan, explaining your approach for each change.\n" +
                    "   c. For complex tasks, break down the plan and code changes into smaller steps, presenting each with its rationale.\n" +
                    "   d. If the user's intent is unclear, ask clarifying questions before proceeding.\n" +
                    "\n" +
                    "5. When providing code modifications:\n" +
                    "   a. Ensure each code block represents a complete, working solution.\n" +
                    "   b. Include all necessary context and dependencies.\n" +
                    "   c. Maintain proper code formatting and structure.\n" +
                    "\n" +
                    "Formatting Guidelines:\n" +
                    "\n" +
                    "1. Begin with a brief, impersonal acknowledgment.\n" +
                    "\n" +
                    "2. Use the following format for code blocks:\n" +
                    "   ```[language]:[full_file_path]\n" +
                    "   [code content]\n" +
                    "   ```\n" +
                    "\n" +
                    "   Example:\n" +
                    "   ```java:/path/to/Main.java\n" +
                    "   public class Main {\n" +
                    "       public static void main(String[] args) {\n" +
                    "           System.out.println(\"Hello, World!\");\n" +
                    "       }\n" +
                    "   }\n" +
                    "   ```\n" +
                    "\n" +
                    "3. For new files, show the entire file content in a single code fence.\n" +
                    "\n" +
                    "4. For editing existing files, provide the complete modified code:\n" +
                    "   ```[language]:[full_file_path]\n" +
                    "   [complete modified file content]\n" +
                    "   ```\n" +
                    "\n" +
                    "   Example:\n" +
                    "   ```java:/path/to/Calculator.java\n" +
                    "   public class Calculator {\n" +
                    "       public int add(int a, int b) {\n" +
                    "           // Added input validation\n" +
                    "           if (a < 0 || b < 0) {\n" +
                    "               throw new IllegalArgumentException(\"Negative numbers not allowed\");\n" +
                    "           }\n" +
                    "           return a + b;\n" +
                    "       }\n" +
                    "       \n" +
                    "       public int subtract(int a, int b) {\n" +
                    "           return a - b;\n" +
                    "       }\n" +
                    "   }\n" +
                    "   ```\n" +
                    "\n" +
                    "5. Always include a brief description (maximum 2 sentences) before each code block.\n" +
                    "\n" +
                    "6. Do not provide an implementation plan for pure explanations or general questions.\n" +
                    "\n" +
                    "7. When refactoring an entire file, provide the complete updated file content in a single code block.\n"
        )
    }

    fun testChatRequestUsesFilteredPersonaPromptInAskMode() {
        useOpenAIService(OpenAIChatCompletionModel.GPT_4_O.code)
        val personaPromptWithSearchReplace = """
            You are a helpful assistant.
            For refactoring or editing an existing file, always generate a SEARCH/REPLACE block.
            When generating SEARCH/REPLACE blocks:
            - Include surrounding context
            - Keep SEARCH blocks concise while including necessary surrounding lines.
            Example:
            ```java
            <<<<<<< SEARCH
            old code
            =======
            new code
            >>>>>>> REPLACE
            ```
        """.trimIndent()
        val customPersona = PersonaPromptDetailsState().apply {
            id = 999L  // Non-default ID
            name = "Custom Test Persona"
            instructions = personaPromptWithSearchReplace
        }
        service<PromptsSettings>().state.personas.selectedPersona = customPersona
        val conversation = ConversationService.getInstance().startConversation(project)
        val message = Message("Please refactor this code")
        val callParameters = ChatCompletionParameters
            .builder(conversation, message)
            .chatMode(ChatMode.ASK)
            .build()

        val request = OpenAIRequestFactory().createChatRequest(callParameters)

        val systemMessages = request.messages
            .filterIsInstance<ee.carlrobert.llm.client.openai.completion.request.OpenAIChatCompletionStandardMessage>()
            .filter { it.role == "system" }
            .map { it.content }
        assertThat(systemMessages).isNotEmpty()
        val systemContent = systemMessages.first()
        assertThat(systemContent).isEqualTo(
            "You are a helpful assistant.\n" +
                    "For refactoring or editing an existing file, provide the complete modified code.\n" +
                    "When providing code modifications:\n" +
                    "   a. Ensure each code block represents a complete, working solution.\n" +
                    "   b. Include all necessary context and dependencies.\n" +
                    "   c. Maintain proper code formatting and structure.\n" +
                    "Example:\n" +
                    "   ```java:/path/to/Calculator.java\n" +
                    "   public int add(int a, int b) {\n" +
                    "       // Added input validation\n" +
                    "       if (a < 0 || b < 0) {\n" +
                    "           throw new IllegalArgumentException(\"Negative numbers not allowed\");\n" +
                    "       }\n" +
                    "       return a + b;\n" +
                    "   }\n" +
                    "   ```\n"
        )
    }

    fun testChatRequestKeepsOriginalPersonaPromptInEditMode() {
        useOpenAIService(OpenAIChatCompletionModel.GPT_4_O.code)
        val personaPromptWithSearchReplace = """
            You are a helpful assistant.
            For refactoring or editing an existing file, always generate a SEARCH/REPLACE block.
        """.trimIndent()
        service<PromptsSettings>().state.personas.selectedPersona.instructions =
            personaPromptWithSearchReplace
        val conversation = ConversationService.getInstance().startConversation(project)
        val message = Message("Please refactor this code")
        val callParameters = ChatCompletionParameters
            .builder(conversation, message)
            .chatMode(ChatMode.EDIT)
            .build()

        val request = OpenAIRequestFactory().createChatRequest(callParameters)

        val systemMessages = request.messages
            .filterIsInstance<ee.carlrobert.llm.client.openai.completion.request.OpenAIChatCompletionStandardMessage>()
            .filter { it.role == "system" }
            .map { it.content }
        assertThat(systemMessages).isNotEmpty()
        val systemContent = systemMessages.first()
        assertThat(systemContent).isEqualTo(
            "You are an AI programming assistant integrated into a JetBrains IDE plugin. Your role is to answer coding questions, suggest new code, and perform refactoring or editing tasks. You have access to the following project information:\n" +
                    "\n" +
                    "Before we proceed with the main instructions, here is the content of relevant files in the project:\n" +
                    "\n" +
                    "<project_path>\n" +
                    "UNDEFINED\n" +
                    "</project_path>\n" +
                    "\n" +
                    "Instructions:\n" +
                    "\n" +
                    "1. Detect the intent behind the user's query:\n" +
                    "   - New code suggestion\n" +
                    "   - Technical explanation\n" +
                    "   - Code refactoring or editing\n" +
                    "\n" +
                    "2. For queries not related to the codebase or for new files, provide a standard code or text block response.\n" +
                    "\n" +
                    "3. For refactoring or editing an existing file, always generate a SEARCH/REPLACE block.\n" +
                    "\n" +
                    "4. For any code generation, refactoring, or editing task:\n" +
                    "   a. First, outline an implementation plan describing the steps to address the user's request.\n" +
                    "   b. As you generate code or SEARCH/REPLACE blocks, reference the relevant step(s) from your plan, explaining your approach for each change.\n" +
                    "   c. For complex tasks, break down the plan and code changes into smaller steps, presenting each with its rationale and code diff together.\n" +
                    "   d. If the user's intent is unclear, ask clarifying questions before proceeding.\n" +
                    "\n" +
                    "5. When generating SEARCH/REPLACE blocks:\n" +
                    "   a. Ensure each block represents an atomic, non-overlapping change that can be applied independently.\n" +
                    "   b. Provide sufficient context in the SEARCH part to uniquely locate the change.\n" +
                    "   c. Keep SEARCH blocks concise while including necessary surrounding lines.\n" +
                    "\n" +
                    "Formatting Guidelines:\n" +
                    "\n" +
                    "1. Begin with a brief, impersonal acknowledgment.\n" +
                    "\n" +
                    "2. Use the following format for code blocks:\n" +
                    "   ```[language]:[full_file_path]\n" +
                    "   [code content]\n" +
                    "   ```\n" +
                    "\n" +
                    "   Example:\n" +
                    "   ```java:/path/to/Main.java\n" +
                    "   public class Main {\n" +
                    "       public static void main(String[] args) {\n" +
                    "           System.out.println(\"Hello, World!\");\n" +
                    "       }\n" +
                    "   }\n" +
                    "   ```\n" +
                    "\n" +
                    "3. For new files, show the entire file content in a single code fence.\n" +
                    "\n" +
                    "4. For editing existing files, use this SEARCH/REPLACE structure:\n" +
                    "   ```[language]:[full_file_path]\n" +
                    "   <<<<<<< SEARCH\n" +
                    "   [exact lines from the file, including whitespace/comments]\n" +
                    "   =======\n" +
                    "   [replacement lines]\n" +
                    "   >>>>>>> REPLACE\n" +
                    "   ```\n" +
                    "\n" +
                    "   Example:\n" +
                    "   ```java:/path/to/Calculator.java\n" +
                    "   <<<<<<< SEARCH\n" +
                    "   public int add(int a, int b) {\n" +
                    "       return a + b;\n" +
                    "   }\n" +
                    "   =======\n" +
                    "   public int add(int a, int b) {\n" +
                    "       // Added input validation\n" +
                    "       if (a < 0 || b < 0) {\n" +
                    "           throw new IllegalArgumentException(\"Negative numbers not allowed\");\n" +
                    "       }\n" +
                    "       return a + b;\n" +
                    "   }\n" +
                    "   >>>>>>> REPLACE\n" +
                    "   ```\n" +
                    "\n" +
                    "5. Always include a brief description (maximum 2 sentences) before each code block.\n" +
                    "\n" +
                    "6. Do not provide an implementation plan for pure explanations or general questions.\n" +
                    "\n" +
                    "7. When refactoring an entire file, output multiple code blocks as needed, keeping changes concise unless a more extensive update is required.\n"
        )
    }

    fun testInlineEditSingleRequestNoHistory() {
        useOpenAIService(OpenAIChatCompletionModel.GPT_4_1.code, FeatureType.INLINE_EDIT)
        val testFileContent = getResourceContent("/inline/TestClass.java")
        val tempFile = File.createTempFile("TestClass", ".java")
        tempFile.writeText(testFileContent)
        tempFile.deleteOnExit()
        val parameters = InlineEditCompletionParameters(
            prompt = "add logging to this method",
            selectedText = "myTestMethod()",
            filePath = tempFile.absolutePath,
            fileExtension = "java",
            projectBasePath = project.basePath,
            referencedFiles = null,
            gitDiff = null,
            conversation = null,
            conversationHistory = null,
            diagnosticsInfo = null
        )

        val request = OpenAIRequestFactory().createInlineEditRequest(parameters)

        val systemMessage = request.messages[0] as OpenAIChatCompletionStandardMessage
        assertThat(systemMessage.role).isEqualTo("system")
        assertThat(systemMessage.content).isEqualTo(
            """
You are a code modification assistant. Generate SEARCH/REPLACE blocks to modify the specified file.

Project Context:
Project root: ${project.basePath}
All file paths should be relative to this project root.

## Current File (Editable)
```java:${tempFile.absolutePath}
$testFileContent
```

## External Context

No external context selected.

## Task
Analyze the user's request and generate modifications for the current file using SEARCH/REPLACE blocks.

## Format
Use SEARCH/REPLACE blocks to specify exact code changes:

Single change in the same file:
```language:filepath
<<<<<<< SEARCH
[exact code to find]
=======
[replacement code]
>>>>>>> REPLACE
```

Multiple changes in the same file:
```language:filepath
<<<<<<< SEARCH
[first section]
=======
[first replacement]
>>>>>>> REPLACE

<<<<<<< SEARCH
[second section]
=======
[second replacement]
>>>>>>> REPLACE
```

## Requirements
• Match indentation and formatting exactly
• Include 2-4 lines of context for unique pattern matching
• Generate blocks only for the current file path
• Use complete code sections without truncation
• Ensure search and replacement content differ
• Include file path after language identifier using colon
• Make search patterns unique within the file

## Example
```java:src/Example.java
<<<<<<< SEARCH
public int calculate(int x, int y) {
    return x + y;
}
=======
public int calculate(int x, int y) {
    if (x < 0 || y < 0) {
        throw new IllegalArgumentException("Values must be non-negative");
    }
    return x + y;
}
>>>>>>> REPLACE
```
""".trimIndent()
        )
        val userMessage = request.messages[1] as OpenAIChatCompletionStandardMessage
        assertThat(userMessage.role).isEqualTo("user")
        assertThat(userMessage.content).isEqualTo(
            """
            Selected code:
            ```java
            myTestMethod()
            ```

            Request: add logging to this method
        """.trimIndent()
        )
    }

    fun testInlineEditFollowUpWithHistory() {
        useOpenAIService(OpenAIChatCompletionModel.GPT_4_1.code, FeatureType.INLINE_EDIT)
        val testFileContent = getResourceContent("/inline/TestClass.java")
        val tempFile = File.createTempFile("TestClass", ".java")
        tempFile.writeText(testFileContent)
        tempFile.deleteOnExit()
        val parameters = InlineEditCompletionParameters(
            prompt = "TEST_FOLLOW_UP_PROMPT",
            selectedText = "myTestMethod()",
            filePath = tempFile.absolutePath,
            fileExtension = "java",
            projectBasePath = project.basePath,
            referencedFiles = mutableListOf(
                ReferencedFile(
                    "TEST_FILE_NAME_1.java",
                    "/path/to/TEST_FILE_NAME_1.java",
                    "TEST_FILE_CONTENT_1"
                ),
                ReferencedFile(
                    "TEST_FILE_NAME_2.java",
                    "/path/to/TEST_FILE_NAME_2.java",
                    "TEST_FILE_CONTENT_2"
                )
            ),
            gitDiff = "TEST_GIT_DIFF",
            conversation = Conversation().apply {
                messages = mutableListOf(
                    Message("PREV_PROMPT").apply {
                        response = "PREV_RESPONSE"
                    }
                )
            },
            conversationHistory = listOf(
                Conversation().apply {
                    messages = mutableListOf(
                        Message("HISTORY_PROMPT_1").apply {
                            response = "HISTORY_RESPONSE_1"
                        }
                    )
                },
                Conversation().apply {
                    messages = mutableListOf(
                        Message("HISTORY_PROMPT_2").apply {
                            response = "HISTORY_RESPONSE_2"
                        }
                    )
                }
            ),
            diagnosticsInfo = null
        )

        val request = OpenAIRequestFactory().createInlineEditRequest(parameters)

        val systemMessage = request.messages[0] as OpenAIChatCompletionStandardMessage
        assertThat(systemMessage.role).isEqualTo("system")
        assertThat(systemMessage.content).isEqualTo(
            """
You are a code modification assistant. Generate SEARCH/REPLACE blocks to modify the specified file.

Project Context:
Project root: ${project.basePath}
All file paths should be relative to this project root.

## Current File (Editable)
```java:${tempFile.absolutePath}
$testFileContent
```

## External Context

### Referenced Files

```java:/path/to/TEST_FILE_NAME_1.java
TEST_FILE_CONTENT_1
```

```java:/path/to/TEST_FILE_NAME_2.java
TEST_FILE_CONTENT_2
```

### Git Diff

```diff
TEST_GIT_DIFF
```

### Conversation History

**User:** HISTORY_PROMPT_1
**Assistant:** HISTORY_RESPONSE_1
**User:** HISTORY_PROMPT_2
**Assistant:** HISTORY_RESPONSE_2

## Task
Analyze the user's request and generate modifications for the current file using SEARCH/REPLACE blocks.

## Format
Use SEARCH/REPLACE blocks to specify exact code changes:

Single change in the same file:
```language:filepath
<<<<<<< SEARCH
[exact code to find]
=======
[replacement code]
>>>>>>> REPLACE
```

Multiple changes in the same file:
```language:filepath
<<<<<<< SEARCH
[first section]
=======
[first replacement]
>>>>>>> REPLACE

<<<<<<< SEARCH
[second section]
=======
[second replacement]
>>>>>>> REPLACE
```

## Requirements
• Match indentation and formatting exactly
• Include 2-4 lines of context for unique pattern matching
• Generate blocks only for the current file path
• Use complete code sections without truncation
• Ensure search and replacement content differ
• Include file path after language identifier using colon
• Make search patterns unique within the file

## Example
```java:src/Example.java
<<<<<<< SEARCH
public int calculate(int x, int y) {
    return x + y;
}
=======
public int calculate(int x, int y) {
    if (x < 0 || y < 0) {
        throw new IllegalArgumentException("Values must be non-negative");
    }
    return x + y;
}
>>>>>>> REPLACE
```
""".trimIndent()
        )
        val prevUserMessage = request.messages[1] as OpenAIChatCompletionStandardMessage
        assertThat(prevUserMessage.role).isEqualTo("user")
        assertThat(prevUserMessage.content).isEqualTo("PREV_PROMPT")
        val prevResponse = request.messages[2] as OpenAIChatCompletionStandardMessage
        assertThat(prevResponse.role).isEqualTo("assistant")
        assertThat(prevResponse.content).isEqualTo("PREV_RESPONSE")
        val followUpMessage = request.messages[3] as OpenAIChatCompletionStandardMessage
        assertThat(followUpMessage.role).isEqualTo("user")
        assertThat(followUpMessage.content).isEqualTo(
            """
            Selected code:
            ```java
            myTestMethod()
            ```

            Request: TEST_FOLLOW_UP_PROMPT
        """.trimIndent()
        )
    }
}