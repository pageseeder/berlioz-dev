package org.pageseeder.berlioz.dev.server;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jetty.logging.JettyLoggerFactory;
import org.eclipse.jetty.logging.StdErrAppender;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Redirects the logs of Jetty (jetty-slf4j-impl, which writes to standard error) to a file, so
 * that they are not mixed with the output of the web application; warnings and errors, with
 * their stack traces, are still printed on standard error.
 *
 * <p>Only the server's own logging is affected: a web application with its own SLF4J binding in
 * WEB-INF/lib (e.g. Logback) keeps logging to the console.
 */
final class JettyLog {

  /**
   * The level in the header of an entry formatted by {@link StdErrAppender}, e.g.
   * {@code 2026-11-26 17:54:53.746:WARN :oejs.Server:main: message}; the first match is the level,
   * a marker in the message itself comes after it.
   */
  private static final Pattern LEVEL = Pattern.compile(":(TRACE|DEBUG|INFO |WARN |ERROR):");

  private JettyLog() {
  }

  /**
   * Sends the Jetty logs to the given file, emptied first, and their warnings and errors to the
   * given console too; returns whether it could be done (false when Jetty does not use
   * jetty-slf4j-impl, e.g. another SLF4J binding is on the launcher classpath).
   */
  static boolean redirectTo(Path file, PrintStream console) throws IOException {
    ILoggerFactory factory;
    try {
      factory = LoggerFactory.getILoggerFactory();
    } catch (LinkageError ex) {
      return false;
    }
    if (!(factory instanceof JettyLoggerFactory jetty)) return false;
    // All Jetty loggers share the appender of the root logger
    if (!(jetty.getJettyLogger(Logger.ROOT_LOGGER_NAME).getAppender() instanceof StdErrAppender appender)) {
      return false;
    }
    Path parent = file.getParent();
    if (parent != null) Files.createDirectories(parent);
    appender.setStream(new TeeStream(new FileOutputStream(file.toFile()), console));
    return true;
  }

  /**
   * The location of jetty-slf4j-impl, as a {@code file:} URI, or {@code null} if unknown.
   *
   * <p>Jetty hides its classes from the web application, but not the resources of its jars: the
   * web application's own SLF4J would find the jetty-slf4j-impl provider in
   * {@code META-INF/services} and report that it cannot load it. Adding this location to the
   * server classes of the web application hides the whole jar.
   */
  static String implementationLocation() {
    try {
      var source = JettyLoggerFactory.class.getProtectionDomain().getCodeSource();
      return source == null || source.getLocation() == null ? null : source.getLocation().toURI().toString();
    } catch (Exception | LinkageError ex) {
      return null;
    }
  }

  /** Whether a log entry is a warning or an error. */
  static boolean isWarningOrError(String entry) {
    Matcher matcher = LEVEL.matcher(entry);
    if (!matcher.find()) return false;
    String level = matcher.group(1);
    return level.startsWith("WARN") || level.equals("ERROR");
  }

  /**
   * Writes everything to the file and copies warnings and errors to the console.
   *
   * <p>{@link StdErrAppender} prints each entry, stack trace included, with a single
   * {@code println(Object)}; anything written otherwise only goes to the file.
   */
  static final class TeeStream extends PrintStream {

    private final PrintStream console;

    TeeStream(OutputStream file, PrintStream console) {
      // Auto-flush, so that the file can be followed while the server runs
      super(file, true, StandardCharsets.UTF_8);
      this.console = console;
    }

    @Override
    public void println(Object x) {
      println(String.valueOf(x));
    }

    @Override
    public void println(String x) {
      super.println(x);
      if (x != null && isWarningOrError(x)) console.println(x);
    }

  }

}
