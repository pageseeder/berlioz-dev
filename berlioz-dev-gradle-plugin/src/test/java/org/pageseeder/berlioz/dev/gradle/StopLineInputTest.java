package org.pageseeder.berlioz.dev.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class StopLineInputTest {

  @Test
  void endsAfterFirstLine() throws IOException {
    StopLineInput in = new StopLineInput(input("\n\u001b[O more\n"));
    byte[] buffer = new byte[64];
    assertEquals(1, in.read(buffer, 0, buffer.length));
    assertEquals('\n', buffer[0]);
    assertEquals(-1, in.read(buffer, 0, buffer.length));
    assertEquals(-1, in.read());
  }

  @Test
  void keepsTextBeforeNewline() throws IOException {
    StopLineInput in = new StopLineInput(input("stop\nignored"));
    assertEquals("stop\n", new String(in.readAllBytes(), StandardCharsets.UTF_8));
  }

  @Test
  void endsWithInput() throws IOException {
    StopLineInput in = new StopLineInput(input(""));
    assertEquals(-1, in.read(new byte[8], 0, 8));
    assertEquals(-1, in.read());
  }

  private static ByteArrayInputStream input(String text) {
    return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
  }

}
