package com.streamx.contentful.connector.client;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.vertx.mutiny.ext.web.client.WebClient;
import org.junit.jupiter.api.Test;

class WebClientProducerTest {

  @Test
  void closesProducedWebClient() {
    WebClient webClient = mock(WebClient.class);

    new WebClientProducer().close(webClient);

    verify(webClient).close();
  }
}
