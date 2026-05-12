package ua.uzhnu.collab;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import ua.uzhnu.collab.repository.*;

@JdbcTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
@Import({
  UserRepository.class,
  TeamRepository.class,
  TeamMemberRepository.class,
  TaskRepository.class,
  TaskAssigneeRepository.class,
  TaskCommentRepository.class,
  ChatMessageRepository.class,
  AppFileRepository.class
})
public abstract class AbstractRepositoryIntegrationTest {}
