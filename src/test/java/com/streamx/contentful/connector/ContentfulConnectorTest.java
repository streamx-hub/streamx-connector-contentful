package com.streamx.contentful.connector;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.contentful.connector.resolvers.delivery.ContentfulContentDeliveryApiResolver;
import io.cloudevents.CloudEvent;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.net.URL;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(value = InMemoryOutgoingMessagingTestResource.class,
    restrictToAnnotatedClass = true)
class ContentfulConnectorTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Duration MESSAGE_TIMEOUT = Duration.ofSeconds(5);

  @Inject
  @Any
  InMemoryConnector connector;

  @InjectMock
  ContentfulContentDeliveryApiResolver contentfulPayloadResolver;

  @TestHTTPResource("/upload")
  URL uploadUrl;

  @BeforeEach
  void clearSink() {
    resources().clear();
  }

  @Test
  void createsStreamxCloudEventFromReceivedWebhookAndResolvedContentfulData() throws Exception {
    JsonNode resolvedPayload = json("""
        {
          "sys": {
            "id": "entry-1",
            "space": {"sys": {"id": "space-1"}},
            "environment": {"sys": {"id": "master"}},
            "contentType": {"sys": {"id": "pageProduct"}}
          },
          "fields": {
            "title": "Resolved product"
          }
        }
        """);

    when(contentfulPayloadResolver.getCompleteDataFor(org.mockito.ArgumentMatchers.any()))
        .thenReturn(Uni.createFrom().item(resolvedPayload));

    given()
        .header(ContentfulConnector.WEBHOOK_ACTION_TOPIC_HEADER,
            "ContentManagement.Entry.publish")
        .body(webhookBody())
        .when()
        .post(uploadUrl)
        .then()
        .statusCode(allOf(greaterThanOrEqualTo(200), lessThan(300)));

    CloudEvent cloudEvent = awaitSingleCloudEvent();
    JsonNode cloudEventData = OBJECT_MAPPER.readTree(cloudEvent.getData().toBytes());

    assertThat(cloudEvent.getType()).isEqualTo("com.streamx.blueprints.data.published.v1");
    assertThat(cloudEvent.getSubject()).isEqualTo(
        "contentful/spaces/space-1/environments/master/entry-1");
    assertThat(cloudEventData.path("type").asText()).isEqualTo("data/product");
    JsonNode content = OBJECT_MAPPER.readTree(
        Base64.getDecoder().decode(cloudEventData.path("content").asText()));
    assertThat(content.path("fields").path("title").asText()).isEqualTo("Resolved product");
  }

  private InMemorySink<CloudEvent> resources() {
    return connector.sink(Channels.RESOURCES);
  }

  private CloudEvent awaitSingleCloudEvent() {
    InMemorySink<CloudEvent> sink = resources();
    long deadline = System.nanoTime() + MESSAGE_TIMEOUT.toNanos();

    while (sink.received().isEmpty() && System.nanoTime() < deadline) {
      try {
        Thread.sleep(25);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Interrupted while waiting for emitted CloudEvent", e);
      }
    }

    assertThat(sink.received()).hasSize(1);
    return sink.received().get(0).getPayload();
  }

  private static String webhookBody() {
    return """
        {
          "sys": {
            "id": "entry-1",
            "space": {"sys": {"id": "space-1"}},
            "environment": {"sys": {"id": "master"}},
            "contentType": {"sys": {"id": "pageProduct"}}
          },
          "fields": {
            "title": "Webhook product"
          }
        }
        """;
  }

  private static JsonNode json(String value) throws Exception {
    return OBJECT_MAPPER.readTree(value);
  }
}
