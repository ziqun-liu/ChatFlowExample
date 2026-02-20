package assign1.metrics;

import java.util.concurrent.atomic.AtomicLong;

public class Metrics {

  private final AtomicLong successCount = new AtomicLong(0);
  private final AtomicLong failureCount = new AtomicLong(0);
  private final AtomicLong sendAttempts = new AtomicLong(0);
  private final AtomicLong connectionCount = new AtomicLong(0);
  private final AtomicLong reconnectionCount = new AtomicLong(0);

  private volatile long startNs = 0L;
  private volatile long endNs = 0L;

  public void start() {
    startNs = System.nanoTime();
    endNs = 0L;
  }

  public void stop() {
    endNs = System.nanoTime();
  }

  public void recordSuccess() {
    successCount.incrementAndGet();
  }

  public void recordFailure() {
    failureCount.incrementAndGet();
  }

  public void recordSendAttempt() {
    sendAttempts.incrementAndGet();
  }

  public void recordSendAttempts(long n) {
    sendAttempts.addAndGet(n);
  }

  public void recordConnection() {
    connectionCount.incrementAndGet();
  }

  public void recordReconnection() {
    reconnectionCount.incrementAndGet();
  }

  public long getSuccessCount() { return successCount.get(); }
  public long getFailureCount() { return failureCount.get(); }
  public long getSendAttempts() { return sendAttempts.get(); }
  public long getConnectionCount() { return connectionCount.get(); }
  public long getReconnectionCount() { return reconnectionCount.get(); }
  public long getTotalProcessed() { return successCount.get() + failureCount.get(); }

  public double elapsedSeconds() {
    long end = (endNs == 0L) ? System.nanoTime() : endNs;
    if (startNs == 0L) return 0.0;
    return (end - startNs) / 1_000_000_000.0;
  }

  public double throughput() {
    double s = elapsedSeconds();
    if (s <= 0.0) return 0.0;
    return successCount.get() / s;
  }

  public void summary(String phase) {
    System.out.println("========================================");
    System.out.println("  " + phase);
    System.out.println("========================================");
    System.out.printf("  Successful messages : %,d%n", successCount.get());
    System.out.printf("  Failed messages     : %,d%n", failureCount.get());
    System.out.printf("  Total processed     : %,d%n", getTotalProcessed());
    System.out.printf("  Send attempts       : %,d%n", sendAttempts.get());
    System.out.printf("  Wall time           : %.3f seconds%n", elapsedSeconds());
    System.out.printf("  Throughput          : %,.2f msg/s%n", throughput());
    System.out.printf("  Total connections   : %,d%n", connectionCount.get());
    System.out.printf("  Reconnections       : %,d%n", reconnectionCount.get());
    System.out.println("========================================");
  }
}