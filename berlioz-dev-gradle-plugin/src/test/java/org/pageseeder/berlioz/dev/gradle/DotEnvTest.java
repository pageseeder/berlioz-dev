package org.pageseeder.berlioz.dev.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

class DotEnvTest {

  @Test
  void parsesCommonSyntax() {
    String content = String.join("\n",
        "# comment",
        "",
        "PLAIN=value",
        "  SPACED = some value  ",
        "export EXPORTED=yes",
        "EMPTY=",
        "COMMENTED=abc # trailing comment",
        "HASH=abc#def",
        "SINGLE='literal \\n # kept'",
        "DOUBLE=\"line1\\nline2 \\\"quoted\\\"\" # comment",
        "EQUALS=a=b=c",
        "PLAIN=overridden",
        "WINDOWS=crlf\r",
        "");
    Map<String, String> env = DotEnv.parse(content, ".env");
    assertEquals("overridden", env.get("PLAIN"));
    assertEquals("some value", env.get("SPACED"));
    assertEquals("yes", env.get("EXPORTED"));
    assertEquals("", env.get("EMPTY"));
    assertEquals("abc", env.get("COMMENTED"));
    assertEquals("abc#def", env.get("HASH"));
    assertEquals("literal \\n # kept", env.get("SINGLE"));
    assertEquals("line1\nline2 \"quoted\"", env.get("DOUBLE"));
    assertEquals("a=b=c", env.get("EQUALS"));
    assertEquals("crlf", env.get("WINDOWS"));
    assertEquals(10, env.size());
  }

  @Test
  void reportsInvalidLines() {
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> DotEnv.parse("OK=1\nnot a variable\n", "local/.env"));
    assertEquals("local/.env:2: expected NAME=value", ex.getMessage());
    assertThrows(IllegalArgumentException.class, () -> DotEnv.parse("A=\"unterminated", ".env"));
    assertThrows(IllegalArgumentException.class, () -> DotEnv.parse("A='x' y", ".env"));
  }

}
