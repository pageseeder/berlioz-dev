package org.pageseeder.berlioz.dev.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.BindException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jetty.ee8.webapp.ClassMatcher;
import org.eclipse.jetty.ee8.webapp.WebAppContext;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 * Runs an exploded Berlioz web application on embedded Jetty 12 (EE8, javax.servlet) for local
 * development.
 *
 * <p>The web application is served from its source directory, or from a staging copy kept in
 * sync with it when tokens must be replaced, so changes to XSLT, configuration and static files
 * are picked up without a restart; classes and libraries are
 * added to the web application class loader from the given classpath, like WEB-INF/classes and
 * WEB-INF/lib.
 *
 * <p>Options are given as {@code --name=value} arguments:
 * <ul>
 *   <li>{@code webapp} (required): the web application directory to serve</li>
 *   <li>{@code webapp-source}: if set, this directory is mirrored into {@code webapp} and kept
 *       in sync while the server runs, replacing tokens in the filtered files</li>
 *   <li>{@code token} (repeatable): a {@code NAME=value} pair; {@code @NAME@} is replaced by the
 *       value in filtered files</li>
 *   <li>{@code filter} (repeatable): an Ant-style pattern of the files in which tokens are
 *       replaced, relative to the web application (e.g. {@code WEB-INF/config/config.xml})</li>
 *   <li>{@code classpath}: the web application classpath, using the platform path separator</li>
 *   <li>{@code context-path}: defaults to {@code /}</li>
 *   <li>{@code hostname}: the host name of the URLs printed at startup and of the self-signed
 *       certificate; defaults to {@code localhost}</li>
 *   <li>{@code http-port}: defaults to 8080</li>
 *   <li>{@code https-port}: HTTPS is disabled unless set</li>
 *   <li>{@code keystore}: the PKCS12 keystore for HTTPS, created with a self-signed certificate
 *       for the host name and localhost if it does not exist, or created again if it is a
 *       certificate made by this launcher for another host name</li>
 *   <li>{@code keystore-password}: defaults to {@code berlioz-dev}</li>
 *   <li>{@code webinf-include-jar-pattern}: which jars are scanned for web fragments and
 *       META-INF/resources</li>
 *   <li>{@code temp-dir}: the Jetty temporary directory for the web application</li>
 *   <li>{@code jetty-log}: the file Jetty logs to, emptied at startup, its warnings and errors
 *       still being printed on standard error; defaults to standard error only (the logs of a
 *       web application with its own SLF4J binding are not affected)</li>
 * </ul>
 *
 * <p>Press ENTER to stop the server (end of input is ignored, so it keeps running when started
 * without an interactive console); it also stops on interrupt. Once stopped, it warns about any
 * port that has not been released.
 */
public final class DevServer {

  private static final String DEFAULT_KEYSTORE_PASSWORD = "berlioz-dev";

  private static final String KEY_ALIAS = "berlioz-dev";

  private DevServer() {
  }

