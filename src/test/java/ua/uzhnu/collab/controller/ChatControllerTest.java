package ua.uzhnu.collab.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import ua.uzhnu.collab.chat.EmbeddedChatClient;
import ua.uzhnu.collab.chat.EmbeddedChatModels;
import ua.uzhnu.collab.config.SessionContext;

@ExtendWith(ApplicationExtension.class)
@DisplayName("ChatController — polling tests")
class ChatControllerTest {

  private static final String DEFAULT_ROOM = "general";

  @Mock private SessionContext userSession;
  @Mock private EmbeddedChatClient chatClient;
  @Mock private DialogHelper dialogs;

  private AutoCloseable mocks;
  private Stage stage;
  private ChatController controller;
  private TextField txtMessageInput;
  private VBox messagesContainer;
  private ScrollPane messagesScroll;

  @Start
  void start(Stage stage) {
    this.stage = stage;
    stage.setScene(new Scene(new VBox(), 200, 200));
    stage.show();
  }

  @BeforeEach
  void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    when(userSession.isAuthenticated()).thenReturn(false);
    when(userSession.getCurrentUser()).thenReturn(null);

    controller = new ChatController(userSession, chatClient, dialogs);
    txtMessageInput = new TextField();
    messagesContainer = new VBox();
    messagesScroll = new ScrollPane();
    messagesScroll.setContent(messagesContainer);

    inject(controller, "txtMessageInput", txtMessageInput);
    inject(controller, "messagesContainer", messagesContainer);
    inject(controller, "messagesScroll", messagesScroll);

    runOnFxThread(
        () -> {
          stage.setScene(new Scene(new VBox(messagesScroll), 400, 300));
          stage.show();
        });
  }

  @AfterEach
  void tearDown() throws Exception {
    if (controller != null) {
      controller.stopPolling();
    }
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  void testPollingStartsOnInitialize() {
    when(chatClient.loadMessages(DEFAULT_ROOM)).thenReturn(List.of());

    controller.initialize();
    assertDoesNotThrow(() -> waitForFxEvents());

    ScheduledExecutorService executor = getExecutor();
    assertNotNull(executor);
    assertFalse(executor.isShutdown());
  }

  @Test
  void testStopPollingShutdownsExecutor() {
    when(chatClient.loadMessages(DEFAULT_ROOM)).thenReturn(List.of());
    controller.initialize();
    assertDoesNotThrow(() -> waitForFxEvents());

    ScheduledExecutorService executor = getExecutor();
    assertFalse(executor.isShutdown());

    controller.stopPolling();
    assertTrue(executor.isShutdown());
  }

  @Test
  void testStopPollingIsIdempotent() {
    when(chatClient.loadMessages(DEFAULT_ROOM)).thenReturn(List.of());
    controller.initialize();
    assertDoesNotThrow(() -> waitForFxEvents());

    controller.stopPolling();
    controller.stopPolling();

    assertTrue(getExecutor().isShutdown());
  }

  @Test
  void testPollMessagesDetectsNewMessages() throws Exception {
    when(chatClient.loadMessages(DEFAULT_ROOM))
        .thenReturn(
            List.of(
                message(1, "general", "Alice", "A", "Hello", "10:00"),
                message(2, "general", "Bob", "B", "Hi", "10:01")));

    setLastMessageCount(0);

    controller.pollMessages();
    waitForFxEvents();

    assertEquals(2, getLastMessageCount());
    assertEquals(2, messagesContainer.getChildren().size());
  }

  @Test
  void testPollMessagesNoOpWhenCountUnchanged() throws Exception {
    runOnFxThread(() -> messagesContainer.getChildren().add(new VBox()));
    when(chatClient.loadMessages(DEFAULT_ROOM))
        .thenReturn(List.of(message(1, "general", "Alice", "A", "Hello", "10:00")));

    setLastMessageCount(1);

    controller.pollMessages();
    waitForFxEvents();

    assertEquals(1, getLastMessageCount());
    assertEquals(1, messagesContainer.getChildren().size());
  }

  @Test
  void testPollMessagesSilentlyHandlesErrors() throws Exception {
    when(chatClient.loadMessages(DEFAULT_ROOM)).thenThrow(new IllegalStateException("Network error"));

    setLastMessageCount(0);

    assertDoesNotThrow(() -> controller.pollMessages());
    waitForFxEvents();

    assertEquals(0, getLastMessageCount());
  }

  @Test
  void testPollMessagesRetriesAfterFailure() throws Exception {
    when(chatClient.loadMessages(DEFAULT_ROOM))
        .thenThrow(new IllegalStateException("Network error"))
        .thenReturn(List.of(message(1, "general", "Alice", "A", "Hello", "10:00")));

    setLastMessageCount(0);

    assertDoesNotThrow(() -> controller.pollMessages());
    waitForFxEvents();
    assertEquals(0, getLastMessageCount());

    assertDoesNotThrow(() -> controller.pollMessages());
    waitForFxEvents();
    assertEquals(1, getLastMessageCount());
  }


  private void runOnFxThread(Runnable action) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    Platform.runLater(
        () -> {
          try {
            action.run();
          } finally {
            latch.countDown();
          }
        });
    assertTrue(latch.await(5, TimeUnit.SECONDS));
  }

  private void waitForFxEvents() throws Exception {
    runOnFxThread(() -> {});
  }

  private void inject(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private ScheduledExecutorService getExecutor() {
    try {
      Field field = ChatController.class.getDeclaredField("executor");
      field.setAccessible(true);
      return (ScheduledExecutorService) field.get(controller);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private int getLastMessageCount() {
    try {
      Field field = ChatController.class.getDeclaredField("lastMessageCount");
      field.setAccessible(true);
      return field.getInt(controller);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private void setLastMessageCount(int value) {
    try {
      Field field = ChatController.class.getDeclaredField("lastMessageCount");
      field.setAccessible(true);
      field.setInt(controller, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private EmbeddedChatModels.MessageView message(
      long id, String room, String author, String initials, String content, String time) {
    return new EmbeddedChatModels.MessageView(id, room, author, initials, content, time);
  }
}


