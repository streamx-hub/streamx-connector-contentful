package com.streamx.contentful.connector.resolvers.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamx.contentful.connector.utils.ContentfulConstants;
import com.streamx.contentful.connector.utils.ContentfulJsonUtils;
import java.util.HashSet;
import java.util.Set;

class ContentfulWebhookLinkFinder {

  ContentfulLinks findIn(JsonNode root) {
    Set<String> assetIds = new HashSet<>();
    Set<String> entryIds = new HashSet<>();

    root.findValues(ContentfulConstants.FIELD_SYS)
        .forEach(sys -> collectLink(sys, assetIds, entryIds));

    return new ContentfulLinks(assetIds, entryIds);
  }

  private void collectLink(JsonNode sys, Set<String> assetIds, Set<String> entryIds) {
    if (!isLink(sys)) {
      return;
    }

    String id = sys.path(ContentfulConstants.FIELD_ID).asText();
    if (id.isBlank()) {
      return;
    }

    addLinkByType(sys, id, assetIds, entryIds);
  }

  private boolean isLink(JsonNode sys) {
    return ContentfulConstants.FIELD_VALUE_LINK.equals(ContentfulJsonUtils.type(sys));
  }

  private void addLinkByType(JsonNode sys, String id, Set<String> assetIds, Set<String> entryIds) {
    if (ContentfulJsonUtils.isAsset(sys)) {
      assetIds.add(id);
    } else if (ContentfulJsonUtils.isEntry(sys)) {
      entryIds.add(id);
    }
  }
}
