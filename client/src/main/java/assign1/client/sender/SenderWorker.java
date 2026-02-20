package assign1.client.sender;

import assign1.client.ClientEndpoint;
import assign1.client.connection.ConnectionManager;
import assign1.client.metrics.Metrics;
import assign1.client.model.ChatMessage;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class SenderWorker implements Runnable {

  private static final int MAX_RETRIES = 5;
  private static final long BASE_BACKOFF_MS = 100;

  private final int roomId;
  private final BlockingQueue<ChatMessage> queue;
  private final ConnectionManager connMgr;
  private final Metrics metrics;

  public SenderWorker(int roomId, BlockingQueue<ChatMessage> queue, ConnectionManager connMgr,
      Metrics metrics) {
    this.roomId = roomId;
    this.queue = queue;
    this.connMgr = connMgr;
    this.metrics = metrics;
  }

  @Override
  public void run() {
    ClientEndpoint endpoint = null;

    try {
      endpoint = connMgr.conn(roomId);

      while (true) {

        ChatMessage msg = queue.poll(2, TimeUnit.SECONDS);

        if (msg == null || msg == ChatMessage.POISON) {  // 2 milliseconds timeout
          break;
        }

        metrics.recordSendAttempt();
        endpoint = sendWithRetry(endpoint, msg);

      }

    } catch (Exception e) {
      System.err.println("[SenderWorker room=" + roomId + "] " + e.getMessage());
    } finally {

      if (endpoint != null) {
        connMgr.release(endpoint, roomId);
      }

    }

  }

  private String send(ClientEndpoint endpoint, ChatMessage msg) throws Exception {
    if (!endpoint.isOpen()) {
      throw new IOException("Connection dropped");
    }

    return endpoint.sendAndWait(msg.getMessageId(), msg.toJson(), 5000);
  }

  private ClientEndpoint sendWithRetry(ClientEndpoint endpoint, ChatMessage msg)
      throws InterruptedException {
    for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {

      try {

        long startMs = System.currentTimeMillis();
        String response = this.send(endpoint, msg);
        if (response != null) {
          long latencyMs = System.currentTimeMillis() - startMs;
          int statusCode = "OK".equals(parseStatus(response)) ? 200 : 400;
          metrics.recordSuccess(statusCode);
          metrics.recordLatency(latencyMs);
          metrics.recordRoomSuccess(roomId);
          if (attempt > 0) {
            metrics.recordRetrySuccess();
          }
          return endpoint;
        }
        long backoff = BASE_BACKOFF_MS * (1L << attempt);
        Thread.sleep(backoff);

      } catch (Exception e) {  // Connection error: session closed or websocket has exception

        System.err.println("[Retry " + attempt + " room=" + roomId + "]" + e.getMessage());
        try {
          endpoint = connMgr.reconn(endpoint, roomId);
        } catch (Exception re) {
          throw new RuntimeException();
        }

        long backoff = BASE_BACKOFF_MS * (1L << attempt);
        Thread.sleep(backoff);

      }
    }

    metrics.recordFailure();
    return endpoint;
  }

  private String parseStatus(String responseJson) {
    try {
      JsonObject obj = JsonParser.parseString(responseJson).getAsJsonObject();
      return obj.get("status").getAsString();
    } catch (Exception e) {
      return "UNKNOWN";
    }
  }


}
