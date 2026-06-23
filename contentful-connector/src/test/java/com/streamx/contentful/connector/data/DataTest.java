package com.streamx.contentful.connector.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class DataTest {

  @Test
  void keepsJsonContentAndType() {
    Data data = new Data("{\"title\":\"Product\"}", Data.TYPE_PUBLISHED);

    assertThat(data.getType()).isEqualTo(Data.TYPE_PUBLISHED);
    assertThat(data.getContentAsString()).isEqualTo("{\"title\":\"Product\"}");
  }

  @Test
  void acceptsByteBufferContent() {
    Data data = new Data(ByteBuffer.wrap("{}".getBytes()), Data.TYPE_UNPUBLISHED);

    assertThat(data.getType()).isEqualTo(Data.TYPE_UNPUBLISHED);
    assertThat(data.getContentAsString()).isEqualTo("{}");
  }
}
