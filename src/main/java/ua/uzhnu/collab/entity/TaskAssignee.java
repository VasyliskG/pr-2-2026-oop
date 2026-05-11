package ua.uzhnu.collab.entity;

import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class TaskAssignee {

  private TaskAssigneeId id;
  private Task task;
  private User user;
  private LocalDateTime assignedAt;
}
