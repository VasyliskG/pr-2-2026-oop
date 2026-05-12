package ua.uzhnu.collab.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import ua.uzhnu.collab.entity.TaskAssignee;
import ua.uzhnu.collab.entity.TaskAssigneeId;
import ua.uzhnu.collab.entity.User;

@Repository
@RequiredArgsConstructor
public class TaskAssigneeRepository {

  private final NamedParameterJdbcTemplate jdbc;

  private static final String SELECT =
      """
        SELECT ta.task_id, ta.user_id, ta.assigned_at,
               u.id AS u_id, u.username AS u_username, u.email AS u_email,
               u.password_hash AS u_password_hash, u.full_name AS u_full_name,
               u.created_at AS u_created_at, u.updated_at AS u_updated_at
        FROM task_assignees ta
        JOIN users u ON u.id = ta.user_id
        """;

  private static final RowMapper<TaskAssignee> ASSIGNEE_MAPPER =
      (rs, n) -> {
        User user = new User();
        user.setId(rs.getLong("u_id"));
        user.setUsername(rs.getString("u_username"));
        user.setEmail(rs.getString("u_email"));
        user.setPasswordHash(rs.getString("u_password_hash"));
        user.setFullName(rs.getString("u_full_name"));
        user.setCreatedAt(rs.getObject("u_created_at", LocalDateTime.class));
        user.setUpdatedAt(rs.getObject("u_updated_at", LocalDateTime.class));

        TaskAssignee ta = new TaskAssignee();
        ta.setId(new TaskAssigneeId(rs.getLong("task_id"), rs.getLong("user_id")));
        ta.setUser(user);
        ta.setAssignedAt(rs.getObject("assigned_at", LocalDateTime.class));
        return ta;
      };

  public TaskAssignee save(TaskAssignee assignee) {
    String sql =
        """
            INSERT INTO task_assignees (task_id, user_id)
            VALUES (:taskId, :userId)
            ON CONFLICT (task_id, user_id) DO NOTHING
            RETURNING assigned_at
            """;
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("taskId", assignee.getId().getTaskId())
            .addValue("userId", assignee.getId().getUserId());
    List<LocalDateTime> result =
        jdbc.query(sql, p, (rs, n) -> rs.getObject("assigned_at", LocalDateTime.class));
    if (!result.isEmpty()) {
      assignee.setAssignedAt(result.get(0));
    }
    return assignee;
  }

  public List<TaskAssignee> findByIdTaskId(Long taskId) {
    return jdbc.query(
        SELECT + "WHERE ta.task_id = :taskId", Map.of("taskId", taskId), ASSIGNEE_MAPPER);
  }

  public List<TaskAssignee> findByIdUserId(Long userId) {
    return jdbc.query(
        SELECT + "WHERE ta.user_id = :userId", Map.of("userId", userId), ASSIGNEE_MAPPER);
  }

  public boolean existsByIdTaskIdAndIdUserId(Long taskId, Long userId) {
    Long n =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM task_assignees WHERE task_id = :taskId AND user_id = :userId",
            Map.of("taskId", taskId, "userId", userId),
            Long.class);
    return n != null && n > 0;
  }

  public void deleteByIdTaskIdAndIdUserId(Long taskId, Long userId) {
    jdbc.update(
        "DELETE FROM task_assignees WHERE task_id = :taskId AND user_id = :userId",
        Map.of("taskId", taskId, "userId", userId));
  }
}
