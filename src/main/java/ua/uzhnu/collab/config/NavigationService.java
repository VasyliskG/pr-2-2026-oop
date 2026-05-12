package ua.uzhnu.collab.config;

import java.io.IOException;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ua.uzhnu.collab.SpringFxmlLoader;
import ua.uzhnu.collab.StageHolder;

/**
 * Сервіс навігації між екранами застосунку.
 *
 * <p>Завантажує FXML-файли через {@link SpringFxmlLoader} та замінює вміст головного вікна.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NavigationService {

  private final SpringFxmlLoader fxmlLoader;
  private final StageHolder stageHolder;

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
      Parent root = fxmlLoader.load(fxmlPath);
      Stage stage = stageHolder.getPrimaryStage();

      Scene scene = new Scene(root, width, height);
      scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

      stage.setTitle(title);
      stage.setScene(scene);
      stage.setMinWidth(Math.min(width, 400));
      stage.setMinHeight(Math.min(height, 400));
      stage.centerOnScreen();

      log.info("Навігація: {}", fxmlPath);
    } catch (IOException e) {
      log.error("Помилка завантаження екрану: {}", fxmlPath, e);
      throw new RuntimeException("Неможливо завантажити екран: " + fxmlPath, e);
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

  /** Екран команди (задачі, чат, файли). */
  public void showTeamView() {
    navigateTo("/fxml/team_view_v2.fxml", "Команда — Student Collab", 1200, 800);
  }
}
