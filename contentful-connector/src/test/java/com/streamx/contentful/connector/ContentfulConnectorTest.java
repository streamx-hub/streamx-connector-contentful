package com.streamx.contentful.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.contentful.connector.resolvers.delivery.ContentfulContentDeliveryApiResolver;
import io.cloudevents.CloudEvent;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.reactivemessaging.http.runtime.IncomingHttpMetadata;
import io.smallrye.mutiny.Uni;
import io.vertx.core.MultiMap;
import jakarta.inject.Inject;
import java.util.Base64;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(InMemoryMessagingTestResource.class)
class ContentfulConnectorTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Inject
  ContentfulConnector connector;

  @InjectMock
  ContentfulContentDeliveryApiResolver contentfulPayloadResolver;

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

    CloudEvent cloudEvent = connector.processMessage(webhookBody(),
        metadataWithTopic("ContentManagement.Entry.publish")).await().indefinitely();

    JsonNode cloudEventData = OBJECT_MAPPER.readTree(cloudEvent.getData().toBytes());

    assertThat(cloudEvent.getType()).isEqualTo("com.streamx.blueprints.data.published.v1");
    assertThat(cloudEvent.getSubject()).isEqualTo(
        "contentful/spaces/space-1/environments/master/entry-1");
    assertThat(cloudEventData.path("type").asText()).isEqualTo("data/product");
    JsonNode content = OBJECT_MAPPER.readTree(
        Base64.getDecoder().decode(cloudEventData.path("content").asText()));
    assertThat(content.path("fields").path("title").asText()).isEqualTo("Resolved product");
  }

  private static IncomingHttpMetadata metadataWithTopic(String contentfulTopic) {
    IncomingHttpMetadata metadata = mock(IncomingHttpMetadata.class);
    MultiMap headers = MultiMap.caseInsensitiveMultiMap()
        .add(ContentfulConnector.WEBHOOK_ACTION_TOPIC_HEADER, contentfulTopic);

    when(metadata.getHeaders()).thenReturn(headers);
    return metadata;
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
