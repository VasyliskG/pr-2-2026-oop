package ua.uzhnu.collab.controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import ua.uzhnu.collab.config.NavigationService;
import ua.uzhnu.collab.config.SessionContext;
import ua.uzhnu.collab.dto.Dtos.*;
import ua.uzhnu.collab.service.UserService;

/** FXML-контролер екрану реєстрації. */
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class RegisterController {

  private final UserService userService;
  private final SessionContext sessionContext;
  private final NavigationService navigation;

  @FXML private TextField txtUsername;
  @FXML private TextField txtFullName;
  @FXML private TextField txtEmail;
  @FXML private PasswordField txtPassword;
  @FXML private Label lblError;
  @FXML private Button btnRegister;
  @FXML private ProgressIndicator spinner;

  @FXML
  public void initialize() {
    txtPassword.setOnAction(e -> handleRegister());
  }

  @FXML
  private void handleRegister() {
    String username = txtUsername.getText().trim();
    String fullName = txtFullName.getText().trim();
    String email = txtEmail.getText().trim();
    String password = txtPassword.getText();

    // Валідація
    if (username.length() < 3) {
      lblError.setText("Ім'я користувача: мінімум 3 символи");
      return;
    }
    if (fullName.isEmpty()) {
      lblError.setText("Введіть повне ім'я");
      return;
    }
    if (!email.contains("@")) {
      lblError.setText("Некоректний формат електронної пошти");
      return;
    }
    if (password.length() < 6) {
      lblError.setText("Пароль: мінімум 6 символів");
      return;
    }

    lblError.setText("");
    spinner.setVisible(true);
    btnRegister.setDisable(true);

    Task<UserDto> task =
        new Task<>() {
          @Override
          protected UserDto call() {
            return userService.register(new UserCreateDto(username, email, password, fullName));
          }
        };

    task.setOnSucceeded(
        e -> {
          spinner.setVisible(false);
          sessionContext.setCurrentUser(task.getValue());
          navigation.showDashboard();
        });
    task.setOnFailed(
        e -> {
          spinner.setVisible(false);
          btnRegister.setDisable(false);
          lblError.setText(task.getException().getMessage());
        });

    new Thread(task).start();
  }

  @FXML
  private void handleGoToLogin() {
    navigation.showLogin();
  }
}
