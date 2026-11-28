# Berlioz dev server: user guide

The `org.pageseeder.berlioz-dev` Gradle plugin runs a Berlioz web application locally on embedded
Jetty 12 (EE8, `javax.servlet`). It replaces Gretty: run `./gradlew appRun`, open the printed URL,
and edit XSLT, configuration and static files while the server runs.

- [Requirements](#requirements)
- [Setting up an app](#setting-up-an-app)
- [Running the server](#running-the-server)
- [Configuration](#configuration)
- [Tokens and filtered files](#tokens-and-filtered-files)
- [HTTPS and host names](#https-and-host-names)
- [Environment variables and `.env` files](#environment-variables-and-env-files)
- [Debugging](#debugging)
- [Migrating from Gretty](#migrating-from-gretty)
- [Troubleshooting](#troubleshooting)

## Requirements

- **Gradle 8.5 or later.** The plugin fails with a clear message on older versions; upgrade the
  wrapper with `./gradlew wrapper --gradle-version=8.5` (or later).
- **Java 17 or later** for the Gradle JVM. The server itself runs on the app's Java toolchain
  (`java { toolchain { ... } }`), or on Gradle's JVM if none is set.
- Access to the PageSeeder GitLab package registry, where the plugin and its
  launcher are published.

## Setting up an app

### 1. Repositories

The plugin is resolved from `pluginManagement`, and the launcher (`berlioz-dev-server`) from the
project's own repositories, so **both** must include the GitLab registry. In `settings.gradle.kts`:

```kotlin
pluginManagement {
  repositories {
    maven {
      name = "mavenGitLab"
      url = uri(providers.gradleProperty("gitlabMavenUrl").get())
      credentials(HttpHeaderCredentials::class) {
        val ciJobToken = System.getenv("CI_JOB_TOKEN")
        name = if (ciJobToken != null) "Job-Token" else "Private-Token"
        value = ciJobToken ?: providers.gradleProperty("gitlabPrivateToken").orNull
      }
      authentication { create<HttpHeaderAuthentication>("header") }
    }
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositories {
    add(pluginManagement.repositories.getByName("mavenGitLab"))
    mavenCentral()
  }
}
```

Locally, put the URL of the registry and your GitLab personal access token (scope `read_api`) in
`~/.gradle/gradle.properties` (in CI, set the `ORG_GRADLE_PROJECT_gitlabMavenUrl` variable):

```properties
gitlabMavenUrl=https://gitlab.example.com/api/v4/projects/<id>/packages/maven
gitlabPrivateToken=glpat-...
```

### 2. Apply the plugin

In `build.gradle.kts`:

```kotlin
plugins {
  id("org.pageseeder.berlioz-dev") version "0.2.0"
}
```

or, with a version catalog (`gradle/libs.versions.toml`):

```toml
[versions]
berlioz-dev = "0.2.0"

[plugins]
berlioz-dev = { id = "org.pageseeder.berlioz-dev", version.ref = "berlioz-dev" }
```

```kotlin
plugins {
  alias(libs.plugins.berlioz.dev)
}
```

The plugin applies the `war` plugin, so you don't need to apply it yourself. Declare the servlet API
as `providedRuntime` (or `providedCompile`) as usual: Jetty provides it, and it is left out of the
webapp classpath.

If the app uses dependency verification, regenerate it after adding or upgrading the plugin:

```shell
./gradlew --write-verification-metadata sha256 help
```

### 3. Configure (optional)

With no configuration, the app is served from `src/main/webapp` at `http://localhost:8080/` and
`https://localhost:8443/`. A typical configuration:

```kotlin
berliozDev {
  hostname.set("myapp.localhost")
  httpPort.set(8999)
  mode.set("local")
  appdata.set(layout.projectDirectory.dir("local/appdata"))
  keystore.set(layout.projectDirectory.file("local/dev-keystore.p12"))
  env.set(layout.projectDirectory.file("local/.env"))
}
```

With Gradle 8.2+, you can also use the assignment syntax: `httpPort = 8999`.

## Running the server

| Task | What it does |
|---|---|
| `./gradlew appRun` | Builds the classes and starts the server |
| `./gradlew appRunDebug` | Same, with a debugger listening on `debugPort` (default 5005) |

Both are in the `berlioz` task group (`./gradlew tasks --group berlioz`). Once started, the server
prints its URLs:

```
Berlioz dev server started:
  http://myapp.localhost:8999/
  https://myapp.localhost:8443/  (self-signed certificate)
Jetty logs: /path/to/myapp/build/berlioz-dev/jetty.log (warnings and errors shown here too)
Press ENTER to stop.
```

**Press ENTER to stop** (Ctrl+C also works). The ports are checked after it stops, and a warning is
printed if one is still held.

### What reloads without a restart

- **XSLT, configuration and static files** under the webapp directory: changes apply immediately.
  When tokens are replaced (the default), the server serves a copy in `build/berlioz-dev/webapp`
  that is synchronised with the source every 500 ms, so allow half a second.
- **Java classes and dependencies**: restart the server (`appRun` rebuilds them first).
- **`.env` file and `berliozDev { ... }` settings**: restart the server.

### Running from an IDE

In IntelliJ IDEA, run the `appRun` task from the Gradle tool window (Tasks › berlioz). To debug,
run `appRunDebug` and attach a *Remote JVM Debug* configuration to `localhost:5005`, or simply
start `appRun` with the IDE's *Debug* action.

Stopping with ENTER needs a console that forwards input. If yours doesn't, use the IDE's *Stop*
button: the server stops on interrupt as well.

The *Stop* button cancels the Gradle build, so IntelliJ reports it as a failure:

```
> Task :appRun FAILED
...
> Build cancelled while executing task ':appRun'
```

This is expected: Gradle reports any cancelled task this way. The server still shuts down cleanly
(Jetty and the web application are stopped and the ports released), but its shutdown messages,
such as `[BERLIOZ_STOP]`, and the port check after it stops are not shown: Gradle stops displaying
the task's output once the build is cancelled. Jetty's own messages are still written to
`build/berlioz-dev/jetty.log`, and the next `appRun` checks the ports before starting.

## Configuration

All settings are in the `berliozDev { ... }` extension.

| Property | Default | Description |
|---|---|---|
| `hostname` | `localhost` | Host name of the printed URLs and the self-signed certificate (see [HTTPS](#https-and-host-names)) |
| `httpPort` | `8080` | HTTP port |
| `httpsEnabled` | `true` | Whether to also listen on HTTPS |
| `httpsPort` | `8443` | HTTPS port |
| `debugPort` | `5005` | Debugger (JDWP) port used by `appRunDebug` |
| `contextPath` | `/` | Context path of the web application |
| `mode` | none | Berlioz mode, passed as `-Dberlioz.mode` (e.g. `local`, `dev`) |
| `appdata` | none | Berlioz application data directory, passed as `-Dberlioz.appdata` |
| `webappDirectory` | `src/main/webapp` | Web application source directory |
| `tokens` | `APP_VERSION`, `APP_DATE` | Tokens replaced in the filtered files (see [Tokens](#tokens-and-filtered-files)) |
| `filteredFiles` | `WEB-INF/config/config.xml` | Ant-style patterns of the files in which tokens are replaced |
| `keystore` | `build/berlioz-dev/keystore.p12` | PKCS12 keystore for HTTPS, created if missing |
| `env` | none | `.env` file loaded into the server's environment |
| `requiredEnv` | none | Environment variables to warn about when missing |
| `jvmArgs` | none | Additional JVM arguments for the server |
| `webInfIncludeJarPattern` | Jetty's default | Regex of the jars scanned for `web-fragment.xml` and `META-INF/resources` |
| `jettyLog` | `build/berlioz-dev/jetty.log` | File the Jetty logs are written to, emptied at each start; warnings and errors also go to the console (see [Logs](#logs)) |
| `jettyVersion` | the launcher's (12.0.41) | Overrides the Jetty 12 version |

### Examples

Extra JVM arguments and system properties:

```kotlin
berliozDev {
  jvmArgs.addAll("-Xmx2g", "--enable-native-access=ALL-UNNAMED", "-Dmy.property=value")
}
```

Serving resources from library jars (`META-INF/resources`) or web fragments, only scanning the jars
that need it (faster startup):

```kotlin
berliozDev {
  webInfIncludeJarPattern.set(".*/(pso-berlioz-kickstart|pso-bzadmin)-[^/]*\\.jar$")
}
```

HTTP only:

```kotlin
berliozDev {
  httpsEnabled.set(false)
}
```

Matching the Jetty version of a Docker image:

```kotlin
berliozDev {
  jettyVersion.set("12.0.41")
}
```

## Logs

Jetty's own logs (startup, deployment, request errors it handles) are written to
`build/berlioz-dev/jetty.log`, emptied each time the server starts, so the console mostly shows the
output of the web application (its own Logback or other SLF4J binding is not affected). Jetty's
warnings and errors, such as an uncaught exception from a request or a deployment problem, are
still printed on the console too, with their stack traces. The path is
printed with the URLs at startup; follow it with `tail -f build/berlioz-dev/jetty.log`.

Another file, or back to the console:

```kotlin
berliozDev {
  jettyLog.set(layout.projectDirectory.file("logs/jetty.log"))
  // jettyLog.convention(null as RegularFile?)  // console (set(null) falls back to the default)
}
```

## Tokens and filtered files

`@NAME@` tokens in the filtered files are replaced, **both in the WAR** built by `./gradlew war` and
in the web application run by `appRun`. By default:

| Token | Value |
|---|---|
| `@APP_VERSION@` | The project version (`version` in `build.gradle.kts` or `gradle.properties`) |
| `@APP_DATE@` | Today's date, e.g. `2026-11-23` |

in `WEB-INF/config/config.xml`. For example:

```xml
<application version="@APP_VERSION@" date="@APP_DATE@"/>
```

Add tokens or files:

```kotlin
berliozDev {
  tokens.put("BUILD_ENV", "local")
  filteredFiles.add("WEB-INF/config/*.xml")
}
```

Replace the defaults entirely with `tokens.set(mapOf(...))` / `filteredFiles.set(listOf(...))`.

When there are tokens to replace, `appRun` serves the synchronised copy in `build/berlioz-dev/webapp`
rather than `src/main/webapp`. Always edit the source: changes made to the copy are overwritten.
To serve `src/main/webapp` directly, turn replacement off with `tokens.empty()` (this also turns it
off in the WAR).

## HTTPS and host names

HTTPS is enabled by default with a self-signed certificate, created on the first run in the
`keystore` file for the `hostname` and `localhost`. Your browser will warn about it the first time:
accept it once.

- **Keep the keystore outside `build/`** (e.g. `local/dev-keystore.p12`, ignored by Git), otherwise
  `./gradlew clean` deletes it and you have to accept a new certificate.
- If you change `hostname`, the certificate is created again for the new name.
- Browsers resolve any `*.localhost` name (e.g. `myapp.localhost`) to your machine, so no hosts file
  entry is needed. Other names need one in `/etc/hosts`.
- The server listens on all interfaces, so it can also be reached from another device on your network
  by IP address (the certificate will not match, but the server accepts the request).

Using distinct host names per app keeps their cookies separate when several apps run at once.

## Environment variables and `.env` files

Secrets and machine-specific settings (e.g. used by `${NAME}` placeholders in the Berlioz
configuration) can be kept out of the build script in a `.env` file:

```kotlin
berliozDev {
  env.set(layout.projectDirectory.file("local/.env"))
  requiredEnv.addAll("DEV_OAUTH_CLIENT", "DEV_OAUTH_SECRET")
}
```

```shell
# local/.env (do not commit it: commit a local/.env.example instead)
DEV_OAUTH_CLIENT=my-client-id
DEV_OAUTH_SECRET='s3cr3t#with-hash'
export API_URL="https://api.example.com"   # comment
```

- Supported syntax: `NAME=value`, optional `export`, `#` comments, single-quoted (literal) and
  double-quoted values (with `\n`, `\t`, `\"`, `\\` escapes). Multi-line values and `${VAR}`
  expansion are not supported.
- **Variables already set in your environment win** over the file, so you can override one for a
  single run: `DEV_OAUTH_SECRET=other ./gradlew appRun`.
- Blank values are ignored.
- The file is read each time the server starts; a missing file is not an error.
- Variables listed in `requiredEnv` that are not set produce a warning, not a failure.

The names of the loaded variables are printed at startup; their values never are.

## Debugging

```shell
./gradlew appRunDebug
```

starts the server with a debugger listening on `debugPort` (default 5005), without waiting for a
debugger to attach. Attach your IDE's remote debugger to `localhost:5005`. If several apps are
debugged at once, give each one its own `debugPort`.

## Migrating from Gretty

1. Remove the Gretty plugin and the `gretty { ... }` block.
2. Apply `org.pageseeder.berlioz-dev` (see [Setting up an app](#setting-up-an-app)).
3. Move the settings to `berliozDev { ... }`:

   | Gretty | berlioz-dev |
   |---|---|
   | `httpPort`, `httpsPort`, `httpsEnabled` | same names |
   | `contextPath` | `contextPath` |
   | `debugPort` | `debugPort` |
   | `jvmArgs` | `jvmArgs` |
   | `systemProperty "berlioz.mode", ...` | `mode` |
   | `systemProperty "berlioz.appdata", ...` | `appdata` |
   | custom `filter` / token replacement in config | `tokens` and `filteredFiles` |

4. Remove any custom task that replaced `@APP_VERSION@` / `@APP_DATE@`: the plugin does it in the WAR
   and locally.
5. Run `./gradlew appRun` (the task names are unchanged).

## Troubleshooting

**"Port(s) already in use"**: the task checks the ports before starting and names the process
holding each one, e.g. a server from a previous run still alive. Stop it (`kill <pid>`) or change
the port in `berliozDev { ... }`.

**"The org.pageseeder.berlioz-dev plugin requires Gradle 8.5 or later"**: upgrade the Gradle wrapper.

**Cannot resolve `org.pageseeder.berlioz:berlioz-dev-server`**: the project's own repositories
(not only `pluginManagement`) must include the GitLab registry, and `gitlabPrivateToken` must be set.

**Dependency verification failed after an upgrade**: run
`./gradlew --write-verification-metadata sha256 help` and commit `gradle/verification-metadata.xml`.

**My change in `src/main/webapp` doesn't appear**: wait half a second for the sync, and check that you
edited the source, not `build/berlioz-dev/webapp`. Java changes need a restart.

**The browser warns about the certificate on every run**: the keystore is in `build/` and is deleted by
`clean`; move it with `keystore.set(layout.projectDirectory.file("local/dev-keystore.p12"))`.

**ENTER doesn't stop the server**: the console doesn't forward input (some IDE run windows); use Ctrl+C
or the IDE's *Stop* button.
