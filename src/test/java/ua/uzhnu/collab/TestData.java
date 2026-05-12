package ua.uzhnu.collab;

import java.time.LocalDateTime;
import ua.uzhnu.collab.entity.*;
import ua.uzhnu.collab.enums.TaskPriority;
import ua.uzhnu.collab.enums.TaskStatus;
import ua.uzhnu.collab.enums.TeamRole;

/**
 * Фабрика тестових сутностей.
 *
 * <p>Дозволяє швидко зібрати валідну сутність із значеннями за замовчуванням, а потім перевизначити
 * лише цікаве полі через builder-методи. Це усуває шум "boilerplate setup" у тестах та робить їх
 * читабельнішими.
 *
 * <p>ID не задається — він присвоюється БД при {@code .save()}.
 */
public final class TestData {

  private TestData() {}

  public static User user(String username) {
    return User.builder()
        .username(username)
        .email(username + "@uzhnu.test")
        .passwordHash("$2a$10$dummyhash" + username) // не справжній BCrypt
        .fullName("Тестовий " + username)
        .build();
  }

  public static Team team(String name, User creator) {
    return Team.builder()
        .name(name)
        .description("Тестова команда " + name)
        .createdBy(creator)
        .build();
  }

  public static TeamMember membership(Team team, User user, TeamRole role) {
    return TeamMember.builder()
        .id(new TeamMemberId(team.getId(), user.getId()))
        .team(team)
        .user(user)
        .role(role)
        .build();
  }

  public static Task task(Team team, User creator, String title) {
    return Task.builder()
        .team(team)
        .title(title)
        .description("Опис " + title)
        .status(TaskStatus.TODO)
        .priority(TaskPriority.MEDIUM)
        .createdBy(creator)
        .build();
  }

  public static Task taskWithStatus(Team team, User creator, String title, TaskStatus status) {
    Task t = task(team, creator, title);
    t.setStatus(status);
    return t;
  }

  public static Task taskWithDueDate(Team team, User creator, String title, LocalDateTime due) {
    Task t = task(team, creator, title);
    t.setDueDate(due);
    return t;
  }

  public static TaskAssignee assignment(Task task, User user) {
    return TaskAssignee.builder()
        .id(new TaskAssigneeId(task.getId(), user.getId()))
        .task(task)
        .user(user)
        .build();
  }

  public static TaskComment comment(Task task, User author, String text) {
    return TaskComment.builder().task(task).user(author).content(text).build();
  }

  public static ChatMessage message(Team team, User author, String content) {
    return ChatMessage.builder().team(team).user(author).content(content).build();
  }

  public static ChatMessage reply(Team team, User author, String content, ChatMessage parent) {
    return ChatMessage.builder()
        .team(team)
        .user(author)
        .content(content)
        .parentMessage(parent)
        .build();
  }

  public static AppFile file(Team team, User uploader, String name, long size) {
    return AppFile.builder()
        .team(team)
        .uploadedBy(uploader)
        .fileName(name)
        .storagePath("storage/team_" + team.getId() + "/" + name)
        .fileSize(size)
        .mimeType("application/octet-stream")
        .build();
  }
}
