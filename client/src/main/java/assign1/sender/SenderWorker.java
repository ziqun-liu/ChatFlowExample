package assign1.sender;

import assign1.ClientEndpoint;
import assign1.ClientMain;
import assign1.connection.ConnectionManager;
import assign1.metrics.Metrics;
import assign1.model.ChatMessage;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class SenderWorker implements Runnable{

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

        if (msg == null) {  // 2 milliseconds timeout
          break;
        }

        if (msg == ChatMessage.POISON) {
          break;
        }

        metrics.recordSendAttempt();
        String response = endpoint.sendAndWait(msg.getMessageId(), msg.toJson(), 5000);

        if (response != null) {
          metrics.recordSuccess();
        } else {
          metrics.recordFailure();
        }

      }

    } catch (Exception e) {

      System.err.println("[SenderWorker room=" + roomId + "] " + e.getMessage());

    } finally {

      if (endpoint != null) {
        try {
          endpoint.close();
        } catch (Exception e) {
          throw new RuntimeException();
        }
      }

    }

  }

}
