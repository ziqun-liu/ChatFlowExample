package assign1;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.websocket.*;

@javax.websocket.ClientEndpoint
public class ClientEndpoint {

  private volatile CountDownLatch clientLatch;
  private volatile String lastResponse;
  private volatile String expectedMessageId;
  private Session session;

  @OnOpen
  public void onOpen(Session session) {

    this.session = session;

    // System.out.println("Session " + session.getId() + " connected to server");

  }

  @OnMessage
  public void onMessage(String message) {
    /**
     * After sendAndWait, the message gets sent to the server.
     * onMessage is triggered when the server broadcast back to this session.
     * Verify the messageId and Count down the CountDownLatch here.
     * The reason to count down here is that we need to block the main thread in Main.
     */
    // System.out.println("msg sent: " + message);

    if (this.expectedMessageId != null && message.contains(this.expectedMessageId)) {
      this.lastResponse = message;
      CountDownLatch latch = this.clientLatch;
      if (latch != null) {
        latch.countDown();
      }
    }

  }

  @OnClose
  public void onClose(Session session, CloseReason reason) {
    // System.out.println("Session closed: " + reason);
  }

  public void close() throws IOException {
    if (this.isOpen()) {
      this.session.close();
    }
  }

  public boolean isOpen() {
    return this.session != null && this.session.isOpen();
  }

  public String sendAndWait(String messageId, String json, long timeoutMs)
      /*
      Await the message round trip
       */
      throws IOException, InterruptedException {
    this.expectedMessageId = messageId;
    this.clientLatch = new CountDownLatch(1);
    this.lastResponse = null;

    this.session.getBasicRemote().sendText(json);

    this.clientLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
    return lastResponse;
  }

  @OnError
  public void onError(Session session, Throwable exception) {
    System.err.println("[WS Error] " + exception.getMessage());
  }
}
