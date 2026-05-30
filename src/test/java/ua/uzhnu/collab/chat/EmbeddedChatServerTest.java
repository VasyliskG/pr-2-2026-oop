package ua.uzhnu.collab.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmbeddedChatServerTest {

  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();

  private EmbeddedChatServer server;

  @BeforeEach
  void setUp() {
    server = new EmbeddedChatServer("127.0.0.1", 0);
    server.start();
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop();
    }
  }

  @Test
  void getMessages_returnsSeedMessages() throws Exception {
    HttpResponse<String> response =
        httpClient.send(
            HttpRequest.newBuilder(
                    server.getBaseUri()
                        .resolve("/api/chat/messages?room=" + encode(EmbeddedChatServer.DEFAULT_ROOM)))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    EmbeddedChatModels.MessageListResponse payload =
        objectMapper.readValue(response.body(), EmbeddedChatModels.MessageListResponse.class);
    assertThat(payload.messages()).isNotEmpty();
    assertThat(payload.messages().get(0).room()).isEqualTo(EmbeddedChatServer.DEFAULT_ROOM);
  }

  @Test
  void postMessage_appendsToRoom() throws Exception {
    String room = "room-" + UUID.randomUUID();
    EmbeddedChatModels.MessageCreateRequest request =
        new EmbeddedChatModels.MessageCreateRequest(room, "Test User", "Hello embedded chat");

    HttpResponse<String> postResponse =
        httpClient.send(
            HttpRequest.newBuilder(server.getBaseUri().resolve("/api/chat/messages"))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(postResponse.statusCode()).isEqualTo(201);
    EmbeddedChatModels.MessageView created =
        objectMapper.readValue(postResponse.body(), EmbeddedChatModels.MessageView.class);
    assertThat(created.room()).isEqualTo(room);
    assertThat(created.authorName()).isEqualTo("Test User");
    assertThat(created.content()).isEqualTo("Hello embedded chat");

    HttpResponse<String> getResponse =
        httpClient.send(
            HttpRequest.newBuilder(
                    server.getBaseUri().resolve("/api/chat/messages?room=" + encode(room)))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    EmbeddedChatModels.MessageListResponse payload =
        objectMapper.readValue(getResponse.body(), EmbeddedChatModels.MessageListResponse.class);
    assertThat(payload.messages())
        .extracting(EmbeddedChatModels.MessageView::content)
        .contains("Hello embedded chat");
  }

  @Test
  void postMessage_rejectsBlankContent() throws Exception {
    String room = "room-" + UUID.randomUUID();
    EmbeddedChatModels.MessageCreateRequest request =
        new EmbeddedChatModels.MessageCreateRequest(room, "Test User", "   ");

    HttpResponse<String> response =
        httpClient.send(
            HttpRequest.newBuilder(server.getBaseUri().resolve("/api/chat/messages"))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains("content");
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}


