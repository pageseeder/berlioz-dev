plugins {
  `java-gradle-plugin`
}

description = "Gradle plugin running a Berlioz web application locally on embedded Jetty"

tasks.withType<JavaCompile>().configureEach {
  options.release = 17
}

gradlePlugin {
  plugins {
    create("berliozDev") {
      id = "org.pageseeder.berlioz-dev"
      implementationClass = "org.pageseeder.berlioz.dev.gradle.BerliozDevPlugin"
      displayName = "Berlioz dev server"
      description = project.description
    }
  }
}

// The plugin adds a dependency on the launcher with the same version as itself
tasks.processResources {
  val version = project.version.toString()
  inputs.property("version", version)
  filesMatching("**/berlioz-dev.properties") {
    expand("version" to version)
  }
}

dependencies {
  testImplementation(platform("org.junit:junit-bom:6.1.3"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
  useJUnitPlatform()
  // Gradle versions the functional tests run against: the minimum supported (8.5, the first to run on Java 21),
  // the last 8.x and the wrapper's. Use `-PtestGradleVersions=current` for a quick run.
  val gradleVersions = providers.gradleProperty("testGradleVersions").orElse("8.5,8.14.7,current")
  inputs.property("gradleVersions", gradleVersions)
  jvmArgumentProviders.add(GradleVersionsArgument(gradleVersions))
}

class GradleVersionsArgument(private val versions: Provider<String>) : CommandLineArgumentProvider {
  override fun asArguments() = listOf("-DberliozDev.gradleVersions=${versions.get()}")
}
