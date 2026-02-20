package assign1.client.metrics;

import java.util.concurrent.atomic.AtomicLong;

public class Metrics {

  private final AtomicLong successCount = new AtomicLong(0);
  private final AtomicLong failureCount = new AtomicLong(0);
  private final AtomicLong sendAttempts = new AtomicLong(0);
  private final AtomicLong connectionCount = new AtomicLong(0);
  private final AtomicLong reconnectionCount = new AtomicLong(0);
  private final AtomicLong retrySuccessCount = new AtomicLong(0);

  private volatile long startNs = 0L;
  private volatile long endNs = 0L;

  public void start() {
    this.startNs = System.nanoTime();
    this.endNs = 0L;
  }

  public void stop() {
    this.endNs = System.nanoTime();
  }

  public void recordSuccess() {
    this.successCount.incrementAndGet();
  }

  public void recordFailure() {
    this.failureCount.incrementAndGet();
  }

  public void recordSendAttempt() {
    this.sendAttempts.incrementAndGet();
  }

  public void recordSendAttempts(long n) {
    this.sendAttempts.addAndGet(n);
  }

  public void recordConnection() {
    this.connectionCount.incrementAndGet();
  }

  public void recordReconnection() {
    this.reconnectionCount.incrementAndGet();
  }

  public void recordRetrySuccess() {
    this.retrySuccessCount.incrementAndGet();
  }

  public long getSuccessCount() {
    return this.successCount.get();
  }

  public long getFailureCount() {
    return this.failureCount.get();
  }

  public long getSendAttempts() {
    return this.sendAttempts.get();
  }

  public long getConnectionCount() {
    return this.connectionCount.get();
  }

  public long getReconnectionCount() {
    return this.reconnectionCount.get();
  }

  public long getTotalProcessed() {
    return this.successCount.get() + this.failureCount.get();
  }

  public long getRetrySuccessCount() {
    return this.retrySuccessCount.get();
  }

  public double elapsedSeconds() {
    long end = (this.endNs == 0L) ? System.nanoTime() : this.endNs;
    if (this.startNs == 0L) {
      return 0.0;
    }
    return (end - this.startNs) / 1_000_000_000.0;
  }

  public double throughput() {
    double s = elapsedSeconds();
    if (s <= 0.0) {
      return 0.0;
    }
    return this.successCount.get() / s;
  }

  public void summary(String phase) {
    System.out.println("========================================");
    System.out.println("  " + phase);
    System.out.println("========================================");
    System.out.printf("  Successful messages : %,d%n", this.successCount.get());
    System.out.printf("  - First attempt      : %,d%n",
        successCount.get() - retrySuccessCount.get());
    System.out.printf("  - After retry        : %,d%n", retrySuccessCount.get());
    System.out.printf("  Failed messages     : %,d%n", this.failureCount.get());
    System.out.printf("  Total processed     : %,d%n", this.getTotalProcessed());
    System.out.printf("  Send attempts       : %,d%n", this.sendAttempts.get());
    System.out.printf("  Wall time           : %.3f seconds%n", this.elapsedSeconds());
    System.out.printf("  Throughput          : %,.2f msg/s%n", this.throughput());
    System.out.printf("  Total connections   : %,d%n", this.connectionCount.get());
    System.out.printf("  Reconnections       : %,d%n", this.reconnectionCount.get());
    System.out.println("========================================");
  }
}