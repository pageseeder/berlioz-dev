package org.pageseeder.berlioz.dev.gradle;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.tasks.JavaExec;

/**
 * The standard input of the server: the console up to the first line, which stops the server,
 * then end of input.
 *
 * <p>Gradle forwards the standard input to the server process as long as it can read it. Anything
 * read once the server has exited (e.g. a key pressed while it stops, or a sequence sent by the
 * terminal itself) makes it fail with "Could not write standard input ... Broken pipe". Ending the
 * input after the first line means nothing is written after it; the launcher ignores the end of
 * input.
 */
final class StopLineInput extends FilterInputStream {

  private boolean ended;

  StopLineInput(InputStream in) {
    super(in);
  }

  @Override
  public synchronized int read() throws IOException {
    if (ended) return -1;
    int b = super.read();
    if (b == '\n' || b < 0) ended = true;
    return b;
  }

  @Override
  public synchronized int read(byte[] buffer, int offset, int length) throws IOException {
    if (ended) return -1;
    if (length == 0) return 0;
    int count = super.read(buffer, offset, length);
    if (count < 0) {
      ended = true;
      return -1;
    }
    for (int i = offset; i < offset + count; i++) {
      if (buffer[i] == '\n') {
        // Anything after the line is dropped
        ended = true;
        return i - offset + 1;
      }
    }
    return count;
  }

  @Override
  public long skip(long n) {
    return 0;
  }

  @Override
  public int available() throws IOException {
    return ended ? 0 : super.available();
  }

  /** Leaves the console open: it is Gradle's. */
  @Override
  public void close() {
    ended = true;
  }

  @Override
  public boolean markSupported() {
    return false;
  }

  /**
   * Sets the standard input of the task when it runs (a custom stream cannot be stored in the
   * configuration cache, unlike {@code System.in}).
   */
  static final class Apply implements Action<Task> {

    @Override
    public void execute(Task task) {
      ((JavaExec) task).setStandardInput(new StopLineInput(System.in));
    }

  }

}
