package dev.jasper.editor.vim;

import dev.jasper.editor.vim.regex.VimRegex;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Vim script's string functions with patterns, for the ports of plugins that build patterns out of patterns
 * (matchit): {@code =~}, match(), matchend(), matchstr(), substitute() and split(). Patterns are magic, with
 * 'ignorecase' as given and 'smartcase' not used, as these functions use them.
 */
final class VimStrings {
    private final VimRegex.Settings settings;
    private final Map<String, VimRegex> compiled = new HashMap<>();

    /**
     * @param ignoreCase 'ignorecase' while the plugin runs
     * @param keyword 'iskeyword' as a Java character-class body
     */
    VimStrings(boolean ignoreCase, String keyword) { settings = new VimRegex.Settings(ignoreCase, false, keyword, null); }

    /** @return the pattern compiled; a bad one fails as Vim reports it */
    VimRegex regex(String pattern) {
        VimRegex re = compiled.get(pattern);
        if (re != null) return re;
        try { re = VimRegex.compile(pattern, settings); }
        catch (IllegalArgumentException bad) { throw new VimFailure(bad.getMessage()); }
        compiled.put(pattern, re);
        return re;
    }

    /** @return {@code string =~ pattern} */
    boolean matches(String string, String pattern) { return regex(pattern).pattern().matcher(string).find(); }

    /** @return match(): where the first match starts, after {@code \zs}; -1 for none */
    int match(String string, String pattern) {
        VimRegex re = regex(pattern);
        Matcher m = re.pattern().matcher(string);
        return m.find() ? re.start(m) : -1;
    }

    /** @return matchend(): where the first match ends; -1 for none */
    int matchend(String string, String pattern) {
        VimRegex re = regex(pattern);
        Matcher m = re.pattern().matcher(string);
        return m.find() ? re.end(m) : -1;
    }

    /** @return matchstr(): the first match, empty for none */
    String matchstr(String string, String pattern) {
        VimRegex re = regex(pattern);
        Matcher m = re.pattern().matcher(string);
        return m.find() ? string.substring(re.start(m), re.end(m)) : "";
    }

    /**
     * substitute(): the first match, or every match with {@code g}, replaced. In the replacement {@code &} and
     * {@code \0} are the match, {@code \1} to {@code \9} its groups, and a backslash before another character makes it
     * plain ({@code \\}, {@code \&}).
     */
    String substitute(String string, String pattern, String sub, String flags) {
        VimRegex re = regex(pattern);
        Matcher m = re.pattern().matcher(string);
        StringBuilder out = new StringBuilder();
        boolean all = flags.contains("g");
        int done = 0, search = 0;
        while (search <= string.length() && m.find(search)) {
            int start = re.start(m), end = re.end(m);
            out.append(string, done, start).append(replacement(re, m, string, sub));
            done = end;
            if (!all) break;
            if (end == start) {
                // After an empty match the next character is kept and the search goes on after it.
                if (end >= string.length()) break;
                int next = end + Character.charCount(string.codePointAt(end));
                out.append(string, end, next);
                done = next;
            }
            search = done;
        }
        return out.append(string.substring(Math.min(done, string.length()))).toString();
    }

    private static String replacement(VimRegex re, Matcher m, String string, String sub) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < sub.length(); i++) {
            char c = sub.charAt(i);
            if (c == '&') out.append(string, re.start(m), re.end(m));
            else if (c == '\\' && i + 1 < sub.length()) {
                char n = sub.charAt(++i);
                if (n >= '0' && n <= '9') {
                    int s = re.groupStart(m, n - '0'), e = re.groupEnd(m, n - '0');
                    if (s >= 0) out.append(string, s, e);
                } else out.append(n);
            } else out.append(c);
        }
        return out.toString();
    }

    /** @return split() (f_split): the parts between the matches, without empty ones at the ends */
    List<String> split(String string, String pattern) {
        VimRegex re = regex(pattern);
        Matcher m = re.pattern().matcher(string);
        List<String> out = new ArrayList<>();
        int str = 0, col = 0;
        while (true) {
            boolean found = str + col <= string.length() && m.find(str + col);
            int end = found ? re.start(m) : string.length();
            if (end > str || !out.isEmpty() && str < string.length() && found && end < re.end(m)) out.add(string.substring(str, end));
            if (!found) break;
            // To just after the match; after an empty one, one character on.
            int matchEnd = re.end(m);
            col = matchEnd > str ? 0 : matchEnd < string.length() ? Character.charCount(string.codePointAt(matchEnd)) : 1;
            str = matchEnd;
        }
        return out;
    }

    /** escape(): a backslash before each of {@code chars}. */
    static String escape(String string, String chars) {
        StringBuilder out = new StringBuilder();
        for (char c : string.toCharArray()) {
            if (chars.indexOf(c) >= 0) out.append('\\');
            out.append(c);
        }
        return out.toString();
    }

    /** strpart(): the part from {@code start}, {@code len} long; out of range positions are clamped. */
    static String strpart(String string, int start, int len) {
        int from = Math.max(0, start), to = Math.min(string.length(), Math.max(from, start + len));
        return from >= string.length() ? "" : string.substring(from, to);
    }

    /** strpart() to the end. */
    static String strpart(String string, int start) { return strpart(string, start, Integer.MAX_VALUE / 2); }
}
