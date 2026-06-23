package com.streamx.contentful.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.contentful.connector.configuration.Configuration;
import com.streamx.contentful.connector.resolvers.ContentfulPayloadResolver;
import com.streamx.contentful.connector.services.impl.ContentfulServiceImpl;
import io.cloudevents.CloudEvent;
import io.quarkus.reactivemessaging.http.runtime.IncomingHttpMetadata;
import io.smallrye.mutiny.Uni;
import io.vertx.core.MultiMap;
import java.util.Base64;
import java.util.Map;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

class ContentfulConnectorTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void createsStreamxCloudEventFromReceivedWebhookAndResolvedContentfulData() throws Exception {
    Configuration configuration = mock(Configuration.class);
    ContentfulPayloadResolver payloadResolver = mock(ContentfulPayloadResolver.class);

    when(configuration.topicMappings()).thenReturn(Map.of(
        "ContentManagement.Entry.publish", "com.streamx.blueprints.data.published.v1"));
    when(configuration.resourceTypeMappings()).thenReturn(Map.of(
        "pageProduct", "data/product"));

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

    when(payloadResolver.getCompleteDataFor(org.mockito.ArgumentMatchers.any()))
        .thenReturn(Uni.createFrom().item(resolvedPayload));

    ContentfulConnector connector = new ContentfulConnector();
    ContentfulServiceImpl contentfulService = new ContentfulServiceImpl();
    setField(contentfulService, "configuration", configuration);

    connector.log = Logger.getLogger(ContentfulConnector.class);
    connector.contentfulService = contentfulService;
    connector.contentfulPayloadResolver = payloadResolver;
    connector.configuration = configuration;

    CloudEvent cloudEvent = connector.processMessage(
            webhookBody(),
            metadataWithTopic("ContentManagement.Entry.publish"))
        .await().indefinitely();

    JsonNode cloudEventData = OBJECT_MAPPER.readTree(cloudEvent.getData().toBytes());

    assertThat(cloudEvent.getType()).isEqualTo("com.streamx.blueprints.data.published.v1");
    assertThat(cloudEvent.getSubject())
        .isEqualTo("contentful/spaces/space-1/environments/master/entry-1");
    assertThat(cloudEventData.path("type").asText()).isEqualTo("data/product");
    JsonNode content = OBJECT_MAPPER.readTree(Base64.getDecoder()
        .decode(cloudEventData.path("content").asText()));
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

  private static void setField(Object target, String fieldName, Object value) throws Exception {
    java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
