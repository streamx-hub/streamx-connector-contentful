package com.streamx.contentful.connector.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.jackson.JsonCloudEventData;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

public class CloudEventUtils {

  private static final Config config = ConfigProvider.getConfig();
  private static final URI FALLBACK_SOURCE = URI.create("streamx-contentful-connector");

  private static final URI DEFAULT_SOURCE =
      config.getOptionalValue("quarkus.application.name", String.class)
        .map(URI::create)
        .orElse(FALLBACK_SOURCE);

  private static final ZoneId DEFAULT_ZONE = ZoneOffset.UTC;
  private static final ObjectMapper strictObjectMapper = new ObjectMapper();

  private CloudEventUtils() {
    // no instance
  }

  public static CloudEvent eventWithData(String subject, String type, Object data) {
    return eventWithData(subject, type, data, getNow());
  }

  public static CloudEvent eventWithData(String subject, String type, Object data,
      OffsetDateTime time) {
    var builder = baseBuilder(subject, type, time);
    return withData(builder, data).build();
  }

  public static io.cloudevents.core.v1.CloudEventBuilder baseBuilder(String subject, String type,
      OffsetDateTime time) {
    return withIdAndSource(CloudEventBuilder.v1())
        .withSubject(subject)
        .withType(type)
        .withTime(time);
  }

  private static io.cloudevents.core.v1.CloudEventBuilder withIdAndSource(
      io.cloudevents.core.v1.CloudEventBuilder builder) {
    return builder
        .withId(UUID.randomUUID().toString())
        .withSource(DEFAULT_SOURCE);
  }

  public static io.cloudevents.core.v1.CloudEventBuilder withData(
      io.cloudevents.core.v1.CloudEventBuilder builder, Object data) {
    return builder
        .withDataContentType("application/json")
        .withData(JsonCloudEventData.wrap(strictObjectMapper.valueToTree(data)));
  }

  public static OffsetDateTime getNow() {
    return OffsetDateTime.now(DEFAULT_ZONE);
  }
}
