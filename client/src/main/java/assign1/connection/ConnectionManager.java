package assign1.connection;

import assign1.ClientEndpoint;
import assign1.ClientMain;
import assign1.metrics.Metrics;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;

public class ConnectionManager {

  private static final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
  private final String serverBaseUrl;
  private final Metrics metrics;
  // roomId -> pool of endpoints
  private final ConcurrentHashMap<Integer, BlockingQueue<ClientEndpoint>> pool = new ConcurrentHashMap<>();
  private static final int POOL_SIZE = 6;

  public ConnectionManager(String serverBaseUrl, Metrics metrics) {
    this.serverBaseUrl = serverBaseUrl;
    this.metrics = metrics;
  }

  public ClientEndpoint conn(int roomId) throws Exception {
    BlockingQueue<ClientEndpoint> roomPool = pool.computeIfAbsent(roomId, k -> new LinkedBlockingQueue<>(
        POOL_SIZE));

    ClientEndpoint endpoint = roomPool.poll();
    if (endpoint != null && endpoint.isOpen()) {
      return endpoint;
    }

    return createConn(roomId);
  }

  public void release(ClientEndpoint endpoint, int roomId) {
    if (!endpoint.isOpen()) {
      return;
    }

    BlockingQueue<ClientEndpoint> roomPoll = pool.get(roomId);
    if (roomPoll == null || !roomPoll.offer(endpoint)) {
      try {
        endpoint.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public ClientEndpoint createConn(int roomId) throws Exception {
    ClientEndpoint endpoint = new ClientEndpoint();

    container.connectToServer(endpoint, new URI(serverBaseUrl + roomId));

    if (!endpoint.isOpen()) {
      throw new RuntimeException("Failed to connect to " + serverBaseUrl + roomId);
    }

    this.metrics.recordConnection();
    return endpoint;
  }

  public ClientEndpoint reconn(ClientEndpoint oldEndpoint, int roomId) throws Exception {
    try {
      oldEndpoint.close();
    } catch (Exception e) {
    }

    this.metrics.recordReconnection();
    return this.createConn(roomId);
  }

}
