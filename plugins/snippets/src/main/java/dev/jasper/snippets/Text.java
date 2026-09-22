package dev.jasper.snippets;

import java.util.Locale;
import java.util.regex.Pattern;

/** Query normalization shared by the scope and its tests: lower case, trimmed, one space between words. */
final class Text {
    private static final Pattern SPACE = Pattern.compile("\\s+");
    private Text() { }
    static String normalize(String text) { return SPACE.matcher(text.strip().toLowerCase(Locale.ROOT)).replaceAll(" "); }
    /** A command on one line for a row title, with a return glyph where lines broke. */
    static String oneLine(String command) { return command.replace("\r", "").replace("\n", " ↵ "); }
}
