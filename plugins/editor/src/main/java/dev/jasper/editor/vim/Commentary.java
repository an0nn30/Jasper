package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The commentary extension (spec 8), ported from Tim Pope's commentary.vim 1.3: {@code gc{motion}}, {@code gcc},
 * Visual {@code gc}, {@code gcu} and the {@code gc} text object comment and uncomment whole lines with
 * 'commentstring', which a tab takes from its language's line comment (RSyntaxTextArea's comment tokens).
 */
final class Commentary {
    private Commentary() { }

    private static final String OPTION = "commentary";

    static void register(VimCommands commands) {
        commands.operator("<Plug>Commentary", "comment or uncomment {motion}'s lines, or the Visual lines (`gc`, with `set commentary`)",
            Commentary::operator);
        commands.action("<Plug>CommentaryLine", "comment or uncomment [count] lines (`gcc`)", true, Commentary::line);
        commands.object("<Plug>Commentary", "the block of comment lines around the cursor (`gc` after an operator; `gcu` uncomments it)",
            Commentary::object);
    }

    /** {@code gc{motion}} and Visual {@code gc}: g@, and s:go on the lines of {@code '[} to {@code ']}. */
    private static void operator(Context c, Span span) {
        Extensions.require(c, OPTION);
        Script.operatorMarks(c, span);
        go(c.editor, c.editor.marks().get('[').line, c.editor.marks().get(']').line);
    }

    /** {@code gcc}: s:go() . '_', that is g@ with {@code [count]_}. */
    private static void line(Context c) {
        Extensions.require(c, OPTION);
        if (!Script.normal(c.editor, (c.hasCount ? String.valueOf(c.count) : "") + "<Plug>Commentary_")) throw Context.fail();
    }

    /** s:surroundings: 'commentstring' with a space either side of {@code %s}, split there. */
    static String[] surroundings(VimEditor editor) {
        String cms = editor.options().string("commentstring");
        if (cms.isEmpty()) cms = "%s";
        cms = cms.replaceFirst("(?<=\\S)%s", " %s").replaceFirst("%s(?=\\S)", "%s ");
        int at = cms.indexOf("%s");
        if (at < 0) return new String[] { cms, "" };
        String rest = cms.substring(at + 2);
        int next = rest.indexOf("%s");
        return new String[] { cms.substring(0, at), next < 0 ? rest : rest.substring(0, next) };
    }

    /** matchstr(line, '\S.*\s\@<!'): the line without its leading and trailing white space. */
    private static String trimmed(String line) { return line.strip(); }

    /** s:strip_white_space: a comment token loses its inner space when the line has the token without it. */
    private static String[] stripWhiteSpace(String l, String r, String line) {
        if (l.endsWith(" ") && (line + " ").indexOf(l) < 0 && line.startsWith(l.substring(0, l.length() - 1))) l = l.substring(0, l.length() - 1);
        if (r.startsWith(" ") && !slice(" " + line, -r.length() - 1, Integer.MAX_VALUE).equals(r)
            && slice(line, -r.length(), Integer.MAX_VALUE).equals(r.substring(1)))
            r = r.substring(1);
        return new String[] { l, r };
    }

    /** Vim's {@code s[from : to]}: {@code to} included, negative indexes counting from the end. */
    private static String slice(String s, int from, int to) {
        int n = s.length();
        int a = from < 0 ? Math.max(0, from + n) : from;
        int b = to == Integer.MAX_VALUE ? n - 1 : to < 0 ? to + n : to;
        if (a >= n || b < a) return "";
        return s.substring(a, Math.min(n, b + 1));
    }

    /** {@code line[strlen(line) - strlen(r) : -1]}: the line's tail as long as {@code r}. */
    private static String tail(String line, String r) { return slice(line, line.length() - r.length(), -1); }

    private static boolean isComment(String line, String l, String r) { return line.startsWith(l) && tail(line, r).equals(r); }

