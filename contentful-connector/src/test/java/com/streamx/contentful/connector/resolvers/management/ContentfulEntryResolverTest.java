package com.streamx.contentful.connector.resolvers.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
@QuarkusTestResource(InMemoryMessagingTestResource.class)
class ContentfulEntryResolverTest {

  @Inject
  ContentfulEntryResolver resolver;

  @InjectMock
  ContentfulWebClient contentfulWebClient;

  @Test
  void returnsNoLinksWhenThereAreNoEntriesToResolve() {
    assertThat(resolver.resolve(null).await().indefinitely().entries()).isEmpty();
    assertThat(resolver.resolve(Set.of()).await().indefinitely().assets()).isEmpty();
    verify(contentfulWebClient, never()).get(any(), any(), any(), any(), any());
  }

  @Test
  void fetchesEntriesAndIncludedAssetsById() {
    HttpResponse<Buffer> response = response("""
        {
          "items": [
            {
              "sys": {"id": "entry-1", "type": "Entry"},
              "fields": {"title": "Root entry"}
            }
          ],
          "includes": {
            "Entry": [
              {
                "sys": {"id": "entry-2", "type": "Entry"},
                "fields": {"title": "Included entry"}
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
        eq(Map.of("sys.id[in]", "entry-1,entry-2", "include", "10")),
        any(),
        eq("ContentfulEntryResolver")))
        .thenReturn(Uni.createFrom().item(response));

    ResolvedContentfulLinks links = resolver
        .resolve(new LinkedHashSet<>(java.util.List.of("entry-1", "entry-2")))
        .await().indefinitely();

    assertThat(links.entries()).containsOnlyKeys("entry-1", "entry-2");
    assertThat(links.entries().get("entry-2").path("fields").path("title").asText())
        .isEqualTo("Included entry");
    assertThat(links.assets().get("asset-1").path("fields").path("file").path("url").asText())
        .isEqualTo("//images.ctfassets.net/asset-1.png");
  }

  @Test
  void reportsInvalidContentfulEntryResponse() {
    HttpResponse<Buffer> response = response("{invalid-json");
    when(contentfulWebClient.get(any(), any(), any(), any(), any()))
        .thenReturn(Uni.createFrom().item(response));

    assertThatThrownBy(() -> resolver
        .resolve(Set.of("entry-1"))
        .await().indefinitely())
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to parse Contentful entries response");
  }

  private static HttpResponse<Buffer> response(String body) {
    HttpResponse<Buffer> response = mock(HttpResponse.class);
    when(response.bodyAsString()).thenReturn(body);
    return response;
  }
}
