package com.streamx.contentful.connector;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import java.util.HashMap;
import java.util.Map;

public class InMemoryMessagingTestResource implements QuarkusTestResourceLifecycleManager {

  @Override
  public Map<String, String> start() {
    Map<String, String> properties = new HashMap<>();
    properties.putAll(InMemoryConnector.switchIncomingChannelsToInMemory(Channels.DATA));
    properties.putAll(InMemoryConnector.switchOutgoingChannelsToInMemory(Channels.RESOURCES));
    return properties;
  }

  @Override
  public void stop() {
    InMemoryConnector.clear();
  }
}
