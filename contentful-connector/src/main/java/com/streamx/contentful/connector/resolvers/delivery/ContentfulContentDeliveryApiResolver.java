package com.streamx.contentful.connector.resolvers.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.streamx.contentful.connector.utils.ContentfulConstants;
import com.streamx.contentful.connector.configuration.Configuration;
import com.streamx.contentful.connector.client.ContentfulWebClient;
import com.streamx.contentful.connector.client.ContentfulWebClient.RetryPolicy;
import com.streamx.contentful.connector.utils.ContentfulJsonUtils;
import com.streamx.contentful.connector.resolvers.ContentfulDataCompletionStrategy;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ContentfulContentDeliveryApiResolver implements ContentfulDataCompletionStrategy {

  @Inject
  Logger log;

  @Inject
  ContentfulWebClient contentfulWebClient;

  @Inject
  ObjectMapper objectMapper;

  @Inject
  Configuration configuration;

  private String baseUrl;
  private String token;
  private int includeLevel;
  private RetryPolicy retryPolicy;

  @PostConstruct
  void init() {

    String spaceId = configuration.spaceId();
    String environment = configuration.environment();
    token = configuration.token();
    includeLevel = configuration.includeLevel();
    baseUrl = configuration.contentfulEntriesUrl()
        .replace("{spaceId}", spaceId)
        .replace("{environment}", environment);
    retryPolicy = new RetryPolicy(
        Duration.ofSeconds(configuration.resolveAssetRequestBackoffInitialSeconds()),
        Duration.ofSeconds(configuration.resolveAssetRequestBackoffMaxWaitSeconds()),
        configuration.resolveAssetRequestBackoffMaxRetries()
    );
  }

  public Uni<JsonNode> getCompleteDataFor(JsonNode webhookPayload) {

    String entryId = ContentfulJsonUtils.id(webhookPayload);

    log.debugf("Resolving entry <%s>", entryId);

    return contentfulWebClient
        .get(
            baseUrl,
            token,
            Map.of(
                ContentfulConstants.QUERY_PARAM_SYS_ID, entryId,
                ContentfulConstants.QUERY_PARAM_INCLUDE_LEVEL, String.valueOf(includeLevel)
            ),
            retryPolicy,
            "ContentfulContentDeliveryApiResolver"
        )
        .map(this::getEntryWithIncludedLinksResolved);
  }

  private JsonNode getEntryWithIncludedLinksResolved(HttpResponse<Buffer> response) {

    JsonNode root = readJson(response);
    JsonNode entry = getRequestedEntry(root);
    Map<String, JsonNode> includedContentById = getIncludedContentById(root);

    return replaceLinksWithIncludedContent(
        entry,
        includedContentById,
        new HashSet<>());
  }

  private JsonNode getRequestedEntry(JsonNode root) {
    JsonNode items = root.path("items");

    if (!items.isArray() || items.isEmpty()) {
      throw new RuntimeException("Entry not found");
    }

    return items.get(0);
  }

  private Map<String, JsonNode> getIncludedContentById(JsonNode root) {

    Map<String, JsonNode> map = new HashMap<>();
    JsonNode includes = root.path("includes");

    if (includes == null || includes.isMissingNode()) {
      return map;
    }

    ContentfulJsonUtils.forEachItem(includes.path("Entry"), map::put);
    ContentfulJsonUtils.forEachItem(includes.path("Asset"), map::put);

    return map;
  }

  private JsonNode replaceLinksWithIncludedContent(JsonNode node, Map<String, JsonNode> includes,
      Set<String> visited) {

    if (ContentfulJsonUtils.isLink(node)) {
      return replaceLinkWithIncludedContent(node, includes, visited);
    }

    if (node.isObject()) {
      return replaceLinksInObject(node, includes, visited);
    }

    if (node.isArray()) {
      return replaceLinksInArray(node, includes, visited);
    }

    return node;
  }

  private JsonNode replaceLinkWithIncludedContent(JsonNode node, Map<String, JsonNode> includes,
      Set<String> visited) {
    String id = ContentfulJsonUtils.id(node);
    JsonNode target = includes.get(id);

    if (target == null || !visited.add(id)) {
      return node;
    }

    JsonNode resolved = replaceLinksWithIncludedContent(target.deepCopy(), includes, visited);
    visited.remove(id);
    return resolved;
  }

  private JsonNode replaceLinksInObject(JsonNode node, Map<String, JsonNode> includes,
      Set<String> visited) {
    ObjectNode rewritten = objectMapper.createObjectNode();
    node.properties().forEach(field ->
        rewritten.set(
            field.getKey(),
            replaceLinksWithIncludedContent(field.getValue(), includes, visited)
        )
    );
    return rewritten;
  }

  private JsonNode replaceLinksInArray(JsonNode node, Map<String, JsonNode> includes,
      Set<String> visited) {
    ArrayNode rewritten = objectMapper.createArrayNode();
    for (JsonNode child : node) {
      rewritten.add(replaceLinksWithIncludedContent(child, includes, visited));
    }
    return rewritten;
  }

  private JsonNode readJson(HttpResponse<Buffer> response) {
    try {
      return objectMapper.readTree(response.bodyAsString());
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse Contentful response", e);
    }
  }
}
