// Build logic for local development: the Berlioz dev-server Gradle plugin and its Jetty launcher.
// Included by the root build both as a plugin build and as a regular build, so the plugin's
// `berliozDevServer` dependency is substituted with the local `berlioz-dev-server` project.
rootProject.name = "build-logic"

dependencyResolutionManagement {
  repositories {
    mavenCentral()
  }
}

include("berlioz-dev-gradle-plugin", "berlioz-dev-server")
