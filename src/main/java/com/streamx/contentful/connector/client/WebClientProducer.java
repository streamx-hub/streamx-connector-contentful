package com.streamx.contentful.connector.client;

import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
class WebClientProducer {

  @Produces
  @ApplicationScoped
  WebClient webClient(Vertx vertx) {
    return WebClient.create(vertx);
  }

  void close(@Disposes WebClient webClient) {
    webClient.close();
  }
}
