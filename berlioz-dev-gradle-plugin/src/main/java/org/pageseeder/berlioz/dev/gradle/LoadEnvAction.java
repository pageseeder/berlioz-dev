package org.pageseeder.berlioz.dev.gradle;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.file.RegularFile;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;

/**
 * Loads the variables of a {@code .env} file into the environment of the server process and
 * warns about required variables that are not set.
 *
 * <p>Variables already set in the environment take precedence over the file, and blank values
 * in the file are ignored. Values are never logged.
 *
 * <p>A class rather than a lambda, so that the task can be stored in the configuration cache;
 * the file is read when the task runs, so changes apply on the next start.
 */
final class LoadEnvAction implements Action<Task> {

  private final Provider<RegularFile> envFile;

  private final Provider<List<String>> required;

  LoadEnvAction(Provider<RegularFile> envFile, Provider<List<String>> required) {
    this.envFile = envFile;
    this.required = required;
  }

  @Override
  public void execute(Task task) {
    JavaExec exec = (JavaExec) task;
    Logger logger = task.getLogger();
    File file = this.envFile.isPresent() ? this.envFile.get().getAsFile() : null;

    if (file != null && file.isFile()) {
      Map<String, String> values;
      try {
        values = DotEnv.parse(Files.readString(file.toPath(), StandardCharsets.UTF_8), file.getName());
      } catch (IOException ex) {
        throw new UncheckedIOException("Unable to read " + file, ex);
      }
      List<String> loaded = new ArrayList<>();
      List<String> overridden = new ArrayList<>();
      for (Map.Entry<String, String> entry : values.entrySet()) {
        if (entry.getValue().isBlank()) continue;
        if (isSet(exec.getEnvironment().get(entry.getKey()))) {
          overridden.add(entry.getKey());
        } else {
          exec.environment(entry.getKey(), entry.getValue());
          loaded.add(entry.getKey());
        }
      }
      logger.lifecycle("Loaded {} environment variable(s) from {}{}", loaded.size(), file,
          loaded.isEmpty() ? "" : ": " + String.join(", ", loaded));
      if (!overridden.isEmpty()) {
        logger.lifecycle("Kept from the current environment: {}", String.join(", ", overridden));
      }
    } else if (file != null) {
      logger.info("Environment file {} not found", file);
    }

    List<String> missing = new ArrayList<>();
    for (String name : this.required.get()) {
      if (!isSet(exec.getEnvironment().get(name))) missing.add(name);
    }
    if (!missing.isEmpty()) {
      String hint = file != null
          ? "Set them in " + file + (file.isFile() ? "" : " (not found)") + " or in the environment."
          : "Set them in the environment, or use berliozDev { env = ... } to load a .env file.";
      logger.warn("\n[WARNING] Missing environment variable(s): {}\n{}\n", String.join(", ", missing), hint);
    }
  }

  private static boolean isSet(Object value) {
    return value != null && !value.toString().isBlank();
  }

}
