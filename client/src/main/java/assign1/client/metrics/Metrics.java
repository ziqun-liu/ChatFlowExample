package assign1.client.metrics;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public class Metrics {

  private final AtomicLong successCount = new AtomicLong(0);
  private final AtomicLong failureCount = new AtomicLong(0);
  private final AtomicLong sendAttempts = new AtomicLong(0);
  private final AtomicLong connectionCount = new AtomicLong(0);
  private final AtomicLong reconnectionCount = new AtomicLong(0);
  private final AtomicLong retrySuccessCount = new AtomicLong(0);
  private final ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
  private final ConcurrentHashMap<Integer, AtomicLong> roomSuccess = new ConcurrentHashMap<>();


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

  public void recordLatency(long latencyMs) {
    latencies.add(latencyMs);
  }

  public void recordRoomSuccess(int roomId) {
    roomSuccess.computeIfAbsent(roomId, k -> new AtomicLong(0)).incrementAndGet();
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

  public void latencyStats() {
    long[] sorted = latencies.stream().mapToLong(Long::longValue).sorted().toArray();
    if (sorted.length == 0) {
      System.out.println("  No latency data.");
      return;
    }
    double mean = Arrays.stream(sorted).average().orElse(0);
    long median = sorted[sorted.length / 2];
    long p95 = sorted[(int) (sorted.length * 0.95)];
    long p99 = sorted[(int) (sorted.length * 0.99)];
    long min = sorted[0];
    long max = sorted[sorted.length - 1];

    System.out.printf("  Mean latency        : %.2f ms%n", mean);
    System.out.printf("  Median latency      : %d ms%n", median);
    System.out.printf("  p95 latency         : %d ms%n", p95);
    System.out.printf("  p99 latency         : %d ms%n", p99);
    System.out.printf("  Min latency         : %d ms%n", min);
    System.out.printf("  Max latency         : %d ms%n", max);
  }

  public void roomThroughput() {
    double elapsed = elapsedSeconds();
    System.out.println("  Per-room throughput:");
    roomSuccess.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(e -> System.out.printf("    Room %2d: %,.2f msg/s%n",
            e.getKey(), e.getValue().get() / elapsed));
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