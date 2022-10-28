package org.opentripplanner.framework.text;

import javax.annotation.Nullable;
import org.opentripplanner.util.lang.StringUtils;

/**
 * This utility can be used to format Markdown text. This is useful when generating documentation.
 */
public class MarkdownFormatter {

  public static int HEADER_1 = 1;
  public static int HEADER_2 = 2;
  public static int HEADER_3 = 3;
  public static int HEADER_4 = 4;

  public static final char NBSP = '\u00A0';
  public static final String NEW_LINE = "\n";

  /** Return the given input as emphasise text. */
  public static String em(String text) {
    return "*" + text + "*";
  }

  /** Return the given input as bold text. */
  public static String bold(Object text) {
    return text == null ? "" : "**" + text + "**";
  }

  /** Return the given input formatted as an inline code fragment. */
  public static String code(@Nullable Object text) {
    return text == null ? "" : "`" + text + "`";
  }

  /**
   * Link to a header in the same document. The "other" element need to be tagged with an
   * "id" attribute equals to the given anchor.
   */
  public static String linkToAnchor(String text, String anchor) {
    return "[%s](#%s)".formatted(text, normalizeAnchor(anchor));
  }

  /**
   * Link to a header in the same document. The "other" element need to be tagged with an
   * "id" attribute equals to the given anchor.
   */
  public static String linkToDoc(String text, String url) {
    return "[%s](%s)".formatted(text, url);
  }

  public static String header(int level, String header, @Nullable String anchor) {
    if (anchor != null && !anchor.isBlank()) {
      return "<h%d id=\"%s\">%s</h%d>".formatted(level, normalizeAnchor(anchor), header, level);
    } else {
      return StringUtils.fill('#', level) + " " + header;
    }
  }

  /** Return a check mark if true, or unchecked id false. */
  public static String checkMark(boolean enable) {
    return enable ? "✓️" : "";
  }

  /**
   * Pipes '|' in the text in a Markdown table cell will cause the table to be rendered wrong, so
   * we must escape pipes in the cell text. The proper way to do this is to use '\|', but that does
   * not work with Intellij and become hard to work with, so for now we substitute the '|' with a
   * broken-pipe '¦' instead.
   */
  public static String escapeInTable(String text) {
    if (text == null || !text.contains("|")) {
      return text;
    }
    // Replace '¦' with '\|' when Intellij markdown renderer supports it
    return text.replace("|", "¦");
  }

  /** Return whitespace witch can be used to indent inside a table cell. */
  public static String indentInTable(int level) {
    return level <= 0 ? "" : StringUtils.fill(NBSP, 3 * level);
  }

  public static String normalizeAnchor(String anchor) {
    return anchor.replaceAll("[-!\"#$%&/.=?+\\[\\]]", "_");
  }

  public static String lineBreak() {
    return " \\";
  }
}
