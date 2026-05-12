package ua.uzhnu.collab.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.uzhnu.collab.TestData.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ua.uzhnu.collab.AbstractRepositoryIntegrationTest;
import ua.uzhnu.collab.entity.*;

@DisplayName("TaskCommentRepository — інтеграційний тест")
class TaskCommentRepositoryTest extends AbstractRepositoryIntegrationTest {

  @Autowired private TaskCommentRepository commentRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private TaskRepository taskRepository;

  private User author;
  private Task task;

  @BeforeEach
  void setUp() {
    author = userRepository.save(user("author"));
    Team team = teamRepository.save(team("Team", author));
    task = taskRepository.save(task(team, author, "T1"));
  }

  @Test
  @DisplayName("save: створює коментар з created_at")
  void save_persistsComment() {
    TaskComment saved = commentRepository.save(comment(task, author, "Hello world"));

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getContent()).isEqualTo("Hello world");
  }

  @Test
  @DisplayName("findByTaskIdOrderByCreatedAtAsc: повертає коментарі у хронологічному порядку")
  void findByTaskIdOrderByCreatedAtAsc_chronological() throws InterruptedException {
    commentRepository.save(comment(task, author, "First"));
    Thread.sleep(5);
    commentRepository.save(comment(task, author, "Second"));
    Thread.sleep(5);
    commentRepository.save(comment(task, author, "Third"));

    List<TaskComment> result = commentRepository.findByTaskIdOrderByCreatedAtAsc(task.getId());

    assertThat(result)
        .extracting(TaskComment::getContent)
        .containsExactly("First", "Second", "Third");
  }

  @Test
  @DisplayName("countByTaskId: рахує коментарі задачі")
  void countByTaskId_correct() {
    commentRepository.save(comment(task, author, "C1"));
    commentRepository.save(comment(task, author, "C2"));
    commentRepository.save(comment(task, author, "C3"));

    assertThat(commentRepository.countByTaskId(task.getId())).isEqualTo(3L);
  }
}
