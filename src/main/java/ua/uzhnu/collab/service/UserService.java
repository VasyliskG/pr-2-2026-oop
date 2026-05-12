package ua.uzhnu.collab.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.uzhnu.collab.dto.Dtos.*;
import ua.uzhnu.collab.entity.User;
import ua.uzhnu.collab.exception.DuplicateEntityException;
import ua.uzhnu.collab.exception.EntityNotFoundException;
import ua.uzhnu.collab.repository.UserRepository;

/**
 * Сервіс бізнес-логіки для сутності {@link User}.
 *
 * <p>Забезпечує реєстрацію, аутентифікацію, пошук та оновлення профілю. Паролі зберігаються у
 * вигляді BCrypt-хешу.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class UserService {

  private final UserRepository userRepository;
  private final DtoMapper mapper;

  private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(10);

  // =====================================================================
  // РЕЄСТРАЦІЯ
  // =====================================================================

  /**
   * Реєструє нового користувача.
   *
   * @throws DuplicateEntityException якщо username або email вже зайнято
   */
  @Transactional
  public UserDto register(UserCreateDto dto) {
    if (userRepository.existsByUsername(dto.username())) {
      throw new DuplicateEntityException("Ім'я користувача '" + dto.username() + "' вже зайнято");
    }
    if (userRepository.existsByEmail(dto.email())) {
      throw new DuplicateEntityException(
          "Електронна пошта '" + dto.email() + "' вже зареєстрована");
    }

    User user =
        User.builder()
            .username(dto.username().trim())
            .email(dto.email().trim().toLowerCase())
            .passwordHash(PASSWORD_ENCODER.encode(dto.password()))
            .fullName(dto.fullName().trim())
            .build();

    user = userRepository.save(user);
    log.info("Зареєстровано користувача: {} (id={})", user.getUsername(), user.getId());
    return mapper.toUserDto(user);
  }

  // =====================================================================
  // АУТЕНТИФІКАЦІЯ
  // =====================================================================

  /**
   * Перевіряє облікові дані та повертає DTO користувача.
   *
   * @return DTO або null якщо пароль невірний
   * @throws EntityNotFoundException якщо username не існує
   */
  public UserDto authenticate(String username, String rawPassword) {
    User user =
        userRepository
            .findByUsername(username)
            .orElseThrow(() -> new EntityNotFoundException("Користувач", "username", username));

    if (!PASSWORD_ENCODER.matches(rawPassword, user.getPasswordHash())) {
      log.warn("Невдала спроба входу для: {}", username);
      return null;
    }

    log.info("Успішна аутентифікація: {}", username);
    return mapper.toUserDto(user);
  }

  // =====================================================================
  // ОТРИМАННЯ
  // =====================================================================

  public UserDto getById(Long id) {
    return mapper.toUserDto(findById(id));
  }

  public UserDto getByUsername(String username) {
    return mapper.toUserDto(
        userRepository
            .findByUsername(username)
            .orElseThrow(() -> new EntityNotFoundException("Користувач", "username", username)));
  }

  public List<UserDto> getAll() {
    return userRepository.findAll().stream().map(mapper::toUserDto).toList();
  }

  /** Пошук за фрагментом імені або логіну (для автодоповнення у UI). */
  public List<UserDto> search(String query) {
    return userRepository.searchByNameOrUsername(query).stream().map(mapper::toUserDto).toList();
  }

  // =====================================================================
  // ОНОВЛЕННЯ ПРОФІЛЮ
  // =====================================================================

  @Transactional
  public UserDto updateProfile(Long userId, String fullName, String email) {
    User user = findById(userId);

    if (email != null && !email.equalsIgnoreCase(user.getEmail())) {
      if (userRepository.existsByEmail(email)) {
        throw new DuplicateEntityException("Електронна пошта '" + email + "' вже зареєстрована");
      }
      user.setEmail(email.trim().toLowerCase());
    }

    if (fullName != null && !fullName.isBlank()) {
      user.setFullName(fullName.trim());
    }

    user = userRepository.save(user);
    log.info("Оновлено профіль: {} (id={})", user.getUsername(), user.getId());
    return mapper.toUserDto(user);
  }

  /** Зміна пароля з перевіркою поточного. */
  @Transactional
  public boolean changePassword(Long userId, String currentPassword, String newPassword) {
    User user = findById(userId);

    if (!PASSWORD_ENCODER.matches(currentPassword, user.getPasswordHash())) {
      return false;
    }

    user.setPasswordHash(PASSWORD_ENCODER.encode(newPassword));
    userRepository.save(user);
    log.info("Пароль змінено: {} (id={})", user.getUsername(), user.getId());
    return true;
  }

  // =====================================================================
  // ВНУТРІШНІ МЕТОДИ
  // =====================================================================

  /** Отримує сутність User за ідентифікатором (для використання в інших сервісах). */
  User findById(Long id) {
    return userRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Користувач", id));
  }
}
