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
import ua.uzhnu.collab.entity.User;

@Repository
@RequiredArgsConstructor
public class TeamRepository {

  private final NamedParameterJdbcTemplate jdbc;

  private static final String SELECT =
      """
        SELECT t.id, t.name, t.description, t.created_by, t.created_at, t.updated_at,
               u.id AS u_id, u.username AS u_username, u.email AS u_email,
               u.password_hash AS u_password_hash, u.full_name AS u_full_name,
               u.created_at AS u_created_at, u.updated_at AS u_updated_at
        FROM teams t
        JOIN users u ON u.id = t.created_by
        """;

  static final RowMapper<Team> TEAM_MAPPER =
      (rs, n) -> {
        User creator = new User();
        creator.setId(rs.getLong("u_id"));
        creator.setUsername(rs.getString("u_username"));
        creator.setEmail(rs.getString("u_email"));
        creator.setPasswordHash(rs.getString("u_password_hash"));
        creator.setFullName(rs.getString("u_full_name"));
        creator.setCreatedAt(rs.getObject("u_created_at", LocalDateTime.class));
        creator.setUpdatedAt(rs.getObject("u_updated_at", LocalDateTime.class));

        Team t = new Team();
        t.setId(rs.getLong("id"));
        t.setName(rs.getString("name"));
        t.setDescription(rs.getString("description"));
        t.setCreatedBy(creator);
        t.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        t.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
        return t;
      };

  public Optional<Team> findById(Long id) {
    List<Team> rows = jdbc.query(SELECT + "WHERE t.id = :id", Map.of("id", id), TEAM_MAPPER);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  public Team save(Team team) {
    return team.getId() == null ? insert(team) : update(team);
  }

  private Team insert(Team team) {
    String sql =
        """
            INSERT INTO teams (name, description, created_by, created_at, updated_at)
            VALUES (:name, :description, :createdBy, CLOCK_TIMESTAMP(), CLOCK_TIMESTAMP())
            RETURNING id, created_at, updated_at
            """;
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("name", team.getName())
            .addValue("description", team.getDescription())
            .addValue("createdBy", team.getCreatedBy().getId());
    return jdbc.queryForObject(
        sql,
        p,
        (rs, n) -> {
          team.setId(rs.getLong("id"));
          team.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
          team.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
          return team;
        });
  }

  private Team update(Team team) {
    String sql =
        """
            UPDATE teams
            SET name = :name, description = :description, updated_at = CLOCK_TIMESTAMP()
            WHERE id = :id
            RETURNING updated_at
            """;
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("id", team.getId())
            .addValue("name", team.getName())
            .addValue("description", team.getDescription());
    return jdbc.queryForObject(
        sql,
        p,
        (rs, n) -> {
          team.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
          return team;
        });
  }

  public void delete(Team team) {
    jdbc.update("DELETE FROM teams WHERE id = :id", Map.of("id", team.getId()));
  }

  public List<Team> findByUserId(Long userId) {
    String sql =
        SELECT
            + """
            JOIN team_members tm ON tm.team_id = t.id
            WHERE tm.user_id = :userId
            ORDER BY t.name
            """;
    return jdbc.query(sql, Map.of("userId", userId), TEAM_MAPPER);
  }

  public List<Team> findByNameContainingIgnoreCase(String fragment) {
    String sql = SELECT + "WHERE LOWER(t.name) LIKE LOWER(:fragment)";
    return jdbc.query(sql, Map.of("fragment", "%" + fragment + "%"), TEAM_MAPPER);
  }
}
