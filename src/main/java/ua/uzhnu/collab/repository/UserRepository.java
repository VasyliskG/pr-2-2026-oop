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
import ua.uzhnu.collab.entity.User;

@Repository
@RequiredArgsConstructor
public class UserRepository {

  private final NamedParameterJdbcTemplate jdbc;

  static final RowMapper<User> USER_MAPPER =
      (rs, n) -> {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setUsername(rs.getString("username"));
        u.setEmail(rs.getString("email"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setFullName(rs.getString("full_name"));
        u.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        u.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
        return u;
      };

  public Optional<User> findById(Long id) {
    List<User> rows =
        jdbc.query("SELECT * FROM users WHERE id = :id", Map.of("id", id), USER_MAPPER);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  public User save(User user) {
    return user.getId() == null ? insert(user) : update(user);
  }

  public User saveAndFlush(User user) {
    return save(user);
  }

  private User insert(User user) {
    String sql =
        """
            INSERT INTO users (username, email, password_hash, full_name,
                               created_at, updated_at)
            VALUES (:username, :email, :passwordHash, :fullName,
                    CLOCK_TIMESTAMP(), CLOCK_TIMESTAMP())
            RETURNING id, created_at, updated_at
            """;
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("username", user.getUsername())
            .addValue("email", user.getEmail())
            .addValue("passwordHash", user.getPasswordHash())
            .addValue("fullName", user.getFullName());
    return jdbc.queryForObject(
        sql,
        p,
        (rs, n) -> {
          user.setId(rs.getLong("id"));
          user.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
          user.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
          return user;
        });
  }

  private User update(User user) {
    String sql =
        """
            UPDATE users
            SET username = :username, email = :email,
                password_hash = :passwordHash, full_name = :fullName,
                updated_at = CLOCK_TIMESTAMP()
            WHERE id = :id
            RETURNING updated_at
            """;
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("id", user.getId())
            .addValue("username", user.getUsername())
            .addValue("email", user.getEmail())
            .addValue("passwordHash", user.getPasswordHash())
            .addValue("fullName", user.getFullName());
    return jdbc.queryForObject(
        sql,
        p,
        (rs, n) -> {
          user.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
          return user;
        });
  }

  public void delete(User user) {
    jdbc.update("DELETE FROM users WHERE id = :id", Map.of("id", user.getId()));
  }

  public Optional<User> findByUsername(String username) {
    List<User> rows =
        jdbc.query(
            "SELECT * FROM users WHERE username = :username",
            Map.of("username", username),
            USER_MAPPER);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  public Optional<User> findByEmail(String email) {
    List<User> rows =
        jdbc.query("SELECT * FROM users WHERE email = :email", Map.of("email", email), USER_MAPPER);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  public boolean existsByUsername(String username) {
    Long n =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM users WHERE username = :username",
            Map.of("username", username),
            Long.class);
    return n != null && n > 0;
  }

  public boolean existsByEmail(String email) {
    Long n =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM users WHERE email = :email", Map.of("email", email), Long.class);
    return n != null && n > 0;
  }

  public List<User> findByTeamId(Long teamId) {
    String sql =
        """
            SELECT u.* FROM users u
            JOIN team_members tm ON tm.user_id = u.id
            WHERE tm.team_id = :teamId
            ORDER BY u.full_name
            """;
    return jdbc.query(sql, Map.of("teamId", teamId), USER_MAPPER);
  }

  public List<User> searchByNameOrUsername(String query) {
    String sql =
        """
            SELECT * FROM users
            WHERE LOWER(full_name) LIKE LOWER(:q)
               OR LOWER(username)  LIKE LOWER(:q)
            """;
    return jdbc.query(sql, Map.of("q", "%" + query + "%"), USER_MAPPER);
  }

  public List<User> findAll() {
    return jdbc.query("SELECT * FROM users", USER_MAPPER);
  }
}
