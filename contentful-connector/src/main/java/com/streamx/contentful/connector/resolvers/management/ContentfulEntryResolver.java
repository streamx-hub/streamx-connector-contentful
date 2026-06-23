package com.streamx.contentful.connector.resolvers.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.contentful.connector.client.ContentfulWebClient;
import com.streamx.contentful.connector.configuration.Configuration;
import com.streamx.contentful.connector.utils.ContentfulConstants;
import com.streamx.contentful.connector.utils.ContentfulJsonUtils;
import com.streamx.contentful.connector.client.ContentfulWebClient.RetryPolicy;
import com.streamx.contentful.connector.resolvers.delivery.ContentfulIncludesParser;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ContentfulEntryResolver {

  @Inject
  Logger log;

  @Inject
  ContentfulWebClient contentfulWebClient;

  @Inject
  ContentfulIncludesParser includesParser;

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

  public Uni<ResolvedContentfulLinks> resolve(Set<String> entryIds) {

    if (entryIds == null || entryIds.isEmpty()) {
      return Uni.createFrom().item(ResolvedContentfulLinks.empty());
    }

    String joinedIds = String.join(",", entryIds);

    return contentfulWebClient
        .get(
            baseUrl,
            token,
            Map.of(
                ContentfulConstants.QUERY_PARAM_SYS_ID_JOINED, joinedIds,
                ContentfulConstants.QUERY_PARAM_INCLUDE_LEVEL, String.valueOf(includeLevel)
            ),
            retryPolicy,
            "ContentfulEntryResolver"
        )
        .map(this::parseEntries);
  }

  private ResolvedContentfulLinks parseEntries(HttpResponse<Buffer> response) {

    JsonNode root = readJson(response);

    Map<String, JsonNode> entries = new HashMap<>();
    Map<String, JsonNode> assets = new HashMap<>();

    ContentfulJsonUtils.forEachItem(root.path("items"), entries::put);

    JsonNode includes = root.path("includes");

    ContentfulJsonUtils.forEachItem(includes.path("Entry"), entries::put);
    ContentfulJsonUtils.forEachItem(includes.path("Asset"), assets::put);

    return new ResolvedContentfulLinks(entries, assets);
  }

  private JsonNode readJson(HttpResponse<Buffer> response) {
    try {
      return objectMapper.readTree(response.bodyAsString());
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse Contentful entries response", e);
    }
  }
}
