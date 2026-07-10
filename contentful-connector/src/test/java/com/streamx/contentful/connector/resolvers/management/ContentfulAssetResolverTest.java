package com.streamx.contentful.connector.resolvers.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamx.contentful.connector.InMemoryMessagingTestResource;
import com.streamx.contentful.connector.client.ContentfulWebClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import jakarta.inject.Inject;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(value = InMemoryMessagingTestResource.class, restrictToAnnotatedClass = true)
class ContentfulAssetResolverTest {

  @Inject
  ContentfulAssetResolver resolver;

  @InjectMock
  ContentfulWebClient contentfulWebClient;

  @Test
  void returnsNoAssetsWhenThereAreNoLinksToResolve() {
    assertThat(resolver.resolve(null).await().indefinitely()).isEmpty();
    assertThat(resolver.resolve(Set.of()).await().indefinitely()).isEmpty();
    verify(contentfulWebClient, never()).get(any(), any(), any(), any(), any());
  }

  @Test
  void fetchesAssetsByIdAndReturnsThemIndexedByContentfulId() {
    HttpResponse<Buffer> response = response("""
        {
          "items": [
            {
              "sys": {"id": "asset-1", "type": "Asset"},
              "fields": {"file": {"url": "//images.ctfassets.net/asset-1.png"}}
            },
            {
              "sys": {"id": "asset-2", "type": "Asset"},
              "fields": {"file": {"url": "//images.ctfassets.net/asset-2.png"}}
            }
          ]
        }
        """);
    when(contentfulWebClient.get(
        eq("https://cdn.contentful.com/spaces/space-1/environments/master/assets"),
        eq("token"),
        eq(Map.of("sys.id[in]", "asset-1,asset-2")),
        any(),
        eq("ContentfulAssetResolver")))
        .thenReturn(Uni.createFrom().item(response));

    Map<String, JsonNode> assets = resolver
        .resolve(new LinkedHashSet<>(java.util.List.of("asset-1", "asset-2")))
        .await().indefinitely();

    assertThat(assets).containsOnlyKeys("asset-1", "asset-2");
    assertThat(assets.get("asset-1").path("fields").path("file").path("url").asText())
        .isEqualTo("//images.ctfassets.net/asset-1.png");
  }

  @Test
  void returnsNoAssetsWhenContentfulResponseDoesNotContainItemsArray() {
    HttpResponse<Buffer> response = response("{\"items\": {}}");
    when(contentfulWebClient.get(any(), any(), any(), any(), any()))
        .thenReturn(Uni.createFrom().item(response));

    Map<String, JsonNode> assets = resolver
        .resolve(Set.of("asset-1"))
        .await().indefinitely();

    assertThat(assets).isEmpty();
  }

  @Test
  void reportsInvalidContentfulAssetResponse() {
    HttpResponse<Buffer> response = response("{invalid-json");
    when(contentfulWebClient.get(any(), any(), any(), any(), any()))
        .thenReturn(Uni.createFrom().item(response));

    assertThatThrownBy(() -> resolver
        .resolve(Set.of("asset-1"))
        .await().indefinitely())
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to parse Contentful assets response");
  }

  private static HttpResponse<Buffer> response(String body) {
    HttpResponse<Buffer> response = mock(HttpResponse.class);
    when(response.bodyAsString()).thenReturn(body);
    return response;
  }
}
