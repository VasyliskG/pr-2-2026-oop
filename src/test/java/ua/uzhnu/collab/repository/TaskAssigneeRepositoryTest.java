package ua.uzhnu.collab.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.uzhnu.collab.TestData.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ua.uzhnu.collab.AbstractRepositoryIntegrationTest;
import ua.uzhnu.collab.entity.*;
import ua.uzhnu.collab.enums.TeamRole;

@DisplayName("TaskAssigneeRepository — інтеграційний тест")
class TaskAssigneeRepositoryTest extends AbstractRepositoryIntegrationTest {

  @Autowired private TaskAssigneeRepository assigneeRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private TeamMemberRepository memberRepository;
  @Autowired private TaskRepository taskRepository;

  private User creator;
  private User assignee;
  private Team team;
  private Task taskA;
  private Task taskB;

  @BeforeEach
  void setUp() {
    creator = userRepository.save(user("creator"));
    assignee = userRepository.save(user("assignee"));
    team = teamRepository.save(team("Team", creator));
    memberRepository.save(membership(team, creator, TeamRole.OWNER));
    memberRepository.save(membership(team, assignee, TeamRole.MEMBER));
    taskA = taskRepository.save(task(team, creator, "Task A"));
    taskB = taskRepository.save(task(team, creator, "Task B"));
  }

  @Test
  @DisplayName("save + findByIdTaskId: знаходить виконавців задачі")
  void findByIdTaskId_returnsAssigneesOfTask() {
    assigneeRepository.save(assignment(taskA, assignee));

    var result = assigneeRepository.findByIdTaskId(taskA.getId());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getUser().getId()).isEqualTo(assignee.getId());
  }

  @Test
  @DisplayName("findByIdUserId: усі задачі, призначені користувачу")
  void findByIdUserId_returnsAllTasksOfUser() {
    assigneeRepository.save(assignment(taskA, assignee));
    assigneeRepository.save(assignment(taskB, assignee));

    var result = assigneeRepository.findByIdUserId(assignee.getId());

    assertThat(result).hasSize(2);
  }

  @Test
  @DisplayName("existsByIdTaskIdAndIdUserId: true/false")
  void existsByIdTaskIdAndIdUserId_works() {
    assigneeRepository.save(assignment(taskA, assignee));

    assertThat(assigneeRepository.existsByIdTaskIdAndIdUserId(taskA.getId(), assignee.getId()))
        .isTrue();
    assertThat(assigneeRepository.existsByIdTaskIdAndIdUserId(taskB.getId(), assignee.getId()))
        .isFalse();
  }

  @Test
  @DisplayName("deleteByIdTaskIdAndIdUserId: знімає виконавця із задачі")
  void deleteByIdTaskIdAndIdUserId_removes() {
    assigneeRepository.save(assignment(taskA, assignee));

    assigneeRepository.deleteByIdTaskIdAndIdUserId(taskA.getId(), assignee.getId());

    assertThat(assigneeRepository.existsByIdTaskIdAndIdUserId(taskA.getId(), assignee.getId()))
        .isFalse();
  }
}
