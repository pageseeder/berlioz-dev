package org.pageseeder.berlioz.dev.gradle;

import java.util.List;
import java.util.Map;

import org.apache.tools.ant.filters.ReplaceTokens;
import org.gradle.api.Action;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.util.PatternSet;

/**
 * Replaces {@code @NAME@} tokens in the files of a copy matching Ant-style patterns.
 *
 * <p>A class rather than a lambda, so that the task can be stored in the configuration cache;
 * the providers are read when the files are copied.
 */
final class ReplaceTokensAction implements Action<FileCopyDetails> {

  private final Provider<Map<String, String>> tokens;

  private final Provider<List<String>> patterns;

  ReplaceTokensAction(Provider<Map<String, String>> tokens, Provider<List<String>> patterns) {
    this.tokens = tokens;
    this.patterns = patterns;
  }

  @Override
  public void execute(FileCopyDetails details) {
    Map<String, String> values = this.tokens.get();
    List<String> includes = this.patterns.get();
    if (values.isEmpty() || includes.isEmpty()) return;
    if (new PatternSet().include(includes).getAsSpec().isSatisfiedBy(details)) {
      details.filter(Map.of("tokens", values), ReplaceTokens.class);
    }
  }

}
