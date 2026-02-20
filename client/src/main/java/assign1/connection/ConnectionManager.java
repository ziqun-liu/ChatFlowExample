package assign1.connection;

import assign1.ClientEndpoint;
import assign1.metrics.Metrics;
import java.net.URI;
import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;

public class ConnectionManager {

  private static final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
  private final String serverBaseUrl;
  private final Metrics metrics;

  public ConnectionManager(String serverBaseUrl, Metrics metrics) {
    this.serverBaseUrl = serverBaseUrl;
    this.metrics = metrics;
  }

  public ClientEndpoint conn(int roomId) throws Exception {
    ClientEndpoint endpoint = new ClientEndpoint();

    container.connectToServer(endpoint, new URI(serverBaseUrl + roomId));

    if (!endpoint.isOpen()) {
      throw new RuntimeException("Failed to connect to " + serverBaseUrl + roomId);
    }

    this.metrics.recordConnection();
    return endpoint;
  }

  public ClientEndpoint reconn(ClientEndpoint oldEndpoint, int roomId) throws Exception {
    oldEndpoint.close();

    this.metrics.recordReconnection();
    return this.conn(roomId);
  }

}
