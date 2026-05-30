package ua.uzhnu.collab.chat;

import java.util.List;

/** Shared DTOs for the embedded local chat server. */
public final class EmbeddedChatModels {

  private EmbeddedChatModels() {}

  /** Single chat message exposed by the embedded HTTP API. */
  public record MessageView(
      long id,
      String room,
      String authorName,
      String initials,
      String content,
      String timestamp) {}

  /** Message creation payload accepted by the embedded HTTP API. */
  public record MessageCreateRequest(String room, String authorName, String content) {}

  /** Small response wrapper used by tests and future extensions. */
  public record MessageListResponse(List<MessageView> messages) {}
}

