package ua.uzhnu.collab.entity;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import lombok.*;
import ua.uzhnu.collab.enums.TaskPriority;
import ua.uzhnu.collab.enums.TaskStatus;

/**
 * Сутність задачі у командній дошці.
 *
 * <p>Задача належить рівно одній {@link Team}. Статус переміщується через state machine
 * ({@code TODO → IN_PROGRESS ↔ DONE}), пріоритет визначає видимість у канбані.
 * До задачі можна прив'язати виконавців ({@link TaskAssignee}),
 * коментарі ({@link TaskComment}) та файли ({@link AppFile}).
 *
 * @see ua.uzhnu.collab.service.TaskService
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class Task {

  /** Первинний ключ (генерується БД). */
  private Long id;

  /** Команда-власник задачі. */
  private Team team;

  /**
   * Коротка назва задачі.
   * Обмеження: не порожня, максимум 200 символів.
   */
  @NotBlank(message = "Назва задачі не може бути порожньою")
  @Size(max = 200, message = "Назва задачі: максимум 200 символів")
  private String title;

  /** Детальний опис задачі (може бути {@code null}). */
  private String description;

  /**
   * Поточний статус (за замовчуванням {@link TaskStatus#TODO}).
   *
   * @see ua.uzhnu.collab.service.TaskService#changeStatus
   */
  @Builder.Default
  private TaskStatus status = TaskStatus.TODO;

  /**
   * Пріоритет задачі (за замовчуванням {@link TaskPriority#MEDIUM}).
   */
  @Builder.Default
  private TaskPriority priority = TaskPriority.MEDIUM;

  /**
   * Крайній термін виконання задачі.
   * Якщо {@code null} — дедлайн не встановлено.
   * Задача вважається простроченою, коли {@code dueDate < now} і статус не {@code DONE}.
   */
  private LocalDateTime dueDate;

  /** Користувач, який створив задачу. */
  private User createdBy;

  /** Час створення (встановлюється БД через {@code CLOCK_TIMESTAMP()}). */
  private LocalDateTime createdAt;

  /** Час останнього оновлення (встановлюється БД). */
  private LocalDateTime updatedAt;
}
