package com.streamx.contentful.connector.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

public interface ContentfulService {

  JsonNode getWebhookPayload(String requestBody) throws JsonProcessingException;

  String getSubject(JsonNode webhookPayload);

  String getStreamxResourceType(JsonNode contentfulData);
}
