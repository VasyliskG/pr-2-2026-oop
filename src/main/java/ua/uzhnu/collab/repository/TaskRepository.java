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
import ua.uzhnu.collab.entity.Task;
import ua.uzhnu.collab.entity.Team;
import ua.uzhnu.collab.entity.User;
import ua.uzhnu.collab.enums.TaskPriority;
import ua.uzhnu.collab.enums.TaskStatus;

/**
 * Репозиторій для роботи з таблицею {@code tasks}.
 *
 * <p>Реалізує CRUD-операції та спеціалізовані запити через {@link NamedParameterJdbcTemplate}.
 * Усі запити повертають повністю ініціалізовані об'єкти {@link ua.uzhnu.collab.entity.Task}
 * з підтягнутими {@code team} та {@code createdBy}.
 */
@Repository
@RequiredArgsConstructor
public class TaskRepository {

  private final NamedParameterJdbcTemplate jdbc;

  private static final String SELECT =
      """
        SELECT t.id, t.team_id, t.title, t.description,
               t.status::TEXT AS status, t.priority::TEXT AS priority,
               t.due_date, t.created_by, t.created_at, t.updated_at,
               tm.name AS tm_name, tm.description AS tm_description,
               u.id AS u_id, u.username AS u_username, u.email AS u_email,
               u.password_hash AS u_password_hash, u.full_name AS u_full_name,
               u.created_at AS u_created_at, u.updated_at AS u_updated_at
        FROM tasks t
        JOIN teams tm ON tm.id = t.team_id
        JOIN users u  ON u.id  = t.created_by
        """;

  static final RowMapper<Task> TASK_MAPPER =
      (rs, n) -> {
        Team team = new Team();
        team.setId(rs.getLong("team_id"));
        team.setName(rs.getString("tm_name"));
        team.setDescription(rs.getString("tm_description"));

        User creator = new User();
        creator.setId(rs.getLong("u_id"));
        creator.setUsername(rs.getString("u_username"));
        creator.setEmail(rs.getString("u_email"));
        creator.setPasswordHash(rs.getString("u_password_hash"));
        creator.setFullName(rs.getString("u_full_name"));
        creator.setCreatedAt(rs.getObject("u_created_at", LocalDateTime.class));
        creator.setUpdatedAt(rs.getObject("u_updated_at", LocalDateTime.class));

        Task t = new Task();
        t.setId(rs.getLong("id"));
        t.setTeam(team);
        t.setTitle(rs.getString("title"));
        t.setDescription(rs.getString("description"));
        t.setStatus(TaskStatus.valueOf(rs.getString("status")));
        t.setPriority(TaskPriority.valueOf(rs.getString("priority")));
        t.setDueDate(rs.getObject("due_date", LocalDateTime.class));
        t.setCreatedBy(creator);
        t.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        t.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
        return t;
      };

