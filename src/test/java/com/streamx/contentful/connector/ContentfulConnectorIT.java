package com.streamx.contentful.connector;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import java.net.URL;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
@QuarkusTestResource(value = ContentfulConnectorIntegrationTestResource.class,
    restrictToAnnotatedClass = true)
class ContentfulConnectorIT {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Duration MESSAGE_TIMEOUT = Duration.ofSeconds(5);

  @TestHTTPResource("/upload")
  URL uploadUrl;

  @Test
  void createsStreamxCloudEventFromReceivedWebhookAndResolvedContentfulData() throws Exception {
    ContentfulConnectorIntegrationTestResource.clearResources();

    given()
        .header(ContentfulConnector.WEBHOOK_ACTION_TOPIC_HEADER,
            "ContentManagement.Entry.publish")
        .body(webhookBody())
        .when()
        .post(uploadUrl)
        .then()
        .statusCode(allOf(greaterThanOrEqualTo(200), lessThan(300)));

    JsonNode cloudEvent = OBJECT_MAPPER.readTree(awaitSingleResource());
    JsonNode cloudEventData = cloudEvent.path("data");

    assertThat(cloudEvent.path("type").asText())
        .isEqualTo("com.streamx.blueprints.data.published.v1");
    assertThat(cloudEvent.path("subject").asText()).isEqualTo(
        "contentful/spaces/space-1/environments/master/entry-1");
    assertThat(cloudEventData.path("type").asText()).isEqualTo("data/product");

    JsonNode content = OBJECT_MAPPER.readTree(
        Base64.getDecoder().decode(cloudEventData.path("content").asText()));
    assertThat(content.path("fields").path("title").asText()).isEqualTo("Resolved product");
  }

  private static String awaitSingleResource() {
    long deadline = System.nanoTime() + MESSAGE_TIMEOUT.toNanos();

    while (ContentfulConnectorIntegrationTestResource.resources().isEmpty()
        && System.nanoTime() < deadline) {
      try {
        Thread.sleep(25);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Interrupted while waiting for emitted CloudEvent", e);
      }
    }

    assertThat(ContentfulConnectorIntegrationTestResource.resources()).hasSize(1);
    return ContentfulConnectorIntegrationTestResource.resources().get(0);
  }

  private static String webhookBody() {
    return """
        {
          "sys": {
            "id": "entry-1",
            "space": {"sys": {"id": "space-1"}},
            "environment": {"sys": {"id": "master"}},
            "contentType": {"sys": {"id": "pageproduct"}}
          },
          "fields": {
            "title": "Webhook product"
          }
        }
        """;
  }
}
