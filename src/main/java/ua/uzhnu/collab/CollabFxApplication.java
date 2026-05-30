package ua.uzhnu.collab;

import java.io.IOException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import ua.uzhnu.collab.config.ThemeManager;

/**
 * JavaFX Application з інтеграцією Spring Boot.
 *
 * <p>Життєвий цикл:
 *
 * <ol>
 *   <li>{@link #init()} — запуск Spring-контексту
 *   <li>{@link #start(Stage)} — завантаження першої FXML-сцени
 *   <li>{@link #stop()} — закриття Spring-контексту
 * </ol>
 *
 * Усі FXML-контролери отримуються зі Spring-контексту через {@link SpringFxmlLoader}, що забезпечує
 * ін'єкцію залежностей.
 */
public class CollabFxApplication extends Application {

  private ConfigurableApplicationContext springContext;

  @Override
  public void init() {
    springContext =
        new SpringApplicationBuilder(CollabApplication.class)
            .headless(false)
            .run(getParameters().getRaw().toArray(new String[0]));
  }

  @Override
  public void start(Stage primaryStage) throws IOException {
    SpringFxmlLoader loader = springContext.getBean(SpringFxmlLoader.class);
    Parent root = loader.load("/fxml/login.fxml");

    Scene scene = new Scene(root, 420, 520);

    primaryStage.setTitle("Student Collab Platform");
    primaryStage.setScene(scene);
    primaryStage.setMinWidth(400);
    primaryStage.setMinHeight(500);

    // Зберігаємо посилання на Stage для навігації та теми
    StageHolder stageHolder = springContext.getBean(StageHolder.class);
    stageHolder.setPrimaryStage(primaryStage);

    // Ініціалізація теми
    ThemeManager themeManager = springContext.getBean(ThemeManager.class);
    themeManager.loadAndApply();

    primaryStage.show();
  }

  @Override
  public void stop() {
    springContext.close();
    Platform.exit();
  }
}
