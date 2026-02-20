package assign1;

import assign1.connection.ConnectionManager;
import assign1.metrics.Metrics;
import assign1.model.ChatMessage;
import assign1.producer.MessageGenerator;
import assign1.sender.SenderWorker;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;

public class ClientMain {

  private static final String WS_URI = "ws://54.148.180.35:8080/server/chat/";
  private static final int TOTAL_MESSAGES = 500_000;

  private static final int WARMUP_THREADS = 32;
  private static final int MESSAGE_PER_THREAD = 1000;
  private static final int NUM_SENDERS = 120;
  private static final int NUM_ROOMS = 20;
  private static final int QUEUE_CAPACITY = 10_000;

  public static void main(String[] args) throws Exception {
    runWarmup();
    runMainPhase();
  }

  // ============================== Warmup ==============================
  private static void runWarmup() throws Exception {
    int warmupTotal = WARMUP_THREADS * MESSAGE_PER_THREAD;  // 32_000
    BlockingQueue<ChatMessage> sharedQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    Thread warmupGenerator = new Thread(new MessageGenerator(sharedQueue, warmupTotal),
        "warmup-producer");
    warmupGenerator.start();
    Thread.sleep(500);

    Metrics warmupMetrics = new Metrics();
    ConnectionManager connMgr = new ConnectionManager(WS_URI, warmupMetrics);
    ExecutorService warmupExecutor = Executors.newFixedThreadPool(WARMUP_THREADS);

    warmupMetrics.start();

    // Warmup has 32 threads
    for (int threadId = 0; threadId < WARMUP_THREADS; threadId++) {
      int roomId = threadId % NUM_ROOMS + 1;  // 0-31 mod 20 -> room 0-19 -> room 1-20

      // Each thread submit a task
      warmupExecutor.submit(() -> {
        // Each thread creates a session
        ClientEndpoint endpoint = null;
        try {

          endpoint = connMgr.conn(roomId);

          // Each thread polls 1000 messages from queue
          for (int i = 0; i < MESSAGE_PER_THREAD; i++) {
            ChatMessage msg = sharedQueue.poll(2, TimeUnit.SECONDS);
            if (msg == null) {
              break;
            }

            // Each thread send 1000 messages to server
            warmupMetrics.recordSendAttempt();
            String responseChatMessage = endpoint.sendAndWait(msg.getMessageId(), msg.toJson(),
                5000);
            if (responseChatMessage != null) {
              warmupMetrics.recordSuccess();
            } else {
              warmupMetrics.recordFailure();
            }
          }

        } catch (Exception e) {

          System.err.println("[Warmup] " + e.getMessage());

        } finally {

          if (endpoint != null) {
            try {
              endpoint.close();
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          }

        }

      });

    }

    warmupExecutor.shutdown();
    warmupExecutor.awaitTermination(10, TimeUnit.MINUTES);
    warmupMetrics.stop();

    warmupGenerator.join();
    System.out.println("Warmup complete.");
    warmupMetrics.summary("Warmup");
  }


  // ============================== Main Phase ==============================
  private static void runMainPhase() throws Exception {

    // roomId -> BlockingQueue
    Map<Integer, BlockingQueue<ChatMessage>> queues = new HashMap<>();
    for (int roomId = 1; roomId <= NUM_ROOMS; roomId++) {
      queues.put(roomId, new LinkedBlockingQueue<>(QUEUE_CAPACITY));
    }

    Metrics mainMetrics = new Metrics();
    ConnectionManager mainConnMgr = new ConnectionManager(WS_URI, mainMetrics);

    Thread mainGenerator = new Thread(new MessageGenerator(queues, TOTAL_MESSAGES),
        "main-phase-producer");
    mainGenerator.start();
    Thread.sleep(500);

    mainMetrics.start();

    ExecutorService mainExecutor = Executors.newFixedThreadPool(NUM_SENDERS);
    for (int workerId = 0; workerId < NUM_SENDERS; workerId++) {
      int roomId = workerId % NUM_ROOMS + 1;
      mainExecutor.submit(new SenderWorker(roomId, queues.get(roomId), mainConnMgr, mainMetrics));
    }

    mainGenerator.join();

    for (int workerId = 0; workerId < NUM_SENDERS; workerId++) {
      int roomId = workerId % NUM_ROOMS + 1;
      queues.get(roomId).put(ChatMessage.POISON);
    }

    mainExecutor.shutdown();
    mainExecutor.awaitTermination(10, TimeUnit.MINUTES);
    mainMetrics.stop();

    mainGenerator.join();
    System.out.println("Main Phase completed");
    mainMetrics.summary("Main Phase");

  }


}
