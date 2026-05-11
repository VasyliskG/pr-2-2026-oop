package ua.uzhnu.collab;

import java.io.IOException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

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
    scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

    primaryStage.setTitle("Student Collab Platform");
    primaryStage.setScene(scene);
    primaryStage.setMinWidth(400);
    primaryStage.setMinHeight(500);
    primaryStage.show();

    // Зберігаємо посилання на Stage для навігації між екранами
    springContext.getBean(StageHolder.class).setPrimaryStage(primaryStage);
  }

  @Override
  public void stop() {
    springContext.close();
    Platform.exit();
  }
}
