package ua.uzhnu.collab.service;

import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.uzhnu.collab.dto.Dtos.*;
import ua.uzhnu.collab.entity.*;
import ua.uzhnu.collab.enums.TaskPriority;
import ua.uzhnu.collab.enums.TaskStatus;
import ua.uzhnu.collab.exception.*;
import ua.uzhnu.collab.repository.*;

/**
 * Сервіс бізнес-логіки для сутностей {@link Task}, {@link TaskAssignee}, {@link TaskComment}.
 *
 * <p>Реалізує створення задач, зміну статусу через state machine, призначення виконавців,
 * коментування, пошук та фільтрацію.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class TaskService {

  private final TaskRepository taskRepository;
  private final TaskAssigneeRepository assigneeRepository;
  private final TaskCommentRepository commentRepository;
  private final AppFileRepository fileRepository;
  private final UserService userService;
  private final TeamService teamService;
  private final DtoMapper mapper;

  /**
   * Допустимі переходи стану задачі (state machine). Ключ — поточний стан, значення — множина
   * допустимих наступних станів.
   */
  private static final Map<TaskStatus, Set<TaskStatus>> VALID_TRANSITIONS =
      Map.of(
          TaskStatus.TODO, EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.DONE),
          TaskStatus.IN_PROGRESS, EnumSet.of(TaskStatus.TODO, TaskStatus.DONE),
          TaskStatus.DONE, EnumSet.of(TaskStatus.IN_PROGRESS));

  // =====================================================================
  // СТВОРЕННЯ ЗАДАЧІ
  // =====================================================================

  /** Створює задачу в команді з опціональним призначенням виконавців. */
  @Transactional
  public TaskDto create(TaskCreateDto dto, Long creatorId) {
    teamService.requireMember(dto.teamId(), creatorId);

    Team team = teamService.findTeamById(dto.teamId());
    User creator = userService.findById(creatorId);

    Task task =
        Task.builder()
            .team(team)
            .title(dto.title().trim())
            .description(dto.description())
            .priority(dto.priority() != null ? dto.priority() : TaskPriority.MEDIUM)
            .dueDate(dto.dueDate())
            .createdBy(creator)
            .build();

    task = taskRepository.save(task);

    // Призначення виконавців (якщо вказані)
    if (dto.assigneeIds() != null && !dto.assigneeIds().isEmpty()) {
      for (Long assigneeId : dto.assigneeIds()) {
        assignTaskInternal(task, assigneeId);
      }
    }

    log.info(
        "Створено задачу '{}' (id={}) у команді '{}'",
        task.getTitle(),
        task.getId(),
        team.getName());

    return toTaskDtoWithContext(task);
  }

  // =====================================================================
  // ЗМІНА СТАТУСУ (STATE MACHINE)
  // =====================================================================

  /**
   * Змінює статус задачі з валідацією допустимості переходу.
   *
   * @throws InvalidStateTransitionException якщо перехід недопустимий
   */
  @Transactional
  public TaskDto changeStatus(Long taskId, TaskStatus newStatus, Long userId) {
    Task task = findTaskById(taskId);
    teamService.requireMember(task.getTeam().getId(), userId);

    TaskStatus currentStatus = task.getStatus();
    if (currentStatus == newStatus) {
      return toTaskDtoWithContext(task); // немає змін
    }

    Set<TaskStatus> allowed = VALID_TRANSITIONS.get(currentStatus);
    if (allowed == null || !allowed.contains(newStatus)) {
      throw new InvalidStateTransitionException(currentStatus, newStatus);
    }

    task.setStatus(newStatus);
    task = taskRepository.save(task);
    log.info("Задача id={}: {} → {}", taskId, currentStatus, newStatus);

    return toTaskDtoWithContext(task);
  }

  // =====================================================================
  // ПРИЗНАЧЕННЯ ВИКОНАВЦІВ
  // =====================================================================

  /**
   * Призначає виконавця на задачу.
   *
   * @throws AccessDeniedException якщо виконавець не є учасником команди
   * @throws DuplicateEntityException якщо вже призначений
   */
  @Transactional
  public void assignUser(Long taskId, Long userId, Long initiatorId) {
    Task task = findTaskById(taskId);
    teamService.requireMember(task.getTeam().getId(), initiatorId);

    assignTaskInternal(task, userId);
    log.info("Користувача id={} призначено на задачу id={}", userId, taskId);
  }

  /** Знімає виконавця із задачі. */
  @Transactional
  public void unassignUser(Long taskId, Long userId, Long initiatorId) {
    Task task = findTaskById(taskId);
    teamService.requireMember(task.getTeam().getId(), initiatorId);

    if (!assigneeRepository.existsByIdTaskIdAndIdUserId(taskId, userId)) {
      throw new EntityNotFoundException("Призначення", userId);
    }
    assigneeRepository.deleteByIdTaskIdAndIdUserId(taskId, userId);
    log.info("Користувача id={} знято із задачі id={}", userId, taskId);
  }

  // =====================================================================
  // КОМЕНТАРІ
  // =====================================================================

  /** Додає коментар до задачі. */
  @Transactional
  public CommentDto addComment(CommentCreateDto dto, Long userId) {
    Task task = findTaskById(dto.taskId());
    teamService.requireMember(task.getTeam().getId(), userId);
    User user = userService.findById(userId);

    TaskComment comment =
        TaskComment.builder().task(task).user(user).content(dto.content().trim()).build();

    comment = commentRepository.save(comment);
    log.info("Додано коментар до задачі id={} від {}", task.getId(), user.getUsername());
    return mapper.toCommentDto(comment);
  }

  /** Коментарі задачі в хронологічному порядку. */
  public List<CommentDto> getComments(Long taskId) {
    return commentRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
        .map(mapper::toCommentDto)
        .toList();
  }

  // =====================================================================
  // ОНОВЛЕННЯ ЗАДАЧІ
  // =====================================================================

  /** Оновлює властивості задачі (назву, опис, пріоритет, дедлайн). */
  @Transactional
  public TaskDto update(
      Long taskId,
      String title,
      String description,
      TaskPriority priority,
      LocalDateTime dueDate,
      Long userId) {
    Task task = findTaskById(taskId);
    teamService.requireMember(task.getTeam().getId(), userId);

    if (title != null && !title.isBlank()) {
      task.setTitle(title.trim());
    }
    if (description != null) {
      task.setDescription(description);
    }
    if (priority != null) {
      task.setPriority(priority);
    }
    task.setDueDate(dueDate); // дозволяє скидання дедлайну в null

    task = taskRepository.save(task);
    log.info("Оновлено задачу id={}: '{}'", task.getId(), task.getTitle());
    return toTaskDtoWithContext(task);
  }

  /** Видаляє задачу (каскадно: виконавці, коментарі; файли — SET NULL). */
  @Transactional
  public void delete(Long taskId, Long userId) {
    Task task = findTaskById(taskId);
    teamService.requireAdminOrOwner(task.getTeam().getId(), userId);

    taskRepository.delete(task);
    log.info("Видалено задачу id={}: '{}'", taskId, task.getTitle());
  }

  // =====================================================================
  // ОТРИМАННЯ ТА ПОШУК
  // =====================================================================

  public TaskDto getById(Long taskId) {
    return toTaskDtoWithContext(findTaskById(taskId));
  }

  /** Усі задачі команди з повним контекстом. */
  public List<TaskDto> getByTeam(Long teamId) {
    return taskRepository.findByTeamIdOrderByCreatedAtDesc(teamId).stream()
        .map(this::toTaskDtoWithContext)
        .toList();
  }

  /** Задачі за статусом (для канбан-дошки). */
  public List<TaskDto> getByTeamAndStatus(Long teamId, TaskStatus status) {
    return taskRepository.findByTeamIdAndStatus(teamId, status).stream()
        .map(this::toTaskDtoWithContext)
        .toList();
  }

  /** Задачі, призначені конкретному користувачу (для особистого дашборду). */
  public List<TaskDto> getByAssignee(Long userId) {
    return taskRepository.findByAssigneeUserId(userId).stream()
        .map(this::toTaskDtoWithContext)
        .toList();
  }

  /** Прострочені задачі команди. */
  public List<TaskDto> getOverdue(Long teamId) {
    return taskRepository.findOverdueByTeam(teamId).stream()
        .map(this::toTaskDtoWithContext)
        .toList();
  }

  /** Пошук задач за текстом. */
  public List<TaskDto> search(Long teamId, String query) {
    return taskRepository.searchInTeam(teamId, query).stream()
        .map(this::toTaskDtoWithContext)
        .toList();
  }

  // =====================================================================
  // ВНУТРІШНІ МЕТОДИ
  // =====================================================================

  Task findTaskById(Long id) {
    return taskRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Задача", id));
  }

  private void assignTaskInternal(Task task, Long userId) {
    teamService.requireMember(task.getTeam().getId(), userId);

    if (assigneeRepository.existsByIdTaskIdAndIdUserId(task.getId(), userId)) {
      throw new DuplicateEntityException("Користувач вже призначений на цю задачу");
    }

    User user = userService.findById(userId);
    TaskAssignee assignee =
        TaskAssignee.builder()
            .id(new TaskAssigneeId(task.getId(), userId))
            .task(task)
            .user(user)
            .build();
    assigneeRepository.save(assignee);
  }

  /** Збирає повний контекст задачі з пов'язаних таблиць для DTO. */
  private TaskDto toTaskDtoWithContext(Task task) {
    List<String> assigneeNames =
        assigneeRepository.findByIdTaskId(task.getId()).stream()
            .map(a -> a.getUser().getFullName())
            .toList();

    int commentCount = (int) commentRepository.countByTaskId(task.getId());
    int fileCount = fileRepository.findByTaskId(task.getId()).size();

    return mapper.toTaskDto(task, assigneeNames, commentCount, fileCount);
  }
}
