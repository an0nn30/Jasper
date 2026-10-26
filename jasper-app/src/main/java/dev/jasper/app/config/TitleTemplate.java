package dev.jasper.app.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * A title template: free text with {@code {name}} pieces, as {@code terminal.tab_name} and a look's
 * {@code [chrome].window_title} use (tab names spec 3); <code>{{</code> and <code>}}</code> write a brace. The text
 * between two pieces shows only when both have a value: an empty piece goes with the text before it, or with the text
 * after it when it comes first. When every piece is empty, the leading and trailing text stay without the separators
 * next to the pieces. A template without pieces is its text.
 *
 * @param literals the text before the first piece, between pieces and after the last: one more than {@code names}
 * @param names the pieces' names, in order
 */
public record TitleTemplate(List<String> literals, List<String> names) {
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,31}");
    /** Spaces and the separators leading text may end with. */
    private static final Pattern TRAILING = Pattern.compile("[\\s—–·:|-]+$");
    /** Spaces and the separators trailing text may start with. */
    private static final Pattern LEADING = Pattern.compile("^[\\s—–·:|-]+");

    public TitleTemplate {
        literals = List.copyOf(literals);
        names = List.copyOf(names);
        if (literals.size() != names.size() + 1) throw new IllegalArgumentException("A template has one more text than pieces");
    }

    /** Whether {@code name} is a piece name: 1 to 32 lowercase letters, digits and {@code _}, starting with a letter. */
    public static boolean isName(String name) { return name != null && NAME.matcher(name).matches(); }

    /**
     * Reads a template.
     *
     * @throws IllegalArgumentException for an unmatched brace or a name that is not a piece name, with a message for the user
     */
    public static TitleTemplate parse(String text) {
        var literals = new ArrayList<String>();
        var names = new ArrayList<String>();
        var literal = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean doubled = i + 1 < text.length() && text.charAt(i + 1) == c;
            if ((c == '{' || c == '}') && doubled) { literal.append(c); i++; continue; }
            if (c == '}') throw new IllegalArgumentException("The } at character " + (i + 1) + " has no {");
            if (c != '{') { literal.append(c); continue; }
            int end = text.indexOf('}', i + 1);
            if (end < 0) throw new IllegalArgumentException("The { at character " + (i + 1) + " has no }");
            String name = text.substring(i + 1, end);
            if (!isName(name))
                throw new IllegalArgumentException("{" + name + "} is not a piece name: use up to 32 lowercase letters, digits and _, starting with a letter");
            literals.add(literal.toString());
            literal.setLength(0);
            names.add(name);
            i = end;
        }
        literals.add(literal.toString());
        return new TitleTemplate(literals, names);
    }

    /** Empty when {@code text} is a template; otherwise what is wrong with it. */
    public static Optional<String> problem(String text) {
        try {
            parse(text);
            return Optional.empty();
        } catch (IllegalArgumentException invalid) {
            return Optional.of(invalid.getMessage());
        }
    }

    /** The template with each piece's value from {@code values}; a null or blank value is empty. */
    public String fill(Function<String, String> values) {
        if (names.isEmpty()) return literals.getFirst();
        var out = new StringBuilder(literals.getFirst());
        boolean any = false;
        for (int i = 0; i < names.size(); i++) {
            String value = values.apply(names.get(i));
            if (value == null || value.isBlank()) continue;
            if (any) out.append(literals.get(i));
            out.append(value);
            any = true;
        }
        if (!any) {
            String head = TRAILING.matcher(literals.getFirst()).replaceAll("");
            String tail = head.isBlank() ? LEADING.matcher(literals.getLast()).replaceAll("") : literals.getLast();
            return (head + tail).strip();
        }
        return out.append(literals.getLast()).toString();
    }
}
