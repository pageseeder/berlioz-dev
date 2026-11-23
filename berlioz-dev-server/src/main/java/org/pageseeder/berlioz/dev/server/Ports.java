package org.pageseeder.berlioz.dev.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Checks whether local TCP ports can be bound, and which process holds them.
 */
final class Ports {

  private static final int CONNECT_TIMEOUT_MS = 200;

  private Ports() {
  }

  /**
   * Whether the port can be bound on all interfaces, as Jetty and the JDWP agent do.
   *
   * <p>A connection to the loopback addresses is also attempted, because on macOS (BSD) a
   * wildcard bind can succeed while another process listens on 127.0.0.1 only.
   */
  static boolean isFree(int port) {
    try (ServerSocket socket = new ServerSocket()) {
      socket.setReuseAddress(true);
      socket.bind(new InetSocketAddress(port));
    } catch (IOException ex) {
      return false;
    }
    for (String host : List.of("127.0.0.1", "::1")) {
      try (Socket socket = new Socket()) {
        socket.connect(new InetSocketAddress(InetAddress.getByName(host), port), CONNECT_TIMEOUT_MS);
        return false;
      } catch (IOException ex) {
        // Nothing listening there
      }
    }
    return true;
  }

  /**
   * The processes listening on the port, as "command (pid N)", using {@code lsof} when available;
   * an empty list when unknown.
   */
  static List<String> owners(int port) {
    List<String> owners = new ArrayList<>();
    if (System.getProperty("os.name", "").toLowerCase().contains("windows")) return owners;
    try {
      Process process = new ProcessBuilder("lsof", "-nP", "-iTCP:" + port, "-sTCP:LISTEN", "-Fpc")
          .redirectErrorStream(true).start();
      String pid = null;
      try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = in.readLine()) != null) {
          if (line.startsWith("p")) {
            pid = line.substring(1);
          } else if (line.startsWith("c") && pid != null) {
            String owner = line.substring(1) + " (pid " + pid + ")";
            if (!owners.contains(owner)) owners.add(owner);
          }
        }
      }
      if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly();
    } catch (IOException ex) {
      // lsof not available
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
    return owners;
  }

}
