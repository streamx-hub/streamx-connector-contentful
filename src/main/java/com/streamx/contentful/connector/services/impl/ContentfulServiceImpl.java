package com.streamx.contentful.connector.services.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.contentful.connector.utils.ContentfulConstants;
import com.streamx.contentful.connector.configuration.Configuration;
import com.streamx.contentful.connector.services.ContentfulService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ContentfulServiceImpl implements ContentfulService {

  @Inject
  Configuration configuration;

  private static String safeGetText(final JsonNode sys, final String key) {
    return safeGetText(sys, key, "");
  }

  private static String safeGetText(final JsonNode sys, final String key,
      final String defaultValue) {
    return sys.path(key).asText(defaultValue);
  }

  @Override
  public JsonNode getWebhookPayload(String requestBody) throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    return mapper.readTree(requestBody);
  }

  private static String getSubjectForData(String spaceId, String environmentId,
      String contentfulId) {
    return "contentful/spaces/" + spaceId + "/environments/" + environmentId + "/" + contentfulId;
  }

  @Override
  public String getSubject(JsonNode webhookPayload) {
    JsonNode sys = webhookPayload.path(ContentfulConstants.FIELD_SYS);
    String spaceId = safeGetText(sys.at("/space/sys"), ContentfulConstants.FIELD_ID);
    String environmentId = safeGetText(sys.at("/environment/sys"), ContentfulConstants.FIELD_ID);
    String contentfulId = safeGetText(sys, ContentfulConstants.FIELD_ID);
    return getSubjectForData(spaceId, environmentId, contentfulId);
  }

  @Override
  public String getStreamxResourceType(JsonNode contentfulData) {
    JsonNode sys = contentfulData.path(ContentfulConstants.FIELD_SYS);
    String contentfulContentType = safeGetText(sys.at("/contentType/sys"), ContentfulConstants.FIELD_ID);
    return configuration.resourceTypeMappings().get(contentfulContentType);
  }

}
