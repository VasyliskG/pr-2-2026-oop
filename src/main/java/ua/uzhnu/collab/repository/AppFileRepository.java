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
import ua.uzhnu.collab.entity.AppFile;
import ua.uzhnu.collab.entity.Task;
import ua.uzhnu.collab.entity.Team;
import ua.uzhnu.collab.entity.User;

@Repository
@RequiredArgsConstructor
public class AppFileRepository {

  private final NamedParameterJdbcTemplate jdbc;

  private static final String SELECT =
      """
        SELECT f.id, f.team_id, f.task_id, f.uploaded_by,
               f.file_name, f.storage_path, f.file_size, f.mime_type, f.uploaded_at,
               u.id AS u_id, u.username AS u_username, u.email AS u_email,
               u.password_hash AS u_password_hash, u.full_name AS u_full_name,
               u.created_at AS u_created_at, u.updated_at AS u_updated_at,
               t.title AS t_title
        FROM files f
        JOIN users u ON u.id = f.uploaded_by
        LEFT JOIN tasks t ON t.id = f.task_id
        """;

  private static final RowMapper<AppFile> FILE_MAPPER =
      (rs, n) -> {
        User uploader = new User();
        uploader.setId(rs.getLong("u_id"));
        uploader.setUsername(rs.getString("u_username"));
        uploader.setEmail(rs.getString("u_email"));
        uploader.setPasswordHash(rs.getString("u_password_hash"));
        uploader.setFullName(rs.getString("u_full_name"));
        uploader.setCreatedAt(rs.getObject("u_created_at", LocalDateTime.class));
        uploader.setUpdatedAt(rs.getObject("u_updated_at", LocalDateTime.class));

        Team team = new Team();
        team.setId(rs.getLong("team_id"));

        AppFile f = new AppFile();
        f.setId(rs.getLong("id"));
        f.setTeam(team);
        f.setUploadedBy(uploader);
        f.setFileName(rs.getString("file_name"));
        f.setStoragePath(rs.getString("storage_path"));
        f.setFileSize(rs.getLong("file_size"));
        f.setMimeType(rs.getString("mime_type"));
        f.setUploadedAt(rs.getObject("uploaded_at", LocalDateTime.class));

        long taskId = rs.getLong("task_id");
        if (!rs.wasNull()) {
          Task task = new Task();
          task.setId(taskId);
          task.setTitle(rs.getString("t_title"));
          f.setTask(task);
        }

        return f;
      };

  public Optional<AppFile> findById(Long id) {
    List<AppFile> rows = jdbc.query(SELECT + "WHERE f.id = :id", Map.of("id", id), FILE_MAPPER);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  public AppFile save(AppFile file) {
    return file.getId() == null ? insert(file) : update(file);
  }

  private AppFile insert(AppFile file) {
    String sql =
        """
            INSERT INTO files (team_id, task_id, uploaded_by,
                               file_name, storage_path, file_size, mime_type, uploaded_at)
            VALUES (:teamId, :taskId, :uploadedBy,
                   :fileName, :storagePath, :fileSize, :mimeType, CLOCK_TIMESTAMP())
            RETURNING id, uploaded_at
            """;
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("teamId", file.getTeam().getId())
            .addValue("taskId", file.getTask() != null ? file.getTask().getId() : null)
            .addValue("uploadedBy", file.getUploadedBy().getId())
            .addValue("fileName", file.getFileName())
            .addValue("storagePath", file.getStoragePath())
            .addValue("fileSize", file.getFileSize())
            .addValue("mimeType", file.getMimeType());
    return jdbc.queryForObject(
        sql,
        p,
        (rs, n) -> {
          file.setId(rs.getLong("id"));
          file.setUploadedAt(rs.getObject("uploaded_at", LocalDateTime.class));
          return file;
        });
  }

  private AppFile update(AppFile file) {
    String sql =
        """
            UPDATE files
            SET file_name = :fileName, storage_path = :storagePath,
                file_size = :fileSize, mime_type = :mimeType
            WHERE id = :id
            """;
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("id", file.getId())
            .addValue("fileName", file.getFileName())
            .addValue("storagePath", file.getStoragePath())
            .addValue("fileSize", file.getFileSize())
            .addValue("mimeType", file.getMimeType());
    jdbc.update(sql, p);
    return file;
  }

  public void delete(AppFile file) {
    jdbc.update("DELETE FROM files WHERE id = :id", Map.of("id", file.getId()));
  }

  public List<AppFile> findByTeamIdOrderByUploadedAtDesc(Long teamId) {
    return jdbc.query(
        SELECT + "WHERE f.team_id = :teamId ORDER BY f.uploaded_at DESC",
        Map.of("teamId", teamId),
        FILE_MAPPER);
  }

  public List<AppFile> findByTaskId(Long taskId) {
    return jdbc.query(SELECT + "WHERE f.task_id = :taskId", Map.of("taskId", taskId), FILE_MAPPER);
  }

  public List<AppFile> findByTeamIdAndTaskIsNull(Long teamId) {
    return jdbc.query(
        SELECT + "WHERE f.team_id = :teamId AND f.task_id IS NULL",
        Map.of("teamId", teamId),
        FILE_MAPPER);
  }

  public long sumFileSizeByTeamId(Long teamId) {
    Long n =
        jdbc.queryForObject(
            "SELECT COALESCE(SUM(file_size), 0) FROM files WHERE team_id = :teamId",
            Map.of("teamId", teamId),
            Long.class);
    return n != null ? n : 0L;
  }

  public long countByTeamId(Long teamId) {
    Long n =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM files WHERE team_id = :teamId",
            Map.of("teamId", teamId),
            Long.class);
    return n != null ? n : 0L;
  }
}
