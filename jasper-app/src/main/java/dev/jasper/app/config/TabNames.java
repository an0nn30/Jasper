package dev.jasper.app.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code terminal.tab_name} (tab names spec 3): the built-in pieces, the default pattern and how a Terminal tab's name
 * is made from a pattern.
 */
public final class TabNames {
    /** The default pattern: the folder's name, the running program and the title a program set. */
    public static final String DEFAULT = "{dir} {process} {title}";
    public static final TitleTemplate DEFAULT_PATTERN = TitleTemplate.parse(DEFAULT);
    /** The built-in pieces, in the order Settings lists them. */
    public static final List<String> BUILT_IN = List.of("dir", "full", "process", "title");
    public static final int MAX_LENGTH = 200;

    private TabNames() { }

    /** Whether {@code text} can be saved: 1 to 200 characters, not blank, and a well-formed template. */
    public static boolean valid(String text) {
        return text != null && !text.isBlank() && text.length() <= MAX_LENGTH && TitleTemplate.problem(text).isEmpty();
    }

    /**
     * A tab's name: {@code pattern} filled from {@code pieces} (a piece they lack is empty), trimmed; {@code fallback}
     * when that is empty, and "Terminal" when {@code fallback} is blank too.
     */
    public static String format(TitleTemplate pattern, Map<String, String> pieces, String fallback) {
        String name = pattern.fill(piece -> pieces.getOrDefault(piece, "")).strip();
        if (!name.isEmpty()) return name;
        String shell = clean(fallback);
        return shell.isEmpty() ? "Terminal" : shell;
    }

    /** A piece's value on one line: control characters, line breaks among them, become spaces; then trimmed. */
    public static String clean(String value) {
        if (value == null) return "";
        var result = new StringBuilder(value.length());
        value.codePoints().forEach(point -> result.appendCodePoint(Character.isISOControl(point) ? ' ' : point));
        return result.toString().strip();
    }

    /** The pieces {@code pattern} names that are neither built in nor in {@code known}, in order, each once. */
    public static List<String> unknown(TitleTemplate pattern, Set<String> known) {
        var names = new LinkedHashSet<String>();
        for (String name : pattern.names()) if (!BUILT_IN.contains(name) && !known.contains(name)) names.add(name);
        return List.copyOf(names);
    }
}
