package com.streamx.contentful.connector.utils;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.function.BiConsumer;

public final class ContentfulJsonUtils {

  private ContentfulJsonUtils() {
  }

  public static String id(JsonNode node) {
    return sys(node).path(ContentfulConstants.FIELD_ID).asText();
  }

  public static JsonNode sys(JsonNode node) {
    return node.path(ContentfulConstants.FIELD_SYS);
  }

  public static String type(JsonNode node) {
    return node.path(ContentfulConstants.FIELD_TYPE).asText();
  }

  public static String linkType(JsonNode node) {
    return node.path(ContentfulConstants.FIELD_LINK_TYPE).asText();
  }

  public static boolean isLink(JsonNode node) {
    return node.isObject()
        && ContentfulConstants.FIELD_VALUE_LINK.equals(type(sys(node)))
        && !id(node).isBlank();
  }

  public static boolean isAsset(JsonNode sys) {
    return ContentfulConstants.FIELD_VALUE_ASSET.equals(linkType(sys));
  }

  public static boolean isEntry(JsonNode sys) {
    return ContentfulConstants.FIELD_VALUE_ENTRY.equals(linkType(sys));
  }

  public static boolean isAssetLink(JsonNode node) {
    return isLink(node) && isAsset(sys(node));
  }

  public static boolean isEntryLink(JsonNode node) {
    return isLink(node) && isEntry(sys(node));
  }

  public static void putById(Map<String, JsonNode> map, JsonNode node) {
    String id = id(node);
    if (!id.isBlank()) {
      map.put(id, node);
    }
  }

  public static void forEachItem(JsonNode array, BiConsumer<String, JsonNode> consumer) {
    if (!array.isArray()) {
      return;
    }

    for (JsonNode node : array) {
      String id = id(node);
      if (!id.isBlank()) {
        consumer.accept(id, node);
      }
    }
  }
}
