package ua.uzhnu.collab.entity;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.*;

/**
 * Повідомлення командного чату.
 *
 * <p>Підтримує ієрархічну структуру відповідей: {@link #parentMessage} вказує на повідомлення,
 * якому дано відповідь. Кореневі повідомлення мають {@code parentMessage = null}.
 *
 * @see ua.uzhnu.collab.service.ChatService
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "parentMessage")
@EqualsAndHashCode(of = "id")
public class ChatMessage {

  /** Первинний ключ (генерується БД). */
  private Long id;

  /** Команда, в чаті якої надіслано повідомлення. */
  private Team team;

  /** Автор повідомлення. */
  private User user;

  /** Текст повідомлення. Не може бути порожнім. */
  @NotBlank(message = "Текст повідомлення не може бути порожнім")
  private String content;

  /**
   * Батьківське повідомлення (якщо це відповідь).
   * {@code null} для кореневих повідомлень.
   * Виключено з {@code toString()} для запобігання рекурсії.
   */
  private ChatMessage parentMessage;

  /** Час надсилання (встановлюється БД). */
  private LocalDateTime createdAt;
}
