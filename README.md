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

The modules are published to a private GitLab package registry, whose URL is the `gitlabMavenUrl` Gradle
property. Without it, `publish` has no repository to publish to.

- **Manually**: set `gitlabMavenUrl` and `gitlabPrivateToken` in `~/.gradle/gradle.properties`, then run
  `./gradlew clean build publish -Pversion=0.1.0`.
- **From GitLab CI**: in a GitLab copy of this repository, pushing a tag named after the version (e.g.
  `0.1.0`) builds, tests and publishes both modules, overriding `version` with the tag. Set the
  `ORG_GRADLE_PROJECT_gitlabMavenUrl` CI variable, and allow the project's job token to write to the
  registry. Pushing a tag to GitHub does not publish anything.

## Licence

[Apache License 2.0](LICENSE)
