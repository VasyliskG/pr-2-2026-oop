package ua.uzhnu.collab.entity;

import java.time.LocalDateTime;
import lombok.*;

/**
 * Призначення виконавця на задачу (зв'язок M:N між {@link Task} та {@link User}).
 *
 * <p>Зберігається у таблиці {@code task_assignees} зі складеним ключем {@link TaskAssigneeId}.
 * Один користувач може бути виконавцем кількох задач; одна задача може мати кількох виконавців.
 *
 * @see ua.uzhnu.collab.service.TaskService#assignUser
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class TaskAssignee {

  /** Складений ключ (taskId + userId). */
  private TaskAssigneeId id;

  /** Задача, на яку призначено виконавця. */
  private Task task;

  /** Призначений виконавець. */
  private User user;

  /** Час призначення (встановлюється БД). */
  private LocalDateTime assignedAt;
}
