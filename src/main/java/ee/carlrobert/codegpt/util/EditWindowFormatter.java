package ee.carlrobert.codegpt.util;

/**
 * Formats a file into a compact, tagged view used for in‑context editing.
 *
 * <p>Output structure:
 * <pre>
 * {@code
 * <|current_file_content|>
 * current_file_path: <path>
 * ...full window lines with an embedded editable window…
 * <|/current_file_content|>
 * }
 * </pre>
 *
 * <p>The editable window is wrapped with {@code <|code_to_edit|>} and {@code <|/code_to_edit|>}
 * tags and contains a {@code <|cursor|>} marker inserted at the exact character offset of the
 * cursor. Window sizes for both the editable and full regions are provided via
 * {@link EditWindowConfig} or default to 15/25 (editable) and 150/250 (full). Line endings are
 * normalized to LF.
 */
public final class EditWindowFormatter {

  /**
   * Result containing formatted content and edit region indices in the original file.
   */
  public record FormatResult(String formattedContent, int editStartIndex, int editEndIndex) {

  }

  private EditWindowFormatter() {
  }

  /**
   * Formats using default window sizes (editable: 15↑/25↓, full: 150↑/250↓) and returns both the
   * formatted content and the edit region indices in the original content.
   *
   * @param fullContent  entire file content
   * @param filePath     path to display after the header
   * @param cursorOffset 0‑based character offset of the cursor
   * @return FormatResult containing formatted content and edit region indices
   */
  public static FormatResult formatWithIndices(String fullContent, String filePath,
      int cursorOffset) {
    return formatWithIndices(fullContent, filePath, cursorOffset, EditWindowConfig.defaults());
  }

  /**
   * Formats using the provided window configuration and returns both the formatted content and the
   * edit region indices in the original content.
   *
   * @param fullContent  entire file content
   * @param filePath     path to display after the header
   * @param cursorOffset 0‑based character offset of the cursor
   * @param config       window sizes for editable and full regions
   * @return FormatResult containing formatted content and edit region indices
   */
  public static FormatResult formatWithIndices(String fullContent, String filePath,
      int cursorOffset, EditWindowConfig config) {
    if (fullContent == null) {
      fullContent = "";
    }
    if (filePath == null) {
      filePath = "";
    }
    if (cursorOffset < 0) {
      cursorOffset = 0;
    }

    String normalized = fullContent.replace("\r\n", "\n").replace('\r', '\n');
    String[] lines = normalized.split("\n", -1);
    int totalLines = lines.length;
    if (cursorOffset > normalized.length()) {
      cursorOffset = normalized.length();
    }

    int cursorLine = 0;
    int lineStartOffset = 0;
    for (int i = 0; i < cursorOffset; i++) {
      if (normalized.charAt(i) == '\n') {
        cursorLine++;
        lineStartOffset = i + 1;
      }
    }

    int editStart = Math.max(0, cursorLine - config.editAbove);
    int editEnd = Math.min(totalLines - 1, cursorLine + config.editBelow);
    int fullStart = Math.max(0, cursorLine - config.fullAbove);
    int fullEnd = Math.min(totalLines - 1, cursorLine + config.fullBelow);

    int editStartIndex = 0;
    int editEndIndex = normalized.length();

    if (editStart > 0) {
      int currentLine = 0;
      for (int i = 0; i < normalized.length(); i++) {
        if (normalized.charAt(i) == '\n') {
          currentLine++;
          if (currentLine == editStart) {
            editStartIndex = i + 1;
            break;
          }
        }
      }
    }

    if (editEnd < totalLines - 1) {
      int currentLine = 0;
      for (int i = 0; i < normalized.length(); i++) {
        if (normalized.charAt(i) == '\n') {
          if (currentLine == editEnd) {
            editEndIndex = i;
            break;
          }
          currentLine++;
        }
      }
    }

    StringBuilder sb = new StringBuilder();
    sb.append("<|current_file_content|>\n");
    sb.append("current_file_path: ").append(filePath).append('\n');

    for (int lineIdx = fullStart; lineIdx <= fullEnd; lineIdx++) {
      if (lineIdx == editStart) {
        sb.append("<|code_to_edit|>\n");
      }

      if (lineIdx == cursorLine) {
        int col = Math.max(0, Math.min(lines[lineIdx].length(), cursorOffset - lineStartOffset));
        sb.append(lines[lineIdx], 0, col).append("<|cursor|>")
            .append(lines[lineIdx].substring(col));
      } else {
        sb.append(lines[lineIdx]);
      }

      if (lineIdx == editEnd) {
        if (lineIdx < fullEnd) {
          sb.append('\n').append("<|/code_to_edit|>").append('\n');
        } else {
          sb.append('\n').append("<|/code_to_edit|>");
        }
      } else if (lineIdx < fullEnd) {
        sb.append('\n');
      }
    }

    sb.append('\n').append("<|/current_file_content|>");
    String formattedContent = sb.toString();

    return new FormatResult(formattedContent, editStartIndex, editEndIndex);
  }
}
