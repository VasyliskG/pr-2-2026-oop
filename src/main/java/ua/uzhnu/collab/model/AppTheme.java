package ua.uzhnu.collab.model;

public enum AppTheme {
  DARK("Dark"),
  LIGHT("Light");

  private final String displayName;

  AppTheme(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }
}
