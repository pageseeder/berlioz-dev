package org.pageseeder.berlioz.dev.gradle;

import java.util.ArrayList;
import java.util.List;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.process.CommandLineArgumentProvider;

/**
 * JVM arguments of the dev server: Berlioz system properties and additional arguments.
 */
public abstract class DevServerJvmArguments implements CommandLineArgumentProvider {

  @Input
  @Optional
  public abstract Property<String> getMode();

  @Internal
  public abstract DirectoryProperty getAppdata();

  @Input
  public abstract ListProperty<String> getJvmArgs();

  @Override
  public Iterable<String> asArguments() {
    List<String> args = new ArrayList<>();
    if (getMode().isPresent()) {
      args.add("-Dberlioz.mode=" + getMode().get());
    }
    if (getAppdata().isPresent()) {
      args.add("-Dberlioz.appdata=" + getAppdata().get().getAsFile().getAbsolutePath());
    }
    args.addAll(getJvmArgs().get());
    return args;
  }

}
