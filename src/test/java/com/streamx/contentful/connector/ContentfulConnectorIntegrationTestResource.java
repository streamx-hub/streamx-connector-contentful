package com.streamx.contentful.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.DevServicesContext;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

public class ContentfulConnectorIntegrationTestResource implements QuarkusTestResourceLifecycleManager,
    DevServicesContext.ContextAware {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5))
      .build();

  private static final int WIREMOCK_PORT = 8080;
  private static final String CONTENTFUL_ALIAS = "contentful-mock";
  private static final String STREAMX_ALIAS = "streamx-mock";
  private static final String WIREMOCK_IMAGE = "wiremock/wiremock:3.9.1";
  private static final Duration WIREMOCK_STARTUP_TIMEOUT = Duration.ofSeconds(30);

  private static String contentfulContainerId;
  private static String streamxContainerId;
  private static int contentfulHostPort;
  private static int streamxHostPort;

  private Optional<String> containerNetworkId = Optional.empty();

  @Override
  public void setIntegrationTestContext(DevServicesContext context) {
    containerNetworkId = context.containerNetworkId();
  }

  @Override
  public Map<String, String> start() {
    try {
      String network = containerNetworkId.orElseThrow(
          () -> new IllegalStateException("Quarkus integration test Docker network is missing"));

      contentfulContainerId = startWireMockContainer(CONTENTFUL_ALIAS, network);
      streamxContainerId = startWireMockContainer(STREAMX_ALIAS, network);
      contentfulHostPort = mappedHostPort(contentfulContainerId);
      streamxHostPort = mappedHostPort(streamxContainerId);

      waitForWireMock(contentfulHostPort);
      waitForWireMock(streamxHostPort);

      stubContentfulEntries();
      stubStreamxResources();

      return Map.of(
          "streamx.contentful.connector.resource-type-mappings.\"pageproduct\"", "data/product",
          "streamx.contentful.connector.space-id", "space-1",
          "streamx.contentful.connector.environment", "master",
          "streamx.contentful.connector.token", "token",
          "streamx.contentful.connector.contentful-entries-url", containerUrl(CONTENTFUL_ALIAS,
              "/entries"),
          "streamx.contentful.connector.contentful-assets-url", containerUrl(CONTENTFUL_ALIAS,
              "/assets"),
          "mp.messaging.outgoing.resources.url", containerUrl(STREAMX_ALIAS, "/resources")
      );
    } catch (IOException e) {
      throw new RuntimeException("Failed to start integration test HTTP resources", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Failed to start integration test HTTP resources", e);
    }
  }

  @Override
  public void stop() {
    stopContainer(contentfulContainerId);
    stopContainer(streamxContainerId);
  }

  static void clearResources() {
    try {
      if (streamxHostPort > 0) {
        send("DELETE", hostUrl(streamxHostPort, "/__admin/requests"), "");
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to clear StreamX mock requests", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Failed to clear StreamX mock requests", e);
    }
  }

  static List<String> resources() {
    try {
      if (streamxHostPort == 0) {
        return List.of();
      }

      String body = send("GET", hostUrl(streamxHostPort,
          "/__admin/requests?method=POST&url=/resources"), "");
      JsonNode requests = OBJECT_MAPPER.readTree(body).path("requests");
      return StreamSupport.stream(requests.spliterator(), false)
          .map(request -> request.path("request").path("body").asText())
          .toList();
    } catch (IOException e) {
      throw new RuntimeException("Failed to read StreamX mock requests", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Failed to read StreamX mock requests", e);
    }
  }

  private static void stubContentfulEntries() throws IOException, InterruptedException {
    String body = """
        {
          "request": {
            "method": "GET",
            "urlPath": "/entries"
          },
          "response": {
            "status": 200,
            "headers": {
              "Content-Type": "application/json"
            },
            "jsonBody": {
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
          }
        }
        """;
    send("POST", hostUrl(contentfulHostPort, "/__admin/mappings"), body);
  }

  private static void stubStreamxResources() throws IOException, InterruptedException {
    String body = """
        {
          "request": {
            "method": "POST",
            "urlPath": "/resources"
          },
          "response": {
            "status": 200,
            "headers": {
              "Content-Type": "text/plain"
            },
            "body": "ACK"
          }
        }
        """;
    send("POST", hostUrl(streamxHostPort, "/__admin/mappings"), body);
  }

  private static String startWireMockContainer(String alias, String network)
      throws IOException, InterruptedException {
    return docker(
        "run",
        "-d",
        "--rm",
        "--network", network,
        "--network-alias", alias,
        "-p", "127.0.0.1::" + WIREMOCK_PORT,
        WIREMOCK_IMAGE
    );
  }

  private static int mappedHostPort(String containerId) throws IOException, InterruptedException {
    String portMapping = docker("port", containerId, WIREMOCK_PORT + "/tcp");
    int separator = portMapping.lastIndexOf(':');
    if (separator < 0 || separator == portMapping.length() - 1) {
      throw new IOException("Unexpected Docker port mapping: " + portMapping);
    }
    return Integer.parseInt(portMapping.substring(separator + 1));
  }

  private static void waitForWireMock(int hostPort) throws IOException, InterruptedException {
    Instant deadline = Instant.now().plus(WIREMOCK_STARTUP_TIMEOUT);
    IOException lastFailure = null;

    while (Instant.now().isBefore(deadline)) {
      try {
        send("GET", hostUrl(hostPort, "/__admin/mappings"), "");
        return;
      } catch (IOException e) {
        lastFailure = e;
        Thread.sleep(250);
      }
    }

    throw new IOException("Timed out waiting for WireMock on localhost:" + hostPort, lastFailure);
  }

  private static String send(String method, String url, String body)
      throws IOException, InterruptedException {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(5));

    if ("GET".equals(method)) {
      request.GET();
    } else {
      request.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
          .header("Content-Type", "application/json");
    }

    HttpResponse<String> response = HTTP_CLIENT.send(request.build(),
        HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IOException("WireMock admin request failed with status " + response.statusCode()
          + ": " + response.body());
    }

    return response.body();
  }

  private static String containerUrl(String alias, String path) {
    return "http://" + alias + ":" + WIREMOCK_PORT + path;
  }

  private static String hostUrl(int port, String path) {
    return "http://localhost:" + port + path;
  }

  private static String docker(String... command) throws IOException, InterruptedException {
    List<String> fullCommand = new ArrayList<>();
    fullCommand.add("docker");
    fullCommand.addAll(Arrays.asList(command));

    Process process = new ProcessBuilder(fullCommand)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new IOException("Docker command failed (" + String.join(" ", fullCommand)
          + ") with exit code " + exitCode + ": " + output);
    }
    return output.trim();
  }

  private static void stopContainer(String containerId) {
    if (containerId != null && !containerId.isBlank()) {
      try {
        docker("stop", containerId);
      } catch (IOException e) {
        throw new RuntimeException("Failed to stop WireMock container " + containerId, e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Failed to stop WireMock container " + containerId, e);
      }
    }
  }
}
