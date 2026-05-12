package ua.uzhnu.collab.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import ua.uzhnu.collab.entity.Team;
import ua.uzhnu.collab.entity.TeamMember;
import ua.uzhnu.collab.entity.TeamMemberId;
import ua.uzhnu.collab.entity.User;
import ua.uzhnu.collab.enums.TeamRole;

@Repository
@RequiredArgsConstructor
public class TeamMemberRepository {

  private final NamedParameterJdbcTemplate jdbc;

  private static final String SELECT =
      """
        SELECT tm.team_id, tm.user_id, tm.role::TEXT AS role, tm.joined_at,
               u.id AS u_id, u.username AS u_username, u.email AS u_email,
               u.password_hash AS u_password_hash, u.full_name AS u_full_name,
               u.created_at AS u_created_at, u.updated_at AS u_updated_at,
               t.name AS t_name, t.description AS t_description
        FROM team_members tm
        JOIN users u ON u.id = tm.user_id
        JOIN teams t ON t.id = tm.team_id
        """;

  private static final RowMapper<TeamMember> MEMBER_MAPPER =
      (rs, n) -> {
        User user = new User();
        user.setId(rs.getLong("u_id"));
        user.setUsername(rs.getString("u_username"));
        user.setEmail(rs.getString("u_email"));
        user.setPasswordHash(rs.getString("u_password_hash"));
        user.setFullName(rs.getString("u_full_name"));
        user.setCreatedAt(rs.getObject("u_created_at", LocalDateTime.class));
        user.setUpdatedAt(rs.getObject("u_updated_at", LocalDateTime.class));

        long teamId = rs.getLong("team_id");
        long userId = rs.getLong("user_id");

        Team team = new Team();
        team.setId(teamId);
        team.setName(rs.getString("t_name"));
        team.setDescription(rs.getString("t_description"));

        TeamMember tm = new TeamMember();
        tm.setId(new TeamMemberId(teamId, userId));
        tm.setTeam(team);
        tm.setUser(user);
        tm.setRole(TeamRole.valueOf(rs.getString("role")));
        tm.setJoinedAt(rs.getObject("joined_at", LocalDateTime.class));
        return tm;
      };

  public TeamMember save(TeamMember member) {
    String sql =
        """
            INSERT INTO team_members (team_id, user_id, role)
            VALUES (:teamId, :userId, :role::team_role)
            ON CONFLICT (team_id, user_id) DO UPDATE SET role = EXCLUDED.role
            RETURNING joined_at
            """;
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("teamId", member.getId().getTeamId())
            .addValue("userId", member.getId().getUserId())
            .addValue("role", member.getRole().name());
    jdbc.queryForObject(
        sql,
        p,
        (rs, n) -> {
          member.setJoinedAt(rs.getObject("joined_at", LocalDateTime.class));
          return null;
        });
    return member;
  }

  public void delete(TeamMember member) {
    jdbc.update(
        "DELETE FROM team_members WHERE team_id = :teamId AND user_id = :userId",
        Map.of("teamId", member.getId().getTeamId(), "userId", member.getId().getUserId()));
  }

  public List<TeamMember> findByIdTeamId(Long teamId) {
    return jdbc.query(
        SELECT + "WHERE tm.team_id = :teamId", Map.of("teamId", teamId), MEMBER_MAPPER);
  }

  public List<TeamMember> findByIdUserId(Long userId) {
    return jdbc.query(
        SELECT + "WHERE tm.user_id = :userId", Map.of("userId", userId), MEMBER_MAPPER);
  }

  public Optional<TeamMember> findByIdTeamIdAndIdUserId(Long teamId, Long userId) {
    MapSqlParameterSource p =
        new MapSqlParameterSource().addValue("teamId", teamId).addValue("userId", userId);
    List<TeamMember> rows =
        jdbc.query(
            SELECT + "WHERE tm.team_id = :teamId AND tm.user_id = :userId", p, MEMBER_MAPPER);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  public boolean existsByIdTeamIdAndIdUserId(Long teamId, Long userId) {
    Long n =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM team_members WHERE team_id = :teamId AND user_id = :userId",
            Map.of("teamId", teamId, "userId", userId),
            Long.class);
    return n != null && n > 0;
  }

  public long countByIdTeamIdAndRole(Long teamId, TeamRole role) {
    Long n =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM team_members WHERE team_id = :teamId AND role = :role::team_role",
            Map.of("teamId", teamId, "role", role.name()),
            Long.class);
    return n != null ? n : 0L;
  }
}
