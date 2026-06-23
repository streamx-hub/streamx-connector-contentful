package com.streamx.contentful.connector.configuration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.Map;

@ConfigMapping(prefix = "streamx.contentful.connector")
public interface Configuration {

  Map<String, String> resourceTypeMappings();

  Map<String, String> topicMappings();

  @WithDefault("true")
  boolean useContentDeliveryAPI();

  String spaceId();

  String environment();

  String token();

  @WithDefault("10")
  int includeLevel();

  @WithDefault("https://cdn.contentful.com/spaces/{spaceId}/environments/{environment}/assets")
  String contentfulAssetsUrl();

  @WithDefault("https://cdn.contentful.com/spaces/{spaceId}/environments/{environment}/entries")
  String contentfulEntriesUrl();

  @WithDefault("1")
  int resolveAssetRequestBackoffInitialSeconds();

  @WithDefault("10")
  int resolveAssetRequestBackoffMaxWaitSeconds();

  @WithDefault("3")
  int resolveAssetRequestBackoffMaxRetries();
}
