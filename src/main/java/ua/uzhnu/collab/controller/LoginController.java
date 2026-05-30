package ua.uzhnu.collab.controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import ua.uzhnu.collab.config.NavigationService;
import ua.uzhnu.collab.config.PreferencesService;
import ua.uzhnu.collab.dto.Dtos.UserDto;
import ua.uzhnu.collab.model.Credentials;
import ua.uzhnu.collab.service.UserService;
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
@Slf4j
public class LoginController {

  private final LoginViewModel viewModel;
  private final NavigationService navigation;
  private final PreferencesService preferencesService;
  private final UserService userService;

  @FXML private TextField txtUsername;
  @FXML private PasswordField txtPassword;
  @FXML private Label lblError;
  @FXML private Button btnLogin;
  @FXML private ProgressIndicator spinner;
  @FXML private CheckBox chkRememberMe;

  @FXML
  public void initialize() {
    // Двосторонній біндінг полів до ViewModel
    txtUsername.textProperty().bindBidirectional(viewModel.usernameProperty());
    txtPassword.textProperty().bindBidirectional(viewModel.passwordProperty());
    lblError.textProperty().bind(viewModel.errorMessageProperty());

    // Біндінг стану завантаження
    spinner.visibleProperty().bind(viewModel.loadingProperty());
    btnLogin.disableProperty().bind(viewModel.loadingProperty());

    // Біндінг checkbox
    chkRememberMe.selectedProperty().bindBidirectional(viewModel.rememberMeProperty());

    // Enter = логін
    txtPassword.setOnAction(e -> handleLogin());

    // Спроба автоматичного входу при ініціалізації
    attemptAutoLogin();
  }

  private void attemptAutoLogin() {
    var credentials = preferencesService.loadCredentials();
    if (credentials.isPresent()) {
      Credentials creds = credentials.get();
      viewModel.loadingProperty().set(true);

      Task<UserDto> autoLoginTask =
          new Task<>() {
            @Override
            protected UserDto call() {
              return userService.authenticate(creds.username(), creds.password());
            }
          };

      autoLoginTask.setOnSucceeded(
          e -> {
            UserDto user = autoLoginTask.getValue();
            if (user != null) {
              viewModel.onLoginSuccess(user);
              navigation.showDashboard();
            } else {
              viewModel.loadingProperty().set(false);
              preferencesService.clearCredentials();
              log.info("Auto-login failed, credentials cleared");
            }
          });

      autoLoginTask.setOnFailed(
          e -> {
            viewModel.loadingProperty().set(false);
            preferencesService.clearCredentials();
            log.warn("Auto-login failed: {}", autoLoginTask.getException().getMessage());
          });

      new Thread(autoLoginTask, "AutoLoginThread").setDaemon(true);
      new Thread(autoLoginTask).start();
    }
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
            if (viewModel.rememberMeProperty().get()) {
              preferencesService.saveCredentials(
                  viewModel.usernameProperty().get(), viewModel.passwordProperty().get());
            } else {
              preferencesService.clearCredentials();
            }
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
