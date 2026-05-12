package ua.uzhnu.collab.dto;

import java.time.LocalDateTime;
import java.util.List;
import ua.uzhnu.collab.enums.TaskPriority;
import ua.uzhnu.collab.enums.TaskStatus;
import ua.uzhnu.collab.enums.TeamRole;

/**
 * Об'єкти передачі даних (DTO) для обміну між шарами застосунку.
 *
 * <p>Використано Java Records (JDK 16+) — незмінні класи даних з автоматичною генерацією
 * конструктора, геттерів, equals, hashCode, toString.
 */
public final class Dtos {

  private Dtos() {}

  // =====================================================================
  // USER
  // =====================================================================

  /** Реєстрація нового користувача. */
  public record UserCreateDto(String username, String email, String password, String fullName) {}

  /** Відображення користувача у списках та профілі. */
  public record UserDto(
      Long id, String username, String email, String fullName, LocalDateTime createdAt) {}

  // =====================================================================
  // TEAM
  // =====================================================================

  /** Створення команди. */
  public record TeamCreateDto(String name, String description) {}

  /** Відображення команди з базовою статистикою. */
  public record TeamDto(
      Long id,
      String name,
      String description,
      String ownerName,
      int memberCount,
      int taskCount,
      LocalDateTime createdAt) {}

  /** Статистика команди для дашборду. */
  public record TeamStatsDto(
      Long teamId,
      String teamName,
      int memberCount,
      int totalTasks,
      int todoCount,
      int inProgressCount,
      int doneCount,
      int overdueCount,
      int fileCount,
      long totalFileSizeBytes,
      long messageCount) {}

  // =====================================================================
  // TEAM MEMBER
  // =====================================================================

  /** Учасник команди з роллю. */
  public record TeamMemberDto(
      Long userId, String username, String fullName, TeamRole role, LocalDateTime joinedAt) {}

  // =====================================================================
  // TASK
  // =====================================================================

  /** Створення задачі. */
  public record TaskCreateDto(
      Long teamId,
      String title,
      String description,
      TaskPriority priority,
      LocalDateTime dueDate,
      List<Long> assigneeIds) {}

  /** Відображення задачі у канбан-дошці. */
  public record TaskDto(
      Long id,
      Long teamId,
      String teamName,
      String title,
      String description,
      TaskStatus status,
      TaskPriority priority,
      LocalDateTime dueDate,
      String creatorName,
      List<String> assigneeNames,
      int commentCount,
      int fileCount,
      boolean overdue,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {}

  // =====================================================================
  // TASK COMMENT
  // =====================================================================

  /** Створення коментаря. */
  public record CommentCreateDto(Long taskId, String content) {}

  /** Відображення коментаря. */
  public record CommentDto(Long id, String authorName, String content, LocalDateTime createdAt) {}

  // =====================================================================
  // CHAT MESSAGE
  // =====================================================================

  /** Надсилання повідомлення. */
  public record ChatMessageCreateDto(Long teamId, String content, Long parentMessageId) {}

  /** Відображення повідомлення з контекстом відповіді. */
  public record ChatMessageDto(
      Long id,
      String authorName,
      String content,
      LocalDateTime createdAt,
      Long parentMessageId,
      String parentAuthorName,
      String parentContentPreview,
      int replyCount) {}

  // =====================================================================
  // FILE
  // =====================================================================

  /** Метадані завантаженого файлу. */
  public record FileDto(
      Long id,
      String fileName,
      long fileSize,
      String mimeType,
      String uploaderName,
      Long taskId,
      String taskTitle,
      LocalDateTime uploadedAt) {}

  // =====================================================================
  // USER WORKLOAD
  // =====================================================================

  /** Робоче навантаження користувача. */
  public record UserWorkloadDto(
      Long userId,
      String fullName,
      int teamCount,
      int todoCount,
      int inProgressCount,
      int doneCount,
      int overdueCount) {}
}
