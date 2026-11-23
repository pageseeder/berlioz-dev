package org.pageseeder.berlioz.dev.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Mirrors a web application source directory into a staging directory, replacing
 * {@code @TOKEN@} placeholders in the files matching the filter patterns (like Gradle's
 * {@code ReplaceTokens} filter on the WAR), and keeps the copy in sync by polling.
 *
 * <p>Polling is used rather than a {@code WatchService} because the JDK implementation polls
 * anyway on macOS, with a delay of several seconds; a web application source tree is small enough
 * to scan every half second.
 */
final class WebappSync {

  private static final long POLL_INTERVAL_MS = 500;

  private final Path source;

  private final Path target;

  private final Map<String, String> tokens;

  private final List<Pattern> filters;

  /** Last modified time and size of each source file by relative path (using '/'). */
  private Map<String, Stamp> snapshot = Map.of();

  WebappSync(Path source, Path target, Map<String, String> tokens, List<String> filterPatterns) {
    this.source = source.toAbsolutePath().normalize();
    this.target = target.toAbsolutePath().normalize();
    if (this.target.startsWith(this.source) || this.source.startsWith(this.target)) {
      throw new IllegalArgumentException("The web application source " + this.source
          + " and staging directory " + this.target + " must not contain each other");
    }
    this.tokens = Map.copyOf(tokens);
    List<Pattern> patterns = new ArrayList<>();
    for (String pattern : filterPatterns) patterns.add(toRegex(pattern));
    this.filters = List.copyOf(patterns);
  }

  /** Copies the whole source directory, removing files from the staging directory that are not in the source. */
  void mirror() throws IOException {
    Files.createDirectories(this.target);
    Map<String, Stamp> current = scan();
    for (String path : current.keySet()) copy(path);
    try (Stream<Path> files = Files.walk(this.target)) {
      List<Path> extraneous = files
          .filter(p -> !p.equals(this.target))
          .filter(p -> !current.containsKey(relative(this.target, p)) && !isSourceDirectory(p))
          .sorted(Comparator.reverseOrder())
          .toList();
      for (Path p : extraneous) Files.deleteIfExists(p);
    }
    this.snapshot = current;
  }

  /** Starts a daemon thread copying changed files and removing deleted ones. */
  void watch() {
    Thread watcher = new Thread(() -> {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          Thread.sleep(POLL_INTERVAL_MS);
          syncChanges();
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
        } catch (IOException | UncheckedIOException ex) {
          // Typically a file being saved while scanning: retry on the next poll
          System.err.println("[berlioz-dev] Unable to sync web application: " + ex.getMessage());
        }
      }
    }, "berlioz-dev-webapp-sync");
    watcher.setDaemon(true);
    watcher.start();
  }

  private void syncChanges() throws IOException {
    Map<String, Stamp> current = scan();
    List<String> changed = new ArrayList<>();
    for (Map.Entry<String, Stamp> entry : current.entrySet()) {
      if (!entry.getValue().equals(this.snapshot.get(entry.getKey()))) {
        copy(entry.getKey());
        changed.add(entry.getKey());
      }
    }
    for (String path : this.snapshot.keySet()) {
      if (!current.containsKey(path)) {
        Files.deleteIfExists(this.target.resolve(path));
        changed.add(path + " (deleted)");
      }
    }
    this.snapshot = current;
    if (!changed.isEmpty()) {
      System.out.println("[berlioz-dev] Synced " + String.join(", ", changed));
    }
  }

  private Map<String, Stamp> scan() throws IOException {
    Map<String, Stamp> stamps = new HashMap<>();
    try (Stream<Path> files = Files.walk(this.source)) {
      for (Path p : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
        stamps.put(relative(this.source, p), new Stamp(Files.getLastModifiedTime(p), Files.size(p)));
      }
    }
    return stamps;
  }

  private void copy(String path) throws IOException {
    Path from = this.source.resolve(path);
    Path to = this.target.resolve(path);
    Files.createDirectories(to.getParent());
    if (isFiltered(path)) {
      String content = Files.readString(from, StandardCharsets.UTF_8);
      for (Map.Entry<String, String> token : this.tokens.entrySet()) {
        content = content.replace("@" + token.getKey() + "@", token.getValue());
      }
      Files.writeString(to, content, StandardCharsets.UTF_8);
      Files.setLastModifiedTime(to, Files.getLastModifiedTime(from));
    } else {
      Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }
  }

  private boolean isFiltered(String path) {
    for (Pattern filter : this.filters) {
      if (filter.matcher(path).matches()) return true;
    }
    return false;
  }

  /** Keeps directories that still exist in the source (they may contain no file). */
  private boolean isSourceDirectory(Path staged) {
    return Files.isDirectory(staged) && Files.isDirectory(this.source.resolve(relative(this.target, staged)));
  }

  private static String relative(Path base, Path path) {
    return base.relativize(path).toString().replace('\\', '/');
  }

  /**
   * Converts an Ant-style pattern, as used by Gradle's {@code filesMatching}, to a regular
   * expression: {@code **} matches any number of directories, {@code *} and {@code ?} match within
   * a path segment.
   */
  static Pattern toRegex(String pattern) {
    StringBuilder regex = new StringBuilder();
    String p = pattern.replace('\\', '/');
    for (int i = 0; i < p.length(); i++) {
      char c = p.charAt(i);
      if (c == '*' && i + 1 < p.length() && p.charAt(i + 1) == '*') {
        boolean slash = i + 2 < p.length() && p.charAt(i + 2) == '/';
        regex.append(slash ? "(?:.*/)?" : ".*");
        i += slash ? 2 : 1;
      } else if (c == '*') {
        regex.append("[^/]*");
      } else if (c == '?') {
        regex.append("[^/]");
      } else {
        regex.append(Pattern.quote(String.valueOf(c)));
      }
    }
    return Pattern.compile(regex.toString());
  }

  private record Stamp(FileTime modified, long size) {
    Stamp {
      Objects.requireNonNull(modified);
    }
  }

}
