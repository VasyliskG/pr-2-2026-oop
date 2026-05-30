package ua.uzhnu.collab.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

/** HTTP client for the embedded local chat server. */
@Component
@Profile("!test")
@RequiredArgsConstructor
public class EmbeddedChatClient {

  private final EmbeddedChatServer server;
  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();

  public List<EmbeddedChatModels.MessageView> loadMessages(String room) {
    HttpRequest request =
        HttpRequest.newBuilder(messagesUri(room)).GET().header("Accept", "application/json").build();
    return sendForMessages(request);
  }

  public EmbeddedChatModels.MessageView sendMessage(
      String room, String authorName, String content) {
    try {
      EmbeddedChatModels.MessageCreateRequest payload =
          new EmbeddedChatModels.MessageCreateRequest(room, authorName, content);
      HttpRequest request =
          HttpRequest.newBuilder(server.getBaseUri().resolve("/api/chat/messages"))
              .header("Content-Type", "application/json; charset=utf-8")
              .header("Accept", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
              .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      ensureSuccess(response);
      return objectMapper.readValue(response.body(), EmbeddedChatModels.MessageView.class);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Failed to send embedded chat message", e);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to send embedded chat message", e);
    }
  }

  private List<EmbeddedChatModels.MessageView> sendForMessages(HttpRequest request) {
    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      ensureSuccess(response);
      EmbeddedChatModels.MessageListResponse parsed =
          objectMapper.readValue(response.body(), EmbeddedChatModels.MessageListResponse.class);
      return parsed.messages();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Failed to load embedded chat messages", e);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load embedded chat messages", e);
    }
  }

  private static void ensureSuccess(HttpResponse<String> response) {
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      return;
    }
    throw new IllegalStateException(
        "Embedded chat server returned HTTP " + response.statusCode() + ": " + response.body());
  }

  private URI messagesUri(String room) {
    String encodedRoom =
        URLEncoder.encode(room == null ? EmbeddedChatServer.DEFAULT_ROOM : room, StandardCharsets.UTF_8);
    return server.getBaseUri().resolve("/api/chat/messages?room=" + encodedRoom);
  }
}




