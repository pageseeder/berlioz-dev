package org.pageseeder.berlioz.dev.gradle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.zip.ZipFile;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BerliozDevPluginFunctionalTest {

  @TempDir
  Path projectDir;

  @Test
  void registersRunTasksAndIsConfigurationCacheCompatible() throws IOException {
    Files.createDirectories(projectDir.resolve("src/main/webapp"));
    Files.writeString(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"sample\"\n");
    Files.writeString(projectDir.resolve("build.gradle.kts"), String.join("\n",
        "plugins { id(\"org.pageseeder.berlioz-dev\") }",
        "version = \"1.2.3\"",
        "berliozDev {",
        "  httpPort = 8999",
        "  httpsEnabled = false",
        "  mode = \"local\"",
        "  appdata = layout.projectDirectory.dir(\"local/appdata\")",
        "  webInfIncludeJarPattern = \".*/sample-[^/]*\\\\.jar$\"",
        "}",
        "tasks.register(\"printArgs\") {",
        "  val run = tasks.named<JavaExec>(\"appRun\")",
        "  val jvm = run.map { t -> t.jvmArgumentProviders.flatMap { it.asArguments() } }",
        "  val args = run.map { t -> t.argumentProviders.flatMap { it.asArguments() } }",
        "  doLast { println(\"JVM=\" + jvm.get()); println(\"ARGS=\" + args.get()) }",
        "}",
        ""));

    BuildResult tasks = run("tasks", "--group=berlioz", "--configuration-cache");
    assertTrue(tasks.getOutput().contains("appRun - "), tasks.getOutput());
    assertTrue(tasks.getOutput().contains("appRunDebug - "), tasks.getOutput());
    assertTrue(tasks.getOutput().contains("Configuration cache entry stored"), tasks.getOutput());

    String output = run("printArgs").getOutput();
    assertTrue(output.contains("-Dberlioz.mode=local"), output);
    assertTrue(output.contains("--http-port=8999"), output);
    assertTrue(output.contains("--webinf-include-jar-pattern=.*/sample-[^/]*\\.jar$"), output);
    assertTrue(!output.contains("--https-port"), output);
    // Tokens are replaced in a staged copy of the web application
    assertTrue(output.contains("--webapp-source=" + projectDir.resolve("src/main/webapp").toRealPath()), output);
    assertTrue(output.contains("berlioz-dev/webapp,"), output);
    assertTrue(output.contains("--token=APP_VERSION=1.2.3"), output);
    assertTrue(output.contains("--filter=WEB-INF/config/config.xml"), output);
  }

  @Test
  void replacesTokensInWar() throws IOException {
    Files.writeString(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"sample\"\n");
    Files.writeString(projectDir.resolve("build.gradle.kts"), String.join("\n",
        "plugins { id(\"org.pageseeder.berlioz-dev\") }",
        "version = \"1.2.3\"",
        ""));
    Path config = projectDir.resolve("src/main/webapp/WEB-INF/config");
    Files.createDirectories(config);
    Files.writeString(config.resolve("config.xml"), "<app version=\"@APP_VERSION@\" date=\"@APP_DATE@\"/>");
    Files.writeString(config.resolve("other.xml"), "<app version=\"@APP_VERSION@\"/>");

    run("war", "--configuration-cache");

    try (ZipFile war = new ZipFile(projectDir.resolve("build/libs/sample-1.2.3.war").toFile())) {
      String filtered = new String(war.getInputStream(war.getEntry("WEB-INF/config/config.xml")).readAllBytes());
      String unfiltered = new String(war.getInputStream(war.getEntry("WEB-INF/config/other.xml")).readAllBytes());
      assertTrue(filtered.equals("<app version=\"1.2.3\" date=\"" + LocalDate.now() + "\"/>"), filtered);
      assertTrue(unfiltered.contains("@APP_VERSION@"), unfiltered);
    }
  }

  private BuildResult run(String... arguments) {
    return GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments(arguments)
        .build();
  }

}
