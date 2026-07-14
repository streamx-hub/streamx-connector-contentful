package com.streamx.contentful.connector.resolvers.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.contentful.connector.InMemoryMessagingTestResource;
import com.streamx.contentful.connector.client.ContentfulWebClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(value = InMemoryMessagingTestResource.class, restrictToAnnotatedClass = true)
class ContentfulContentDeliveryApiResolverTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Inject
  ContentfulContentDeliveryApiResolver resolver;

  @InjectMock
  ContentfulWebClient contentfulWebClient;

  @Test
  void returnsEntryWithContentfulIncludesResolved() throws Exception {
    HttpResponse<Buffer> response = mock(HttpResponse.class);
    when(response.bodyAsString()).thenReturn("""
        {
          "items": [
            {
              "sys": {"id": "root-entry", "type": "Entry"},
              "fields": {
                "hero": {
                  "sys": {"id": "asset-1", "type": "Link", "linkType": "Asset"}
                },
                "related": {
                  "sys": {"id": "entry-1", "type": "Link", "linkType": "Entry"}
                }
              }
            }
          ],
          "includes": {
            "Entry": [
              {
                "sys": {"id": "entry-1", "type": "Entry"},
                "fields": {"title": "Resolved entry"}
              }
            ],
            "Asset": [
              {
                "sys": {"id": "asset-1", "type": "Asset"},
                "fields": {"file": {"url": "//images.ctfassets.net/asset-1.png"}}
              }
            ]
          }
        }
        """);

    when(contentfulWebClient.get(
        eq("https://cdn.contentful.com/spaces/space-1/environments/master/entries"),
        eq("token"),
        eq(Map.of("sys.id", "root-entry", "include", "10")),
        any(),
        eq("ContentfulContentDeliveryApiResolver")))
        .thenReturn(Uni.createFrom().item(response));

    JsonNode completedPayload = resolver.getCompleteDataFor(json("""
        {
          "sys": {"id": "root-entry", "type": "Entry"}
        }
        """)).await().indefinitely();

    assertThat(completedPayload.path("fields").path("hero").path("fields").path("file").path("url").asText())
        .isEqualTo("//images.ctfassets.net/asset-1.png");
    assertThat(completedPayload.path("fields").path("related").path("fields").path("title").asText())
        .isEqualTo("Resolved entry");
  }

  private static JsonNode json(String value) throws Exception {
    return OBJECT_MAPPER.readTree(value);
  }
}
