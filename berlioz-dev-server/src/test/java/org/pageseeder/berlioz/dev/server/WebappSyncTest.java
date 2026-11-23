package org.pageseeder.berlioz.dev.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebappSyncTest {

  @TempDir
  Path dir;

  @Test
  void mirrorsAndReplacesTokensInFilteredFilesOnly() throws IOException {
    Path source = dir.resolve("src");
    Path target = dir.resolve("staged");
    write(source.resolve("WEB-INF/config/config.xml"), "<app version=\"@APP_VERSION@\"/>");
    write(source.resolve("WEB-INF/xslt/page.xsl"), "<xsl:stylesheet version=\"@APP_VERSION@\"/>");
    write(target.resolve("stale.txt"), "removed");

    newSync(source, target).mirror();

    assertEquals("<app version=\"1.0\"/>", Files.readString(target.resolve("WEB-INF/config/config.xml")));
    assertEquals("<xsl:stylesheet version=\"@APP_VERSION@\"/>", Files.readString(target.resolve("WEB-INF/xslt/page.xsl")));
    assertFalse(Files.exists(target.resolve("stale.txt")));
  }

  @Test
  void syncsChangesAndDeletions() throws Exception {
    Path source = dir.resolve("src");
    Path target = dir.resolve("staged");
    Path xsl = source.resolve("WEB-INF/xslt/page.xsl");
    Path css = source.resolve("css/old.css");
    write(xsl, "v1");
    write(css, "body{}");
    WebappSync sync = newSync(source, target);
    sync.mirror();
    sync.watch();

    write(xsl, "v2 updated");
    Files.setLastModifiedTime(xsl, FileTime.from(Instant.now().plusSeconds(5)));
    Files.delete(css);
    write(source.resolve("WEB-INF/config/config.xml"), "@APP_VERSION@");

    awaitTrue(() -> Files.readString(target.resolve("WEB-INF/xslt/page.xsl")).equals("v2 updated")
        && !Files.exists(target.resolve("css/old.css"))
        && Files.exists(target.resolve("WEB-INF/config/config.xml"))
        && Files.readString(target.resolve("WEB-INF/config/config.xml")).equals("1.0"));
  }

  @Test
  void rejectsNestedDirectories() {
    assertThrows(IllegalArgumentException.class, () -> newSync(dir, dir.resolve("build/webapp")));
    assertThrows(IllegalArgumentException.class, () -> newSync(dir.resolve("src"), dir));
  }

  @Test
  void convertsAntPatterns() {
    assertTrue(WebappSync.toRegex("WEB-INF/config/config.xml").matcher("WEB-INF/config/config.xml").matches());
    assertFalse(WebappSync.toRegex("WEB-INF/config/config.xml").matcher("WEB-INF/config/config-local.xml").matches());
    assertTrue(WebappSync.toRegex("WEB-INF/config/*.xml").matcher("WEB-INF/config/config-local.xml").matches());
    assertFalse(WebappSync.toRegex("WEB-INF/*.xml").matcher("WEB-INF/config/config.xml").matches());
    assertTrue(WebappSync.toRegex("**/config.xml").matcher("config.xml").matches());
    assertTrue(WebappSync.toRegex("**/config.xml").matcher("WEB-INF/config/config.xml").matches());
    assertTrue(WebappSync.toRegex("WEB-INF/**").matcher("WEB-INF/xslt/html/default.xsl").matches());
  }

  private static WebappSync newSync(Path source, Path target) {
    return new WebappSync(source, target, Map.of("APP_VERSION", "1.0"), List.of("WEB-INF/config/config.xml"));
  }

  private static void write(Path file, String content) throws IOException {
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  private interface Check {
    boolean test() throws IOException;
  }

  private static void awaitTrue(Check check) throws Exception {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
    while (Instant.now().isBefore(deadline)) {
      try {
        if (check.test()) return;
      } catch (IOException ex) {
        // not synced yet
      }
      Thread.sleep(100);
    }
    throw new AssertionError("Staged web application not synced within 10 seconds");
  }

}
