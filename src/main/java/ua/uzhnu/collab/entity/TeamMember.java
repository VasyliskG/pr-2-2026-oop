package ua.uzhnu.collab.entity;

import java.time.LocalDateTime;
import lombok.*;
import ua.uzhnu.collab.enums.TeamRole;

/**
 * Учасник команди — зв'язок M:N між {@link Team} та {@link User} з додатковим полем ролі.
 *
 * <p>Зберігається у таблиці {@code team_members} зі складеним ключем {@link TeamMemberId}.
 * Кожна команда має щонайменше одного {@link TeamRole#OWNER}.
 *
 * @see ua.uzhnu.collab.service.TeamService#addMember
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class TeamMember {

  /** Складений ключ (teamId + userId). */
  private TeamMemberId id;

  /** Команда, до якої належить учасник. */
  private Team team;

  /** Користувач-учасник. */
  private User user;

  /**
   * Роль учасника в команді.
   *
   * @see TeamRole
   */
  private TeamRole role;

  /** Час вступу до команди (встановлюється БД). */
  private LocalDateTime joinedAt;
}
