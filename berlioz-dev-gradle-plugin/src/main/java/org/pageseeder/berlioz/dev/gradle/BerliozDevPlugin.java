package org.pageseeder.berlioz.dev.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.bundling.War;
import org.gradle.jvm.toolchain.JavaToolchainService;

/**
 * Runs a Berlioz web application locally on embedded Jetty 12 (EE8), as a replacement for Gretty.
 *
 * <p>Applies the {@code war} plugin and adds:
 * <ul>
 *   <li>the {@code berliozDev} extension (see {@link BerliozDevExtension})</li>
 *   <li>the {@code berliozDevServer} configuration: the launcher and Jetty</li>
 *   <li>{@code appRun}: runs the web application in place</li>
 *   <li>{@code appRunDebug}: same, with a JDWP agent listening on the debug port</li>
 * </ul>
 *
 * <p>Both tasks can load environment variables from a {@code .env} file (see
 * {@link BerliozDevExtension#getEnv()}).
 *
 * <p>Tokens such as {@code @APP_VERSION@} are replaced in the filtered files of the WAR and of the
 * web application run locally (see {@link BerliozDevExtension#getTokens()}).
 *
 * <p>The web application classpath is the same as the WAR's WEB-INF/classes and WEB-INF/lib:
 * the main output plus the runtime classpath, without {@code providedRuntime} dependencies
 * (Jetty provides the servlet API).
 */
public abstract class BerliozDevPlugin implements Plugin<Project> {

  public static final String EXTENSION_NAME = "berliozDev";

  public static final String CONFIGURATION_NAME = "berliozDevServer";

  public static final String LAUNCHER_MAIN_CLASS = "org.pageseeder.berlioz.dev.server.DevServer";

  private static final String LAUNCHER_MODULE = "org.pageseeder.berlioz:berlioz-dev-server";

  @Override
  public void apply(Project project) {
    project.getPluginManager().apply(WarPlugin.class);
    ProjectLayout layout = project.getLayout();

    BerliozDevExtension extension = project.getExtensions().create(EXTENSION_NAME, BerliozDevExtension.class);
    extension.getHttpPort().convention(8080);
    extension.getHttpsEnabled().convention(true);
    extension.getHttpsPort().convention(8443);
    extension.getDebugPort().convention(5005);
    extension.getContextPath().convention("/");
    extension.getWebappDirectory().convention(layout.getProjectDirectory().dir("src/main/webapp"));
    extension.getKeystore().convention(layout.getBuildDirectory().file("berlioz-dev/keystore.p12"));
    String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
    extension.getTokens().convention(project.provider(() ->
        Map.of("APP_VERSION", project.getVersion().toString(), "APP_DATE", today)));
    extension.getFilteredFiles().convention(List.of("WEB-INF/config/config.xml"));

    project.getTasks().named(WarPlugin.WAR_TASK_NAME, War.class, war -> {
      war.getInputs().property("berliozDev.tokens", extension.getTokens());
      war.getInputs().property("berliozDev.filteredFiles", extension.getFilteredFiles());
      war.eachFile(new ReplaceTokensAction(extension.getTokens(), extension.getFilteredFiles()));
    });

    Configuration server = createServerConfiguration(project, extension);

    ConfigurationContainer configurations = project.getConfigurations();
    SourceSet main = project.getExtensions().getByType(SourceSetContainer.class).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
    JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
    JavaToolchainService toolchains = project.getExtensions().getByType(JavaToolchainService.class);

    registerRunTask(project, "appRun", "Runs the web application on embedded Jetty", false,
        extension, server, main, configurations, java, toolchains);
    registerRunTask(project, "appRunDebug", "Runs the web application on embedded Jetty with a debugger port", true,
        extension, server, main, configurations, java, toolchains);
  }

