package ua.uzhnu.collab.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.concurrent.Task;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import ua.uzhnu.collab.chat.EmbeddedChatClient;
import ua.uzhnu.collab.chat.EmbeddedChatModels;
import ua.uzhnu.collab.config.SessionContext;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class ChatController {

  private static final String DEFAULT_ROOM = "general";

  private final SessionContext userSession;
  private final EmbeddedChatClient chatClient;
  private final DialogHelper dialogs;

  private ScheduledExecutorService executor;
  private int lastMessageCount = 0;

  @FXML private TextField txtMessageInput;
  @FXML private VBox messagesContainer;
  @FXML private ScrollPane messagesScroll;

  @FXML
  public void initialize() {
    loadMessages();
    startPolling();
    registerLifecycleCleanup();
  }

  @FXML
  private void handleSend() {
    if (txtMessageInput == null) return;
    String text = txtMessageInput.getText();
    if (text == null || text.isBlank()) return;

    String authorName = resolveAuthorName();
    Task<EmbeddedChatModels.MessageView> task =
        new Task<>() {
          @Override
          protected EmbeddedChatModels.MessageView call() {
            return chatClient.sendMessage(DEFAULT_ROOM, authorName, text);
          }
        };

    task.setOnSucceeded(
        e -> {
          appendMessage(task.getValue());
          txtMessageInput.clear();
        });
    task.setOnFailed(
        e -> dialogs.showError("Помилка чату", task.getException().getMessage()));
    runDaemon(task);
  }

  private void loadMessages() {
    Task<List<EmbeddedChatModels.MessageView>> task =
        new Task<>() {
          @Override
          protected List<EmbeddedChatModels.MessageView> call() {
            return chatClient.loadMessages(DEFAULT_ROOM);
          }
        };

    task.setOnSucceeded(
        e -> {
          replaceMessages(task.getValue());
          lastMessageCount = task.getValue().size();
          scrollToBottom();
        });
    task.setOnFailed(
        e -> dialogs.showError("Помилка завантаження чату", task.getException().getMessage()));
    runDaemon(task);
  }

  private void appendMessage(EmbeddedChatModels.MessageView message) {
    messagesContainer.getChildren().add(buildRow(message));
    lastMessageCount++;
    scrollToBottom();
  }

  private void replaceMessages(List<EmbeddedChatModels.MessageView> messages) {
    messagesContainer.getChildren().clear();
    for (EmbeddedChatModels.MessageView message : messages) {
      messagesContainer.getChildren().add(buildRow(message));
    }
  }

  private void startPolling() {
    if (executor != null && !executor.isShutdown()) {
      return;
    }
    executor = Executors.newScheduledThreadPool(1);
    executor.scheduleAtFixedRate(this::pollMessages, 10, 10, TimeUnit.SECONDS);
  }

  void pollMessages() {
    try {
      List<EmbeddedChatModels.MessageView> messages = chatClient.loadMessages(DEFAULT_ROOM);
      int newCount = messages.size();

      if (newCount > lastMessageCount) {
        Platform.runLater(
            () -> {
              replaceMessages(messages);
              scrollToBottom();
              lastMessageCount = newCount;
            });
      }
    } catch (Exception ignored) {
      // Silent retry on next poll cycle.
    }
  }

  public void stopPolling() {
    if (executor != null && !executor.isShutdown()) {
      executor.shutdown();
    }
  }

  private void registerLifecycleCleanup() {
    if (messagesContainer == null) {
      return;
    }

    messagesContainer
        .sceneProperty()
        .addListener(
            (obs, oldScene, newScene) -> {
              if (oldScene != null && newScene != oldScene) {
                stopPolling();
              }
            });
  }

  private HBox buildRow(EmbeddedChatModels.MessageView message) {
    HBox row = new HBox(10);
    row.setAlignment(Pos.TOP_LEFT);

    Label avatar = new Label(message.initials());
    avatar.getStyleClass().add("mini-avatar");

    Label name = new Label(message.authorName());
    name.getStyleClass().add("message-author");

    Label time = new Label(message.timestamp());
    time.getStyleClass().add("message-time");

    HBox meta = new HBox(8);
    meta.setAlignment(Pos.CENTER_LEFT);
    meta.getChildren().addAll(name, time);

    Label msg = new Label(message.content());
    msg.getStyleClass().add("message-text");
    msg.setWrapText(true);

    VBox content = new VBox(2);
    content.getChildren().addAll(meta, msg);
    HBox.setHgrow(content, Priority.ALWAYS);

    row.getChildren().addAll(avatar, content);
    return row;
  }

  private void scrollToBottom() {
    if (messagesScroll == null) {
      return;
    }

    if (Platform.isFxApplicationThread()) {
      messagesScroll.setVvalue(1.0);
    } else {
      Platform.runLater(() -> messagesScroll.setVvalue(1.0));
    }
  }

  private String resolveAuthorName() {
    if (userSession.isAuthenticated() && userSession.getCurrentUser() != null) {
      return userSession.getCurrentUser().fullName();
    }
    return "Guest";
  }

  private static void runDaemon(Task<?> task) {
    Thread thread = new Thread(task, "embedded-chat-ui");
    thread.setDaemon(true);
    thread.start();
  }
}
