package ua.uzhnu.collab;

import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входу в застосунок.
 *
 * <p>Запуск відбувається через JavaFX {@link Application#launch}, який делегує ініціалізацію
 * Spring-контексту класу {@link CollabFxApplication}.
 */
@SpringBootApplication
public class CollabApplication {

  public static void main(String[] args) {
    Application.launch(CollabFxApplication.class, args);
  }
}
