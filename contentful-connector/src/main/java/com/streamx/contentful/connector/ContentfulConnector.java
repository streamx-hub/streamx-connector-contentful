package com.streamx.contentful.connector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.streamx.contentful.connector.configuration.Configuration;
import com.streamx.contentful.connector.data.Data;
import com.streamx.contentful.connector.resolvers.ContentfulDataCompletionStrategy;
import com.streamx.contentful.connector.services.ContentfulService;
import com.streamx.contentful.connector.utils.CloudEventUtils;
import io.cloudevents.CloudEvent;
import io.quarkus.reactivemessaging.http.runtime.IncomingHttpMetadata;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ContentfulConnector {

  public static final String WEBHOOK_ACTION_TOPIC_HEADER = "X-Contentful-Topic";

  @Inject
  Logger log;

  @Inject
  ContentfulService contentfulService;

  @Inject
  Instance<ContentfulDataCompletionStrategy> contentfulPayloadResolver;

  @Inject
  Configuration configuration;

  @Incoming(Channels.DATA)
  @Outgoing(Channels.RESOURCES)
  public Uni<CloudEvent> processMessage(String requestBody, IncomingHttpMetadata metadata) {

    return getWebhookDataFromRequest(requestBody, metadata)
        .chain(this::completeWebhookData)
        .map(this::createStreamxCloudEvent)
        .onFailure().invoke(this::logProcessingFailure);
  }

  private Uni<ContentfulWebhookData> getWebhookDataFromRequest(String requestBody,
      IncomingHttpMetadata metadata) {
    return Uni.createFrom().item(() -> {
      try {
        JsonNode webhookPayload = contentfulService.getWebhookPayload(requestBody);
        String contentfulTopic = metadata.getHeaders().get(WEBHOOK_ACTION_TOPIC_HEADER);
        String streamxEventType = configuration.topicMappings().get(contentfulTopic);
        String subject = contentfulService.getSubject(webhookPayload);

        return new ContentfulWebhookData(streamxEventType, subject, webhookPayload);
      } catch (JsonProcessingException | IllegalArgumentException e) {
        throw new RuntimeException(e);
      }
    });
  }

  private Uni<ContentfulWebhookData> completeWebhookData(ContentfulWebhookData webhookData) {
    log.tracef("Filling webhook data <%s>", webhookData);
    return contentfulPayloadResolver.get().getCompleteDataFor(webhookData.rawData())
        .map(webhookData::withRawData);
  }

  private CloudEvent createStreamxCloudEvent(ContentfulWebhookData webhookData) {
    JsonNode eventBody = webhookData.rawData();
    final String streamxResourceType = this.contentfulService.getStreamxResourceType(eventBody);
    final Data payload = new Data(
        eventBody.toString(),
        streamxResourceType);
    log.tracef("Creating a CloudEvent with payload <%s>", payload);
    return CloudEventUtils.eventWithData(
        webhookData.subject(),
        webhookData.type(),
        payload);
  }

  private void logProcessingFailure(Throwable e) {
    log.errorf(e, "Failed to process a Contentful webhook message");
  }

  private record ContentfulWebhookData(String type, String subject, JsonNode rawData) {

    ContentfulWebhookData withRawData(JsonNode rawData) {
      return new ContentfulWebhookData(type, subject, rawData);
    }
  }
}
