package ua.uzhnu.collab.entity;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class TaskComment {

  private Long id;
  private Task task;
  private User user;

  @NotBlank(message = "Текст коментаря не може бути порожнім")
  private String content;

  private LocalDateTime createdAt;
}
