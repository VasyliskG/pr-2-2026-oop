package ua.uzhnu.collab.entity;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * Зареєстрований користувач платформи.
 *
 * <p>Пароль зберігається виключно у вигляді BCrypt-хешу (10 раундів).
 * Один користувач може бути учасником кількох команд з різними ролями.
 *
 * @see ua.uzhnu.collab.service.UserService
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class User {

  /** Первинний ключ (генерується БД). */
  private Long id;

  /**
   * Унікальний логін користувача.
   * Обмеження: не порожній, від 3 до 50 символів.
   */
  @NotBlank(message = "Ім'я користувача не може бути порожнім")
  @Size(min = 3, max = 50, message = "Ім'я користувача: від 3 до 50 символів")
  private String username;

  /**
   * Унікальна електронна адреса.
   * Зберігається в нижньому регістрі.
   */
  @NotBlank(message = "Електронна пошта не може бути порожньою")
  @Email(message = "Некоректний формат електронної пошти")
  private String email;

  /**
   * BCrypt-хеш пароля (10 раундів).
   * Ніколи не передається у DTO-шарі.
   */
  @NotBlank(message = "Хеш пароля не може бути порожнім")
  private String passwordHash;

  /**
   * Повне ім'я для відображення в UI та коментарях.
   */
  @NotBlank(message = "Повне ім'я не може бути порожнім")
  private String fullName;

  /** Час реєстрації (встановлюється БД). */
  private LocalDateTime createdAt;

  /** Час останнього оновлення профілю (встановлюється БД). */
  private LocalDateTime updatedAt;
}
