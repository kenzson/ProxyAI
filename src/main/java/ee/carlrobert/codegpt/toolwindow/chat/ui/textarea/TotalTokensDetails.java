package ee.carlrobert.codegpt.toolwindow.chat.ui.textarea;

public class TotalTokensDetails {

  private final int systemPromptTokens;
  private int conversationTokens;
  private int userPromptTokens;
  private int highlightedTokens;
  private int referencedFilesTokens;
  private int psiTokens;
  private int mcpToolInputTokens;
  private int mcpToolOutputTokens;

  public TotalTokensDetails(int systemPromptTokens) {
    this.systemPromptTokens = systemPromptTokens;
  }

  public int getSystemPromptTokens() {
    return systemPromptTokens;
  }

  public void setConversationTokens(int conversationTokens) {
    this.conversationTokens = conversationTokens;
  }

  public int getConversationTokens() {
    return conversationTokens;
  }

  public void setUserPromptTokens(int userPromptTokens) {
    this.userPromptTokens = userPromptTokens;
  }

  public int getUserPromptTokens() {
    return userPromptTokens;
  }

  public void setHighlightedTokens(int highlightedTokens) {
    this.highlightedTokens = highlightedTokens;
  }

  public int getHighlightedTokens() {
    return highlightedTokens;
  }

  public void setReferencedFilesTokens(int referencedFilesTokens) {
    this.referencedFilesTokens = referencedFilesTokens;
  }

  public int getReferencedFilesTokens() {
    return referencedFilesTokens;
  }

  public void setPsiTokens(int psiTokens) {
    this.psiTokens = psiTokens;
  }

  public int getPsiTokens() {
    return psiTokens;
  }

  public void setMcpToolInputTokens(int mcpToolInputTokens) {
    this.mcpToolInputTokens = mcpToolInputTokens;
  }

  public int getMcpToolInputTokens() {
    return mcpToolInputTokens;
  }

  public void setMcpToolOutputTokens(int mcpToolOutputTokens) {
    this.mcpToolOutputTokens = mcpToolOutputTokens;
  }

  public int getMcpToolOutputTokens() {
    return mcpToolOutputTokens;
  }

  public int getTotal() {
    return systemPromptTokens
        + conversationTokens
        + userPromptTokens
        + highlightedTokens
        + referencedFilesTokens
        + psiTokens
        + mcpToolInputTokens
        + mcpToolOutputTokens;
  }
}
