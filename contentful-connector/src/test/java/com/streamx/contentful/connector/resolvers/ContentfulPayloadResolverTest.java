package com.streamx.contentful.connector.resolvers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.contentful.connector.configuration.Configuration;
import com.streamx.contentful.connector.resolvers.delivery.ContentfulContentDeliveryApiResolver;
import com.streamx.contentful.connector.resolvers.webhook.ContentfulLinkResolver;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

class ContentfulPayloadResolverTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void completesPayloadWithContentDeliveryApiWhenConfigured() throws Exception {
    JsonNode webhookPayload = json("{\"sys\":{\"id\":\"entry-1\"}}");
    JsonNode contentDeliveryApiPayload = json("{\"source\":\"content-delivery-api\"}");

    ContentfulPayloadResolver resolver = resolverConfiguredToUseContentDeliveryApi(true);
    when(resolver.contentDeliveryApiResolver.getCompleteDataFor(webhookPayload))
        .thenReturn(Uni.createFrom().item(contentDeliveryApiPayload));

    JsonNode completedPayload = resolver.getCompleteDataFor(webhookPayload)
        .await().indefinitely();

    assertThat(completedPayload).isEqualTo(contentDeliveryApiPayload);
  }

  @Test
  void completesPayloadWithWebhookLinkResolverWhenContentDeliveryApiIsDisabled() throws Exception {
    JsonNode webhookPayload = json("{\"sys\":{\"id\":\"entry-1\"}}");
    JsonNode linkResolvedPayload = json("{\"source\":\"webhook-link-resolver\"}");

    ContentfulPayloadResolver resolver = resolverConfiguredToUseContentDeliveryApi(false);
    when(resolver.webhookPayloadLinkResolver.getCompleteDataFor(webhookPayload))
        .thenReturn(Uni.createFrom().item(linkResolvedPayload));

    JsonNode completedPayload = resolver.getCompleteDataFor(webhookPayload)
        .await().indefinitely();

    assertThat(completedPayload).isEqualTo(linkResolvedPayload);
  }

  private static ContentfulPayloadResolver resolverConfiguredToUseContentDeliveryApi(boolean enabled) {
    Configuration configuration = mock(Configuration.class);
    when(configuration.useContentDeliveryAPI()).thenReturn(enabled);

    ContentfulPayloadResolver resolver = new ContentfulPayloadResolver();
    resolver.configuration = configuration;
    resolver.contentDeliveryApiResolver = mock(ContentfulContentDeliveryApiResolver.class);
    resolver.webhookPayloadLinkResolver = mock(ContentfulLinkResolver.class);
    resolver.selectResolver();

    return resolver;
  }

  private static JsonNode json(String value) throws Exception {
    return OBJECT_MAPPER.readTree(value);
  }
}
