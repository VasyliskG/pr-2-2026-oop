package ua.uzhnu.collab.config;

import javafx.application.Platform;
import javafx.scene.Scene;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ua.uzhnu.collab.StageHolder;
import ua.uzhnu.collab.model.AppTheme;

@Component
@RequiredArgsConstructor
@Slf4j
public class ThemeManager {

  private final PreferencesService preferencesService;
  private final StageHolder stageHolder;

  private AppTheme currentTheme = AppTheme.DARK;

  public void loadAndApply() {
    try {
      currentTheme = preferencesService.loadTheme();
      applyTheme(currentTheme);
      log.info("Theme loaded and applied: {}", currentTheme);
    } catch (Exception e) {
      log.warn("Failed to load theme, using DARK: {}", e.getMessage());
      currentTheme = AppTheme.DARK;
      applyTheme(currentTheme);
    }
  }

  public void applyTheme(AppTheme theme) {
    Platform.runLater(
        () -> {
          try {
            String cssFile = getCssFile(theme);
            Scene scene = stageHolder.getPrimaryStage().getScene();

            if (scene != null) {
              // Remove old theme stylesheets
              scene.getStylesheets().removeIf(
                  url -> url.contains("styles-dark.css") || url.contains("styles-light.css"));
              // Add new theme stylesheet
              scene.getStylesheets().add(getClass().getResource(cssFile).toExternalForm());
            }

            currentTheme = theme;
            preferencesService.saveTheme(theme);
            log.info("Theme applied: {}", theme);
          } catch (Exception e) {
            log.error("Failed to apply theme: {}", e.getMessage());
          }
        });
  }

  public AppTheme getCurrentTheme() {
    return currentTheme;
  }

  public String getCurrentThemeCssFile() {
    return getCssFile(currentTheme);
  }

  private String getCssFile(AppTheme theme) {
    return theme == AppTheme.DARK ? "/css/styles-dark.css" : "/css/styles-light.css";
  }
}
