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
}
