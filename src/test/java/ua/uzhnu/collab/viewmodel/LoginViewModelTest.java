package ua.uzhnu.collab.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.uzhnu.collab.config.SessionContext;
import ua.uzhnu.collab.service.UserService;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginViewModel — валідація полів")
class LoginViewModelTest {

  @Mock private UserService userService;
  @Mock private SessionContext sessionContext;

  private LoginViewModel viewModel;

  @BeforeEach
  void setUp() {
    viewModel = new LoginViewModel(userService, sessionContext);
  }

  @Nested
  @DisplayName("validate()")
  class ValidateTests {

    @Test
    @DisplayName("порожній username → false + повідомлення про помилку")
    void emptyUsername_returnsFalse() {
      viewModel.usernameProperty().set("");
      viewModel.passwordProperty().set("password123");

      assertThat(viewModel.validate()).isFalse();
      assertThat(viewModel.errorMessageProperty().get()).isNotBlank();
    }

    @Test
    @DisplayName("null username → false + повідомлення про помилку")
    void nullUsername_returnsFalse() {
      viewModel.usernameProperty().set(null);
      viewModel.passwordProperty().set("password123");

      assertThat(viewModel.validate()).isFalse();
      assertThat(viewModel.errorMessageProperty().get()).isNotBlank();
    }

    @Test
    @DisplayName("порожній пароль → false + повідомлення про помилку")
    void emptyPassword_returnsFalse() {
      viewModel.usernameProperty().set("alice");
      viewModel.passwordProperty().set("");

      assertThat(viewModel.validate()).isFalse();
      assertThat(viewModel.errorMessageProperty().get()).isNotBlank();
    }

    @Test
    @DisplayName("обидва поля заповнені → true + порожня помилка")
    void bothFilled_returnsTrue() {
      viewModel.usernameProperty().set("alice");
      viewModel.passwordProperty().set("password123");

      assertThat(viewModel.validate()).isTrue();
      assertThat(viewModel.errorMessageProperty().get()).isBlank();
    }

    @Test
    @DisplayName("тільки пробіли у username → false")
    void whitespaceUsername_returnsFalse() {
      viewModel.usernameProperty().set("   ");
      viewModel.passwordProperty().set("password123");

      assertThat(viewModel.validate()).isFalse();
    }
  }

  @Nested
  @DisplayName("reset()")
  class ResetTests {

    @Test
    @DisplayName("скидає всі поля до початкового стану")
    void reset_clearsAllFields() {
      viewModel.usernameProperty().set("alice");
      viewModel.passwordProperty().set("secret");
      viewModel.errorMessageProperty().set("Помилка");
      viewModel.loadingProperty().set(true);

      viewModel.reset();

      assertThat(viewModel.usernameProperty().get()).isBlank();
      assertThat(viewModel.passwordProperty().get()).isBlank();
      assertThat(viewModel.errorMessageProperty().get()).isBlank();
      assertThat(viewModel.loadingProperty().get()).isFalse();
    }
  }
}
