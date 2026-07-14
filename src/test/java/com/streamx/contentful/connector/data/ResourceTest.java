package com.streamx.contentful.connector.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class ResourceTest {

  @Test
  void exposesContentAsStringAndBytes() {
    Resource resource = new Resource("content", "data/product");

    assertThat(resource.getType()).isEqualTo("data/product");
    assertThat(resource.getContentAsString()).isEqualTo("content");
    assertThat(resource.getContentAsBytes()).containsExactly("content".getBytes());
    assertThat(Resource.isEmpty(resource)).isFalse();
  }

  @Test
  void treatsNullResourcesAndNullContentAsEmpty() {
    Resource empty = new Resource((ByteBuffer) null, "data/product");

    assertThat(Resource.isEmpty(empty)).isTrue();
    assertThat(empty.getContent()).isNull();
    assertThat(empty.getContentAsString()).isNull();
    assertThat(empty.getContentAsBytes()).isNull();
  }

  @Test
  void acceptsByteContent() {
    Resource resource = new Resource("content".getBytes(), "data/product");

    assertThat(resource.getContentAsString()).isEqualTo("content");
  }
}
