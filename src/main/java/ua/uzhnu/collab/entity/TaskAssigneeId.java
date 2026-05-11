package ua.uzhnu.collab.entity;

import java.io.Serializable;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TaskAssigneeId implements Serializable {

  private Long taskId;
  private Long userId;
}
