package ua.uzhnu.collab.viewmodel;

import javafx.beans.property.*;
import javafx.concurrent.Task;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ua.uzhnu.collab.config.SessionContext;
import ua.uzhnu.collab.dto.Dtos.UserDto;
import ua.uzhnu.collab.service.UserService;

/**
 * ViewModel для екрану входу (патерн MVVM).
 *
 * <p>Містить JavaFX {@link Property}-властивості, до яких FXML-контролер виконує двосторонній
 * біндінг. Логіка аутентифікації виконується асинхронно через {@link javafx.concurrent.Task}, щоб
 * не блокувати потік JavaFX Application Thread.
 */
@Component
@RequiredArgsConstructor
public class LoginViewModel {

  private final UserService userService;
  private final SessionContext sessionContext;

  // ---- Властивості для біндінгу з UI ----

  private final StringProperty username = new SimpleStringProperty("");
  private final StringProperty password = new SimpleStringProperty("");
  private final StringProperty errorMessage = new SimpleStringProperty("");
  private final BooleanProperty loading = new SimpleBooleanProperty(false);
  private final BooleanProperty rememberMe = new SimpleBooleanProperty(false);

  // ---- Геттери властивостей (для біндінгу) ----

  public StringProperty usernameProperty() {
    return username;
  }

  public StringProperty passwordProperty() {
    return password;
  }

  public StringProperty errorMessageProperty() {
    return errorMessage;
  }

  public BooleanProperty loadingProperty() {
    return loading;
  }

  public BooleanProperty rememberMeProperty() {
    return rememberMe;
  }

  // ---- Бізнес-логіка ----

  /** Створює асинхронну задачу аутентифікації. Результат: UserDto при успіху, null при невдачі. */
  public Task<UserDto> createLoginTask() {
    String user = username.get().trim();
    String pass = password.get();

    return new Task<>() {
      @Override
      protected UserDto call() {
        return userService.authenticate(user, pass);
      }
    };
  }

  /** Зберігає автентифікованого користувача у сесії. */
  public void onLoginSuccess(UserDto user) {
    sessionContext.setCurrentUser(user);
    errorMessage.set("");
  }

  /**
   * Валідація полів перед відправкою.
   *
   * @return true якщо поля заповнені
   */
  public boolean validate() {
    if (username.get() == null || username.get().trim().isEmpty()) {
      errorMessage.set("Введіть ім'я користувача");
      return false;
    }
    if (password.get() == null || password.get().isEmpty()) {
      errorMessage.set("Введіть пароль");
      return false;
    }
    errorMessage.set("");
    return true;
  }

  /** Скидає стан форми. */
  public void reset() {
    username.set("");
    password.set("");
    errorMessage.set("");
    loading.set(false);
    rememberMe.set(false);
  }
}
