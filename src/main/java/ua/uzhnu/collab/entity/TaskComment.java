package ua.uzhnu.collab.entity;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.*;

/**
 * Коментар до задачі.
 *
 * <p>Коментарі відображаються у хронологічному порядку у діалозі деталей задачі.
 * Автором може бути будь-який учасник команди, якій належить задача.
 *
 * @see ua.uzhnu.collab.service.TaskService#addComment
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class TaskComment {

  /** Первинний ключ (генерується БД). */
  private Long id;

  /** Задача, до якої залишено коментар. */
  private Task task;

  /** Автор коментаря. */
  private User user;

  /** Текст коментаря. Не може бути порожнім. */
  @NotBlank(message = "Текст коментаря не може бути порожнім")
  private String content;

  /** Час створення (встановлюється БД). */
  private LocalDateTime createdAt;
}
