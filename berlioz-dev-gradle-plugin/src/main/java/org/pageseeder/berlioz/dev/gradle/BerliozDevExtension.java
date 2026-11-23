package org.pageseeder.berlioz.dev.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

/**
 * Settings of the Berlioz dev server, available as {@code berliozDev { ... }}.
 */
public abstract class BerliozDevExtension {

  /**
   * Host name of the server URLs, e.g. {@code developer.localhost} (default: {@code localhost}):
   * used in the URLs printed at startup and in the self-signed certificate, which is created
   * again when it does not cover this name. The server still listens on all interfaces; browsers
   * resolve {@code *.localhost} to the loopback address, other names need a hosts file entry.
   */
  public abstract Property<String> getHostname();

  /** HTTP port (default: 8080). */
  public abstract Property<Integer> getHttpPort();

  /** Whether to also listen on HTTPS with a self-signed certificate (default: true). */
  public abstract Property<Boolean> getHttpsEnabled();

  /** HTTPS port (default: 8443). */
  public abstract Property<Integer> getHttpsPort();

  /** JDWP port used by {@code appRunDebug} (default: 5005). */
  public abstract Property<Integer> getDebugPort();

  /** Context path of the web application (default: {@code /}). */
  public abstract Property<String> getContextPath();

  /** Berlioz mode, passed as {@code -Dberlioz.mode} (default: none, Berlioz uses its own default). */
  public abstract Property<String> getMode();

  /** Berlioz application data directory, passed as {@code -Dberlioz.appdata} (default: none). */
  public abstract DirectoryProperty getAppdata();

  /** Web application source directory (default: {@code src/main/webapp}). */
  public abstract DirectoryProperty getWebappDirectory();

  /**
   * Tokens replaced in the {@link #getFilteredFiles() filtered files}: {@code @NAME@} is replaced
   * by the value, both in the WAR and in the web application run by {@code appRun}
   * (default: {@code APP_VERSION} = project version, {@code APP_DATE} = today in ISO format).
   */
  public abstract MapProperty<String, String> getTokens();

  /**
   * Ant-style patterns of the web application files in which tokens are replaced, relative to
   * the web application directory (default: {@code WEB-INF/config/config.xml}).
   *
   * <p>When tokens are replaced, {@code appRun} serves a copy of the web application in
   * {@code build/berlioz-dev/webapp} kept in sync with the source directory while it runs.
   */
  public abstract ListProperty<String> getFilteredFiles();

  /**
   * Pattern of the jars scanned for {@code web-fragment.xml} and {@code META-INF/resources},
   * set as the {@code org.eclipse.jetty.server.webapp.WebInfIncludeJarPattern} context attribute
   * (default: none, Jetty's own default).
   */
  public abstract Property<String> getWebInfIncludeJarPattern();

  /**
   * PKCS12 keystore for HTTPS; created with a self-signed certificate for {@link #getHostname()
   * the host name} and localhost if it does not exist (default: {@code build/berlioz-dev/keystore.p12}). Keep it outside the build
   * directory to avoid accepting a new certificate after each clean.
   */
  public abstract RegularFileProperty getKeystore();

  /**
   * A {@code .env} file whose variables are set in the environment of the server process
   * (default: none). Variables already set in the environment take precedence; blank values are
   * ignored. Read each time the server starts.
   */
  public abstract RegularFileProperty getEnv();

  /** Environment variables to warn about when the server starts without them (default: none). */
  public abstract ListProperty<String> getRequiredEnv();

  /** Additional JVM arguments for the server. */
  public abstract ListProperty<String> getJvmArgs();

  /** Overrides the Jetty version of the launcher (default: none, the launcher's own version). */
  public abstract Property<String> getJettyVersion();

}
