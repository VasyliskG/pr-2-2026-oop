package ua.uzhnu.collab.entity;

import java.io.Serializable;
import lombok.*;

/**
 * Складений первинний ключ таблиці {@code team_members}.
 *
 * <p>Унікально ідентифікує запис «команда — учасник».
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TeamMemberId implements Serializable {

  /** Ідентифікатор команди. */
  private Long teamId;

  /** Ідентифікатор користувача-учасника. */
  private Long userId;
}
