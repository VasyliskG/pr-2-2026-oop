package ua.uzhnu.collab.service;

import java.util.List;
import java.util.concurrent.Callable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.uzhnu.collab.dto.Dtos.*;
import ua.uzhnu.collab.entity.ChatMessage;
import ua.uzhnu.collab.entity.Team;
import ua.uzhnu.collab.entity.User;
import ua.uzhnu.collab.exception.EntityNotFoundException;
import ua.uzhnu.collab.repository.ChatMessageRepository;

/**
 * Сервіс бізнес-логіки для сутності {@link ChatMessage}.
 *
 * <p>Забезпечує надсилання повідомлень, побудову дерева відповідей, пагіноване отримання історії
 * чату.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class ChatService {

  private final ChatMessageRepository messageRepository;
  private final UserService userService;
  private final TeamService teamService;
  private final DtoMapper mapper;

  private static final int DEFAULT_PAGE_SIZE = 50;
  private static final int MAX_RETRIES = 3;
  private static final long RETRY_DELAY_MS = 100;

  /** Виконує callable з повторними спробами при помилці підключення. */
  private <T> T executeWithRetry(Callable<T> operation) throws Exception {
    DataAccessResourceFailureException lastError = null;
    for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
      try {
        return operation.call();
      } catch (DataAccessResourceFailureException e) {
        lastError = e;
        if (attempt < MAX_RETRIES - 1) {
          long delay = (long) Math.pow(2, attempt) * RETRY_DELAY_MS;
          log.warn(
              "Спроба {} не вдалась, очікування {} мс...", attempt + 1, delay);
          try {
            Thread.sleep(delay);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ie);
          }
        }
      }
    }
    throw lastError;
  }

  /**
   * Надсилає повідомлення в командний чат.
   *
   * @param dto містить teamId, content, опціональний parentMessageId
   */
  @Transactional
  public ChatMessageDto sendMessage(ChatMessageCreateDto dto, Long senderId) {
    teamService.requireMember(dto.teamId(), senderId);

    Team team = teamService.findTeamById(dto.teamId());
    User sender = userService.findById(senderId);

    ChatMessage parent = null;
    if (dto.parentMessageId() != null) {
      parent =
          messageRepository
              .findById(dto.parentMessageId())
              .orElseThrow(
                  () ->
                      new EntityNotFoundException(
                          "Батьківське повідомлення", dto.parentMessageId()));

      // Перевірка: батьківське повідомлення має бути з тієї ж команди
      if (!parent.getTeam().getId().equals(dto.teamId())) {
        throw new IllegalArgumentException("Батьківське повідомлення належить іншій команді");
      }
    }

    ChatMessage message =
        ChatMessage.builder()
            .team(team)
            .user(sender)
            .content(dto.content().trim())
            .parentMessage(parent)
            .build();

    message = messageRepository.save(message);
    log.info(
        "Повідомлення в команді '{}' від {}: {}",
        team.getName(),
        sender.getUsername(),
        message.getContent().substring(0, Math.min(50, message.getContent().length())));

    int replyCount =
        messageRepository.findByParentMessageIdOrderByCreatedAtAsc(message.getId()).size();
    return mapper.toChatMessageDto(message, replyCount);
  }

  /** Останні повідомлення команди (пагіноване). */
  public List<ChatMessageDto> getRecentMessages(Long teamId, int page) throws Exception {
    return executeWithRetry(
        () ->
            messageRepository
                .findByTeamIdOrderByCreatedAtDesc(teamId, page, DEFAULT_PAGE_SIZE)
                .stream()
                .map(
                    msg -> {
                      try {
                        int rc =
                            messageRepository
                                .findByParentMessageIdOrderByCreatedAtAsc(msg.getId())
                                .size();
                        return mapper.toChatMessageDto(msg, rc);
                      } catch (DataAccessResourceFailureException e) {
                        throw new RuntimeException(e);
                      }
                    })
                .toList());
  }

  /** Кореневі повідомлення (без батьків) — для потокового відображення. */
  public List<ChatMessageDto> getRootMessages(Long teamId, int page) throws Exception {
    return executeWithRetry(
        () ->
            messageRepository.findRootMessagesByTeam(teamId, page, DEFAULT_PAGE_SIZE).stream()
                .map(
                    msg -> {
                      try {
                        int rc =
                            messageRepository
                                .findByParentMessageIdOrderByCreatedAtAsc(msg.getId())
                                .size();
                        return mapper.toChatMessageDto(msg, rc);
                      } catch (DataAccessResourceFailureException e) {
                        throw new RuntimeException(e);
                      }
                    })
                .toList());
  }

  /** Відповіді на конкретне повідомлення (для розгортання потоку). */
  public List<ChatMessageDto> getReplies(Long parentMessageId) throws Exception {
    return executeWithRetry(
        () ->
            messageRepository.findByParentMessageIdOrderByCreatedAtAsc(parentMessageId).stream()
                .map(
                    msg -> {
                      try {
                        int rc =
                            messageRepository
                                .findByParentMessageIdOrderByCreatedAtAsc(msg.getId())
                                .size();
                        return mapper.toChatMessageDto(msg, rc);
                      } catch (DataAccessResourceFailureException e) {
                        throw new RuntimeException(e);
                      }
                    })
                .toList());
  }

  /** Нові кореневі повідомлення після певного ID (для real-time polling). */
  public List<ChatMessageDto> loadNewMessages(Long teamId, long afterId) throws Exception {
    return executeWithRetry(
        () ->
            messageRepository.findNewRootMessages(teamId, afterId).stream()
                .map(
                    msg -> {
                      try {
                        int rc =
                            messageRepository
                                .findByParentMessageIdOrderByCreatedAtAsc(msg.getId())
                                .size();
                        return mapper.toChatMessageDto(msg, rc);
                      } catch (DataAccessResourceFailureException e) {
                        throw new RuntimeException(e);
                      }
                    })
                .toList());
  }
}
