package org.pageseeder.berlioz.dev.gradle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.process.CommandLineArgumentProvider;

/**
 * Command line arguments of the dev server launcher.
 *
 * <p>The web application classpath is a {@code @Classpath} input so that running the server
 * builds the classes and resolves the libraries first.
 */
public abstract class DevServerArguments implements CommandLineArgumentProvider {

  /** Directory served by Jetty: the staging copy when tokens are replaced, otherwise the source. */
  @Internal
  public abstract DirectoryProperty getWebappDirectory();

  /** Source directory mirrored into the web application directory (unset to serve the source in place). */
  @Internal
  public abstract DirectoryProperty getWebappSource();

  @Input
  public abstract MapProperty<String, String> getTokens();

  @Input
  public abstract ListProperty<String> getFilteredFiles();

  @Classpath
  public abstract ConfigurableFileCollection getWebappClasspath();

  @Input
  public abstract Property<String> getContextPath();

  @Input
  public abstract Property<String> getHostname();

  @Input
  public abstract Property<Integer> getHttpPort();

  @Input
  @Optional
  public abstract Property<Integer> getHttpsPort();

  @Internal
  public abstract RegularFileProperty getKeystore();

  @Input
  @Optional
  public abstract Property<String> getWebInfIncludeJarPattern();

  @Internal
  public abstract DirectoryProperty getTempDirectory();

  /** File the Jetty logs are written to (unset for standard error). */
  @Internal
  public abstract RegularFileProperty getJettyLog();

  @Override
  public Iterable<String> asArguments() {
    List<String> args = new ArrayList<>();
    args.add("--webapp=" + getWebappDirectory().get().getAsFile().getAbsolutePath());
    if (getWebappSource().isPresent()) {
      args.add("--webapp-source=" + getWebappSource().get().getAsFile().getAbsolutePath());
      getTokens().get().forEach((name, value) -> args.add("--token=" + name + "=" + value));
      getFilteredFiles().get().forEach(pattern -> args.add("--filter=" + pattern));
    }
    args.add("--classpath=" + getWebappClasspath().getAsPath());
    args.add("--context-path=" + getContextPath().get());
    args.add("--hostname=" + getHostname().get());
    args.add("--http-port=" + getHttpPort().get());
    if (getHttpsPort().isPresent()) {
      args.add("--https-port=" + getHttpsPort().get());
      args.add("--keystore=" + getKeystore().get().getAsFile().getAbsolutePath());
    }
    if (getWebInfIncludeJarPattern().isPresent()) {
      args.add("--webinf-include-jar-pattern=" + getWebInfIncludeJarPattern().get());
    }
    if (getTempDirectory().isPresent()) {
      File dir = getTempDirectory().get().getAsFile();
      args.add("--temp-dir=" + dir.getAbsolutePath());
    }
    if (getJettyLog().isPresent()) {
      args.add("--jetty-log=" + getJettyLog().get().getAsFile().getAbsolutePath());
    }
    return args;
  }

}
