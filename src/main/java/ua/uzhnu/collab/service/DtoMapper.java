package ua.uzhnu.collab.service;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;
import ua.uzhnu.collab.dto.Dtos.*;
import ua.uzhnu.collab.entity.*;
import ua.uzhnu.collab.enums.TaskStatus;

/**
 * Маппер для перетворення JPA-сутностей у DTO та навпаки.
 *
 * <p>Реалізовано як Spring-компонент для можливості ін'єкції та тестування. Використовуються
 * фабричні методи (патерн Factory Method).
 */
@Component
public class DtoMapper {

  // =====================================================================
  // USER
  // =====================================================================

  public UserDto toUserDto(User user) {
    return new UserDto(
        user.getId(), user.getUsername(), user.getEmail(), user.getFullName(), user.getCreatedAt());
  }

  // =====================================================================
  // TEAM
  // =====================================================================

  public TeamDto toTeamDto(Team team, int memberCount, int taskCount) {
    return new TeamDto(
        team.getId(),
        team.getName(),
        team.getDescription(),
        team.getCreatedBy().getFullName(),
        memberCount,
        taskCount,
        team.getCreatedAt());
  }

  // =====================================================================
  // TEAM MEMBER
  // =====================================================================

  public TeamMemberDto toTeamMemberDto(TeamMember tm) {
    return new TeamMemberDto(
        tm.getUser().getId(),
        tm.getUser().getUsername(),
        tm.getUser().getFullName(),
        tm.getRole(),
        tm.getJoinedAt());
  }

  // =====================================================================
  // TASK
  // =====================================================================

  public TaskDto toTaskDto(Task task, List<String> assigneeNames, int commentCount, int fileCount) {
    boolean overdue =
        task.getStatus() != TaskStatus.DONE
            && task.getDueDate() != null
            && task.getDueDate().isBefore(LocalDateTime.now());

    return new TaskDto(
        task.getId(),
        task.getTeam().getId(),
        task.getTeam().getName(),
        task.getTitle(),
        task.getDescription(),
        task.getStatus(),
        task.getPriority(),
        task.getDueDate(),
        task.getCreatedBy().getFullName(),
        assigneeNames,
        commentCount,
        fileCount,
        overdue,
        task.getCreatedAt(),
        task.getUpdatedAt());
  }

  // =====================================================================
  // COMMENT
  // =====================================================================

  public CommentDto toCommentDto(TaskComment comment) {
    return new CommentDto(
        comment.getId(),
        comment.getUser().getFullName(),
        comment.getContent(),
        comment.getCreatedAt());
  }

  // =====================================================================
  // CHAT MESSAGE
  // =====================================================================

  public ChatMessageDto toChatMessageDto(ChatMessage msg, int replyCount) {
    ChatMessage parent = msg.getParentMessage();
    String parentAuthor = null;
    String parentPreview = null;

    if (parent != null) {
      parentAuthor = parent.getUser().getFullName();
      String full = parent.getContent();
      parentPreview = full.length() > 60 ? full.substring(0, 57) + "..." : full;
    }

    return new ChatMessageDto(
        msg.getId(),
        msg.getUser().getFullName(),
        msg.getContent(),
        msg.getCreatedAt(),
        parent != null ? parent.getId() : null,
        parentAuthor,
        parentPreview,
        replyCount);
  }

  // =====================================================================
  // FILE
  // =====================================================================

  public FileDto toFileDto(AppFile file) {
    return new FileDto(
        file.getId(),
        file.getFileName(),
        file.getFileSize(),
        file.getMimeType(),
        file.getUploadedBy().getFullName(),
        file.getTask() != null ? file.getTask().getId() : null,
        file.getTask() != null ? file.getTask().getTitle() : null,
        file.getUploadedAt());
  }
}