    /**
     * s:go: the lines are uncommented when every non-blank one is commented, else commented, each non-blank line
     * wrapped after the first line's indent; nested block comments are numbered ({@code /1*}) and back.
     */
    static void go(VimEditor editor, int first, int last) {
        Buffer b = editor.buffer();
        String[] lr = surroundings(editor);
        String l = lr[0], r = lr[1];
        boolean uncomment = true;
        for (int n = first; n <= last; n++) {
            String line = trimmed(b.line(n));
            String[] s = stripWhiteSpace(l, r, line);
            l = s[0];
            r = s[1];
            if (!line.isEmpty() && (!line.startsWith(l) || !tail(line, r).equals(r))) uncomment = false;
        }
        String indent = leadingWhite(b.line(first));
        List<String> lines = new ArrayList<>();
        for (int n = first; n <= last; n++) {
            String line = b.line(n);
            if (r.length() > 2 && !(l + r).contains("\\")) line = renumber(line, l, r, uncomment ? 2 : 0);
            if (uncomment) {
                Matcher m = Pattern.compile("\\S.*(?<!\\s)").matcher(line);
                if (m.find()) line = line.substring(0, m.start()) + slice(m.group(), l.length(), -r.length() - 1) + line.substring(m.end());
            } else {
                Matcher m = Pattern.compile("^(?:" + Pattern.quote(indent) + "|\\s*)(.*(?<=\\S))").matcher(line);
                if (m.find()) line = line.substring(0, m.start(1)) + l + m.group(1) + r + line.substring(m.end(1));
            }
            lines.add(line);
        }
        // setline() leaves the cursor where it is.
        Pos cursor = editor.cursor();
        for (int i = 0; i < lines.size(); i++) if (!lines.get(i).equals(b.line(first + i))) b.setLine(first + i, lines.get(i));
        editor.setCursor(new Pos(cursor.line, Math.min(cursor.col, Math.max(0, b.line(cursor.line).length() - 1))));
    }

    private static String leadingWhite(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) i++;
        return line.substring(0, i);
    }

    /**
     * The nested-comment numbering: the digits before the last character of the left token and after the first of the
     * right one ({@code /1*}, {@code *1/}) go up by one when commenting and down when uncommenting, vanishing at 0.
     */
    private static String renumber(String line, String l, String r, int uncomment) {
        Matcher last = Pattern.compile("\\S\\s*$").matcher(l);
        Matcher first = Pattern.compile("\\S").matcher(r);
        if (!last.find() || !first.find()) return line;
        int lk = last.start(), rk = first.start() + 1;
        Pattern digits = Pattern.compile("(?<=" + Pattern.quote(l.substring(0, lk)) + ")\\d*(?=" + Pattern.quote(l.substring(lk)) + ")"
            + "|(?<=" + Pattern.quote(r.substring(0, rk)) + ")\\d*(?=" + Pattern.quote(r.substring(rk)) + ")");
        Matcher m = digits.matcher(line);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            long n = (m.group().isEmpty() ? 0 : Long.parseLong(m.group())) + 1 - uncomment;
            m.appendReplacement(out, n <= 0 ? "" : Long.toString(n));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * s:textobject: the comment lines around the cursor, linewise; after {@code c} the blank lines at either end are
     * left out.
     */
    private static Span object(Context c) {
        Extensions.require(c, OPTION);
        VimEditor editor = c.editor;
        Buffer b = editor.buffer();
        boolean inner = "c".equals(c.operator);
        String[] lr = surroundings(editor);
        String l = lr[0], r = lr[1];
        int cursor = editor.cursor().line + 1, count = b.count();
        int[] lnums = { cursor + 1, cursor - 2 };
        int[][] passes = { { 0, -1, 1 }, { 1, 1, count } };
        for (int[] pass : passes) {
            int index = pass[0], dir = pass[1], bound = pass[2];
            String line = "";
            while (lnums[index] != bound && line.isEmpty() || isComment(line, l, r)) {
                lnums[index] += dir;
                line = trimmed(getline(b, lnums[index] + dir));
                String[] s = stripWhiteSpace(l, r, line);
                l = s[0];
                r = s[1];
            }
        }
        while ((inner || lnums[1] != count) && getline(b, lnums[0]).isEmpty()) lnums[0]++;
        while (inner && getline(b, lnums[1]).isEmpty()) lnums[1]--;
        if (lnums[0] > lnums[1]) throw Context.fail();
        return new Span(new Pos(lnums[0] - 1, 0), new Pos(lnums[1] - 1, 0), Target.Kind.LINEWISE);
    }

    /** getline(): a line by its number, empty outside the buffer. */
    private static String getline(Buffer b, int lnum) { return lnum < 1 || lnum > b.count() ? "" : b.line(lnum - 1); }
}
