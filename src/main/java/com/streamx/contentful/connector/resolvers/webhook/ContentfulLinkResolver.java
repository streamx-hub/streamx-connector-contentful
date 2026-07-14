package com.streamx.contentful.connector.resolvers.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.contentful.connector.resolvers.ContentfulDataCompletionStrategy;
import com.streamx.contentful.connector.resolvers.management.ContentfulAssetResolver;
import com.streamx.contentful.connector.resolvers.management.ContentfulEntryResolver;
import com.streamx.contentful.connector.resolvers.management.ResolvedContentfulLinks;
import io.quarkus.arc.lookup.LookupIfProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

@ApplicationScoped
@LookupIfProperty(
    name = "streamx.contentful.connector.use-content-delivery-api",
    stringValue = "false"
)
public class ContentfulLinkResolver implements ContentfulDataCompletionStrategy {

  @Inject
  Logger log;

  @Inject
  ObjectMapper objectMapper;

  @Inject
  ContentfulAssetResolver assetResolver;

  @Inject
  ContentfulEntryResolver entryResolver;

  private final Map<String, JsonNode> assetCache = new ConcurrentHashMap<>();
  private final Map<String, JsonNode> entryCache = new ConcurrentHashMap<>();
  private final ContentfulWebhookLinkFinder linkFinder = new ContentfulWebhookLinkFinder();

  public Uni<JsonNode> getCompleteDataFor(JsonNode webhookPayload) {
    log.tracef("Consuming message contents: <%s>", webhookPayload.toString());
    try {
      ContentfulLinks linksInWebhookPayload = findLinksInWebhookPayload(webhookPayload);

      if (hasNoLinks(linksInWebhookPayload)) {
        log.trace("Links are empty");
        return Uni.createFrom().item(webhookPayload);
      }

      if (allLinksAreAlreadyCached(linksInWebhookPayload)) {
        return Uni.createFrom().item(rewriteLinksInWebhookPayload(webhookPayload));
      }

      return getMissingLinksFromContentful(linksInWebhookPayload)
          .chain(ignore -> safelyRewriteLinksInWebhookPayload(webhookPayload));

    } catch (Exception e) {
      // throw so that Uni<> can manage it
      throw new RuntimeException(e);
    }
  }

  private boolean hasNoLinks(ContentfulLinks links) {
    return links.assets().isEmpty() && links.entries().isEmpty();
  }

  private boolean allLinksAreAlreadyCached(ContentfulLinks links) {
    return assetCache.keySet().containsAll(links.assets())
        && entryCache.keySet().containsAll(links.entries());
  }

  private Uni<Void> getMissingLinksFromContentful(ContentfulLinks links) {
    Set<String> missingAssetIds = missingIds(links.assets(), assetCache);
    Set<String> missingEntryIds = missingIds(links.entries(), entryCache);

    return assetResolver.resolve(missingAssetIds)
        .chain(resolvedAssets -> entryResolver.resolve(missingEntryIds)
            .invoke(resolvedLinks -> cacheResolvedLinks(resolvedAssets, resolvedLinks)))
        .replaceWithVoid();
  }

  private void cacheResolvedLinks(Map<String, JsonNode> resolvedAssets,
      ResolvedContentfulLinks resolvedLinks) {
    assetCache.putAll(resolvedAssets);
    entryCache.putAll(resolvedLinks.entries());
    assetCache.putAll(resolvedLinks.assets());
  }

  private Uni<JsonNode> safelyRewriteLinksInWebhookPayload(JsonNode webhookPayload) {
    try {
      return Uni.createFrom().item(rewriteLinksInWebhookPayload(webhookPayload));
    } catch (IOException e) {
      //Unable to rewrite links, return event without rewritten links as a failsafe
      log.warnf(e, "Failed to rewrite event data: %s", webhookPayload);
      return Uni.createFrom().item(webhookPayload);
    }
  }

  ContentfulLinks findLinksInWebhookPayload(JsonNode root) {
    return linkFinder.findIn(root);
  }

  private Set<String> missingIds(Set<String> ids, Map<String, JsonNode> cache) {
    Set<String> missingIds = new HashSet<>(ids);
    missingIds.removeAll(cache.keySet());
    return missingIds;
  }

  private JsonNode rewriteLinksInWebhookPayload(JsonNode data) throws IOException {
    return new CachedContentfulLinksRewriter(objectMapper, assetCache, entryCache)
        .replaceCachedLinks(data);
  }

}
