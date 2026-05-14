package ua.uzhnu.collab.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.uzhnu.collab.TestData.*;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ua.uzhnu.collab.AbstractRepositoryIntegrationTest;
import ua.uzhnu.collab.entity.*;
import ua.uzhnu.collab.enums.TaskPriority;
import ua.uzhnu.collab.enums.TaskStatus;

@DisplayName("TaskRepository — інтеграційний тест")
class TaskRepositoryTest extends AbstractRepositoryIntegrationTest {

  @Autowired private TaskRepository taskRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private TaskAssigneeRepository assigneeRepository;

  private User creator;
  private Team team;

  @BeforeEach
  void setUp() {
    creator = userRepository.save(user("creator"));
    team = teamRepository.save(team("TestTeam", creator));
  }

  // =====================================================================
  // CRUD
  // =====================================================================

  @Test
  @DisplayName("save: створює задачу з default-значеннями TODO + MEDIUM")
  void save_assignsDefaultStatusAndPriority() {
    Task saved = taskRepository.save(task(team, creator, "First task"));

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getStatus()).isEqualTo(TaskStatus.TODO);
    assertThat(saved.getPriority()).isEqualTo(TaskPriority.MEDIUM);
    assertThat(saved.getCreatedAt()).isNotNull();
  }

  // =====================================================================
  // findByTeamIdOrderByCreatedAtDesc
  // =====================================================================

  @Test
  @DisplayName("findByTeamId...DESC: повертає задачі команди в зворотному хронологічному порядку")
  void findByTeamIdOrderByCreatedAtDesc_works() throws InterruptedException {
    Task t1 = taskRepository.save(task(team, creator, "First"));
    Thread.sleep(5);
    Task t2 = taskRepository.save(task(team, creator, "Second"));
    Thread.sleep(5);
    Task t3 = taskRepository.save(task(team, creator, "Third"));

    List<Task> tasks = taskRepository.findByTeamIdOrderByCreatedAtDesc(team.getId());

    assertThat(tasks).extracting(Task::getId).containsExactly(t3.getId(), t2.getId(), t1.getId());
  }

  @Test
  @DisplayName("findByTeamId...: задачі іншої команди НЕ потрапляють у результат")
  void findByTeamIdOrderByCreatedAtDesc_doesNotLeakOtherTeams() {
    Team otherTeam = teamRepository.save(team("Other", creator));

    taskRepository.save(task(team, creator, "Mine"));
    taskRepository.save(task(otherTeam, creator, "Foreign"));

    List<Task> tasks = taskRepository.findByTeamIdOrderByCreatedAtDesc(team.getId());

    assertThat(tasks).extracting(Task::getTitle).containsExactly("Mine");
  }

  // =====================================================================
  // findByTeamIdAndStatus / countByTeamIdAndStatus
  // =====================================================================

  @Test
  @DisplayName("findByTeamIdAndStatus: фільтрує задачі за статусом")
  void findByTeamIdAndStatus_filtersCorrectly() {
    taskRepository.save(taskWithStatus(team, creator, "T1", TaskStatus.TODO));
    taskRepository.save(taskWithStatus(team, creator, "T2", TaskStatus.IN_PROGRESS));
    taskRepository.save(taskWithStatus(team, creator, "T3", TaskStatus.IN_PROGRESS));
    taskRepository.save(taskWithStatus(team, creator, "T4", TaskStatus.DONE));

    assertThat(taskRepository.findByTeamIdAndStatus(team.getId(), TaskStatus.TODO)).hasSize(1);
    assertThat(taskRepository.findByTeamIdAndStatus(team.getId(), TaskStatus.IN_PROGRESS))
        .hasSize(2);
    assertThat(taskRepository.findByTeamIdAndStatus(team.getId(), TaskStatus.DONE)).hasSize(1);
  }

  @Test
  @DisplayName("countByTeamIdAndStatus: повертає кількість за статусом")
  void countByTeamIdAndStatus_returnsCorrectCount() {
    taskRepository.save(taskWithStatus(team, creator, "T1", TaskStatus.TODO));
    taskRepository.save(taskWithStatus(team, creator, "T2", TaskStatus.TODO));
    taskRepository.save(taskWithStatus(team, creator, "T3", TaskStatus.DONE));

    assertThat(taskRepository.countByTeamIdAndStatus(team.getId(), TaskStatus.TODO)).isEqualTo(2L);
    assertThat(taskRepository.countByTeamIdAndStatus(team.getId(), TaskStatus.DONE)).isEqualTo(1L);
    assertThat(taskRepository.countByTeamIdAndStatus(team.getId(), TaskStatus.IN_PROGRESS))
        .isZero();
  }

  // =====================================================================
  // findByTeamIdAndPriority
  // =====================================================================

  @Test
  @DisplayName("findByTeamIdAndPriority: фільтрує за пріоритетом")
  void findByTeamIdAndPriority_filtersCorrectly() {
    Task critical = task(team, creator, "Urgent");
    critical.setPriority(TaskPriority.CRITICAL);
    taskRepository.save(critical);
    taskRepository.save(task(team, creator, "Normal"));

    List<Task> urgent = taskRepository.findByTeamIdAndPriority(team.getId(), TaskPriority.CRITICAL);
    assertThat(urgent).hasSize(1).first().extracting(Task::getTitle).isEqualTo("Urgent");
  }

  // =====================================================================
  // findOverdueByTeam
  // =====================================================================

  @Test
  @DisplayName("findOverdueByTeam: повертає тільки прострочені (не-DONE з dueDate у минулому)")
  void findOverdueByTeam_returnsOnlyTrulyOverdue() {
    LocalDateTime past = LocalDateTime.now().minusDays(1);
    LocalDateTime future = LocalDateTime.now().plusDays(1);

    Task overdue = taskWithDueDate(team, creator, "Overdue", past);
    Task futureTsk = taskWithDueDate(team, creator, "Future", future);
    Task doneOverdue = taskWithDueDate(team, creator, "DoneOverdue", past);
    doneOverdue.setStatus(TaskStatus.DONE);
    Task noDueDate = task(team, creator, "NoDue");

    taskRepository.save(overdue);
    taskRepository.save(futureTsk);
    taskRepository.save(doneOverdue);
    taskRepository.save(noDueDate);

    List<Task> result = taskRepository.findOverdueByTeam(team.getId());

    assertThat(result).extracting(Task::getTitle).containsExactly("Overdue");
  }

  // =====================================================================
  // findUpcomingByTeam
  // =====================================================================

  @Test
  @DisplayName("findUpcomingByTeam: повертає не-DONE задачі з дедлайном у діапазоні")
  void findUpcomingByTeam_returnsTasksInDateRange() {
    LocalDateTime now = LocalDateTime.now();

    taskRepository.save(taskWithDueDate(team, creator, "Tomorrow", now.plusDays(1)));
    taskRepository.save(taskWithDueDate(team, creator, "InThreeDays", now.plusDays(3)));
    taskRepository.save(taskWithDueDate(team, creator, "InTenDays", now.plusDays(10)));

    Task doneTomorrow = taskWithDueDate(team, creator, "DoneTomorrow", now.plusDays(1));
    doneTomorrow.setStatus(TaskStatus.DONE);
    taskRepository.save(doneTomorrow);

    List<Task> upcoming = taskRepository.findUpcomingByTeam(team.getId(), now, now.plusDays(7));

    assertThat(upcoming).extracting(Task::getTitle).containsExactly("Tomorrow", "InThreeDays");
  }

  // =====================================================================
  // searchInTeam
  // =====================================================================

  @Test
  @DisplayName("searchInTeam: знаходить за фрагментом у назві (case-insensitive)")
  void searchInTeam_byTitle_caseInsensitive() {
    taskRepository.save(task(team, creator, "Implement Login Form"));
    taskRepository.save(task(team, creator, "Add Settings Page"));

    List<Task> found = taskRepository.searchInTeam(team.getId(), "LOGIN");

    assertThat(found).extracting(Task::getTitle).containsExactly("Implement Login Form");
  }

  @Test
  @DisplayName("searchInTeam: знаходить за фрагментом в описі")
  void searchInTeam_byDescription() {
    Task t = task(team, creator, "Some task");
    t.setDescription("This contains the word AUTHENTICATION");
    taskRepository.save(t);
    taskRepository.save(task(team, creator, "Other task"));

    List<Task> found = taskRepository.searchInTeam(team.getId(), "authentication");

    assertThat(found).hasSize(1).first().extracting(Task::getTitle).isEqualTo("Some task");
  }

  // =====================================================================
  // findByAssigneeUserId
  // =====================================================================

  @Test
  @DisplayName("findByAssigneeUserId: повертає лише задачі, призначені користувачу")
  void findByAssigneeUserId_returnsAssignedTasks() {
    User assignee = userRepository.save(user("alice"));

    Task assigned = taskRepository.save(task(team, creator, "AssignedToAlice"));
    Task unassigned = taskRepository.save(task(team, creator, "Unassigned"));

    assigneeRepository.save(assignment(assigned, assignee));

    List<Task> aliceTasks = taskRepository.findByAssigneeUserId(assignee.getId());

    assertThat(aliceTasks)
        .extracting(Task::getTitle)
        .containsExactly("AssignedToAlice")
        .doesNotContain("Unassigned");
  }

  // =====================================================================
  // findByAssigneeUserIdAndTeamId
  // =====================================================================

  @Test
  @DisplayName("findByAssigneeUserIdAndTeamId: повертає лише задачі призначеного користувача в конкретній команді")
  void findByAssigneeUserIdAndTeamId_excludesOtherTeams() {
    User assignee = userRepository.save(user("assignee-filter"));
    Team otherTeam = teamRepository.save(team("Other", creator));

    Task inTeam = taskRepository.save(task(team, creator, "In team"));
    Task inOther = taskRepository.save(task(otherTeam, creator, "In other"));

    assigneeRepository.save(assignment(inTeam, assignee));
    assigneeRepository.save(assignment(inOther, assignee));

    List<Task> result =
        taskRepository.findByAssigneeUserIdAndTeamId(assignee.getId(), team.getId());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo(inTeam.getId());
  }
}
