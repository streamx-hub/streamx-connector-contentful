package com.streamx.contentful.connector.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HttpResponseExceptionTest {

  @Test
  void exposesStatusCodeAndResponseBody() {
    HttpResponseException exception = new HttpResponseException(429, "Too many requests");

    assertThat(exception).hasMessage("HTTP 429");
    assertThat(exception.getStatusCode()).isEqualTo(429);
    assertThat(exception.getBody()).isEqualTo("Too many requests");
  }
}
