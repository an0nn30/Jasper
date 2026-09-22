package dev.jasper.snippets;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One saved command. Placeholders are {@code {{identifier}}}; a backslash before the opening double brace makes it literal. */
public record Snippet(String name, String command, List<String> keywords) {
    public static final int MAX_NAME = 128;
    public static final int MAX_COMMAND = 16 * 1024;
    private static final Pattern PLACEHOLDER = Pattern.compile("(?<!\\\\)\\{\\{([A-Za-z_][A-Za-z0-9_]*)\\}\\}");

    public Snippet {
        name = validName(name);
        Objects.requireNonNull(command, "command");
        if (command.isBlank()) throw new IllegalArgumentException("Snippet needs a command");
        if (command.length() > MAX_COMMAND) throw new IllegalArgumentException("Snippet command is longer than 16 KiB");
        keywords = List.copyOf(keywords);
        for (String keyword : keywords) if (keyword.isBlank()) throw new IllegalArgumentException("Keywords must not be blank");
    }

    static String validName(String name) {
        if (name == null) throw new IllegalArgumentException("Snippet needs a name");
        String trimmed = name.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME || trimmed.chars().anyMatch(c -> c < 0x20 || c == 0x7f))
            throw new IllegalArgumentException("Snippet names are 1–128 printable characters");
        return trimmed;
    }

    /** Case-insensitive identity used for uniqueness. */
    public String key() { return name.toLowerCase(Locale.ROOT); }

    /** Distinct placeholder identifiers in order of first appearance. */
    public List<String> placeholders() {
        var names = new LinkedHashSet<String>();
        Matcher matcher = PLACEHOLDER.matcher(command);
        while (matcher.find()) names.add(matcher.group(1));
        return List.copyOf(names);
    }

    /** The command with every placeholder replaced (missing values become empty) and escaped opening braces restored. */
    public String fill(Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(command);
        var out = new StringBuilder();
        while (matcher.find())
            matcher.appendReplacement(out, Matcher.quoteReplacement(values.getOrDefault(matcher.group(1), "")));
        matcher.appendTail(out);
        return out.toString().replace("\\{{", "{{");
    }
}
