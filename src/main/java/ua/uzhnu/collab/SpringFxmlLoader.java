package ua.uzhnu.collab;

import java.io.IOException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Завантажувач FXML із підтримкою Spring DI.
 *
 * <p>Встановлює {@link ApplicationContext#getBean} як фабрику контролерів для {@link FXMLLoader},
 * що дозволяє FXML-контролерам отримувати залежності через конструкторну ін'єкцію Spring.
 */
@Component
public class SpringFxmlLoader {

  private final ApplicationContext context;

  public SpringFxmlLoader(ApplicationContext context) {
    this.context = context;
  }

  /**
   * Завантажує FXML-файл з classpath та створює контролер зі Spring-контексту.
   *
   * @param fxmlPath шлях до FXML-файлу (наприклад, "/fxml/login.fxml")
   * @return кореневий вузол завантаженої сцени
   */
  public Parent load(String fxmlPath) throws IOException {
    FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
    loader.setControllerFactory(context::getBean);
    return loader.load();
  }

  /** Завантажує FXML та повертає FXMLLoader для доступу до контролера. */
  public FXMLLoader getLoader(String fxmlPath) {
    FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
    loader.setControllerFactory(context::getBean);
    return loader;
  }
}
