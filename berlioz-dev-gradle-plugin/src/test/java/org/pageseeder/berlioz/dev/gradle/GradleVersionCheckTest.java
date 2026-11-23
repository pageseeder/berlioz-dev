package org.pageseeder.berlioz.dev.gradle;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.api.GradleException;
import org.gradle.util.GradleVersion;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Gradle versions older than the minimum cannot run on the JVM used for the functional tests, so the check
 * is tested directly.
 */
class GradleVersionCheckTest {

  @ParameterizedTest
  @ValueSource(strings = {"8.5", "8.5-rc-1", "8.14.7", "9.9.2", "10.0"})
  void acceptsSupportedVersions(String version) {
    assertDoesNotThrow(() -> BerliozDevPlugin.checkGradleVersion(GradleVersion.version(version)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"8.4", "8.0.2", "7.6.4"})
  void rejectsOlderVersions(String version) {
    GradleException ex = assertThrows(GradleException.class,
        () -> BerliozDevPlugin.checkGradleVersion(GradleVersion.version(version)));
    assertTrue(ex.getMessage().contains("requires Gradle 8.5 or later, this build uses " + version), ex.getMessage());
  }

}
