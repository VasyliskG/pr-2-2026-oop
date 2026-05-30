package ua.uzhnu.collab.viewmodel;

import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ua.uzhnu.collab.config.SessionContext;
import ua.uzhnu.collab.dto.Dtos.*;
import ua.uzhnu.collab.enums.TaskStatus;
import ua.uzhnu.collab.service.*;

/**
 * ViewModel для екрану команди — управління задачами, чатом, файлами.
 *
 * <p>Містить окремі списки задач за статусами для канбан-дошки, повідомлення чату, список файлів та
 * учасників.
 *
 * <p>Scope = singleton: інакше {@code DashboardController} і {@code TeamViewControllerV2}
 * отримували б РІЗНІ екземпляри (prototype), і встановлене з dashboard {@code teamId} було б
 * невидиме з team-екрана. Стан перезаписується у {@link #setTeamId(Long)} при кожному відкритті
 * команди, тому єдиний екземпляр на всю сесію — безпечний.
 */
@Component
@RequiredArgsConstructor
public class TeamViewModel {

  private final TaskService taskService;
  private final ChatService chatService;
  private final FileService fileService;
  private final TeamService teamService;
  private final SessionContext sessionContext;

  // ---- Властивості ----

  private final ObjectProperty<TeamDto> team = new SimpleObjectProperty<>();
  private final StringProperty teamName = new SimpleStringProperty("");
  private final BooleanProperty loading = new SimpleBooleanProperty(false);

  // Канбан-дошка: три колонки
  private final ObservableList<TaskDto> todoTasks = FXCollections.observableArrayList();
  private final ObservableList<TaskDto> inProgressTasks = FXCollections.observableArrayList();
  private final ObservableList<TaskDto> doneTasks = FXCollections.observableArrayList();

  // Чат
  private final ObservableList<ChatMessageDto> chatMessages = FXCollections.observableArrayList();
  private final StringProperty chatInput = new SimpleStringProperty("");

  // Файли та учасники
  private final ObservableList<FileDto> files = FXCollections.observableArrayList();
  private final ObservableList<TeamMemberDto> members = FXCollections.observableArrayList();
  private final ObservableList<UserWorkloadDto> memberWorkloads = FXCollections.observableArrayList();
  // Календар дедлайнів — список задач для обраної дати
  private final ObservableList<TaskDto> calendarTasks = FXCollections.observableArrayList();

  // Статистика
  private final ObjectProperty<TeamStatsDto> stats = new SimpleObjectProperty<>();

  // ---- Геттери ----

  public ObjectProperty<TeamDto> teamProperty() {
    return team;
  }

  public StringProperty teamNameProperty() {
    return teamName;
  }

  public BooleanProperty loadingProperty() {
    return loading;
  }

  public ObservableList<TaskDto> getTodoTasks() {
    return todoTasks;
  }

  public ObservableList<TaskDto> getInProgressTasks() {
    return inProgressTasks;
  }

  public ObservableList<TaskDto> getDoneTasks() {
    return doneTasks;
  }

  public ObservableList<ChatMessageDto> getChatMessages() {
    return chatMessages;
  }

  public StringProperty chatInputProperty() {
    return chatInput;
  }

  public ObservableList<FileDto> getFiles() {
    return files;
  }

  public ObservableList<TeamMemberDto> getMembers() {
    return members;
  }

  public ObservableList<UserWorkloadDto> getMemberWorkloads() {
    return memberWorkloads;
  }

  public ObservableList<TaskDto> getCalendarTasks() {
    return calendarTasks;
  }

  public ObjectProperty<TeamStatsDto> statsProperty() {
    return stats;
  }

  // ---- Ініціалізація ----

  public void setTeamId(Long teamId) {
    TeamDto t = teamService.getById(teamId);
    team.set(t);
    teamName.set(t.name());
  }

  /** Завантажує всі дані команди (задачі, чат, файли, учасники). */
  public Task<Void> createLoadAllTask() {
    Long teamId = team.get().id();
    Long userId = sessionContext.getCurrentUserId();

    return new Task<>() {
      @Override
      protected Void call() {
        List<TaskDto> allTasks = taskService.getByTeam(teamId);
        List<ChatMessageDto> msgs = chatService.getRecentMessages(teamId, 0);
        List<FileDto> teamFiles = fileService.getByTeam(teamId);
        List<TeamMemberDto> teamMembers = teamService.getMembers(teamId);
        List<UserWorkloadDto> workloads = teamService.getMemberWorkloads(teamId);
        TeamStatsDto teamStats = teamService.getStatistics(teamId);

        // Оновлення UI у потоці JavaFX
        javafx.application.Platform.runLater(
            () -> {
              todoTasks.setAll(
                  allTasks.stream().filter(t -> t.status() == TaskStatus.TODO).toList());
              inProgressTasks.setAll(
                  allTasks.stream().filter(t -> t.status() == TaskStatus.IN_PROGRESS).toList());
              doneTasks.setAll(
                  allTasks.stream().filter(t -> t.status() == TaskStatus.DONE).toList());

              chatMessages.setAll(msgs);
              files.setAll(teamFiles);
              members.setAll(teamMembers);
              memberWorkloads.setAll(workloads);
              stats.set(teamStats);
            });

        return null;
      }
    };
  }

