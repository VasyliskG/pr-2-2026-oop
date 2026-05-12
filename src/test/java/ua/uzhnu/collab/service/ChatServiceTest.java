package ua.uzhnu.collab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.uzhnu.collab.dto.Dtos.ChatMessageCreateDto;
import ua.uzhnu.collab.dto.Dtos.ChatMessageDto;
import ua.uzhnu.collab.entity.ChatMessage;
import ua.uzhnu.collab.entity.Team;
import ua.uzhnu.collab.entity.User;
import ua.uzhnu.collab.exception.AccessDeniedException;
import ua.uzhnu.collab.exception.EntityNotFoundException;
import ua.uzhnu.collab.repository.ChatMessageRepository;

/**
 * Юніт-тести {@link ChatService}.
 *
 * <p>Особлива увага — перевірка cross-team isolation: не можна відповідати на повідомлення іншої
 * команди.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChatService — юніт-тест")
class ChatServiceTest {

  @Mock private ChatMessageRepository messageRepository;
  @Mock private UserService userService;
  @Mock private TeamService teamService;
  @Spy private DtoMapper mapper = new DtoMapper();

  @InjectMocks private ChatService chatService;

  private User sender;
  private Team team;

  @BeforeEach
  void setUp() {
    sender =
        User.builder().id(1L).username("u").fullName("U").email("u@x").passwordHash("x").build();
    team = Team.builder().id(10L).name("T").createdBy(sender).build();
  }

  @Test
  @DisplayName("sendMessage: створює кореневе повідомлення")
  void sendMessage_root_succeeds() {
    ChatMessageCreateDto dto = new ChatMessageCreateDto(10L, "Hello", null);

    doNothing().when(teamService).requireMember(10L, 1L);
    when(teamService.findTeamById(10L)).thenReturn(team);
    when(userService.findById(1L)).thenReturn(sender);
    when(messageRepository.save(any(ChatMessage.class)))
        .thenAnswer(
            inv -> {
              ChatMessage m = inv.getArgument(0);
              m.setId(500L);
              return m;
            });
    when(messageRepository.findByParentMessageIdOrderByCreatedAtAsc(500L))
        .thenReturn(Collections.emptyList());

    ChatMessageDto result = chatService.sendMessage(dto, 1L);

    assertThat(result.id()).isEqualTo(500L);
    assertThat(result.parentMessageId()).isNull();
    assertThat(result.replyCount()).isZero();
  }

  @Test
  @DisplayName("sendMessage: створення відповіді з parentMessageId")
  void sendMessage_reply_linksParent() {
    ChatMessage parent =
        ChatMessage.builder().id(50L).team(team).user(sender).content("Parent").build();
    ChatMessageCreateDto dto = new ChatMessageCreateDto(10L, "Reply", 50L);

    doNothing().when(teamService).requireMember(10L, 1L);
    when(teamService.findTeamById(10L)).thenReturn(team);
    when(userService.findById(1L)).thenReturn(sender);
    when(messageRepository.findById(50L)).thenReturn(Optional.of(parent));
    when(messageRepository.save(any(ChatMessage.class)))
        .thenAnswer(
            inv -> {
              ChatMessage m = inv.getArgument(0);
              m.setId(51L);
              return m;
            });
    when(messageRepository.findByParentMessageIdOrderByCreatedAtAsc(51L))
        .thenReturn(Collections.emptyList());

    ChatMessageDto result = chatService.sendMessage(dto, 1L);

    assertThat(result.parentMessageId()).isEqualTo(50L);
    assertThat(result.parentAuthorName()).isEqualTo("U");
  }

  @Test
  @DisplayName("sendMessage: parent з іншої команди — IllegalArgumentException")
  void sendMessage_parentFromOtherTeam_throws() {
    Team otherTeam = Team.builder().id(99L).createdBy(sender).build();
    ChatMessage foreignParent =
        ChatMessage.builder().id(50L).team(otherTeam).user(sender).content("Foreign").build();
    ChatMessageCreateDto dto = new ChatMessageCreateDto(10L, "Reply", 50L);

    doNothing().when(teamService).requireMember(10L, 1L);
    when(teamService.findTeamById(10L)).thenReturn(team);
    when(userService.findById(1L)).thenReturn(sender);
    when(messageRepository.findById(50L)).thenReturn(Optional.of(foreignParent));

    assertThatThrownBy(() -> chatService.sendMessage(dto, 1L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("іншій команді");

    verify(messageRepository, never()).save(any());
  }

  @Test
  @DisplayName("sendMessage: parent не існує — EntityNotFoundException")
  void sendMessage_parentNotFound_throws() {
    ChatMessageCreateDto dto = new ChatMessageCreateDto(10L, "Reply", 999L);

    doNothing().when(teamService).requireMember(10L, 1L);
    when(teamService.findTeamById(10L)).thenReturn(team);
    when(userService.findById(1L)).thenReturn(sender);
    when(messageRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> chatService.sendMessage(dto, 1L))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  @DisplayName("sendMessage: не учасник команди — AccessDeniedException")
  void sendMessage_byNonMember_throws() {
    ChatMessageCreateDto dto = new ChatMessageCreateDto(10L, "Hi", null);
    doThrow(new AccessDeniedException("no")).when(teamService).requireMember(10L, 1L);

    assertThatThrownBy(() -> chatService.sendMessage(dto, 1L))
        .isInstanceOf(AccessDeniedException.class);

    verify(messageRepository, never()).save(any());
  }
}
