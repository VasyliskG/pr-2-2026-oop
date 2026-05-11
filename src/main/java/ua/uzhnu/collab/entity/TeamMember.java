package ua.uzhnu.collab.entity;

import java.time.LocalDateTime;
import lombok.*;
import ua.uzhnu.collab.enums.TeamRole;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class TeamMember {

  private TeamMemberId id;
  private Team team;
  private User user;
  private TeamRole role;
  private LocalDateTime joinedAt;
}
