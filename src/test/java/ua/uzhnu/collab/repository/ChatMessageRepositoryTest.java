package ua.uzhnu.collab.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.uzhnu.collab.TestData.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ua.uzhnu.collab.AbstractRepositoryIntegrationTest;
import ua.uzhnu.collab.entity.ChatMessage;
import ua.uzhnu.collab.entity.Team;
import ua.uzhnu.collab.entity.User;

@DisplayName("ChatMessageRepository — інтеграційний тест")
class ChatMessageRepositoryTest extends AbstractRepositoryIntegrationTest {

  @Autowired private ChatMessageRepository messageRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TeamRepository teamRepository;

  private User author;
  private Team team;

  private static final int PAGE_SIZE = 10;

  @BeforeEach
  void setUp() {
    author = userRepository.save(user("author"));
    team = teamRepository.save(team("Team", author));
  }

  @Test
  @DisplayName("save: створює кореневе повідомлення (parent == null)")
  void save_rootMessage() {
    ChatMessage saved = messageRepository.save(message(team, author, "Hi"));

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getParentMessage()).isNull();
    assertThat(saved.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("save: створює відповідь зі зв'язком parent_message_id")
  void save_replyMessage_linksParent() {
    ChatMessage root = messageRepository.save(message(team, author, "Root"));
    ChatMessage reply = messageRepository.save(reply(team, author, "Reply", root));

    assertThat(reply.getParentMessage()).isNotNull();
    assertThat(reply.getParentMessage().getId()).isEqualTo(root.getId());
  }

  @Test
  @DisplayName("findByTeamIdOrderByCreatedAtDesc: пагіноване, DESC за датою")
  void findByTeamIdOrderByCreatedAtDesc_paged() throws InterruptedException {
    ChatMessage m1 = messageRepository.save(message(team, author, "Msg1"));
    Thread.sleep(5);
    ChatMessage m2 = messageRepository.save(message(team, author, "Msg2"));
    Thread.sleep(5);
    ChatMessage m3 = messageRepository.save(message(team, author, "Msg3"));

    List<ChatMessage> page =
        messageRepository.findByTeamIdOrderByCreatedAtDesc(team.getId(), 0, PAGE_SIZE);

    assertThat(page)
        .extracting(ChatMessage::getId)
        .containsExactly(m3.getId(), m2.getId(), m1.getId());
  }

  @Test
  @DisplayName("findRootMessagesByTeam: повертає лише кореневі (parent IS NULL)")
  void findRootMessagesByTeam_excludesReplies() {
    ChatMessage root = messageRepository.save(message(team, author, "Root"));
    messageRepository.save(reply(team, author, "Reply1", root));
    messageRepository.save(reply(team, author, "Reply2", root));

    List<ChatMessage> roots = messageRepository.findRootMessagesByTeam(team.getId(), 0, PAGE_SIZE);

    assertThat(roots).hasSize(1).first().extracting(ChatMessage::getContent).isEqualTo("Root");
  }

  @Test
  @DisplayName(
      "findByParentMessageIdOrderByCreatedAtAsc: повертає відповіді в хронологічному порядку")
  void findByParentMessageIdOrderByCreatedAtAsc_works() throws InterruptedException {
    ChatMessage root = messageRepository.save(message(team, author, "Root"));
    messageRepository.save(reply(team, author, "First reply", root));
    Thread.sleep(5);
    messageRepository.save(reply(team, author, "Second reply", root));

    List<ChatMessage> replies =
        messageRepository.findByParentMessageIdOrderByCreatedAtAsc(root.getId());

    assertThat(replies)
        .extracting(ChatMessage::getContent)
        .containsExactly("First reply", "Second reply");
  }

  @Test
  @DisplayName("countByTeamId: рахує усі повідомлення команди (включно з відповідями)")
  void countByTeamId_includesReplies() {
    ChatMessage root = messageRepository.save(message(team, author, "Root"));
    messageRepository.save(reply(team, author, "Reply", root));

    assertThat(messageRepository.countByTeamId(team.getId())).isEqualTo(2L);
  }
}