  public Optional<Task> findById(Long id) {
    List<Task> rows = jdbc.query(SELECT + "WHERE t.id = :id", Map.of("id", id), TASK_MAPPER);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  public Task save(Task task) {
    return task.getId() == null ? insert(task) : update(task);
  }

  private Task insert(Task task) {
    String sql =
        """
            INSERT INTO tasks (team_id, title, description, status, priority, due_date,
                               created_by, created_at, updated_at)
            VALUES (:teamId, :title, :description,
                   :status::task_status, :priority::task_priority,
                   :dueDate, :createdBy, CLOCK_TIMESTAMP(), CLOCK_TIMESTAMP())
            RETURNING id, created_at, updated_at
            """;
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("teamId", task.getTeam().getId())
            .addValue("title", task.getTitle())
            .addValue("description", task.getDescription())
            .addValue("status", task.getStatus().name())
            .addValue("priority", task.getPriority().name())
            .addValue("dueDate", task.getDueDate())
            .addValue("createdBy", task.getCreatedBy().getId());
    return jdbc.queryForObject(
        sql,
        p,
        (rs, n) -> {
          task.setId(rs.getLong("id"));
          task.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
          task.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
          return task;
        });
  }

  private Task update(Task task) {
    String sql =
        """
            UPDATE tasks
            SET title = :title, description = :description,
                status = :status::task_status, priority = :priority::task_priority,
                due_date = :dueDate, updated_at = CLOCK_TIMESTAMP()
            WHERE id = :id
            RETURNING updated_at
            """;
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("id", task.getId())
            .addValue("title", task.getTitle())
            .addValue("description", task.getDescription())
            .addValue("status", task.getStatus().name())
            .addValue("priority", task.getPriority().name())
            .addValue("dueDate", task.getDueDate());
    return jdbc.queryForObject(
        sql,
        p,
        (rs, n) -> {
          task.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
          return task;
        });
  }

  public void delete(Task task) {
    jdbc.update("DELETE FROM tasks WHERE id = :id", Map.of("id", task.getId()));
  }

  public List<Task> findByTeamIdOrderByCreatedAtDesc(Long teamId) {
    return jdbc.query(
        SELECT + "WHERE t.team_id = :teamId ORDER BY t.created_at DESC",
        Map.of("teamId", teamId),
        TASK_MAPPER);
  }

  public List<Task> findByTeamIdAndStatus(Long teamId, TaskStatus status) {
    MapSqlParameterSource p =
        new MapSqlParameterSource().addValue("teamId", teamId).addValue("status", status.name());
    return jdbc.query(
        SELECT + "WHERE t.team_id = :teamId AND t.status = :status::task_status", p, TASK_MAPPER);
  }

  public List<Task> findByTeamIdAndPriority(Long teamId, TaskPriority priority) {
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("teamId", teamId)
            .addValue("priority", priority.name());
    return jdbc.query(
        SELECT + "WHERE t.team_id = :teamId AND t.priority = :priority::task_priority",
        p,
        TASK_MAPPER);
  }

  public List<Task> findUpcomingByTeam(Long teamId, LocalDateTime from, LocalDateTime to) {
    String sql =
        SELECT
            + """
            WHERE t.team_id = :teamId
              AND t.status <> 'DONE'::task_status
              AND t.due_date BETWEEN :from AND :to
            ORDER BY t.due_date
            """;
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("teamId", teamId)
            .addValue("from", from)
            .addValue("to", to);
    return jdbc.query(sql, p, TASK_MAPPER);
  }

  public List<Task> findOverdueByTeam(Long teamId) {
    String sql =
        SELECT
            + """
            WHERE t.team_id = :teamId
              AND t.status <> 'DONE'::task_status
              AND t.due_date < NOW()
            ORDER BY t.due_date
            """;
    return jdbc.query(sql, Map.of("teamId", teamId), TASK_MAPPER);
  }

  public List<Task> searchInTeam(Long teamId, String query) {
    String sql =
        SELECT
            + """
            WHERE t.team_id = :teamId
              AND (LOWER(t.title) LIKE LOWER(:q) OR LOWER(t.description) LIKE LOWER(:q))
            """;
    MapSqlParameterSource p =
        new MapSqlParameterSource().addValue("teamId", teamId).addValue("q", "%" + query + "%");
    return jdbc.query(sql, p, TASK_MAPPER);
  }

  public List<Task> findByAssigneeUserId(Long userId) {
    String sql =
        SELECT
            + """
            JOIN task_assignees ta ON ta.task_id = t.id
            WHERE ta.user_id = :userId
            ORDER BY t.due_date NULLS LAST
            """;
    return jdbc.query(sql, Map.of("userId", userId), TASK_MAPPER);
  }

  public long countByTeamIdAndStatus(Long teamId, TaskStatus status) {
    Long n =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM tasks WHERE team_id = :teamId AND status = :status::task_status",
            Map.of("teamId", teamId, "status", status.name()),
            Long.class);
    return n != null ? n : 0L;
  }
}
