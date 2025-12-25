package ee.carlrobert.codegpt.util;

public final class EditWindowConfig {
  public final int editAbove;
  public final int editBelow;
  public final int fullAbove;
  public final int fullBelow;

  public EditWindowConfig(int editAbove, int editBelow, int fullAbove, int fullBelow) {
    if (editAbove < 0 || editBelow < 0 || fullAbove < 0 || fullBelow < 0) {
      throw new IllegalArgumentException("window sizes must be non-negative");
    }
    this.editAbove = editAbove;
    this.editBelow = editBelow;
    this.fullAbove = fullAbove;
    this.fullBelow = fullBelow;
  }

  public static EditWindowConfig defaults() {
    return new EditWindowConfig(15, 25, 150, 250);
    
  }
}

