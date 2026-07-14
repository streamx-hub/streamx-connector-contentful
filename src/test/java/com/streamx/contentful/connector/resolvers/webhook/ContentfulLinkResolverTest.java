package com.streamx.contentful.connector.resolvers.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.contentful.connector.resolvers.management.ContentfulAssetResolver;
import com.streamx.contentful.connector.resolvers.management.ContentfulEntryResolver;
import com.streamx.contentful.connector.resolvers.management.ResolvedContentfulLinks;
import io.smallrye.mutiny.Uni;
import java.util.Map;
import java.util.Set;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

class ContentfulLinkResolverTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void replacesWebhookAssetAndEntryLinksWithFetchedContentfulData() throws Exception {
    ContentfulAssetResolver assetResolver = mock(ContentfulAssetResolver.class);
    ContentfulEntryResolver entryResolver = mock(ContentfulEntryResolver.class);

    when(assetResolver.resolve(eq(Set.of("asset-1"))))
        .thenReturn(Uni.createFrom().item(Map.of("asset-1", json("""
            {
              "sys": {"id": "asset-1", "type": "Asset"},
              "fields": {"file": {"url": "//images.ctfassets.net/asset-1.png"}}
            }
            """))));

    when(entryResolver.resolve(eq(Set.of("entry-1"))))
        .thenReturn(Uni.createFrom().item(new ResolvedContentfulLinks(
            Map.of("entry-1", json("""
                {
                  "sys": {"id": "entry-1", "type": "Entry"},
                  "fields": {
                    "title": "Related entry",
                    "thumbnail": {
                      "sys": {"id": "asset-2", "type": "Link", "linkType": "Asset"}
                    }
                  }
                }
                """)),
            Map.of("asset-2", json("""
                {
                  "sys": {"id": "asset-2", "type": "Asset"},
                  "fields": {"file": {"url": "//images.ctfassets.net/asset-2.png"}}
                }
                """)))));

    ContentfulLinkResolver resolver = new ContentfulLinkResolver();
    resolver.log = Logger.getLogger(ContentfulLinkResolver.class);
    resolver.objectMapper = OBJECT_MAPPER;
    resolver.assetResolver = assetResolver;
    resolver.entryResolver = entryResolver;

    JsonNode completedPayload = resolver.getCompleteDataFor(json("""
        {
          "sys": {"id": "root", "type": "Entry"},
          "fields": {
            "hero": {
              "sys": {"id": "asset-1", "type": "Link", "linkType": "Asset"}
            },
            "related": {
              "sys": {"id": "entry-1", "type": "Link", "linkType": "Entry"}
            }
          }
        }
        """)).await().indefinitely();

    assertThat(completedPayload.path("fields").path("hero").path("fields").path("file").path("url").asText())
        .isEqualTo("//images.ctfassets.net/asset-1.png");
    assertThat(completedPayload.path("fields").path("related").path("fields").path("title").asText())
        .isEqualTo("Related entry");
    assertThat(completedPayload.path("fields").path("related").path("fields").path("thumbnail")
        .path("fields").path("file").path("url").asText())
        .isEqualTo("//images.ctfassets.net/asset-2.png");
  }

  private static JsonNode json(String value) throws Exception {
    return OBJECT_MAPPER.readTree(value);
  }
}
