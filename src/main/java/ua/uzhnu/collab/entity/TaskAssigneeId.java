package ua.uzhnu.collab.entity;

import java.io.Serializable;
import lombok.*;

/**
 * Складений первинний ключ таблиці {@code task_assignees}.
 *
 * <p>Унікально ідентифікує запис «задача — виконавець».
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TaskAssigneeId implements Serializable {

  /** Ідентифікатор задачі. */
  private Long taskId;

  /** Ідентифікатор виконавця. */
  private Long userId;
}
