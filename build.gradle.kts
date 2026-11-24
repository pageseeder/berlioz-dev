// Publishes both modules to the GitLab package registry set by the `gitlabMavenUrl` Gradle property. The plugin
// resolves `berlioz-dev-server` with its own version, so both must always be published together.
// The URL is private: keep it out of this repository, in `~/.gradle/gradle.properties` or, in CI, in the
// `ORG_GRADLE_PROJECT_gitlabMavenUrl` variable. Without it, there is no repository to publish to.
val gitlabMavenUrl = providers.gradleProperty("gitlabMavenUrl").orNull

subprojects {
  apply(plugin = "maven-publish")

  if (!gitlabMavenUrl.isNullOrBlank()) {
    configure<PublishingExtension> {
      repositories {
        maven {
          name = "gitlab"
          url = uri(gitlabMavenUrl)
          val ciJobToken = System.getenv("CI_JOB_TOKEN")
          credentials(HttpHeaderCredentials::class) {
            if (ciJobToken != null) {
              // GitLab CI: the job token of this project must be allowed to write to that registry
              name = "Job-Token"
              value = ciJobToken
            } else {
              // Manual publish: requires a personal access token with the `api` scope
              name = "Private-Token"
              value = providers.gradleProperty("gitlabPrivateToken").orNull
            }
          }
          authentication {
            create<HttpHeaderAuthentication>("header")
          }
        }
      }
    }
  }
}
