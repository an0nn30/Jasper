package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The matchit extension (spec 8), ported from Vim's own matchit.vim (the {@code pack/dist/opt/matchit} of Vim 9.1):
 * {@code %} and {@code g%} go between the words of a group ({@code if}, {@code else}, {@code endif}; HTML tags)
 * as the tab's language defines them in {@code b:match_words} ({@link MatchitLanguages}), besides 'matchpairs',
 * C comments and {@code #if}; {@code [%} and {@code ]%} go to the enclosing group's start and end, and Visual
 * {@code a%} takes the group. Like the plugin, the port builds Vim patterns out of the words, resolves their
 * back references, and moves with searchpair(), passing over matches in comments and strings as the language says.
 */
final class Matchit {
    private static final String OPTION = "matchit";
    /** s:notslash: not after an odd number of backslashes. */
    private static final String NOTSLASH = "\\\\\\@1<!\\%(\\\\\\\\\\)*";
    private static final String DEFAULT_WRAPPER = "\\/\\*:\\*\\/,#\\s*if\\%(n\\=def\\)\\=:#\\s*else\\>:#\\s*elif\\%(n\\=def\\)\\=\\>:#\\s*endif\\>";
    private static final String DEFAULT_MULTI = "\\/\\*:\\*\\/,#\\s*if\\%(n\\=def\\)\\=:#\\s*else\\>:#\\s*elif\\>:#\\s*endif\\>";

    /** The plugin's script variables: the parsed words, which each use builds again. */
    private boolean doBR;
    private String pat = "", all = "", patBR = "";

    static void register(VimCommands commands) {
        for (String mode : new String[] { "Normal", "Visual", "Operation" }) {
            commands.motion("<Plug>(Matchit" + mode + "Forward)", "to the next word of the group at the cursor (`%`, with `set matchit` or `packadd matchit`)",
                c -> wrapper(c, true));
            commands.motion("<Plug>(Matchit" + mode + "Backward)", "to the previous word of the group at the cursor (`g%`)", c -> wrapper(c, false));
            commands.motion("<Plug>(Matchit" + mode + "MultiBackward)", "back to the start of the group around the cursor (`[%`)", c -> multi(c, true));
            commands.motion("<Plug>(Matchit" + mode + "MultiForward)", "on to the end of the group around the cursor (`]%`)", c -> multi(c, false));
        }
        commands.visual("<Plug>(MatchitVisualTextObject)", "the group around the Visual area, from its start to its end (`a%`)", false, false, c -> {
            Extensions.require(c, OPTION);
            // xmap <Plug>(MatchitVisualTextObject) <Plug>(MatchitVisualMultiBackward)o<Plug>(MatchitVisualMultiForward)
            Script.normal(c.editor, "<Plug>(MatchitVisualMultiBackward)o<Plug>(MatchitVisualMultiForward)");
        });
    }

    /** {@code :packadd matchit} switches matchit on; another package is not there. */
    static void packadd(ExCommand cmd) {
        String name = cmd.arg.strip();
        if (name.isEmpty()) throw new VimFailure("E471: Argument required");
        if (!name.equals("matchit")) throw new VimFailure("E919: Directory not found in 'packpath': \"pack/*/opt/" + name + "\"");
        cmd.editor.options().set(OPTION, true);
    }

    /** What the mode means for a motion: an operator's text is the area Visual mode would take. */
    private static Target target(Context c, Pos pos, boolean jump) {
        // In Vim the operator's : motion becomes Visual mode (norm! v) unless the type was forced with v or V;
        // v then makes the exclusive : motion inclusive, which an inclusive target turned exclusive by v here is.
        Target t = c.operator != null && c.forced == 'v' ? Target.exclusive(pos) : Target.inclusive(pos);
        t = t.withoutAdjust();
        if (c.operator != null && c.forced == 0) t = t.asArea();
        return jump ? t.asJump() : t;
    }

    /** matchit#Match_wrapper: {@code %} and {@code g%}. */
    private static Target wrapper(Context c, boolean forward) {
        Extensions.require(c, OPTION);
        VimEditor editor = c.editor;
        Pos startpos = editor.cursor();
        Buffer b = editor.buffer();
        if (c.hasCount) {
            // normal! {count}%: a line by percentage.
            int line = Math.max(0, Math.min(b.count() - 1, (c.count * b.count() + 99) / 100 - 1));
            return Target.exclusive(new Pos(line, Text.firstNonBlank(b.line(line)))).asJump();
        }
        MatchitLanguages.Language language = MatchitLanguages.of(editor);
        VimStrings s = strings(editor, language);
        Matchit script = editor.vim().extensions().script(Matchit.class, Matchit::new);
        String matchWords = language.words();
        String mps = editor.options().string("matchpairs");
        String dflt = VimStrings.escape(mps, "[$^.*~\\/?]") + (mps.isEmpty() ? "" : ",") + DEFAULT_WRAPPER;
        matchWords = matchWords + (matchWords.isEmpty() ? "" : ",") + dflt;
        script.parse(s, matchWords, true);
        String matchline = b.line(startpos.line);
        String regexp = wholematch(s, matchline, script.all, startpos.col);
        int curcol = s.match(matchline, regexp);
        if (curcol == -1) return target(c, startpos, false);
        int endcol = s.matchend(matchline, regexp);
        int suf = matchline.length() - endcol;
        String prefix = curcol != 0 ? "^.*\\%" + (curcol + 1) + "c\\%(" : "^\\%(";
        String suffix = suf != 0 ? "\\)\\%" + (endcol + 1) + "c.*$" : "\\)$";
        String group = choose(s, script.pat, matchline, ",", ":", prefix, suffix, script.patBR);
        if (group == null) return target(c, startpos, false);
        int i = s.matchend(group, NOTSLASH + ",");
        String groupBR = VimStrings.strpart(group, i);
        group = VimStrings.strpart(group, 0, i - 1);
        if (script.doBR) group = insertRefs(s, groupBR, prefix, group, suffix, matchline);
        i = s.matchend(group, NOTSLASH + ":");
        int j = s.matchend(group, ".*" + NOTSLASH + ":");
        String ini = VimStrings.strpart(group, 0, i - 1);
        String mid = s.substitute(VimStrings.strpart(group, i, j - i - 1), NOTSLASH + "\\zs:", "\\\\|", "g");
        String fin = VimStrings.strpart(group, j);
        ini = plain(s, ini);
        mid = plain(s, mid);
        fin = plain(s, fin);
        boolean atFin = s.matches(matchline, prefix + fin + suffix), atIni = s.matches(matchline, prefix + ini + suffix);
        if (forward && atFin || !forward && atIni) mid = "";
        boolean backward = forward && atFin || !forward && !atIni;
        Predicate<Pos> skip = skip(editor, s, language.skip());
        Pos at = new Pos(startpos.line, curcol);
        // The skip expression is dropped when the cursor itself is where it would skip (in a comment).
        if (skip.test(at)) skip = p -> false;
        editor.setCursor(at);
        int found = Search.searchpair(editor, s, ini, mid, fin, backward, false, skip);
        Pos finalPos = found > 0 ? editor.cursor() : startpos;
        editor.setCursor(startpos);
        // normal! m' at the start, then the match; s:CleanUp takes an operator to the end of the word found.
        if (c.operator != null && finalPos.compareTo(startpos) > 0) {
            String line = b.line(finalPos.line);
            String end = wholematch(s, line, mid + "\\|" + fin, finalPos.col);
            int endCol = s.matchend(line, end);
            if (endCol > finalPos.col + 1) finalPos = new Pos(finalPos.line, endCol - 1);
        }
        return target(c, finalPos, true);
    }

    /** matchit#MultiMatch: {@code [%} ({@code bW}) and {@code ]%} ({@code W}), {@code v:count1} levels out. */
    private static Target multi(Context c, boolean backward) {
        Extensions.require(c, OPTION);
        VimEditor editor = c.editor;
        Pos startpos = editor.cursor();
        MatchitLanguages.Language language = MatchitLanguages.of(editor);
        VimStrings s = strings(editor, language);
        Matchit script = editor.vim().extensions().script(Matchit.class, Matchit::new);
        String matchWords = language.words();
        String mps = editor.options().string("matchpairs");
        String dflt = VimStrings.escape(mps, "[$^.*~\\/?]") + (mps.isEmpty() ? "" : ",") + DEFAULT_MULTI;
        matchWords = matchWords + (matchWords.isEmpty() ? "" : ",") + dflt;
        script.parse(s, matchWords, false);
        List<String> openlist = s.split(script.pat + ",", NOTSLASH + "\\zs:.\\{-}" + NOTSLASH + ",");
        List<String> closelist = new ArrayList<>(), middlelist = new ArrayList<>();
        for (String midclo : s.split("," + script.pat, NOTSLASH + "\\zs,.\\{-}" + NOTSLASH + ":")) {
            List<String> parts = s.split(midclo, NOTSLASH + ":");
            if (parts.isEmpty()) continue;
            closelist.add(parts.getLast());
            middlelist.addAll(parts.subList(0, parts.size() - 1));
        }
        String open = join(s, openlist), middle = join(s, middlelist), close = join(s, closelist);
        String openpat = s.substitute(s.substitute(open, "\\%(" + NOTSLASH + "\\)\\@<=\\\\(", "\\\\%(", "g"), ",", "\\\\|", "g");
        String closepat = s.substitute(s.substitute(close, "\\%(" + NOTSLASH + "\\)\\@<=\\\\(", "\\\\%(", "g"), ",", "\\\\|", "g");
        String middlepat = s.substitute(s.substitute(middle, "\\%(" + NOTSLASH + "\\)\\@<=\\\\(", "\\\\%(", "g"), ",", "\\\\|", "g");
        Predicate<Pos> skip = skip(editor, s, language.skip());
        if (skip.test(startpos)) skip = p -> false;
        // mark ', then each level out; a level not found ends it where the one before stopped.
        for (int level = c.count; level > 0; level--) {
            if (Search.searchpair(editor, s, openpat, middlepat, closepat, backward, false, skip) < 1) break;
        }
        Pos finalPos = editor.cursor();
        editor.setCursor(startpos);
        return target(c, finalPos, true);
    }

    /** The patterns of a list, those with {@code \|} in {@code \%(\)}, joined with commas. */
    private static String join(VimStrings s, List<String> list) {
        List<String> out = new ArrayList<>();
        for (String v : list) out.add(s.matches(v, NOTSLASH + "\\\\|") ? "\\%(" + v + "\\)" : v);
        return String.join(",", out);
    }

    /** ini, mid and fin: escaped {@code :} and {@code ,} plain, and groups not capturing. */
    private static String plain(VimStrings s, String part) {
        part = s.substitute(part, NOTSLASH + "\\zs\\\\\\(:\\|,\\)", "\\1", "g");
        return s.substitute(part, NOTSLASH + "\\zs\\\\(", "\\\\%(", "g");
    }

    /** The string functions with 'ignorecase' as b:match_ignorecase says (s:RestoreOptions). */
    private static VimStrings strings(VimEditor editor, MatchitLanguages.Language language) {
        boolean ic = language.ignoreCase() != null ? language.ignoreCase() : editor.options().flag("ignorecase");
        return new VimStrings(ic, editor.options().keywords().javaClass());
    }

    /**
     * The words parsed, as each use does: {@code s:pat} (with back references resolved by s:ParseWords when there are
     * any), {@code s:all} (every word, for finding the one at the cursor) and {@code s:patBR}.
     */
    private void parse(VimStrings s, String matchWords, boolean wrapper) {
        if (!s.matches(matchWords, NOTSLASH + "\\\\\\d")) {
            doBR = false;
            pat = matchWords;
        } else {
            doBR = true;
            pat = parseWords(s, matchWords);
        }
        if (wrapper) {
            all = s.substitute(pat, NOTSLASH + "\\zs[,:]\\+", "\\\\|", "g");
            all = s.substitute(all, "\\\\,", ",", "g");
            all = s.substitute(all, "\\\\(", "\\\\%(", "g");
        } else all = s.substitute(pat, "[,:]\\+", "\\\\|", "g");
        all = "\\%(" + all + "\\)";
        patBR = s.substitute(matchWords + ",", NOTSLASH + "\\zs[,:]*,[,:]*", ",", "g");
        patBR = s.substitute(patBR, NOTSLASH + "\\zs:\\{2,}", ":", "g");
        if (wrapper) patBR = s.substitute(patBR, "\\\\,", ",", "g");
    }

    /**
     * s:ParseSkip and its evaluation: {@code s:} and {@code S:} name syntax groups, which Jasper takes from the tab's
     * tokens ({@code Comment}, {@code String}, {@code Character}, {@code Regex}); {@code r:} and {@code R:} test the
     * line up to the position; any other expression is taken as {@code s:comment\|string}.
     */
    static Predicate<Pos> skip(VimEditor editor, VimStrings s, String skip) {
        if (skip == null || skip.isEmpty()) skip = "s:comment\\|string";
        if (skip.length() < 2 || skip.charAt(1) != ':' || "sSrR".indexOf(skip.charAt(0)) < 0) skip = "s:comment\\|string";
        char kind = skip.charAt(0);
        String pattern = skip.substring(2);
        Buffer b = editor.buffer();
        if (kind == 'r' || kind == 'R') {
            return p -> {
                String line = b.line(p.line);
                boolean m = s.matches(VimStrings.strpart(line, 0, p.col + 1), pattern);
                return kind == 'r' ? m : !m;
            };
        }
        VimStrings ci = new VimStrings(true, editor.options().keywords().javaClass());
        return p -> {
            String group = syntaxGroup(editor, p);
            if (group.isEmpty()) return false;
            boolean m = ci.matches(group, pattern);
            return kind == 's' ? m : !m;
        };
    }

    /** The syntax group at a position, as far as the tab's language tokens tell it; empty in plain text. */
    private static String syntaxGroup(VimEditor editor, Pos p) {
        if (!(editor.area() instanceof org.fife.ui.rsyntaxtextarea.RSyntaxTextArea area)) return "";
        if (org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_NONE.equals(area.getSyntaxEditingStyle())) return "";
        org.fife.ui.rsyntaxtextarea.Token token = area.modelToToken(editor.buffer().offset(p));
        if (token == null) return "";
        return switch (token.getType()) {
            case org.fife.ui.rsyntaxtextarea.TokenTypes.COMMENT_EOL, org.fife.ui.rsyntaxtextarea.TokenTypes.COMMENT_MULTILINE,
                org.fife.ui.rsyntaxtextarea.TokenTypes.COMMENT_DOCUMENTATION, org.fife.ui.rsyntaxtextarea.TokenTypes.COMMENT_KEYWORD,
                org.fife.ui.rsyntaxtextarea.TokenTypes.COMMENT_MARKUP, org.fife.ui.rsyntaxtextarea.TokenTypes.MARKUP_COMMENT -> "Comment";
            case org.fife.ui.rsyntaxtextarea.TokenTypes.LITERAL_STRING_DOUBLE_QUOTE, org.fife.ui.rsyntaxtextarea.TokenTypes.LITERAL_BACKQUOTE,
                org.fife.ui.rsyntaxtextarea.TokenTypes.ERROR_STRING_DOUBLE -> "String";
            case org.fife.ui.rsyntaxtextarea.TokenTypes.LITERAL_CHAR, org.fife.ui.rsyntaxtextarea.TokenTypes.ERROR_CHAR -> "Character";
            case org.fife.ui.rsyntaxtextarea.TokenTypes.REGEX -> "Regex";
            default -> "";
        };
    }

    /** s:Wholematch: the pattern that finds the word ending on or after {@code start}, starting at or before it. */
    private static String wholematch(VimStrings s, String string, String pattern, int start) {
        String group = "\\%(" + pattern + "\\)";
        String prefix = start != 0 ? "\\(^.*\\%<" + (start + 2) + "c\\)\\zs" : "^";
        String suffix = start + 1 < string.length() ? "\\(\\%>" + (start + 1) + "c.*$\\)\\@=" : "$";
        if (!s.matches(string, prefix + group + suffix)) prefix = "";
        return prefix + group + suffix;
    }

    /**
     * s:Choose: the first of the comma-separated groups whose words (separated by {@code branch}) match the string
     * between prefix and suffix, with the corresponding group of {@code alt} after a comma; null for none.
     */
    private static String choose(VimStrings s, String patterns, String string, String comma, String branch, String prefix, String suffix,
                                 String alt) {
        String tail = s.matches(patterns, comma + "$") ? patterns : patterns + comma;
        int i = s.matchend(tail, NOTSLASH + comma);
        String alttail = null;
        int j = 0;
        if (alt != null) {
            alttail = s.matches(alt, comma + "$") ? alt : alt + comma;
            j = s.matchend(alttail, NOTSLASH + comma);
        }
        String current = VimStrings.strpart(tail, 0, i - 1);
        String currpat = branchPattern(s, current, branch);
        while (!s.matches(string, prefix + currpat + suffix)) {
            tail = VimStrings.strpart(tail, i);
            i = s.matchend(tail, NOTSLASH + comma);
            if (i == -1) return null;
            current = VimStrings.strpart(tail, 0, i - 1);
            currpat = branchPattern(s, current, branch);
            if (alt != null) {
                alttail = VimStrings.strpart(alttail, j);
                j = s.matchend(alttail, NOTSLASH + comma);
            }
        }
        if (alt != null) current = current + comma + VimStrings.strpart(alttail, 0, j - 1);
        return current;
    }

    private static String branchPattern(VimStrings s, String current, String branch) {
        String currpat = branch.isEmpty() ? current : s.substitute(current, NOTSLASH + branch, "\\\\|", "g");
        return s.substitute(currpat, "\\\\,", ",", "g");
    }

    /** s:InsertRefs: the group's back references filled in with what the words at the cursor matched. */
    private static String insertRefs(VimStrings s, String groupBR, String prefix, String group, String suffix, String matchline) {
        if (!s.matches(matchline, prefix + s.substitute(group, NOTSLASH + "\\zs:", "\\\\|", "g") + suffix)) return group;
        int i = s.matchend(groupBR, NOTSLASH + ":");
        String ini = VimStrings.strpart(groupBR, 0, i - 1);
        String tailBR = VimStrings.strpart(groupBR, i);
        String word = choose(s, group, matchline, ":", "", prefix, suffix, groupBR);
        if (word == null) return group;
        i = s.matchend(word, NOTSLASH + ":");
        String wordBR = VimStrings.strpart(word, i);
        word = VimStrings.strpart(word, 0, i - 1);
        String table;
        if (!wordBR.equals(ini)) table = resolve(s, ini, wordBR, "table");
        else {
            StringBuilder t = new StringBuilder();
            for (int d = 0; d < 10; d++) t.append(s.matches(tailBR, NOTSLASH + "\\\\" + d) ? (char) ('0' + d) : '-');
            table = t.toString();
        }
        for (int d = 9; d > 0; d--) {
            if (table.charAt(d) == '-') continue;
            String backref = s.substitute(matchline, prefix + word + suffix, "\\" + table.charAt(d), "");
            backref = VimStrings.escape(backref, "*,:");
            int[] startLen = refStartLen(s, ini, d);
            if (startLen == null) continue;
            ini = VimStrings.strpart(ini, 0, startLen[0]) + backref + VimStrings.strpart(ini, startLen[0] + startLen[1]);
            tailBR = s.substitute(tailBR, NOTSLASH + "\\zs\\\\" + d, VimStrings.escape(backref, "\\&"), "g");
        }
        return ini + ":" + tailBR;
    }

    /** s:ParseWords: each word's back references to its group's first word resolved. */
    private static String parseWords(VimStrings s, String groupsIn) {
        String groups = s.substitute(groupsIn + ",", NOTSLASH + "\\zs[,:]*,[,:]*", ",", "g");
        groups = s.substitute(groups, NOTSLASH + "\\zs:\\{2,}", ":", "g");
        StringBuilder parsed = new StringBuilder();
        while (s.matches(groups, "[^,:]")) {
            int i = s.matchend(groups, NOTSLASH + ":");
            int j = s.matchend(groups, NOTSLASH + ",");
            String ini = VimStrings.strpart(groups, 0, i - 1);
            String tail = VimStrings.strpart(groups, i, j - i - 1) + ":";
            groups = VimStrings.strpart(groups, j);
            parsed.append(ini);
            i = s.matchend(tail, NOTSLASH + ":");
            while (i != -1) {
                String word = VimStrings.strpart(tail, 0, i - 1);
                tail = VimStrings.strpart(tail, i);
                i = s.matchend(tail, NOTSLASH + ":");
                parsed.append(':').append(resolve(s, ini, word, "word"));
            }
            parsed.append(',');
        }
        return s.substitute(parsed.toString(), ",$", "", "");
    }

    /**
     * s:Ref with {@code "start", "len"}: where the {@code d}-th {@code \(\)} group of the pattern starts and how long
     * it is, the {@code \(} and {@code \)} included; null when there is none.
     */
    private static int[] refStartLen(VimStrings s, String string, int d) {
        int len = string.length();
        if (d == 0) return new int[] { 0, len };
        String match = string;
        for (int cnt = d; cnt > 0; cnt--) {
            int index = s.matchend(match, NOTSLASH + "\\\\(");
            if (index == -1) return null;
            match = VimStrings.strpart(match, index);
        }
        int start = len - match.length();
        int cnt = 1;
        while (cnt != 0) {
            int index = s.matchend(match, NOTSLASH + "\\\\(\\|\\\\)") - 1;
            if (index == -2) return null;
            cnt += match.charAt(index) == '(' ? 1 : -1;
            match = VimStrings.strpart(match, index + 1);
        }
        start -= 2;
        return new int[] { start, len - start - match.length() };
    }

    /** s:Ref with no more arguments: the {@code d}-th group's text; empty when there is none. */
    private static String ref(VimStrings s, String string, int d) {
        int[] startLen = refStartLen(s, string, d);
        return startLen == null ? "" : VimStrings.strpart(string, startLen[0], startLen[1]);
    }

    /** s:Count(string, '\(', '1'): the number of {@code \(} in the string. */
    private static int countOpen(VimStrings s, String string) {
        int result = 0;
        String foo = string;
        int index = s.matchend(foo, "\\\\(");
        while (index != -1) {
            result++;
            foo = VimStrings.strpart(foo, index);
            index = s.matchend(foo, "\\\\(");
        }
        return result;
    }

    /**
     * s:Resolve: the target word's back references ({@code \1}) to the source's groups replaced by those groups, and
     * the table saying which group of the word each of the source's groups became.
     */
    private static String resolve(VimStrings s, String source, String target, String output) {
        String word = target;
        int i = s.matchend(word, NOTSLASH + "\\\\\\d") - 1;
        StringBuilder table = new StringBuilder("----------");
        while (i != -2) {
            int d = word.charAt(i) - '0';
            String backref = ref(s, source, d);
            int w = countOpen(s, s.substitute(VimStrings.strpart(word, 0, i - 1), "\\\\\\\\", "", "g"));
            int b = 1, sn = d;
            while (b <= countOpen(s, s.substitute(backref, "\\\\\\\\", "", "g")) && sn < 10) {
                if (table.charAt(sn) == '-') {
                    if (w + b < 10) table.setCharAt(sn, (char) ('0' + w + b));
                    b++;
                    sn++;
                } else {
                    int[] startLen = refStartLen(s, backref, b);
                    if (startLen == null) break;
                    String refText = VimStrings.strpart(backref, startLen[0], startLen[1]);
                    backref = VimStrings.strpart(backref, 0, startLen[0]) + ":" + table.charAt(sn) + VimStrings.strpart(backref, startLen[0] + startLen[1]);
                    sn += countOpen(s, s.substitute(refText, "\\\\\\\\", "", "g"));
                }
            }
            word = VimStrings.strpart(word, 0, i - 1) + backref + VimStrings.strpart(word, i + 1);
            i = s.matchend(word, NOTSLASH + "\\\\\\d") - 1;
        }
        word = s.substitute(word, NOTSLASH + "\\zs:", "\\\\", "g");
        return switch (output) {
            case "table" -> table.toString();
            case "word" -> word;
            default -> table + word;
        };
    }
}
