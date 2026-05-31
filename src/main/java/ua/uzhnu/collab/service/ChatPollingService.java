package ua.uzhnu.collab.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ua.uzhnu.collab.dto.Dtos.ChatMessageDto;

/**
 * Service that periodically polls database for new chat messages.
 * Enables real-time chat updates without WebSocket overhead.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatPollingService {

  private final ChatService chatService;
  private final ScheduledExecutorService executor =
      Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "chat-polling");
        t.setDaemon(true);
        return t;
      });

  // room-id -> last message id seen
  private final Map<Long, AtomicLong> roomLastIds = new HashMap<>();

  // room-id -> callback for new messages
  private final Map<Long, Consumer<ChatMessageDto>> roomCallbacks = new HashMap<>();

  /**
   * Start polling room for new messages.
   * @param roomId team or task id (context for messages)
   * @param callback fired when new message arrives
   */
  public synchronized void startPolling(Long roomId, Consumer<ChatMessageDto> callback) {
    startPolling(roomId, callback, -1L);
  }

  /**
   * Start polling room for new messages with explicit initial lastId.
   * @param roomId team or task id (context for messages)
   * @param callback fired when new message arrives
   * @param initialLastId messages with id > initialLastId will be fetched
   */
  public synchronized void startPolling(Long roomId, Consumer<ChatMessageDto> callback, long initialLastId) {
    if (roomCallbacks.containsKey(roomId)) {
      log.debug("Polling already active for room {}", roomId);
      return;
    }

    roomCallbacks.put(roomId, callback);
    AtomicLong lastId = roomLastIds.computeIfAbsent(roomId, k -> new AtomicLong(initialLastId));

    executor.scheduleAtFixedRate(
        () -> pollRoom(roomId),
        2, // initial delay 2s
        5, // poll every 5s (adjustable)
        TimeUnit.SECONDS);

    log.info("Started polling room {} with initial lastId={}", roomId, lastId.get());
  }

  /**
   * Stop polling room.
   */
  public synchronized void stopPolling(Long roomId) {
    roomCallbacks.remove(roomId);
    roomLastIds.remove(roomId);
    log.info("Stopped polling room {}", roomId);
  }

  /**
   * Manually trigger poll for room.
   */
  public void pollNow(Long roomId) {
    pollRoom(roomId);
  }

  private void pollRoom(Long roomId) {
    try {
      Consumer<ChatMessageDto> callback = roomCallbacks.get(roomId);
      if (callback == null) {
        return;
      }

      long lastId = roomLastIds.get(roomId).get();
      List<ChatMessageDto> messages = chatService.loadNewMessages(roomId, lastId);

      if (!messages.isEmpty()) {
        log.debug("Poll room {}: found {} new messages after id {}", roomId, messages.size(), lastId);
        System.out.println("[POLL] Room " + roomId + ": found " + messages.size() + " new messages after id " + lastId);
      }

      for (ChatMessageDto msg : messages) {
        log.debug("Firing callback for message id={} from {}", msg.id(), msg.authorName());
        System.out.println("[POLL] Firing callback for message id=" + msg.id() + " from " + msg.authorName());
        callback.accept(msg);
        roomLastIds.get(roomId).set(msg.id());
      }
    } catch (Exception e) {
      log.warn("Poll error for room {}: {}", roomId, e.getMessage(), e);
      System.out.println("[POLL ERROR] Room " + roomId + ": " + e.getMessage());
    }
  }

  public void shutdown() {
    executor.shutdown();
  }
}
