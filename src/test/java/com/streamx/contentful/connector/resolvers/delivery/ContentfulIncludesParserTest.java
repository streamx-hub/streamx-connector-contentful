package com.streamx.contentful.connector.resolvers.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContentfulIncludesParserTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void returnsNoIncludesWhenContentfulResponseHasNoIncludesSection() {
    ContentfulIncludesParser parser = new ContentfulIncludesParser();

    assertThat(parser.parse(null)).isEmpty();
    assertThat(parser.parse(OBJECT_MAPPER.missingNode())).isEmpty();
  }

  @Test
  void indexesIncludedEntriesAndAssetsById() throws Exception {
    ContentfulIncludesParser parser = new ContentfulIncludesParser();

    Map<String, JsonNode> includes = parser.parse(OBJECT_MAPPER.readTree("""
        {
          "Entry": [
            {
              "sys": {"id": "entry-1", "type": "Entry"},
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
        """));

    assertThat(includes).containsOnlyKeys("entry-1", "asset-1");
    assertThat(includes.get("entry-1").path("fields").path("title").asText())
        .isEqualTo("Included entry");
  }
}
