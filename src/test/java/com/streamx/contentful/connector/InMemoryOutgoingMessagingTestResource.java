package com.streamx.contentful.connector;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import java.util.Map;

public class InMemoryOutgoingMessagingTestResource implements QuarkusTestResourceLifecycleManager {

  @Override
  public Map<String, String> start() {
    return InMemoryConnector.switchOutgoingChannelsToInMemory(Channels.RESOURCES);
  }

  @Override
  public void stop() {
    InMemoryConnector.clear();
  }
}
