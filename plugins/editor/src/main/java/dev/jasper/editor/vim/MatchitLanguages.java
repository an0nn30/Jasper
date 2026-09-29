package dev.jasper.editor.vim;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * What Vim's filetype plugins tell matchit about each language (spec 8): {@code b:match_words},
 * {@code b:match_ignorecase}, {@code b:match_skip} and 'matchpairs', by the tab's RSyntaxTextArea style. The table,
 * {@code matchit-languages.tsv}, was made by Vim 9.1 with its filetype plugins and matchit loaded.
 */
final class MatchitLanguages {
    private MatchitLanguages() { }

    /**
     * A language.
     *
     * @param style the RSyntaxTextArea style ({@code text/html})
     * @param filetype Vim's name for it ({@code html})
     * @param ignoreCase b:match_ignorecase, or null when the plugin leaves 'ignorecase' to decide
     * @param skip b:match_skip, or null for matchit's own ({@code s:comment\|string})
     * @param matchpairs 'matchpairs' as the filetype plugin leaves it
     * @param words b:match_words, empty for none
     */
    record Language(String style, String filetype, Boolean ignoreCase, String skip, String matchpairs, String words) {
        /** @return the pairs its filetype plugin adds to Vim's default 'matchpairs' */
        java.util.List<String> addedPairs() {
            java.util.List<String> added = new java.util.ArrayList<>(java.util.List.of(matchpairs.split(",")));
            added.removeAll(java.util.List.of("(:)", "{:}", "[:]", ""));
            return added;
        }
    }

    /** A language no filetype plugin changes: plain text, and languages without an entry. */
    static final Language NONE = new Language("text/plain", "", null, null, "(:),{:},[:]", "");

    private static final Map<String, Language> BY_STYLE = load();

    private static Map<String, Language> load() {
        Map<String, Language> out = new LinkedHashMap<>();
        try (InputStream in = MatchitLanguages.class.getResourceAsStream("matchit-languages.tsv")) {
            if (in == null) throw new IllegalStateException("matchit-languages.tsv is missing");
            var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] f = line.split("\t", -1);
                Boolean ic = f[2].equals("-") ? null : !f[2].equals("0");
                out.put(f[0], new Language(f[0], f[1], ic, f[3].equals("-") ? null : f[3], f[4], f[5]));
            }
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
        return out;
    }

    /** @return the language of the editor's tab */
    static Language of(VimEditor editor) {
        return editor.area() instanceof org.fife.ui.rsyntaxtextarea.RSyntaxTextArea area ? of(area.getSyntaxEditingStyle()) : NONE;
    }

    /** @return the language of an RSyntaxTextArea style */
    static Language of(String style) { return BY_STYLE.getOrDefault(style, NONE); }

    /** @return the language Vim calls {@code filetype} */
    static Optional<Language> byFiletype(String filetype) {
        return BY_STYLE.values().stream().filter(language -> language.filetype().equals(filetype)).findFirst();
    }
}
