package com.streamx.contentful.connector.resolvers;

import com.fasterxml.jackson.databind.JsonNode;
import io.smallrye.mutiny.Uni;

public interface ContentfulDataCompletionStrategy {

  Uni<JsonNode> getCompleteDataFor(JsonNode webhookPayload);
}
