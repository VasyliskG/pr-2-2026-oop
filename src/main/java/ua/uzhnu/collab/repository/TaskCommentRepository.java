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
import ua.uzhnu.collab.entity.TaskComment;
import ua.uzhnu.collab.entity.User;

@Repository
@RequiredArgsConstructor
public class TaskCommentRepository {

  private final NamedParameterJdbcTemplate jdbc;

  private static final String SELECT =
      """
        SELECT c.id, c.task_id, c.user_id, c.content, c.created_at,
               u.id AS u_id, u.username AS u_username, u.email AS u_email,
               u.password_hash AS u_password_hash, u.full_name AS u_full_name,
               u.created_at AS u_created_at, u.updated_at AS u_updated_at
        FROM task_comments c
        JOIN users u ON u.id = c.user_id
        """;

  private static final RowMapper<TaskComment> COMMENT_MAPPER =
      (rs, n) -> {
        User user = new User();
        user.setId(rs.getLong("u_id"));
        user.setUsername(rs.getString("u_username"));
        user.setEmail(rs.getString("u_email"));
        user.setPasswordHash(rs.getString("u_password_hash"));
        user.setFullName(rs.getString("u_full_name"));
        user.setCreatedAt(rs.getObject("u_created_at", LocalDateTime.class));
        user.setUpdatedAt(rs.getObject("u_updated_at", LocalDateTime.class));

        Task task = new Task();
        task.setId(rs.getLong("task_id"));

        TaskComment c = new TaskComment();
        c.setId(rs.getLong("id"));
        c.setTask(task);
        c.setContent(rs.getString("content"));
        c.setUser(user);
        c.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        return c;
      };

  public TaskComment save(TaskComment comment) {
    return comment.getId() == null ? insert(comment) : update(comment);
  }

  private TaskComment insert(TaskComment comment) {
    String sql =
        """
            INSERT INTO task_comments (task_id, user_id, content, created_at)
            VALUES (:taskId, :userId, :content, CLOCK_TIMESTAMP())
            RETURNING id, created_at
            """;
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("taskId", comment.getTask().getId())
            .addValue("userId", comment.getUser().getId())
            .addValue("content", comment.getContent());
    return jdbc.queryForObject(
        sql,
        p,
        (rs, n) -> {
          comment.setId(rs.getLong("id"));
          comment.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
          return comment;
        });
  }

  private TaskComment update(TaskComment comment) {
    jdbc.update(
        "UPDATE task_comments SET content = :content WHERE id = :id",
        Map.of("id", comment.getId(), "content", comment.getContent()));
    return comment;
  }

  public Optional<TaskComment> findById(Long id) {
    List<TaskComment> rows =
        jdbc.query(SELECT + "WHERE c.id = :id", Map.of("id", id), COMMENT_MAPPER);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  public List<TaskComment> findByTaskIdOrderByCreatedAtAsc(Long taskId) {
    return jdbc.query(
        SELECT + "WHERE c.task_id = :taskId ORDER BY c.created_at ASC",
        Map.of("taskId", taskId),
        COMMENT_MAPPER);
  }

  public long countByTaskId(Long taskId) {
    Long n =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM task_comments WHERE task_id = :taskId",
            Map.of("taskId", taskId),
            Long.class);
    return n != null ? n : 0L;
  }
}
