package ua.uzhnu.collab.entity;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import lombok.*;
import ua.uzhnu.collab.enums.TaskPriority;
import ua.uzhnu.collab.enums.TaskStatus;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class Task {

  private Long id;

  private Team team;

  @NotBlank(message = "Назва задачі не може бути порожньою")
  @Size(max = 200, message = "Назва задачі: максимум 200 символів")
  private String title;

  private String description;

  @Builder.Default private TaskStatus status = TaskStatus.TODO;

  @Builder.Default private TaskPriority priority = TaskPriority.MEDIUM;

  private LocalDateTime dueDate;

  private User createdBy;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