  // ---- Операції з задачами ----

  /** Створює задачу та додає до відповідної колонки канбану. */
  public Task<TaskDto> createTaskAction(TaskCreateDto dto) {
    Long userId = sessionContext.getCurrentUserId();
    return new Task<>() {
      @Override
      protected TaskDto call() {
        return taskService.create(dto, userId);
      }
    };
  }

  public void onTaskCreated(TaskDto task) {
    todoTasks.add(task);
  }

  /** Змінює статус задачі та переміщує між колонками канбану. */
  public Task<TaskDto> changeStatusAction(Long taskId, TaskStatus newStatus) {
    Long userId = sessionContext.getCurrentUserId();
    return new Task<>() {
      @Override
      protected TaskDto call() {
        return taskService.changeStatus(taskId, newStatus, userId);
      }
    };
  }

  public void onStatusChanged(TaskDto updated) {
    // Видаляємо з усіх колонок
    todoTasks.removeIf(t -> t.id().equals(updated.id()));
    inProgressTasks.removeIf(t -> t.id().equals(updated.id()));
    doneTasks.removeIf(t -> t.id().equals(updated.id()));

    // Додаємо до правильної
    switch (updated.status()) {
      case TODO -> todoTasks.add(updated);
      case IN_PROGRESS -> inProgressTasks.add(updated);
      case DONE -> doneTasks.add(updated);
    }
  }

  /** Видаляє задачу з БД через TaskService. */
  public Task<Void> deleteTaskAction(Long taskId) {
    Long userId = sessionContext.getCurrentUserId();
    return new Task<>() {
      @Override
      protected Void call() {
        taskService.delete(taskId, userId);
        return null;
      }
    };
  }

  /** Видаляє задачу з усіх колонок канбану після успішного видалення з БД. */
  public void onTaskDeleted(Long taskId) {
    todoTasks.removeIf(t -> t.id().equals(taskId));
    inProgressTasks.removeIf(t -> t.id().equals(taskId));
    doneTasks.removeIf(t -> t.id().equals(taskId));
  }

  // ---- Операції з чатом ----

  /** Надсилає повідомлення в чат. */
  public Task<ChatMessageDto> sendMessageAction(Long parentId) {
    Long teamId = team.get().id();
    Long userId = sessionContext.getCurrentUserId();
    String content = chatInput.get().trim();

    return new Task<>() {
      @Override
      protected ChatMessageDto call() {
        return chatService.sendMessage(new ChatMessageCreateDto(teamId, content, parentId), userId);
      }
    };
  }

  public void onMessageSent(ChatMessageDto msg) {
    // нове повідомлення вгорі списку
    chatMessages.add(0, msg);
    chatInput.set("");
  }

  /** Завантажує задачі для обраної дати (00:00 — 23:59). */
  public Task<List<TaskDto>> loadTasksForDateAction(LocalDate date) {
    return loadTasksForRangeAction(date, date);
  }

  /** Завантажує задачі для діапазону дат включно. */
  public Task<List<TaskDto>> loadTasksForRangeAction(LocalDate fromDate, LocalDate toDate) {
    Long teamId = team.get().id();
    LocalDateTime from = fromDate.atStartOfDay();
    LocalDateTime to = toDate.atTime(23, 59, 59);
    return new Task<>() {
      @Override
      protected List<TaskDto> call() {
        return taskService.getUpcomingForTeamBetween(teamId, from, to);
      }
    };
  }

  // ---- Пошук задач ----

  /** Фільтрує задачі за текстом. */
  public Task<List<TaskDto>> searchTasksAction(String query) {
    Long teamId = team.get().id();
    return new Task<>() {
      @Override
      protected List<TaskDto> call() {
        return taskService.search(teamId, query);
      }
    };
  }

  /** Пошук файлів у команді за назвою. */
  public Task<List<FileDto>> searchFilesAction(String query) {
    Long teamId = team.get().id();
    return new Task<>() {
      @Override
      protected List<FileDto> call() {
        return fileService.searchFiles(teamId, query);
      }
    };
  }
}
