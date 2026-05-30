package ua.uzhnu.collab.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;

/**
 * Lightweight embedded HTTP server for the standalone chat page.
 *
 * <p>It keeps messages in memory and exposes a tiny REST-like API:
 *
 * <ul>
 *   <li>GET {@code /api/chat/messages?room=general}
 *   <li>POST {@code /api/chat/messages}
 * </ul>
 */
@Slf4j
public class EmbeddedChatServer {

  public static final String DEFAULT_ROOM = "general";
  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

  private final String host;
  private final int configuredPort;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AtomicLong idSequence = new AtomicLong(1L);
  private final Map<String, CopyOnWriteArrayList<StoredMessage>> rooms = new ConcurrentHashMap<>();

  private volatile HttpServer server;
  private volatile URI baseUri;

  public EmbeddedChatServer(String host, int configuredPort) {
    this.host = host;
    this.configuredPort = configuredPort;
    this.baseUri = URI.create("http://" + host + ":" + configuredPort);
  }

  public synchronized void start() {
    if (server != null) {
      return;
    }

    try {
      seedDemoMessages();
      server = HttpServer.create(new InetSocketAddress(host, configuredPort), 0);
      server.createContext("/api/chat/health", this::handleHealth);
      server.createContext("/api/chat/messages", this::handleMessages);
      server.setExecutor(
          Executors.newCachedThreadPool(
              new ThreadFactory() {
                private final AtomicLong threadId = new AtomicLong(1L);

                @Override
                public Thread newThread(Runnable r) {
                  Thread t = new Thread(r, "embedded-chat-" + threadId.getAndIncrement());
                  t.setDaemon(true);
                  return t;
                }
              }));
      server.start();
      baseUri = URI.create("http://" + host + ":" + server.getAddress().getPort());
      log.info("Embedded chat server started at {}", baseUri);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to start embedded chat server", e);
    }
  }

  public synchronized void stop() {
    if (server != null) {
      server.stop(0);
      server = null;
      log.info("Embedded chat server stopped");
    }
  }

  public URI getBaseUri() {
    return baseUri;
  }

  public List<EmbeddedChatModels.MessageView> getMessages(String room) {
    return rooms.getOrDefault(normalizeRoom(room), new CopyOnWriteArrayList<>()).stream()
        .map(StoredMessage::toView)
        .toList();
  }

  public EmbeddedChatModels.MessageView addMessage(String room, String authorName, String content) {
    String normalizedRoom = normalizeRoom(room);
    String normalizedAuthor = requireText(authorName, "authorName");
    String normalizedContent = requireText(content, "content");

    StoredMessage message =
        new StoredMessage(
            idSequence.getAndIncrement(),
            normalizedRoom,
            normalizedAuthor,
            initials(normalizedAuthor),
            normalizedContent,
            LocalTime.now().format(TIME_FMT));

    rooms.computeIfAbsent(normalizedRoom, ignored -> new CopyOnWriteArrayList<>()).add(message);
    return message.toView();
  }

  private void handleHealth(HttpExchange exchange) throws IOException {
    try {
      respondText(exchange, 200, "OK");
    } finally {
      exchange.close();
    }
  }

  private void handleMessages(HttpExchange exchange) throws IOException {
    try {
      switch (exchange.getRequestMethod()) {
        case "GET" -> handleGetMessages(exchange);
        case "POST" -> handlePostMessage(exchange);
        default -> respondText(exchange, 405, "Method Not Allowed");
      }
    } catch (IllegalArgumentException e) {
      respondText(exchange, 400, e.getMessage());
    } catch (IOException e) {
      respondText(exchange, 400, "Malformed request body");
    } finally {
      exchange.close();
    }
  }

  private void handleGetMessages(HttpExchange exchange) throws IOException {
    String room = queryParam(exchange.getRequestURI(), "room").orElse(DEFAULT_ROOM);
    writeJson(exchange, 200, new EmbeddedChatModels.MessageListResponse(getMessages(room)));
  }

  private void handlePostMessage(HttpExchange exchange) throws IOException {
    try (InputStream body = exchange.getRequestBody()) {
      EmbeddedChatModels.MessageCreateRequest request =
          objectMapper.readValue(body, EmbeddedChatModels.MessageCreateRequest.class);
      EmbeddedChatModels.MessageView created =
          addMessage(request.room(), request.authorName(), request.content());
      writeJson(exchange, 201, created);
    }
  }

  private void seedDemoMessages() {
    rooms.computeIfAbsent(DEFAULT_ROOM, ignored -> new CopyOnWriteArrayList<>());
    if (!rooms.get(DEFAULT_ROOM).isEmpty()) {
      return;
    }

    addSeed(DEFAULT_ROOM, "Alex Petrenko", "Вбудований чат запущено локально.");
    addSeed(DEFAULT_ROOM, "Maria Shevchenko", "Повідомлення зберігаються в пам'яті застосунку.");
    addSeed(DEFAULT_ROOM, "Lukas Weber", "Можна надсилати нові повідомлення без зовнішнього сервера.");
  }

  private void addSeed(String room, String authorName, String content) {
    StoredMessage seed =
        new StoredMessage(
            idSequence.getAndIncrement(),
            normalizeRoom(room),
            authorName,
            initials(authorName),
            content,
            LocalTime.now().format(TIME_FMT));
    rooms.computeIfAbsent(normalizeRoom(room), ignored -> new CopyOnWriteArrayList<>()).add(seed);
  }

  private void writeJson(HttpExchange exchange, int status, Object body) throws IOException {
    byte[] payload = objectMapper.writeValueAsBytes(body);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(status, payload.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(payload);
    }
  }

  private void respondText(HttpExchange exchange, int status, String message) throws IOException {
    byte[] payload = message.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
    exchange.sendResponseHeaders(status, payload.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(payload);
    }
  }

  private static String normalizeRoom(String room) {
    return room == null || room.isBlank() ? DEFAULT_ROOM : room.trim();
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value.trim();
  }

  private static String initials(String fullName) {
    String[] parts = fullName.trim().split("\\s+");
    StringBuilder result = new StringBuilder();
    for (String part : parts) {
      if (!part.isBlank()) {
        result.append(Character.toUpperCase(part.charAt(0)));
      }
      if (result.length() == 2) {
        break;
      }
    }
    return result.length() == 0 ? "?" : result.toString();
  }

  private static java.util.Optional<String> queryParam(URI uri, String key) {
    String query = uri.getRawQuery();
    if (query == null || query.isBlank()) {
      return java.util.Optional.empty();
    }

    for (String pair : query.split("&")) {
      String[] kv = pair.split("=", 2);
      if (kv.length == 0) {
        continue;
      }
      String decodedKey = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
      if (!key.equals(decodedKey)) {
        continue;
      }
      String decodedValue = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
      return java.util.Optional.of(decodedValue);
    }
    return java.util.Optional.empty();
  }

  private record StoredMessage(
      long id,
      String room,
      String authorName,
      String initials,
      String content,
      String timestamp) {

    EmbeddedChatModels.MessageView toView() {
      return new EmbeddedChatModels.MessageView(id, room, authorName, initials, content, timestamp);
    }
  }
}


