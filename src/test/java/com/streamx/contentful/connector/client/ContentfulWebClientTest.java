package com.streamx.contentful.connector.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpRequest;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContentfulWebClientTest {

  @Test
  void sendsAuthorizedContentfulRequestWithConfiguredQueryParameters() {
    WebClient webClient = mock(WebClient.class);
    HttpRequest<Buffer> request = mock(HttpRequest.class);
    HttpResponse<Buffer> response = mock(HttpResponse.class);
    when(webClient.getAbs("https://cdn.contentful.com/entries")).thenReturn(request);
    when(request.putHeader("Authorization", "Bearer token")).thenReturn(request);
    when(request.addQueryParam("sys.id", "entry-1")).thenReturn(request);
    when(request.addQueryParam("include", "10")).thenReturn(request);
    when(request.send()).thenReturn(Uni.createFrom().item(response));

    HttpResponse<Buffer> actual = client(webClient)
        .get(
            "https://cdn.contentful.com/entries",
            "token",
            Map.of("sys.id", "entry-1", "include", "10"),
            retryPolicy(),
            "test")
        .await().indefinitely();

    assertThat(actual).isSameAs(response);
    verify(request).putHeader("Authorization", "Bearer token");
    verify(request).addQueryParam("sys.id", "entry-1");
    verify(request).addQueryParam("include", "10");
  }

  @Test
  void returnsRequestFailureToTheCaller() {
    WebClient webClient = mock(WebClient.class);
    HttpRequest<Buffer> request = mock(HttpRequest.class);
    HttpResponseException failure = new HttpResponseException(500, "Internal error");
    when(webClient.getAbs("https://cdn.contentful.com/entries")).thenReturn(request);
    when(request.putHeader("Authorization", "Bearer token")).thenReturn(request);
    when(request.send()).thenReturn(Uni.createFrom().failure(failure));

    assertThatThrownBy(() -> client(webClient)
        .get(
            "https://cdn.contentful.com/entries",
            "token",
            Map.of(),
            retryPolicy(),
            "test")
        .await().indefinitely())
        .isSameAs(failure);
  }

  @ParameterizedTest
  @ValueSource(ints = {408, 429, 500, 503})
  void retriesRetryableHttpResponseStatuses(int statusCode) {
    WebClient webClient = mock(WebClient.class);
    HttpRequest<Buffer> request = mock(HttpRequest.class);
    HttpResponse<Buffer> response = mock(HttpResponse.class);
    AtomicInteger attempts = new AtomicInteger();
    when(webClient.getAbs("https://cdn.contentful.com/entries")).thenReturn(request);
    when(request.putHeader("Authorization", "Bearer token")).thenReturn(request);
    when(request.send()).thenReturn(Uni.createFrom().deferred(() -> {
      if (attempts.incrementAndGet() == 1) {
        return Uni.createFrom().failure(new HttpResponseException(statusCode, "Retryable"));
      }
      return Uni.createFrom().item(response);
    }));

    HttpResponse<Buffer> actual = client(webClient)
        .get(
            "https://cdn.contentful.com/entries",
            "token",
            Map.of(),
            retryPolicy(1),
            "test")
        .await().indefinitely();

    assertThat(actual).isSameAs(response);
    assertThat(attempts).hasValue(2);
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 404, 499})
  void doesNotRetryNonRetryableHttpResponseStatuses(int statusCode) {
    WebClient webClient = mock(WebClient.class);
    HttpRequest<Buffer> request = mock(HttpRequest.class);
    HttpResponseException failure = new HttpResponseException(statusCode, "Not retryable");
    AtomicInteger attempts = new AtomicInteger();
    when(webClient.getAbs("https://cdn.contentful.com/entries")).thenReturn(request);
    when(request.putHeader("Authorization", "Bearer token")).thenReturn(request);
    when(request.send()).thenReturn(Uni.createFrom().deferred(() -> {
      attempts.incrementAndGet();
      return Uni.createFrom().failure(failure);
    }));

    assertThatThrownBy(() -> client(webClient)
        .get(
            "https://cdn.contentful.com/entries",
            "token",
            Map.of(),
            retryPolicy(1),
            "test")
        .await().indefinitely())
        .isSameAs(failure);
    assertThat(attempts).hasValue(1);
  }

  private static ContentfulWebClient client(WebClient webClient) {
    ContentfulWebClient contentfulWebClient = new ContentfulWebClient();
    contentfulWebClient.webClient = webClient;
    contentfulWebClient.log = Logger.getLogger(ContentfulWebClient.class);
    return contentfulWebClient;
  }

  private static ContentfulWebClient.RetryPolicy retryPolicy() {
    return retryPolicy(0);
  }

  private static ContentfulWebClient.RetryPolicy retryPolicy(int maxRetries) {
    return new ContentfulWebClient.RetryPolicy(
        Duration.ofMillis(1), Duration.ofMillis(1), maxRetries);
  }
}
