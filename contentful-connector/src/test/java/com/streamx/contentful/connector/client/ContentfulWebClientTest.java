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
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

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

  private static ContentfulWebClient client(WebClient webClient) {
    ContentfulWebClient contentfulWebClient = new ContentfulWebClient();
    contentfulWebClient.webClient = webClient;
    contentfulWebClient.log = Logger.getLogger(ContentfulWebClient.class);
    return contentfulWebClient;
  }

  private static ContentfulWebClient.RetryPolicy retryPolicy() {
    return new ContentfulWebClient.RetryPolicy(Duration.ofMillis(1), Duration.ofMillis(1), 0);
  }
}
