package com.streamx.contentful.connector.client;

import io.smallrye.mutiny.TimeoutException;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.ConnectException;
import java.time.Duration;
import java.util.Map;
import java.util.function.Predicate;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ContentfulWebClient {

  @Inject
  WebClient webClient;

  @Inject
  Logger log;

  public Uni<HttpResponse<Buffer>> get(
      String url,
      String token,
      Map<String, String> queryParams,
      RetryPolicy retryPolicy,
      String context
  ) {

    var request = webClient
        .getAbs(url)
        .putHeader("Authorization", "Bearer " + token);

    queryParams.forEach(request::addQueryParam);

    return request.send()
        .onFailure(isRetryable())
        .retry()
        .withBackOff(retryPolicy.initialBackoff(), retryPolicy.maxBackoff())
        .atMost(retryPolicy.maxRetries())
        .onFailure()
        .invoke(e -> log.errorf("Contentful request failed (%s)", context, e));
  }

  private Predicate<Throwable> isRetryable() {
    return this::isRetryable;
  }

  private boolean isRetryable(Throwable exception) {
    if (exception instanceof ConnectException || exception instanceof TimeoutException) {
      return true;
    }

    if (exception instanceof HttpResponseException responseException) {
      return isRetryableStatus(responseException.getStatusCode());
    }

    return false;
  }

  private boolean isRetryableStatus(int statusCode) {
    return statusCode == 408
        || statusCode == 429
        || statusCode >= 500;
  }

  public record RetryPolicy(
      Duration initialBackoff,
      Duration maxBackoff,
      int maxRetries
  ) {}
}
