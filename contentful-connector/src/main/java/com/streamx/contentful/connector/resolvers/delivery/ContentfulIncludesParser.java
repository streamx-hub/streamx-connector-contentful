package com.streamx.contentful.connector.resolvers.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamx.contentful.connector.utils.ContentfulJsonUtils;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class ContentfulIncludesParser {

  public Map<String, JsonNode> parse(JsonNode includes) {
    Map<String, JsonNode> map = new HashMap<>();
    if (includes == null || includes.isMissingNode()) {
      return map;
    }

    ContentfulJsonUtils.forEachItem(includes.path("Entry"), map::put);
    ContentfulJsonUtils.forEachItem(includes.path("Asset"), map::put);

    return map;
  }
}
