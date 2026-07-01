package com.streamx.contentful.connector.resolvers.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamx.contentful.connector.utils.ContentfulConstants;
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

    ContentfulJsonUtils.forEachItem(includes.path(ContentfulConstants.FIELD_VALUE_ENTRY), map::put);
    ContentfulJsonUtils.forEachItem(includes.path(ContentfulConstants.FIELD_VALUE_ASSET), map::put);

    return map;
  }
}
