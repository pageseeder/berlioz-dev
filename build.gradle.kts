// Publishes both modules to the shared PageSeeder GitLab package registry, which already
// holds the private PageSeeder artifacts used by the Berlioz apps. The plugin resolves `berlioz-dev-server`
// with its own version, so both must always be published together.
subprojects {
  apply(plugin = "maven-publish")

  configure<PublishingExtension> {
    repositories {
      maven {
        name = "gitlab"
        url = uri("https://gitlab.example.com/api/v4/projects/0/packages/maven")
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
