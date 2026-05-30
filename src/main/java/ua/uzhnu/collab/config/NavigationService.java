package ua.uzhnu.collab.config;

import java.io.IOException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ua.uzhnu.collab.SpringFxmlLoader;
import ua.uzhnu.collab.StageHolder;
import ua.uzhnu.collab.controller.ChatController;
import ua.uzhnu.collab.model.AppTheme;

/**
 * Сервіс навігації між екранами застосунку.
 *
 * <p>Завантажує FXML-файли через {@link SpringFxmlLoader} та замінює вміст головного вікна.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NavigationService {

  private static final String ACTIVE_CONTROLLER_KEY = NavigationService.class.getName() + ".activeController";

  private final SpringFxmlLoader fxmlLoader;
  private final StageHolder stageHolder;
  private final ThemeManager themeManager;

  /**
   * Переходить на вказаний екран.
   *
   * @param fxmlPath шлях до FXML (наприклад, "/fxml/dashboard.fxml")
   * @param title заголовок вікна
   * @param width ширина вікна
   * @param height висота вікна
   */
  public void navigateTo(String fxmlPath, String title, double width, double height) {
    try {
      FXMLLoader loader = fxmlLoader.getLoader(fxmlPath);
      Parent root = loader.load();
      Object controller = loader.getController();
      Stage stage = stageHolder.getPrimaryStage();

      cleanupActiveController(stage);

      Scene scene = new Scene(root, width, height);
      applyCurrentTheme(scene);

      stage.setTitle(title);
      stage.setScene(scene);
      stage.getProperties().put(ACTIVE_CONTROLLER_KEY, controller);
      stage.setOnCloseRequest(e -> cleanupActiveController(stage));
      stage.setMinWidth(Math.min(width, 400));
      stage.setMinHeight(Math.min(height, 400));
      stage.centerOnScreen();

      log.info("Навігація: {}", fxmlPath);
    } catch (IOException e) {
      log.error("Помилка завантаження екрану: {}", fxmlPath, e);
      throw new RuntimeException("Неможливо завантажити екран: " + fxmlPath, e);
    }
  }

  private void applyCurrentTheme(Scene scene) {
    AppTheme currentTheme = themeManager.getCurrentTheme();
    String cssFile = currentTheme == AppTheme.DARK ? "/css/styles-dark.css" : "/css/styles-light.css";
    scene.getStylesheets().add(getClass().getResource(cssFile).toExternalForm());
  }

  private void cleanupActiveController(Stage stage) {
    Object controller = stage.getProperties().remove(ACTIVE_CONTROLLER_KEY);
    if (controller instanceof ChatController chatController) {
      chatController.stopPolling();
    }
  }

  /** Екран входу. */
  public void showLogin() {
    navigateTo("/fxml/login.fxml", "Вхід — Student Collab", 420, 520);
  }

  /** Екран реєстрації. */
  public void showRegister() {
    navigateTo("/fxml/register.fxml", "Реєстрація — Student Collab", 420, 600);
  }

  /** Головний дашборд. */
  public void showDashboard() {
    navigateTo("/fxml/dashboard.fxml", "Student Collab Platform", 1100, 750);
  }

  /** Standalone chat screen backed by the embedded local HTTP server. */
  public void showChat() {
    navigateTo("/fxml/pages/chat.fxml", "Локальний чат — Student Collab", 1100, 760);
  }

  /** Екран команди (задачі, чат, файли). */
  public void showTeamView() {
    navigateTo("/fxml/team_view_v2.fxml", "Команда — Student Collab", 1200, 800);
  }

  /** Екран налаштувань користувача. */
  public void showSettings() {
    navigateTo("/fxml/settings.fxml", "Налаштування — Student Collab", 600, 520);
  }

  /** Екран профілю/акаунта користувача. */
  public void showAccount() {
    navigateTo("/fxml/account.fxml", "Профіль — Student Collab", 600, 520);
  }
}
