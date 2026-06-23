package com.streamx.contentful.connector.resolvers.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.contentful.connector.utils.ContentfulConstants;
import com.streamx.contentful.connector.configuration.Configuration;
import com.streamx.contentful.connector.client.ContentfulWebClient;
import com.streamx.contentful.connector.client.ContentfulWebClient.RetryPolicy;
import com.streamx.contentful.connector.utils.ContentfulJsonUtils;
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
public class ContentfulAssetResolver {

  @Inject
  Logger log;

  @Inject
  ContentfulWebClient contentfulWebClient;

  @Inject
  ObjectMapper objectMapper;

  @Inject
  Configuration configuration;

  private String token;
  private String baseUrl;

  private RetryPolicy retryPolicy;

  @PostConstruct
  void init() {
    String spaceId = configuration.spaceId();
    String environment = configuration.environment();
    token = configuration.token();

    baseUrl = configuration.contentfulAssetsUrl()
        .replace("{spaceId}", spaceId)
        .replace("{environment}", environment);

    retryPolicy = new RetryPolicy(
        Duration.ofSeconds(configuration.resolveAssetRequestBackoffInitialSeconds()),
        Duration.ofSeconds(configuration.resolveAssetRequestBackoffMaxWaitSeconds()),
        configuration.resolveAssetRequestBackoffMaxRetries()
    );

    log.debugf("Asset resolver initialized with URL <%s>", baseUrl);
  }

  public Uni<Map<String, JsonNode>> resolve(Set<String> assetIds) {

    if (assetIds == null || assetIds.isEmpty()) {
      return Uni.createFrom().item(Map.of());
    }

    String joinedIds = String.join(",", assetIds);

    return contentfulWebClient
        .get(
            baseUrl,
            token,
            Map.of(ContentfulConstants.QUERY_PARAM_SYS_ID_JOINED, joinedIds),
            retryPolicy,
            "ContentfulAssetResolver"
        )
        .map(this::parseAssets);
  }

  private Map<String, JsonNode> parseAssets(HttpResponse<Buffer> response) {

    JsonNode root = readJson(response);
    JsonNode items = root.path("items");
    Map<String, JsonNode> assets = new HashMap<>();

    if (!items.isArray()) {
      return assets;
    }

    ContentfulJsonUtils.forEachItem(items, assets::put);

    return assets;
  }

  private JsonNode readJson(HttpResponse<Buffer> response) {
    try {
      return objectMapper.readTree(response.bodyAsString());
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse Contentful assets response", e);
    }
  }
}
