plugins {
  `java-library`
  `maven-publish`
}

description = "Embedded Jetty 12 (EE8) launcher for running a Berlioz web application locally"

tasks.withType<JavaCompile>().configureEach {
  options.release = 17
}

// Keep in line with the Jetty version of the apps' Docker images
val jettyVersion = "12.0.41"

dependencies {
  api("org.eclipse.jetty.ee8:jetty-ee8-webapp:$jettyVersion")
  runtimeOnly("org.eclipse.jetty:jetty-slf4j-impl:$jettyVersion")
}

// The plugin project needs none: java-gradle-plugin creates the plugin jar and marker publications
publishing {
  publications {
    create<MavenPublication>("maven") {
      from(components["java"])
    }
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
