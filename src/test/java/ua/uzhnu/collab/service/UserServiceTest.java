package ua.uzhnu.collab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import ua.uzhnu.collab.dto.Dtos.UserCreateDto;
import ua.uzhnu.collab.dto.Dtos.UserDto;
import ua.uzhnu.collab.entity.User;
import ua.uzhnu.collab.exception.DuplicateEntityException;
import ua.uzhnu.collab.exception.EntityNotFoundException;
import ua.uzhnu.collab.repository.UserRepository;

/**
 * Юніт-тести {@link UserService} з повним мокуванням залежностей.
 *
 * <p>Перевіряють чисту бізнес-логіку без участі бази даних: валідацію унікальності, хешування
 * пароля, обробку винятків, нормалізацію вхідних даних (trim, lowercase).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService — юніт-тест")
class UserServiceTest {

  @Mock private UserRepository userRepository;

  @Spy
  private DtoMapper mapper = new DtoMapper(); // справжній маппер — він простий і легко тестується

  @InjectMocks private UserService userService;

  private UserCreateDto validRegistration;

  @BeforeEach
  void setUp() {
    validRegistration = new UserCreateDto("newuser", "new@uzhnu.ua", "password123", "New User");
  }

  // =====================================================================
  // РЕЄСТРАЦІЯ
  // =====================================================================

  @Nested
  @DisplayName("register()")
  class RegisterTests {

    @Test
    @DisplayName("успішна реєстрація: повертає UserDto з присвоєним ID")
    void register_validInput_returnsUserDto() {
      when(userRepository.existsByUsername("newuser")).thenReturn(false);
      when(userRepository.existsByEmail("new@uzhnu.ua")).thenReturn(false);
      when(userRepository.save(any(User.class)))
          .thenAnswer(
              inv -> {
                User u = inv.getArgument(0);
                u.setId(42L); // імітуємо присвоєння ID базою
                return u;
              });

      UserDto result = userService.register(validRegistration);

      assertThat(result.id()).isEqualTo(42L);
      assertThat(result.username()).isEqualTo("newuser");
    }

    @Test
    @DisplayName("реєстрація: пароль зберігається у вигляді BCrypt-хешу, не в plaintext")
    void register_passwordIsHashed() {
      when(userRepository.existsByUsername(any())).thenReturn(false);
      when(userRepository.existsByEmail(any())).thenReturn(false);

      ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
      when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

      userService.register(validRegistration);

      User saved = captor.getValue();
      assertThat(saved.getPasswordHash())
          .isNotEqualTo("password123")
          .startsWith("$2a$"); // BCrypt-формат

      // Хеш дійсно перевіряється BCrypt-енкодером
      assertThat(new BCryptPasswordEncoder().matches("password123", saved.getPasswordHash()))
          .isTrue();
    }

    @Test
    @DisplayName("реєстрація: email нормалізується до нижнього регістру з trim")
    void register_normalizesEmailAndUsername() {
      UserCreateDto dto =
          new UserCreateDto("  user1  ", "  UPPER@UZHNU.UA  ", "password", "  Full Name  ");
      when(userRepository.existsByUsername(any())).thenReturn(false);
      when(userRepository.existsByEmail(any())).thenReturn(false);

      ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
      when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

      userService.register(dto);

      User saved = captor.getValue();
      assertThat(saved.getUsername()).isEqualTo("user1");
      assertThat(saved.getEmail()).isEqualTo("upper@uzhnu.ua");
      assertThat(saved.getFullName()).isEqualTo("Full Name");
    }

    @Test
    @DisplayName("дубль username: викидає DuplicateEntityException")
    void register_duplicateUsername_throws() {
      when(userRepository.existsByUsername("newuser")).thenReturn(true);

      assertThatThrownBy(() -> userService.register(validRegistration))
          .isInstanceOf(DuplicateEntityException.class)
          .hasMessageContaining("newuser");

      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("дубль email: викидає DuplicateEntityException")
    void register_duplicateEmail_throws() {
      when(userRepository.existsByUsername(any())).thenReturn(false);
      when(userRepository.existsByEmail("new@uzhnu.ua")).thenReturn(true);

      assertThatThrownBy(() -> userService.register(validRegistration))
          .isInstanceOf(DuplicateEntityException.class)
          .hasMessageContaining("new@uzhnu.ua");

      verify(userRepository, never()).save(any());
    }
  }

  // =====================================================================
  // АУТЕНТИФІКАЦІЯ
  // =====================================================================

  @Nested
  @DisplayName("authenticate()")
  class AuthenticateTests {

    private User existing;

    @BeforeEach
    void prepareExisting() {
      existing =
          User.builder()
              .id(1L)
              .username("alice")
              .email("alice@uzhnu.ua")
              .passwordHash(new BCryptPasswordEncoder(10).encode("realpassword"))
              .fullName("Alice")
              .build();
    }

    @Test
    @DisplayName("правильний пароль: повертає UserDto")
    void authenticate_correctPassword_returnsDto() {
      when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existing));

      UserDto dto = userService.authenticate("alice", "realpassword");

      assertThat(dto).isNotNull();
      assertThat(dto.username()).isEqualTo("alice");
    }

    @Test
    @DisplayName("неправильний пароль: повертає null")
    void authenticate_wrongPassword_returnsNull() {
      when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existing));

      UserDto dto = userService.authenticate("alice", "wrongpassword");

      assertThat(dto).isNull();
    }

    @Test
    @DisplayName("неіснуючий username: викидає EntityNotFoundException")
    void authenticate_unknownUser_throws() {
      when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

      assertThatThrownBy(() -> userService.authenticate("ghost", "anything"))
          .isInstanceOf(EntityNotFoundException.class)
          .hasMessageContaining("ghost");
    }
  }

  // =====================================================================
  // changePassword
  // =====================================================================

  @Nested
  @DisplayName("changePassword()")
  class ChangePasswordTests {

    private User existing;

    @BeforeEach
    void prepareExisting() {
      existing =
          User.builder()
              .id(1L)
              .username("alice")
              .passwordHash(new BCryptPasswordEncoder(10).encode("oldpass"))
              .fullName("Alice")
              .email("alice@x.test")
              .build();
    }

    @Test
    @DisplayName("правильний поточний пароль: оновлює хеш і повертає true")
    void changePassword_correctCurrent_updates() {
      when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
      when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

      boolean ok = userService.changePassword(1L, "oldpass", "newpass");

      assertThat(ok).isTrue();
      assertThat(new BCryptPasswordEncoder().matches("newpass", existing.getPasswordHash()))
          .isTrue();
    }

    @Test
    @DisplayName("невірний поточний пароль: повертає false, не оновлює")
    void changePassword_wrongCurrent_returnsFalse() {
      when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

      boolean ok = userService.changePassword(1L, "wrong", "newpass");

      assertThat(ok).isFalse();
      verify(userRepository, never()).save(any());
    }
  }
}
