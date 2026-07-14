package com.streamx.contentful.connector.client;

public class HttpResponseException extends RuntimeException {

  private final int statusCode;
  private final String body;

  public HttpResponseException(int statusCode, String body) {
    super("HTTP " + statusCode);
    this.statusCode = statusCode;
    this.body = body;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getBody() {
    return body;
  }
}
