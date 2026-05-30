package ua.uzhnu.collab.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import ua.uzhnu.collab.config.NavigationService;
import ua.uzhnu.collab.config.ThemeManager;
import ua.uzhnu.collab.model.AppTheme;

@Component
@Scope("prototype")
@RequiredArgsConstructor
@Slf4j
public class SettingsController {

  private final NavigationService navigation;
  private final ThemeManager themeManager;

  @FXML private CheckBox cbNotifications;
  @FXML private RadioButton rbThemeDark;
  @FXML private RadioButton rbThemeLight;
  @FXML private ToggleGroup themeGroup;
  @FXML private Button btnSave;
  @FXML private Button btnBack;

  @FXML
  public void initialize() {
    setupThemeToggle();
    cbNotifications.setSelected(true);
    preventWindowClose();
  }

  private void preventWindowClose() {
    javafx.scene.Scene scene = btnBack.getScene();
    if (scene != null && scene.getWindow() != null) {
      scene.getWindow()
          .setOnCloseRequest(
              e -> {
                e.consume();
                handleBack();
              });
    }
  }

  private void setupThemeToggle() {
    if (themeGroup == null) {
      themeGroup = new ToggleGroup();
      rbThemeDark.setToggleGroup(themeGroup);
      rbThemeLight.setToggleGroup(themeGroup);
    }

    AppTheme currentTheme = themeManager.getCurrentTheme();
    if (currentTheme == AppTheme.DARK) {
      rbThemeDark.setSelected(true);
    } else {
      rbThemeLight.setSelected(true);
    }

    themeGroup
        .selectedToggleProperty()
        .addListener(
            (obs, oldVal, newVal) -> {
              if (newVal == rbThemeDark) {
                themeManager.applyTheme(AppTheme.DARK);
              } else if (newVal == rbThemeLight) {
                themeManager.applyTheme(AppTheme.LIGHT);
              }
            });
  }

  @FXML
  private void handleSave() {
    navigation.showDashboard();
    log.info("Settings saved");
    btnSave.getScene().getWindow().hide();
  }

  @FXML
  private void handleBack() {
    navigation.showDashboard();
  }
}

