package ua.uzhnu.collab.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import ua.uzhnu.collab.config.NavigationService;
import ua.uzhnu.collab.dto.Dtos.UserDto;
import ua.uzhnu.collab.viewmodel.LoginViewModel;

/**
 * FXML-контролер екрана входу.
 *
 * <p>Відповідає за біндінг елементів інтерфейсу до {@link LoginViewModel} та обробку подій
 * (натискання кнопок, Enter). Контролер є «тонким»: вся логіка делегується ViewModel.
 */
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class LoginController {

  private final LoginViewModel viewModel;
  private final NavigationService navigation;

  @FXML private TextField txtUsername;
  @FXML private PasswordField txtPassword;
  @FXML private Label lblError;
  @FXML private Button btnLogin;
  @FXML private ProgressIndicator spinner;

  @FXML
  public void initialize() {
    // Двосторонній біндінг полів до ViewModel
    txtUsername.textProperty().bindBidirectional(viewModel.usernameProperty());
    txtPassword.textProperty().bindBidirectional(viewModel.passwordProperty());
    lblError.textProperty().bind(viewModel.errorMessageProperty());

    // Біндінг стану завантаження
    spinner.visibleProperty().bind(viewModel.loadingProperty());
    btnLogin.disableProperty().bind(viewModel.loadingProperty());

    // Enter = логін
    txtPassword.setOnAction(e -> handleLogin());
  }

  @FXML
  private void handleLogin() {
    if (!viewModel.validate()) return;

    viewModel.loadingProperty().set(true);

    var task = viewModel.createLoginTask();
    task.setOnSucceeded(
        e -> {
          viewModel.loadingProperty().set(false);
          UserDto user = task.getValue();
          if (user != null) {
            viewModel.onLoginSuccess(user);
            navigation.showDashboard();
          } else {
            viewModel.errorMessageProperty().set("Невірний пароль");
          }
        });
    task.setOnFailed(
        e -> {
          viewModel.loadingProperty().set(false);
          Throwable ex = task.getException();
          viewModel.errorMessageProperty().set(ex.getMessage());
        });

    new Thread(task).start();
  }

  @FXML
  private void handleGoToRegister() {
    viewModel.reset();
    navigation.showRegister();
  }
}
