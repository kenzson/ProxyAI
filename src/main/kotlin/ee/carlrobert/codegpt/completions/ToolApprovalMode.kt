package ee.carlrobert.codegpt.completions

/**
 * Defines how tool calls should be approved during chat completion.
 */
enum class ToolApprovalMode {
    /**
     * All tool calls are automatically approved without user interaction.
     */
    AUTO_APPROVE,
    
    /**
     * Each tool call requires explicit user approval before execution.
     */
    REQUIRE_APPROVAL,
    
    /**
     * Tool calls are blocked and not executed.
     */
    BLOCK_ALL
}