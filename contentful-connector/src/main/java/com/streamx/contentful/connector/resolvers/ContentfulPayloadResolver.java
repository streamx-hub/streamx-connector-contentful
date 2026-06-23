package com.streamx.contentful.connector.resolvers;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamx.contentful.connector.configuration.Configuration;
import com.streamx.contentful.connector.resolvers.delivery.ContentfulContentDeliveryApiResolver;
import com.streamx.contentful.connector.resolvers.webhook.ContentfulLinkResolver;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ContentfulPayloadResolver {

  @Inject
  Configuration configuration;

  @Inject
  ContentfulContentDeliveryApiResolver contentDeliveryApiResolver;

  @Inject
  ContentfulLinkResolver webhookPayloadLinkResolver;

  private ContentfulDataCompletionStrategy selectedResolver;

  @PostConstruct
  void selectResolver() {
    selectedResolver = configuration.useContentDeliveryAPI()
        ? contentDeliveryApiResolver
        : webhookPayloadLinkResolver;
  }

  public Uni<JsonNode> getCompleteDataFor(JsonNode webhookPayload) {
    return selectedResolver.getCompleteDataFor(webhookPayload);
  }
}
