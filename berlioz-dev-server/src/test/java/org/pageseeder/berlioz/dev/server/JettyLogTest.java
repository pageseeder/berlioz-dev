package org.pageseeder.berlioz.dev.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.jetty.logging.JettyLevel;
import org.eclipse.jetty.logging.JettyLogger;
import org.eclipse.jetty.logging.JettyLoggerConfiguration;
import org.eclipse.jetty.logging.JettyLoggerFactory;
import org.eclipse.jetty.logging.StdErrAppender;
import org.junit.jupiter.api.Test;

class JettyLogTest {

  @Test
  void detectsLevelInHeader() {
    assertTrue(JettyLog.isWarningOrError("2026-11-26 17:54:53.746:WARN :oejs.Server:main: oops"));
    assertTrue(JettyLog.isWarningOrError("2026-11-26 17:54:53.746:ERROR:oejs.Server:main: oops"));
    assertFalse(JettyLog.isWarningOrError("2026-11-26 17:54:53.746:INFO :oejs.Server:main: started"));
    // A level in the message does not count
    assertFalse(JettyLog.isWarningOrError("2026-11-26 17:54:53.746:INFO :oejs.Server:main: see :WARN :"));
    assertFalse(JettyLog.isWarningOrError("no header"));
  }

  @Test
  void copiesWarningsAndErrorsWithStackTracesToConsole() {
    ByteArrayOutputStream file = new ByteArrayOutputStream();
    ByteArrayOutputStream console = new ByteArrayOutputStream();
    JettyLoggerConfiguration config = new JettyLoggerConfiguration();
    StdErrAppender appender = new StdErrAppender(config,
        new JettyLog.TeeStream(file, new PrintStream(console, true, StandardCharsets.UTF_8)));
    JettyLogger test = new JettyLogger(new JettyLoggerFactory(config), "org.eclipse.jetty.test", appender);
    test.setLevel(JettyLevel.INFO);
    test.info("started");
    test.warn("request failed", new IllegalStateException("boom"));

    String fileText = file.toString(StandardCharsets.UTF_8);
    String consoleText = console.toString(StandardCharsets.UTF_8);
    assertTrue(fileText.contains("started"), fileText);
    assertTrue(fileText.contains("request failed"), fileText);
    assertFalse(consoleText.contains("started"), consoleText);
    assertTrue(consoleText.contains("request failed"), consoleText);
    assertTrue(consoleText.contains("IllegalStateException: boom"), consoleText);
    assertTrue(consoleText.contains("\tat "), consoleText);
    assertEquals(2, fileText.lines().filter(l -> l.matches("^\\d{4}-.*")).count(), fileText);
  }

}