  private static Configuration createServerConfiguration(Project project, BerliozDevExtension extension) {
    Configuration server = project.getConfigurations().create(CONFIGURATION_NAME, c -> {
      c.setDescription("Embedded Jetty launcher used by the appRun tasks");
      c.setCanBeConsumed(false);
      c.setCanBeResolved(true);
    });
    server.defaultDependencies(deps ->
        deps.add(project.getDependencies().create(LAUNCHER_MODULE + ":" + pluginVersion())));
    Provider<String> jettyVersion = extension.getJettyVersion();
    server.getResolutionStrategy().eachDependency(details -> {
      String group = details.getRequested().getGroup();
      boolean isJetty = group.equals("org.eclipse.jetty") || group.startsWith("org.eclipse.jetty.ee");
      if (isJetty && jettyVersion.isPresent()) {
        details.useVersion(jettyVersion.get());
        details.because("berliozDev.jettyVersion");
      }
    });
    return server;
  }

  private static void registerRunTask(Project project, String name, String description, boolean debug,
                                      BerliozDevExtension extension, Configuration server, SourceSet main,
                                      ConfigurationContainer configurations, JavaPluginExtension java,
                                      JavaToolchainService toolchains) {
    project.getTasks().register(name, JavaExec.class, task -> {
      task.setGroup("berlioz");
      task.setDescription(description);
      task.setClasspath(server);
      task.getMainClass().set(LAUNCHER_MAIN_CLASS);
      task.getJavaLauncher().convention(toolchains.launcherFor(java.getToolchain()));
      task.setWorkingDir(project.getProjectDir());
      // Lets the launcher stop when ENTER is pressed
      task.setStandardInput(System.in);
      task.doFirst(new LoadEnvAction(extension.getEnv(), extension.getRequiredEnv()));

      DevServerJvmArguments jvmArguments = project.getObjects().newInstance(DevServerJvmArguments.class);
      jvmArguments.getMode().set(extension.getMode());
      jvmArguments.getAppdata().set(extension.getAppdata());
      jvmArguments.getJvmArgs().set(extension.getJvmArgs());
      task.getJvmArgumentProviders().add(jvmArguments);

      DevServerArguments arguments = project.getObjects().newInstance(DevServerArguments.class);
      // Serve a synchronised copy when there are tokens to replace, otherwise the source in place
      Provider<Boolean> staged = extension.getTokens().zip(extension.getFilteredFiles(),
          (tokens, files) -> !tokens.isEmpty() && !files.isEmpty());
      arguments.getWebappSource().set(staged.flatMap(s -> s
          ? extension.getWebappDirectory()
          : project.getObjects().directoryProperty()));
      arguments.getWebappDirectory().set(staged.flatMap(s -> s
          ? project.getLayout().getBuildDirectory().dir("berlioz-dev/webapp")
          : extension.getWebappDirectory()));
      arguments.getTokens().set(extension.getTokens());
      arguments.getFilteredFiles().set(extension.getFilteredFiles());
      arguments.getWebappClasspath().from(main.getOutput(),
          configurations.named(main.getRuntimeClasspathConfigurationName())
              .map(runtime -> runtime.minus(configurations.getByName(WarPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME))));
      arguments.getContextPath().set(extension.getContextPath());
      arguments.getHttpPort().set(extension.getHttpPort());
      arguments.getHttpsPort().set(extension.getHttpsEnabled().flatMap(enabled -> enabled
          ? extension.getHttpsPort()
          : project.getProviders().provider(() -> null)));
      arguments.getKeystore().set(extension.getKeystore());
      arguments.getWebInfIncludeJarPattern().set(extension.getWebInfIncludeJarPattern());
      arguments.getTempDirectory().set(project.getLayout().getBuildDirectory().dir("berlioz-dev/" + name));
      task.getArgumentProviders().add(arguments);

      if (debug) {
        task.debugOptions(options -> {
          options.getEnabled().set(true);
          options.getPort().set(extension.getDebugPort());
          options.getServer().set(true);
          options.getSuspend().set(false);
        });
      }
    });
  }

  private static String pluginVersion() {
    Properties properties = new Properties();
    try (InputStream in = BerliozDevPlugin.class.getResourceAsStream("berlioz-dev.properties")) {
      if (in == null) throw new IllegalStateException("Missing berlioz-dev.properties");
      properties.load(in);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
    return properties.getProperty("version");
  }

}
