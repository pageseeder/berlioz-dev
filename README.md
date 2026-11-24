# Berlioz dev server

Gradle plugin (`org.pageseeder.berlioz-dev`) running a Berlioz web application locally on embedded
Jetty 12 (EE8), replacing Gretty. It adds `appRun` / `appRunDebug` (debugger on port 5005), configured
with the `berliozDev { ... }` extension, and runs the WAR's classpath minus `providedRuntime`.

The plugin replaces `@APP_VERSION@` (the project version) and `@APP_DATE@` in
`WEB-INF/config/config.xml`, both in the WAR and locally: `appRun` serves `build/berlioz-dev/webapp`,
a copy of `src/main/webapp` kept in sync every 500 ms.

**See the [user guide](docs/user-guide.md)** for setup, configuration, HTTPS, `.env` files,
debugging, migrating from Gretty and troubleshooting.

## Modules

| Module | Purpose |
|---|---|
| `berlioz-dev-gradle-plugin` | The plugin and `berliozDev` extension (tested with TestKit) |
| `berlioz-dev-server` | The `DevServer` launcher; pins the Jetty version, keep it in line with the apps' Docker images |

The plugin resolves `org.pageseeder.berlioz:berlioz-dev-server` with its own version, so both modules
are always released together (version in `gradle.properties`).

## Using it

In the app's `settings.gradle.kts`, add the GitLab package registry to
`pluginManagement { repositories { ... } }`, then apply the plugin:

```kotlin
plugins {
  id("org.pageseeder.berlioz-dev") version "0.1.0"
}
```

The launcher is resolved from the app's own `repositories`, which must include the same registry.
See the [user guide](docs/user-guide.md) for the details.

## Developing

```shell
./gradlew build                  # compile + tests
./gradlew publishToMavenLocal    # for testing with mavenLocal()
```

To try changes in an app without publishing, from the app: `./gradlew appRun --include-build ../berlioz-dev`.

## Releasing

Push a tag named after the version (e.g. `0.1.0`): GitLab CI builds, tests and publishes both modules
to the GitLab package registry, overriding `version` with the tag. This project's CI job token must be
allowed to write to that registry. A manual `./gradlew publish` uses `gitlabPrivateToken` instead.
The registry URL is the `gitlabMavenUrl` Gradle property (`ORG_GRADLE_PROJECT_gitlabMavenUrl` in CI).
