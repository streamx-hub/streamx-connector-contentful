package com.streamx.contentful.connector.resolvers.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.streamx.contentful.connector.utils.ContentfulJsonUtils;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class CachedContentfulLinksRewriter {

  private final ObjectMapper objectMapper;
  private final Map<String, JsonNode> assetCache;
  private final Map<String, JsonNode> entryCache;

  CachedContentfulLinksRewriter(ObjectMapper objectMapper, Map<String, JsonNode> assetCache,
      Map<String, JsonNode> entryCache) {
    this.objectMapper = objectMapper;
    this.assetCache = assetCache;
    this.entryCache = entryCache;
  }

  JsonNode replaceCachedLinks(JsonNode node) {
    return replaceCachedLinks(node, new HashSet<>());
  }

  private JsonNode replaceCachedLinks(JsonNode node, Set<String> visited) {
    if (isCachedAssetLink(node)) {
      return replaceCachedAssetLink(node, visited);
    }

    if (isCachedEntryLink(node)) {
      return replaceCachedEntryLink(node, visited);
    }

    if (node.isObject()) {
      return replaceLinksInObject(node, visited);
    }

    if (node.isArray()) {
      return replaceLinksInArray(node, visited);
    }

    return node;
  }

  private JsonNode replaceCachedAssetLink(JsonNode node, Set<String> visited) {
    String id = ContentfulJsonUtils.id(node);
    return replaceCachedLinks(assetCache.get(id).deepCopy(), visited);
  }

  private JsonNode replaceCachedEntryLink(JsonNode node, Set<String> visited) {
    String id = ContentfulJsonUtils.id(node);
    String visitedKey = "Entry:" + id;

    if (!visited.add(visitedKey)) {
      return node;
    }

    JsonNode resolved = replaceCachedLinks(entryCache.get(id).deepCopy(), visited);
    visited.remove(visitedKey);
    return resolved;
  }

  private JsonNode replaceLinksInObject(JsonNode node, Set<String> visited) {
    ObjectNode rewritten = objectMapper.createObjectNode();
    node.properties().forEach(field ->
        rewritten.set(
            field.getKey(),
            replaceCachedLinks(field.getValue(), visited)
        )
    );
    return rewritten;
  }

  private JsonNode replaceLinksInArray(JsonNode node, Set<String> visited) {
    ArrayNode rewritten = objectMapper.createArrayNode();
    for (JsonNode child : node) {
      rewritten.add(replaceCachedLinks(child, visited));
    }
    return rewritten;
  }

  private boolean isCachedAssetLink(JsonNode node) {
    return ContentfulJsonUtils.isAssetLink(node)
        && assetCache.containsKey(ContentfulJsonUtils.id(node));
  }

  private boolean isCachedEntryLink(JsonNode node) {
    return ContentfulJsonUtils.isEntryLink(node)
        && entryCache.containsKey(ContentfulJsonUtils.id(node));
  }
}
