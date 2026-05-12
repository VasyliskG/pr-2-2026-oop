package ua.uzhnu.collab.controller;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import ua.uzhnu.collab.config.NavigationService;
import ua.uzhnu.collab.config.SessionContext;
import ua.uzhnu.collab.service.UserService;
import ua.uzhnu.collab.viewmodel.LoginViewModel;

@ExtendWith(ApplicationExtension.class)
@DisplayName("LoginController — UI тести")
class LoginControllerTest {

  @Mock private UserService userService;
  @Mock private SessionContext sessionContext;
  @Mock private NavigationService navigation;

  private LoginViewModel viewModel;

  @Start
  void start(Stage stage) throws Exception {
    MockitoAnnotations.openMocks(this);
    viewModel = new LoginViewModel(userService, sessionContext);
    LoginController controller = new LoginController(viewModel, navigation);

    FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
    loader.setControllerFactory(type -> controller);
    Parent root = loader.load();

    stage.setScene(new Scene(root, 420, 520));
    stage.show();
  }

  private void fireLoginButton(FxRobot robot) {
    robot.interact(() -> robot.lookup("#btnLogin").<Button>query().fire());
  }

  @Test
  @DisplayName("клік 'Увійти' з порожніми полями → показує повідомлення про помилку")
  void login_emptyFields_showsError(FxRobot robot) {
    fireLoginButton(robot);

    String error = robot.lookup("#lblError").queryLabeled().getText();
    assertThat(error).isNotBlank();
  }

  @Test
  @DisplayName("заповнено тільки username → помилка про пароль")
  void login_missingPassword_showsPasswordError(FxRobot robot) {
    robot.interact(() -> viewModel.usernameProperty().set("alice"));
    fireLoginButton(robot);

    String error = robot.lookup("#lblError").queryLabeled().getText();
    assertThat(error).containsIgnoringCase("пароль");
  }

  @Test
  @DisplayName("заповнено тільки пароль → помилка про username")
  void login_missingUsername_showsUsernameError(FxRobot robot) {
    robot.interact(() -> viewModel.passwordProperty().set("secret"));
    fireLoginButton(robot);

    String error = robot.lookup("#lblError").queryLabeled().getText();
    assertThat(error).containsIgnoringCase("користувач");
  }
}
