package com.streamx.contentful.connector.contentful;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;

import com.streamx.contentful.connector.utils.ContentfulJsonUtils;
import org.junit.jupiter.api.Test;

class ContentfulJsonUtilsTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void identifiesContentfulAssetAndEntryLinks() throws Exception {
    JsonNode assetLink = json("""
        {"sys": {"id": "asset-1", "type": "Link", "linkType": "Asset"}}
        """);
    JsonNode entryLink = json("""
        {"sys": {"id": "entry-1", "type": "Link", "linkType": "Entry"}}
        """);
    JsonNode entry = json("""
        {"sys": {"id": "entry-2", "type": "Entry"}}
        """);

    assertThat(ContentfulJsonUtils.isLink(assetLink)).isTrue();
    assertThat(ContentfulJsonUtils.isAssetLink(assetLink)).isTrue();
    assertThat(ContentfulJsonUtils.isEntryLink(assetLink)).isFalse();
    assertThat(ContentfulJsonUtils.isEntryLink(entryLink)).isTrue();
    assertThat(ContentfulJsonUtils.isLink(entry)).isFalse();
    assertThat(ContentfulJsonUtils.id(entry)).isEqualTo("entry-2");
  }

  @Test
  void indexesOnlyContentfulItemsWithIds() throws Exception {
    JsonNode items = json("""
        [
          {"sys": {"id": "entry-1"}, "fields": {"title": "Entry 1"}},
          {"sys": {"id": ""}, "fields": {"title": "Missing id"}}
        ]
        """);
    Map<String, JsonNode> indexed = new HashMap<>();

    ContentfulJsonUtils.forEachItem(items, indexed::put);
    ContentfulJsonUtils.putById(indexed, json("""
        {"sys": {"id": "entry-2"}, "fields": {"title": "Entry 2"}}
        """));
    ContentfulJsonUtils.putById(indexed, json("""
        {"sys": {"id": ""}, "fields": {"title": "Ignored"}}
        """));

    assertThat(indexed).containsOnlyKeys("entry-1", "entry-2");
  }

  @Test
  void ignoresNonArrayItemCollections() throws Exception {
    Map<String, JsonNode> indexed = new HashMap<>();

    ContentfulJsonUtils.forEachItem(json("{}"), indexed::put);

    assertThat(indexed).isEmpty();
  }

  private static JsonNode json(String value) throws Exception {
    return OBJECT_MAPPER.readTree(value);
  }
}