  public static void main(String[] args) throws Exception {
    Map<String, List<String>> multiOptions = parse(args);
    Map<String, String> options = new HashMap<>();
    multiOptions.forEach((name, values) -> options.put(name, values.get(values.size() - 1)));
    Path jettyLog = null;
    String jettyLogOption = options.get("jetty-log");
    if (jettyLogOption != null && !jettyLogOption.isBlank()) {
      // Before any Jetty class logs anything
      Path file = Path.of(jettyLogOption).toAbsolutePath();
      if (JettyLog.redirectTo(file, System.err)) {
        jettyLog = file;
      } else {
        System.err.println("[WARNING] Unable to redirect the Jetty logs to " + file + ": Jetty is not using jetty-slf4j-impl");
      }
    }
    Path webapp = Path.of(required(options, "webapp")).toAbsolutePath();

    WebappSync sync = null;
    String sourceOption = options.get("webapp-source");
    if (sourceOption != null && !sourceOption.isBlank()) {
      Path source = Path.of(sourceOption).toAbsolutePath();
      if (!Files.isDirectory(source)) {
        throw new IllegalArgumentException("Web application source directory not found: " + source);
      }
      sync = new WebappSync(source, webapp, toTokens(multiOptions.getOrDefault("token", List.of())),
          multiOptions.getOrDefault("filter", List.of()));
      sync.mirror();
    }
    if (!Files.isDirectory(webapp)) {
      throw new IllegalArgumentException("Web application directory not found: " + webapp);
    }
    String hostname = options.getOrDefault("hostname", "localhost").trim();
    if (hostname.isEmpty()) hostname = "localhost";
    int httpPort = Integer.parseInt(options.getOrDefault("http-port", "8080"));
    String httpsPortOption = options.get("https-port");

    Server server = new Server();
    // Stopped by our own shutdown hook, so that the ports can be checked once released
    server.setStopAtShutdown(false);

    HttpConfiguration http = new HttpConfiguration();
    http.setSendServerVersion(false);
    ServerConnector httpConnector = new ServerConnector(server, new HttpConnectionFactory(http));
    httpConnector.setPort(httpPort);
    server.addConnector(httpConnector);

    Integer httpsPort = null;
    if (httpsPortOption != null && !httpsPortOption.isBlank()) {
      httpsPort = Integer.parseInt(httpsPortOption);
      Path keystore = Path.of(required(options, "keystore")).toAbsolutePath();
      String password = options.getOrDefault("keystore-password", DEFAULT_KEYSTORE_PASSWORD);
      ensureKeystore(keystore, password, hostname);

      http.setSecureScheme("https");
      http.setSecurePort(httpsPort);
      HttpConfiguration https = new HttpConfiguration(http);
      SecureRequestCustomizer customizer = new SecureRequestCustomizer();
      // The self-signed certificate is only for the host name: allow others (e.g. a LAN IP)
      customizer.setSniHostCheck(false);
      https.addCustomizer(customizer);

      SslContextFactory.Server ssl = new SslContextFactory.Server();
      ssl.setKeyStorePath(keystore.toString());
      ssl.setKeyStoreType("PKCS12");
      ssl.setKeyStorePassword(password);
      ServerConnector httpsConnector = new ServerConnector(server,
          new SslConnectionFactory(ssl, "http/1.1"), new HttpConnectionFactory(https));
      httpsConnector.setPort(httpsPort);
      server.addConnector(httpsConnector);
    }

    String contextPath = options.getOrDefault("context-path", "/");
    WebAppContext context = new WebAppContext();
    context.setContextPath(contextPath);
    context.setBaseResourceAsPath(webapp);
    // The web application uses its own SLF4J binding: hide Jetty's, which it would otherwise find and fail to load
    String jettyLogging = JettyLog.implementationLocation();
    if (jettyLogging != null) context.addServerClassMatcher(new ClassMatcher(jettyLogging));
    String classpath = options.get("classpath");
    if (classpath != null && !classpath.isBlank()) {
      context.setExtraClasspath(toExtraClasspath(classpath));
    }
    String jarPattern = options.get("webinf-include-jar-pattern");
    if (jarPattern != null && !jarPattern.isBlank()) {
      context.setAttribute("org.eclipse.jetty.server.webapp.WebInfIncludeJarPattern", jarPattern);
    }
    String tempDir = options.get("temp-dir");
    if (tempDir != null && !tempDir.isBlank()) {
      File dir = new File(tempDir).getAbsoluteFile();
      Files.createDirectories(dir.toPath());
      context.setTempDirectory(dir);
    }
    // Do not lock static files (allows editing them while the server runs)
    context.setInitParameter("org.eclipse.jetty.servlet.Default.useFileMappedBuffer", "false");
    context.setThrowUnavailableOnStartupException(true);
    server.setHandler(context);

    Map<Integer, String> ports = new LinkedHashMap<>();
    ports.put(httpPort, "HTTP");
    if (httpsPort != null) ports.put(httpsPort, "HTTPS");

    try {
      server.start();
    } catch (Exception ex) {
      BindException bind = findBindException(ex);
      if (bind == null) throw ex;
      stopQuietly(server);
      StringBuilder message = new StringBuilder("\n[WARNING] Unable to start the Berlioz dev server: ")
          .append(bind.getMessage()).append('\n');
      for (Map.Entry<Integer, String> port : ports.entrySet()) {
        List<String> owners = Ports.owners(port.getKey());
        if (!owners.isEmpty()) {
          message.append("  ").append(port.getKey()).append(" (").append(port.getValue()).append(") is held by ")
              .append(String.join(", ", owners)).append('\n');
        }
      }
      System.err.println(message);
      System.exit(1);
    }
    AtomicBoolean stopped = new AtomicBoolean();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> stop(server, ports, stopped), "berlioz-dev-shutdown"));
    if (sync != null) sync.watch();

    StringBuilder ready = new StringBuilder("\nBerlioz dev server started:\n");
    ready.append("  http://").append(hostname).append(':').append(httpPort).append(contextPath).append('\n');
    if (httpsPort != null) {
      ready.append("  https://").append(hostname).append(':').append(httpsPort).append(contextPath)
          .append("  (self-signed certificate)\n");
    }
    if (jettyLog != null) ready.append("Jetty logs: ").append(jettyLog).append(" (warnings and errors shown here too)\n");
    ready.append("Press ENTER to stop.\n");
    System.out.println(ready);

    startConsoleListener(server, ports, stopped);
    server.join();
  }

  /**
   * Stops the server (once) and checks that its ports have been released, warning otherwise.
   */
  private static void stop(Server server, Map<Integer, String> ports, AtomicBoolean stopped) {
    if (!stopped.compareAndSet(false, true)) return;
    try {
      server.stop();
    } catch (Exception ex) {
      System.err.println("[WARNING] Error while stopping the Berlioz dev server: " + ex);
    }
    List<String> held = new ArrayList<>();
    for (Map.Entry<Integer, String> port : ports.entrySet()) {
      if (!Ports.isFree(port.getKey())) {
        List<String> owners = Ports.owners(port.getKey());
        held.add(port.getKey() + " (" + port.getValue() + ")"
            + (owners.isEmpty() ? "" : " held by " + String.join(", ", owners)));
      }
    }
    if (!held.isEmpty()) {
      System.err.println("[WARNING] Port(s) still in use after stopping the Berlioz dev server: "
          + String.join("; ", held));
    }
  }

  private static void stopQuietly(Server server) {
    try {
      server.stop();
    } catch (Exception ignored) {
      // Already failing to start
    }
  }

  private static BindException findBindException(Throwable ex) {
    for (Throwable t = ex; t != null; t = t.getCause()) {
      if (t instanceof BindException bind) return bind;
      for (Throwable suppressed : t.getSuppressed()) {
        BindException bind = findBindException(suppressed);
        if (bind != null) return bind;
      }
    }
    return null;
  }

  /**
   * Stops the server when a line is entered on the console; end of input is ignored so that the
   * server keeps running when there is no interactive console.
   */
  private static void startConsoleListener(Server server, Map<Integer, String> ports, AtomicBoolean stopped) {
    Thread listener = new Thread(() -> {
      try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
        if (in.readLine() != null) {
          System.out.println("Stopping Berlioz dev server...");
          stop(server, ports, stopped);
        }
      } catch (Exception ex) {
        // Unable to read the console: rely on interrupt to stop
      }
    }, "berlioz-dev-console");
    listener.setDaemon(true);
    listener.start();
  }

  /**
   * Creates a PKCS12 keystore with a self-signed certificate for the host name and localhost
   * using the keytool of the running JVM, unless it already exists. A keystore created by this
   * launcher for another host name is replaced; any other keystore is left as is.
   */
  private static void ensureKeystore(Path keystore, String password, String hostname)
      throws IOException, InterruptedException {
    if (Files.exists(keystore)) {
      if (!isOwnCertificateForOtherHost(keystore, password, hostname)) return;
      System.out.println("Replacing self-signed certificate in " + keystore + " to cover " + hostname);
      Files.delete(keystore);
    }
    Path parent = keystore.getParent();
    if (parent != null) Files.createDirectories(parent);
    String exe = System.getProperty("os.name").toLowerCase().contains("windows") ? "keytool.exe" : "keytool";
    Path keytool = Path.of(System.getProperty("java.home"), "bin", exe);
    String san = "dns:localhost" + ("localhost".equalsIgnoreCase(hostname) ? "" : ",dns:" + hostname);
    List<String> command = List.of(keytool.toString(), "-genkeypair",
        "-alias", KEY_ALIAS,
        "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650",
        "-dname", "CN=" + hostname,
        "-ext", "SAN=" + san + ",ip:127.0.0.1,ip:0:0:0:0:0:0:0:1",
        "-storetype", "PKCS12",
        "-keystore", keystore.toString(),
        "-storepass", password, "-keypass", password);
    Process process = new ProcessBuilder(command).inheritIO().start();
    if (process.waitFor() != 0) {
      throw new IOException("Unable to create the development keystore " + keystore + " with " + keytool);
    }
    System.out.println("Created self-signed certificate for " + hostname + " in " + keystore);
  }

  /**
   * Whether the keystore holds a certificate created by this launcher (under its alias, with its
   * password) that does not list the host name: such a certificate must be created again.
   */
  private static boolean isOwnCertificateForOtherHost(Path keystore, String password, String hostname) {
    try (var in = Files.newInputStream(keystore)) {
      KeyStore store = KeyStore.getInstance("PKCS12");
      store.load(in, password.toCharArray());
      Certificate certificate = store.getCertificate(KEY_ALIAS);
      if (!(certificate instanceof X509Certificate x509)) return false;
      Collection<List<?>> names = x509.getSubjectAlternativeNames();
      if (names != null) {
        for (List<?> name : names) {
          // Type 2 is a DNS name
          if (Integer.valueOf(2).equals(name.get(0)) && hostname.equalsIgnoreCase(String.valueOf(name.get(1)))) {
            return false;
          }
        }
      }
      return true;
    } catch (Exception ex) {
      // Not a keystore created by this launcher: leave it alone
      return false;
    }
  }

  /** Converts a platform classpath to Jetty's comma-separated extra classpath (skipping missing entries). */
  private static String toExtraClasspath(String classpath) {
    List<String> entries = new ArrayList<>();
    for (String entry : classpath.split(File.pathSeparator)) {
      if (!entry.isBlank() && new File(entry).exists()) entries.add(entry);
    }
    return String.join(",", entries);
  }

  private static Map<String, List<String>> parse(String[] args) {
    Map<String, List<String>> options = new HashMap<>();
    for (String arg : args) {
      if (!arg.startsWith("--") || arg.indexOf('=') < 0) {
        throw new IllegalArgumentException("Expected an argument of the form --name=value but got: " + arg);
      }
      int eq = arg.indexOf('=');
      options.computeIfAbsent(arg.substring(2, eq), k -> new ArrayList<>()).add(arg.substring(eq + 1));
    }
    return options;
  }

  private static Map<String, String> toTokens(List<String> pairs) {
    Map<String, String> tokens = new LinkedHashMap<>();
    for (String pair : pairs) {
      int eq = pair.indexOf('=');
      if (eq <= 0) throw new IllegalArgumentException("Expected --token=NAME=value but got: --token=" + pair);
      tokens.put(pair.substring(0, eq), pair.substring(eq + 1));
    }
    return tokens;
  }

  private static String required(Map<String, String> options, String name) {
    String value = options.get(name);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing required argument --" + name + "=...");
    }
    return value;
  }

}
