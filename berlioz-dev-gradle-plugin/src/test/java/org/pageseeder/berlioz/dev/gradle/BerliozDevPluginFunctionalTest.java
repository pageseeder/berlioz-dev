package org.pageseeder.berlioz.dev.gradle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs the plugin in a sample build with each Gradle version listed in the {@code berliozDev.gradleVersions}
 * system property (comma-separated, {@code current} for the Gradle version running the tests).
 */
class BerliozDevPluginFunctionalTest {

  private static final String CURRENT = "current";

  @TempDir
  Path projectDir;

  static Stream<String> gradleVersions() {
    return Arrays.stream(System.getProperty("berliozDev.gradleVersions", CURRENT).split(","))
        .map(String::trim)
        .filter(v -> !v.isEmpty());
  }

  @ParameterizedTest(name = "Gradle {0}")
  @MethodSource("gradleVersions")
  void registersRunTasksAndIsConfigurationCacheCompatible(String gradleVersion) throws IOException {
    Files.createDirectories(projectDir.resolve("src/main/webapp"));
    Files.writeString(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"sample\"\n");
    Files.writeString(projectDir.resolve("build.gradle.kts"), String.join("\n",
        "plugins { id(\"org.pageseeder.berlioz-dev\") }",
        "version = \"1.2.3\"",
        "berliozDev {",
        "  hostname.set(\"sample.localhost\")",
        "  httpPort.set(8999)",
        "  httpsEnabled.set(false)",
        "  mode.set(\"local\")",
        "  appdata.set(layout.projectDirectory.dir(\"local/appdata\"))",
        "  webInfIncludeJarPattern.set(\".*/sample-[^/]*\\\\.jar$\")",
        "}",
        "tasks.register(\"printArgs\") {",
        "  val run = tasks.named<JavaExec>(\"appRun\")",
        "  val jvm = run.map { t -> t.jvmArgumentProviders.flatMap { it.asArguments() } }",
        "  val args = run.map { t -> t.argumentProviders.flatMap { it.asArguments() } }",
        "  doLast { println(\"JVM=\" + jvm.get()); println(\"ARGS=\" + args.get()) }",
        "}",
        ""));

    BuildResult tasks = run(gradleVersion, "tasks", "--group=berlioz", "--configuration-cache");
    assertTrue(tasks.getOutput().contains("appRun - "), tasks.getOutput());
    assertTrue(tasks.getOutput().contains("appRunDebug - "), tasks.getOutput());
    assertTrue(tasks.getOutput().contains("Configuration cache entry stored"), tasks.getOutput());

    String output = run(gradleVersion, "printArgs").getOutput();
    assertTrue(output.contains("-Dberlioz.mode=local"), output);
    assertTrue(output.contains("--hostname=sample.localhost"), output);
    assertTrue(output.contains("--http-port=8999"), output);
    assertTrue(output.contains("--webinf-include-jar-pattern=.*/sample-[^/]*\\.jar$"), output);
    assertTrue(!output.contains("--https-port"), output);
    // Tokens are replaced in a staged copy of the web application
    assertTrue(output.contains("--webapp-source=" + projectDir.resolve("src/main/webapp").toRealPath()), output);
    assertTrue(output.contains("berlioz-dev/webapp,"), output);
    assertTrue(output.contains("--token=APP_VERSION=1.2.3"), output);
    assertTrue(output.contains("--filter=WEB-INF/config/config.xml"), output);
  }

  @ParameterizedTest(name = "Gradle {0}")
  @MethodSource("gradleVersions")
  void replacesTokensInWar(String gradleVersion) throws IOException {
    Files.writeString(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"sample\"\n");
    Files.writeString(projectDir.resolve("build.gradle.kts"), String.join("\n",
        "plugins { id(\"org.pageseeder.berlioz-dev\") }",
        "version = \"1.2.3\"",
        ""));
    Path config = projectDir.resolve("src/main/webapp/WEB-INF/config");
    Files.createDirectories(config);
    Files.writeString(config.resolve("config.xml"), "<app version=\"@APP_VERSION@\" date=\"@APP_DATE@\"/>");
    Files.writeString(config.resolve("other.xml"), "<app version=\"@APP_VERSION@\"/>");

    run(gradleVersion, "war", "--configuration-cache");

    try (ZipFile war = new ZipFile(projectDir.resolve("build/libs/sample-1.2.3.war").toFile())) {
      String filtered = new String(war.getInputStream(war.getEntry("WEB-INF/config/config.xml")).readAllBytes());
      String unfiltered = new String(war.getInputStream(war.getEntry("WEB-INF/config/other.xml")).readAllBytes());
      assertTrue(filtered.equals("<app version=\"1.2.3\" date=\"" + LocalDate.now() + "\"/>"), filtered);
      assertTrue(unfiltered.contains("@APP_VERSION@"), unfiltered);
    }
  }

  private BuildResult run(String gradleVersion, String... arguments) {
    GradleRunner runner = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments(arguments);
    if (!CURRENT.equals(gradleVersion)) {
      runner.withGradleVersion(gradleVersion);
    }
    return runner.build();
  }

}
