package dev.jasper.editor.markdown;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

/** Allocates fragment names from visible heading text, in document order. */
final class HeadingIds {
    private final Set<String> allocated = new HashSet<>();
    private final Map<String, Integer> nextSuffix = new HashMap<>();

    String next(String visibleText) {
        var name = new StringBuilder();
        boolean whitespace = false;
        for (int cp : visibleText.toLowerCase(Locale.ROOT).codePoints().toArray()) {
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                if (!whitespace) name.append('-');
                whitespace = true;
            } else {
                whitespace = false;
                int type = Character.getType(cp);
                if (Character.isLetterOrDigit(cp) || type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK || type == Character.ENCLOSING_MARK || cp == '_' || cp == '-')
                    name.appendCodePoint(cp);
            }
        }
        String base = name.isEmpty() ? "section" : name.toString();
        if (allocated.add(base)) return base;
        int suffix = nextSuffix.getOrDefault(base, 1);
        String id;
        do { id = base + "-" + suffix++; } while (!allocated.add(id));
        nextSuffix.put(base, suffix);
        return id;
    }
}
