package com.streamx.contentful.connector;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.DevServicesContext;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class ContentfulConnectorIntegrationTestResource implements QuarkusTestResourceLifecycleManager,
    DevServicesContext.ContextAware {

  private static final List<String> RESOURCE_REQUESTS = new CopyOnWriteArrayList<>();

  private HttpServer contentfulServer;
  private HttpServer streamxServer;
  private String appReachableHost = "localhost";

  @Override
  public void setIntegrationTestContext(DevServicesContext context) {
    appReachableHost = context.containerNetworkId().isPresent()
        ? "host.docker.internal"
        : "localhost";
  }

  @Override
  public Map<String, String> start() {
    try {
      contentfulServer = HttpServer.create(new InetSocketAddress(0), 0);
      contentfulServer.createContext("/entries", this::handleContentfulEntries);
      contentfulServer.start();

      streamxServer = HttpServer.create(new InetSocketAddress(0), 0);
      streamxServer.createContext("/resources", this::handleResources);
      streamxServer.start();

      return Map.of(
          "streamx.contentful.connector.resource-type-mappings.\"pageproduct\"", "data/product",
          "streamx.contentful.connector.space-id", "space-1",
          "streamx.contentful.connector.environment", "master",
          "streamx.contentful.connector.token", "token",
          "streamx.contentful.connector.contentful-entries-url", contentfulUrl("/entries"),
          "streamx.contentful.connector.contentful-assets-url", contentfulUrl("/assets"),
          "mp.messaging.outgoing.resources.url", streamxUrl("/resources")
      );
    } catch (IOException e) {
      throw new RuntimeException("Failed to start integration test HTTP resources", e);
    }
  }

  @Override
  public void stop() {
    if (contentfulServer != null) {
      contentfulServer.stop(0);
    }
    if (streamxServer != null) {
      streamxServer.stop(0);
    }
    RESOURCE_REQUESTS.clear();
  }

  static void clearResources() {
    RESOURCE_REQUESTS.clear();
  }

  static List<String> resources() {
    return List.copyOf(RESOURCE_REQUESTS);
  }

  private String contentfulUrl(String path) {
    return "http://" + appReachableHost + ":" + contentfulServer.getAddress().getPort() + path;
  }

  private String streamxUrl(String path) {
    return "http://" + appReachableHost + ":" + streamxServer.getAddress().getPort() + path;
  }

  private void handleContentfulEntries(HttpExchange exchange) throws IOException {
    String body = """
        {
          "items": [
            {
              "sys": {
                "id": "entry-1",
                "space": {"sys": {"id": "space-1"}},
                "environment": {"sys": {"id": "master"}},
                "contentType": {"sys": {"id": "pageproduct"}}
              },
              "fields": {
                "title": "Resolved product"
              }
            }
          ]
        }
        """;
    respond(exchange, 200, body);
  }

  private void handleResources(HttpExchange exchange) throws IOException {
    RESOURCE_REQUESTS.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    respond(exchange, 200, "ACK");
  }

  private void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
