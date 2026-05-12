package ua.uzhnu.collab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.uzhnu.collab.dto.Dtos.TaskCreateDto;
import ua.uzhnu.collab.dto.Dtos.TaskDto;
import ua.uzhnu.collab.entity.*;
import ua.uzhnu.collab.enums.TaskPriority;
import ua.uzhnu.collab.enums.TaskStatus;
import ua.uzhnu.collab.exception.*;
import ua.uzhnu.collab.repository.*;

/**
 * Юніт-тести {@link TaskService}.
 *
 * <p>Особлива увага — на state machine допустимих переходів статусів: TODO → IN_PROGRESS/DONE,
 * IN_PROGRESS → TODO/DONE, DONE → IN_PROGRESS (тільки).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskService — юніт-тест")
class TaskServiceTest {

  @Mock private TaskRepository taskRepository;
  @Mock private TaskAssigneeRepository assigneeRepository;
  @Mock private TaskCommentRepository commentRepository;
  @Mock private AppFileRepository fileRepository;
  @Mock private UserService userService;
  @Mock private TeamService teamService;
  @Spy private DtoMapper mapper = new DtoMapper();

  @InjectMocks private TaskService taskService;

  private User creator;
  private Team team;
  private Task baseTask;

  @BeforeEach
  void setUp() {
    creator =
        User.builder()
            .id(1L)
            .username("creator")
            .fullName("Creator")
            .email("c@x")
            .passwordHash("x")
            .build();
    team = Team.builder().id(10L).name("Team").createdBy(creator).build();
    baseTask =
        Task.builder()
            .id(100L)
            .team(team)
            .title("T")
            .description("d")
            .status(TaskStatus.TODO)
            .priority(TaskPriority.MEDIUM)
            .createdBy(creator)
            .build();
  }

  // =====================================================================
  // CREATE
  // =====================================================================

  @Nested
  @DisplayName("create()")
  class CreateTests {

    @Test
    @DisplayName("створення задачі без виконавців — успіх, дефолтний пріоритет MEDIUM якщо null")
    void create_withoutAssignees() {
      TaskCreateDto dto =
          new TaskCreateDto(
              10L,
              "New task",
              "Some description",
              null, // priority — null, має стати MEDIUM
              null,
              null);

      doNothing().when(teamService).requireMember(10L, 1L);
      when(teamService.findTeamById(10L)).thenReturn(team);
      when(userService.findById(1L)).thenReturn(creator);
      when(taskRepository.save(any(Task.class)))
          .thenAnswer(
              inv -> {
                Task t = inv.getArgument(0);
                t.setId(50L);
                return t;
              });
      when(assigneeRepository.findByIdTaskId(50L)).thenReturn(Collections.emptyList());
      when(commentRepository.countByTaskId(50L)).thenReturn(0L);
      when(fileRepository.findByTaskId(50L)).thenReturn(Collections.emptyList());

      TaskDto result = taskService.create(dto, 1L);

      assertThat(result.id()).isEqualTo(50L);
      assertThat(result.priority()).isEqualTo(TaskPriority.MEDIUM);
      verify(assigneeRepository, never()).save(any());
    }

    @Test
    @DisplayName("не учасник команди — AccessDeniedException, save не викликається")
    void create_byNonMember_throws() {
      TaskCreateDto dto = new TaskCreateDto(10L, "T", "d", null, null, null);

      doThrow(new AccessDeniedException("не учасник")).when(teamService).requireMember(10L, 1L);

      assertThatThrownBy(() -> taskService.create(dto, 1L))
          .isInstanceOf(AccessDeniedException.class);

      verify(taskRepository, never()).save(any());
    }
  }

  // =====================================================================
  // STATE MACHINE — допустимі та недопустимі переходи
  // =====================================================================

  @Nested
  @DisplayName("changeStatus() — state machine")
  class StatusTransitionTests {

    @Test
    @DisplayName("TODO → IN_PROGRESS — дозволено")
    void todo_toInProgress_allowed() {
      baseTask.setStatus(TaskStatus.TODO);
      mockTaskFlow();

      TaskDto result = taskService.changeStatus(100L, TaskStatus.IN_PROGRESS, 1L);

      assertThat(result.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("TODO → DONE — дозволено (можна одразу закрити)")
    void todo_toDone_allowed() {
      baseTask.setStatus(TaskStatus.TODO);
      mockTaskFlow();

      TaskDto result = taskService.changeStatus(100L, TaskStatus.DONE, 1L);

      assertThat(result.status()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    @DisplayName("IN_PROGRESS → TODO — дозволено (можна повернути)")
    void inProgress_toTodo_allowed() {
      baseTask.setStatus(TaskStatus.IN_PROGRESS);
      mockTaskFlow();

      taskService.changeStatus(100L, TaskStatus.TODO, 1L);

      verify(taskRepository).save(argThat(t -> t.getStatus() == TaskStatus.TODO));
    }

    @Test
    @DisplayName("IN_PROGRESS → DONE — дозволено")
    void inProgress_toDone_allowed() {
      baseTask.setStatus(TaskStatus.IN_PROGRESS);
      mockTaskFlow();

      taskService.changeStatus(100L, TaskStatus.DONE, 1L);

      verify(taskRepository).save(argThat(t -> t.getStatus() == TaskStatus.DONE));
    }

    @Test
    @DisplayName("DONE → IN_PROGRESS — дозволено (можна відкрити задачу)")
    void done_toInProgress_allowed() {
      baseTask.setStatus(TaskStatus.DONE);
      mockTaskFlow();

      taskService.changeStatus(100L, TaskStatus.IN_PROGRESS, 1L);

      verify(taskRepository).save(argThat(t -> t.getStatus() == TaskStatus.IN_PROGRESS));
    }

    @Test
    @DisplayName("DONE → TODO — ЗАБОРОНЕНО (треба через IN_PROGRESS)")
    void done_toTodo_disallowed() {
      baseTask.setStatus(TaskStatus.DONE);
      when(taskRepository.findById(100L)).thenReturn(Optional.of(baseTask));
      doNothing().when(teamService).requireMember(10L, 1L);

      assertThatThrownBy(() -> taskService.changeStatus(100L, TaskStatus.TODO, 1L))
          .isInstanceOf(InvalidStateTransitionException.class)
          .hasMessageContaining("DONE")
          .hasMessageContaining("TODO");

      verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("той самий статус (no-op) — НЕ викликає save, повертає DTO")
    void sameStatus_isNoOp() {
      baseTask.setStatus(TaskStatus.TODO);
      when(taskRepository.findById(100L)).thenReturn(Optional.of(baseTask));
      doNothing().when(teamService).requireMember(10L, 1L);
      when(assigneeRepository.findByIdTaskId(100L)).thenReturn(Collections.emptyList());
      when(commentRepository.countByTaskId(100L)).thenReturn(0L);
      when(fileRepository.findByTaskId(100L)).thenReturn(Collections.emptyList());

      taskService.changeStatus(100L, TaskStatus.TODO, 1L);

      verify(taskRepository, never()).save(any());
    }

    // Допоміжний метод для повного циклу changeStatus
    private void mockTaskFlow() {
      when(taskRepository.findById(100L)).thenReturn(Optional.of(baseTask));
      doNothing().when(teamService).requireMember(10L, 1L);
      when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
      when(assigneeRepository.findByIdTaskId(100L)).thenReturn(Collections.emptyList());
      when(commentRepository.countByTaskId(100L)).thenReturn(0L);
      when(fileRepository.findByTaskId(100L)).thenReturn(Collections.emptyList());
    }
  }

  // =====================================================================
  // ASSIGNMENT
  // =====================================================================

  @Nested
  @DisplayName("assignUser() / unassignUser()")
  class AssignmentTests {

    @Test
    @DisplayName("призначення нового виконавця — успіх")
    void assignUser_new_succeeds() {
      User assignee =
          User.builder().id(2L).username("a").fullName("A").email("a@x").passwordHash("x").build();

      when(taskRepository.findById(100L)).thenReturn(Optional.of(baseTask));
      doNothing().when(teamService).requireMember(10L, 1L); // initiator
      doNothing().when(teamService).requireMember(10L, 2L); // assignee теж учасник
      when(assigneeRepository.existsByIdTaskIdAndIdUserId(100L, 2L)).thenReturn(false);
      when(userService.findById(2L)).thenReturn(assignee);

      taskService.assignUser(100L, 2L, 1L);

      verify(assigneeRepository).save(any(TaskAssignee.class));
    }

    @Test
    @DisplayName("повторне призначення того ж виконавця — DuplicateEntityException")
    void assignUser_alreadyAssigned_throws() {
      when(taskRepository.findById(100L)).thenReturn(Optional.of(baseTask));
      doNothing().when(teamService).requireMember(10L, 1L);
      doNothing().when(teamService).requireMember(10L, 2L);
      when(assigneeRepository.existsByIdTaskIdAndIdUserId(100L, 2L)).thenReturn(true);

      assertThatThrownBy(() -> taskService.assignUser(100L, 2L, 1L))
          .isInstanceOf(DuplicateEntityException.class);

      verify(assigneeRepository, never()).save(any());
    }

    @Test
    @DisplayName("призначення користувача який не у команді — AccessDeniedException")
    void assignUser_notTeamMember_throws() {
      when(taskRepository.findById(100L)).thenReturn(Optional.of(baseTask));
      doNothing().when(teamService).requireMember(10L, 1L);
      doThrow(new AccessDeniedException("not member"))
          .when(teamService)
          .requireMember(10L, 99L); // 99 — не учасник

      assertThatThrownBy(() -> taskService.assignUser(100L, 99L, 1L))
          .isInstanceOf(AccessDeniedException.class);

      verify(assigneeRepository, never()).save(any());
    }

    @Test
    @DisplayName("unassignUser: знімає виконавця коли призначення існує")
    void unassignUser_existing_succeeds() {
      when(taskRepository.findById(100L)).thenReturn(Optional.of(baseTask));
      doNothing().when(teamService).requireMember(10L, 1L);
      when(assigneeRepository.existsByIdTaskIdAndIdUserId(100L, 2L)).thenReturn(true);

      taskService.unassignUser(100L, 2L, 1L);

      verify(assigneeRepository).deleteByIdTaskIdAndIdUserId(100L, 2L);
    }

    @Test
    @DisplayName("unassignUser: коли призначення немає — EntityNotFoundException")
    void unassignUser_nonExisting_throws() {
      when(taskRepository.findById(100L)).thenReturn(Optional.of(baseTask));
      doNothing().when(teamService).requireMember(10L, 1L);
      when(assigneeRepository.existsByIdTaskIdAndIdUserId(100L, 2L)).thenReturn(false);

      assertThatThrownBy(() -> taskService.unassignUser(100L, 2L, 1L))
          .isInstanceOf(EntityNotFoundException.class);
    }
  }

  // =====================================================================
  // DELETE — потрібна роль ADMIN/OWNER
  // =====================================================================

  @Test
  @DisplayName("delete: вимагає роль ADMIN або OWNER")
  void delete_callsRequireAdminOrOwner() {
    when(taskRepository.findById(100L)).thenReturn(Optional.of(baseTask));
    doNothing().when(teamService).requireAdminOrOwner(10L, 1L);

    taskService.delete(100L, 1L);

    verify(teamService).requireAdminOrOwner(10L, 1L);
    verify(taskRepository).delete(baseTask);
  }

  @Test
  @DisplayName("delete без права — AccessDeniedException")
  void delete_byNonPrivileged_throws() {
    when(taskRepository.findById(100L)).thenReturn(Optional.of(baseTask));
    doThrow(new AccessDeniedException("no rights")).when(teamService).requireAdminOrOwner(10L, 1L);

    assertThatThrownBy(() -> taskService.delete(100L, 1L))
        .isInstanceOf(AccessDeniedException.class);

    verify(taskRepository, never()).delete(any());
  }

  // =====================================================================
  // FIND BY ID — обробка not found
  // =====================================================================

  @Test
  @DisplayName("getById: невідома задача — EntityNotFoundException")
  void getById_notFound_throws() {
    when(taskRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> taskService.getById(999L)).isInstanceOf(EntityNotFoundException.class);
  }

  // =====================================================================
  // SEARCH
  // =====================================================================

  @Test
  @DisplayName("search: делегує до taskRepository.searchInTeam")
  void search_delegatesToRepository() {
    when(taskRepository.searchInTeam(10L, "login")).thenReturn(List.of(baseTask));
    when(assigneeRepository.findByIdTaskId(100L)).thenReturn(Collections.emptyList());
    when(commentRepository.countByTaskId(100L)).thenReturn(0L);
    when(fileRepository.findByTaskId(100L)).thenReturn(Collections.emptyList());

    List<TaskDto> result = taskService.search(10L, "login");

    assertThat(result).hasSize(1);
    verify(taskRepository).searchInTeam(10L, "login");
  }
}
