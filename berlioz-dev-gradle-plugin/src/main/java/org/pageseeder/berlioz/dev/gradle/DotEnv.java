package org.pageseeder.berlioz.dev.gradle;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal parser for {@code .env} files, compatible with the common dotenv syntax:
 *
 * <ul>
 *   <li>{@code NAME=value} lines, optionally prefixed by {@code export}</li>
 *   <li>blank lines and lines starting with {@code #} are ignored</li>
 *   <li>values may be single-quoted (literal) or double-quoted ({@code \n}, {@code \t},
 *       {@code \"} and {@code \\} escapes are supported)</li>
 *   <li>in unquoted values, {@code #} preceded by whitespace starts a comment; surrounding
 *       whitespace is removed</li>
 * </ul>
 *
 * <p>Variable expansion and multi-line values are not supported.
 */
final class DotEnv {

  private static final Pattern LINE = Pattern.compile("^\\s*(?:export\\s+)?([A-Za-z_][A-Za-z0-9_.]*)\\s*=\\s*(.*)$");

  private DotEnv() {
  }

  /**
   * @param content the content of the file
   * @param source  the file name for error messages
   * @return the variables in the order they are defined (later definitions win)
   * @throws IllegalArgumentException if a line cannot be parsed
   */
  static Map<String, String> parse(String content, String source) {
    Map<String, String> values = new LinkedHashMap<>();
    String[] lines = content.split("\\r?\\n", -1);
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
      Matcher m = LINE.matcher(line);
      if (!m.matches()) {
        throw new IllegalArgumentException(source + ":" + (i + 1) + ": expected NAME=value");
      }
      values.put(m.group(1), value(m.group(2).trim(), source, i + 1));
    }
    return values;
  }

  private static String value(String raw, String source, int line) {
    if (raw.isEmpty()) return "";
    char quote = raw.charAt(0);
    if (quote == '\'' || quote == '"') {
      int end = closingQuote(raw, quote);
      if (end < 0) throw new IllegalArgumentException(source + ":" + line + ": unterminated " + quote + " quote");
      String rest = raw.substring(end + 1).trim();
      if (!rest.isEmpty() && !rest.startsWith("#")) {
        throw new IllegalArgumentException(source + ":" + line + ": unexpected characters after closing quote");
      }
      String inner = raw.substring(1, end);
      return quote == '"' ? unescape(inner) : inner;
    }
    Matcher comment = Pattern.compile("\\s#").matcher(raw);
    return (comment.find() ? raw.substring(0, comment.start()) : raw).trim();
  }

  private static int closingQuote(String raw, char quote) {
    for (int i = 1; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (quote == '"' && c == '\\') {
        i++;
      } else if (c == quote) {
        return i;
      }
    }
    return -1;
  }

  private static String unescape(String s) {
    StringBuilder out = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\\' && i + 1 < s.length()) {
        char next = s.charAt(++i);
        switch (next) {
          case 'n' -> out.append('\n');
          case 't' -> out.append('\t');
          case 'r' -> out.append('\r');
          default -> out.append(next);
        }
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }

}
