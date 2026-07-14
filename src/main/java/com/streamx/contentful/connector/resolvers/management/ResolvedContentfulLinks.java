package com.streamx.contentful.connector.resolvers.management;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public record ResolvedContentfulLinks(Map<String, JsonNode> entries, Map<String, JsonNode> assets) {

  public static ResolvedContentfulLinks empty() {
    return new ResolvedContentfulLinks(Map.of(), Map.of());
  }
}
