package org.pageseeder.berlioz.dev.gradle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Provider;

/**
 * Checks the ports of the dev server, either before it starts (failing with a warning that names
 * the processes holding them) or after it stops (warning if they are still held).
 *
 * <p>A class rather than a lambda, so that the task can be stored in the configuration cache.
 */
final class CheckPortsAction implements Action<Task> {

  enum When { BEFORE_START, AFTER_STOP }

  private final Provider<Integer> httpPort;

  private final Provider<Integer> httpsPort;

  private final Provider<Integer> debugPort;

  private final When when;

  CheckPortsAction(Provider<Integer> httpPort, Provider<Integer> httpsPort, Provider<Integer> debugPort, When when) {
    this.httpPort = httpPort;
    this.httpsPort = httpsPort;
    this.debugPort = debugPort;
    this.when = when;
  }

  @Override
  public void execute(Task task) {
    Map<String, Integer> ports = new LinkedHashMap<>();
    if (this.httpPort.isPresent()) ports.put("HTTP, berliozDev { httpPort }", this.httpPort.get());
    if (this.httpsPort.isPresent()) ports.put("HTTPS, berliozDev { httpsPort }", this.httpsPort.get());
    if (this.debugPort.isPresent()) ports.put("debugger, berliozDev { debugPort }", this.debugPort.get());

    Logger logger = task.getLogger();
    List<String> busy = new ArrayList<>();
    StringBuilder message = new StringBuilder();
    for (Map.Entry<String, Integer> entry : ports.entrySet()) {
      int port = entry.getValue();
      if (Ports.isFree(port)) continue;
      busy.add(Integer.toString(port));
      List<String> owners = Ports.owners(port);
      message.append("\n  ").append(port).append(" (").append(entry.getKey()).append(')');
      message.append(owners.isEmpty() ? "" : " is held by " + String.join(", ", owners));
    }

    if (this.when == When.BEFORE_START) {
      if (busy.isEmpty()) return;
      logger.warn("\n[WARNING] Unable to start the Berlioz dev server: port(s) already in use:{}\n"
          + "Stop the process(es) holding them, or use other ports.\n", message);
      throw new GradleException("Port(s) already in use: " + String.join(", ", busy));
    }

    if (busy.isEmpty()) {
      logger.lifecycle("Berlioz dev server stopped; port(s) {} released.", ports.values());
    } else {
      logger.warn("\n[WARNING] The Berlioz dev server stopped but port(s) are still in use:{}\n"
          + "Another process may have taken them, or the server may still be running: stop it before the next run "
          + "(e.g. kill <pid>).\n", message);
    }
  }

}
