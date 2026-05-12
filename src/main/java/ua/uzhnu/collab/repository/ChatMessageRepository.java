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
import ua.uzhnu.collab.entity.ChatMessage;
import ua.uzhnu.collab.entity.Team;
import ua.uzhnu.collab.entity.User;

/**
 * Репозиторій для роботи з таблицею {@code chat_messages}.
 *
 * <p>Підтримує пагінацію ({@code page}, {@code pageSize}), отримання кореневих повідомлень
 * та відповідей ({@code findByParentMessageIdOrderByCreatedAtAsc}).
 * Кожне повідомлення підтягує автора та батьківське повідомлення одним запитом через LEFT JOIN.
 */
@Repository
@RequiredArgsConstructor
public class ChatMessageRepository {

  private final NamedParameterJdbcTemplate jdbc;

  private static final String SELECT =
      """
        SELECT cm.id, cm.team_id, cm.user_id, cm.content,
               cm.parent_message_id, cm.created_at,
               u.id AS u_id, u.username AS u_username, u.email AS u_email,
               u.password_hash AS u_password_hash, u.full_name AS u_full_name,
               u.created_at AS u_created_at, u.updated_at AS u_updated_at,
               pm.content AS p_content, pm.created_at AS p_created_at,
               pm.user_id AS p_user_id, pu.full_name AS p_user_full_name
        FROM chat_messages cm
        JOIN users u ON u.id = cm.user_id
        LEFT JOIN chat_messages pm ON pm.id = cm.parent_message_id
        LEFT JOIN users pu ON pu.id = pm.user_id
        """;

  private static final RowMapper<ChatMessage> MSG_MAPPER =
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
        Team team = new Team();
        team.setId(teamId);

        ChatMessage msg = new ChatMessage();
        msg.setId(rs.getLong("id"));
        msg.setTeam(team);
        msg.setUser(user);
        msg.setContent(rs.getString("content"));
        msg.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));

        long parentId = rs.getLong("parent_message_id");
        if (!rs.wasNull()) {
          User parentUser = new User();
          parentUser.setId(rs.getLong("p_user_id"));
          parentUser.setFullName(rs.getString("p_user_full_name"));

          ChatMessage parent = new ChatMessage();
          parent.setId(parentId);
          parent.setContent(rs.getString("p_content"));
          parent.setCreatedAt(rs.getObject("p_created_at", LocalDateTime.class));
          parent.setUser(parentUser);
          parent.setTeam(team);

          msg.setParentMessage(parent);
        }

        return msg;
      };

  public Optional<ChatMessage> findById(Long id) {
    List<ChatMessage> rows = jdbc.query(SELECT + "WHERE cm.id = :id", Map.of("id", id), MSG_MAPPER);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  public ChatMessage save(ChatMessage msg) {
    return msg.getId() == null ? insert(msg) : update(msg);
  }

  private ChatMessage insert(ChatMessage msg) {
    String sql =
        """
            INSERT INTO chat_messages (team_id, user_id, content, parent_message_id, created_at)
            VALUES (:teamId, :userId, :content, :parentId, CLOCK_TIMESTAMP())
            RETURNING id, created_at
            """;
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("teamId", msg.getTeam().getId())
            .addValue("userId", msg.getUser().getId())
            .addValue("content", msg.getContent())
            .addValue(
                "parentId", msg.getParentMessage() != null ? msg.getParentMessage().getId() : null);
    return jdbc.queryForObject(
        sql,
        p,
        (rs, n) -> {
          msg.setId(rs.getLong("id"));
          msg.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
          return msg;
        });
  }

  private ChatMessage update(ChatMessage msg) {
    jdbc.update(
        "UPDATE chat_messages SET content = :content WHERE id = :id",
        Map.of("id", msg.getId(), "content", msg.getContent()));
    return msg;
  }

  public List<ChatMessage> findByTeamIdOrderByCreatedAtDesc(Long teamId, int page, int pageSize) {
    String sql =
        SELECT
            + """
            WHERE cm.team_id = :teamId
            ORDER BY cm.created_at DESC
            LIMIT :limit OFFSET :offset
            """;
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("teamId", teamId)
            .addValue("limit", pageSize)
            .addValue("offset", (long) page * pageSize);
    return jdbc.query(sql, p, MSG_MAPPER);
  }

  public List<ChatMessage> findRootMessagesByTeam(Long teamId, int page, int pageSize) {
    String sql =
        SELECT
            + """
            WHERE cm.team_id = :teamId AND cm.parent_message_id IS NULL
            ORDER BY cm.created_at DESC
            LIMIT :limit OFFSET :offset
            """;
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("teamId", teamId)
            .addValue("limit", pageSize)
            .addValue("offset", (long) page * pageSize);
    return jdbc.query(sql, p, MSG_MAPPER);
  }

  public List<ChatMessage> findByParentMessageIdOrderByCreatedAtAsc(Long parentMessageId) {
    return jdbc.query(
        SELECT + "WHERE cm.parent_message_id = :parentId ORDER BY cm.created_at ASC",
        Map.of("parentId", parentMessageId),
        MSG_MAPPER);
  }

  public long countByTeamId(Long teamId) {
    Long n =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM chat_messages WHERE team_id = :teamId",
            Map.of("teamId", teamId),
            Long.class);
    return n != null ? n : 0L;
  }
}
