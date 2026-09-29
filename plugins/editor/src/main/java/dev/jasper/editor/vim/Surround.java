package dev.jasper.editor.vim;

import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The surround extension (spec 8), ported from Tim Pope's surround.vim 2.2: {@code ds}, {@code cs}, {@code cS},
 * {@code ys{motion}}, {@code yS{motion}}, {@code yss}, {@code ySS} and Visual {@code S} and {@code gS}. As the plugin
 * does, it deletes and puts through the unnamed register with Vim's own commands ({@code d2i"}, {@code ""P},
 * {@code gv"xy}), and reads its targets and replacements as keys, not mapped, and a tag or function name as a line.
 */
final class Surround {
    private Surround() { }

    private static final String OPTION = "surround";
    private static final String ESC = "\033", CTRL_C = "\003";

    static void register(VimCommands commands) {
        commands.action("<Plug>Dsurround", "delete the surroundings {char} (`ds`, with `set surround`)", true, Surround::delete);
        commands.action("<Plug>Csurround", "change the surroundings {char} to {char} (`cs`)", true, c -> change(c, false));
        commands.action("<Plug>CSurround", "change the surroundings, the text on lines of its own (`cS`)", true, c -> change(c, true));
        commands.operator("<Plug>Ysurround", "surround {motion} with {char} (`ys`)", (c, span) -> operator(c, span, false));
        commands.operator("<Plug>YSurround", "surround {motion} with {char}, on lines of its own (`yS`)", (c, span) -> operator(c, span, true));
        commands.action("<Plug>Yssurround", "surround [count] lines' text with {char} (`yss`)", true, Surround::lines);
        commands.action("<Plug>YSsurround", "surround [count] lines with {char}, on lines of their own (`ySS`)", true, Surround::linesOnTheirOwn);
        commands.visual("<Plug>VSurround", "surround the Visual area with {char} (`S`)", false, true, c -> visual(c, false));
        commands.visual("<Plug>VgSurround", "surround the Visual area with {char}, the other way about lines (`gS`)", false, true,
            c -> visual(c, true));
    }

    // ---------------------------------------------------------------- input

    /** A key as the plugin's getchar() gives it: the character, or its Vim codes. */
    private static String text(KeyInput key) { return KeyCodes.text(List.of(key)); }

    /** s:inputtarget: a key; digits read on (a count), and a space reads one more; {@code <Esc>} gives nothing. */
    private static void inputTarget(Context c, Consumer<String> then) {
        c.editor.machine().readKey(key -> target(c, text(key), then));
    }

    private static void target(Context c, String got, Consumer<String> then) {
        if (got.matches("\\d+")) {
            c.editor.machine().readKey(key -> target(c, got + text(key), then));
            return;
        }
        if (got.equals(" ")) {
            c.editor.machine().readKey(key -> finishTarget(got + text(key), then));
            return;
        }
        finishTarget(got, then);
    }

    private static void finishTarget(String got, Consumer<String> then) {
        then.accept(got.contains(ESC) || got.contains(CTRL_C) || got.contains("\0") ? "" : got);
    }

    /** s:inputreplacement: a key, and one more after a space; {@code <Esc>} gives nothing. */
    private static void inputReplacement(Context c, Consumer<String> then) {
        c.editor.machine().readKey(key -> {
            String got = text(key);
            if (got.equals(" ")) c.editor.machine().readKey(next -> then.accept(replacement(got + text(next))));
            else then.accept(replacement(got));
        });
    }

    private static String replacement(String got) { return got.contains(ESC) || got.contains(CTRL_C) ? "" : got; }

    /** The line a replacement reads (a tag, a LaTeX environment, a function name), or null when it reads none. */
    private record Prompt(String prompt, String initial, char end) { }

    /** The last surroundings {@code ds} or {@code cs} removed, for {@code T}'s default tag (s:lastdel). */
    private static String lastdel = "";

    private static Prompt prompt(String newchar) {
        String c = newchar.startsWith(" ") ? newchar.substring(1) : newchar;
        if (c.equals("t") || c.equals("T") || c.equals("\u0014") || c.equals("<")) {
            String initial = "";
            if (c.equals("T")) {
                Matcher m = Pattern.compile("<(.*?)>", Pattern.DOTALL).matcher(lastdel);
                if (m.find()) initial = m.group(1);
            }
            return new Prompt("<", initial, '>');
        }
        if (c.equals("l") || c.equals("\\")) return new Prompt("\\begin{", "", (char) 0);
        if (c.equals("f") || c.equals("F") || c.equals("\u0006")) return new Prompt("function: ", "", (char) 0);
        return null;
    }

    /** Reads the replacement's line, when it has one, then goes on with it (null when it has none). */
    private static void withInput(Context c, String newchar, Consumer<String> then) {
        Prompt p = prompt(newchar);
        if (p == null) then.accept(null);
        else c.editor.machine().readLine(p.prompt(), p.initial(), p.end(), then);
    }

    // ---------------------------------------------------------------- commands

    /** {@code <Plug>Dsurround}: s:dosurround(s:inputtarget()). */
    private static void delete(Context c) {
        Extensions.require(c, OPTION);
        inputTarget(c, target -> dosurround(c, target, null, null, false));
    }

    /** {@code <Plug>Csurround}: s:changesurround(), a target and then a replacement. */
    private static void change(Context c, boolean special) {
        Extensions.require(c, OPTION);
        inputTarget(c, a -> {
            if (a.isEmpty()) throw Context.fail();
            inputReplacement(c, b -> {
                if (b.isEmpty()) throw Context.fail();
                withInput(c, b, input -> dosurround(c, a, b, input, special));
            });
        });
    }

    /** {@code <Plug>Ysurround} and {@code <Plug>YSurround}: g@, then s:opfunc on the motion's text. */
    private static void operator(Context c, Span span, boolean special) {
        Extensions.require(c, OPTION);
        String type = Script.operatorMarks(c, span);
        read(c, type, special);
    }

    /** {@code <Plug>Yssurround}: {@code ^}, then g@ over {@code [count]g_}. */
    private static void lines(Context c) {
        Extensions.require(c, OPTION);
        int count = c.count;
        VimEditor editor = c.editor;
        Script.normal(editor, "^");
        Pos start = editor.cursor();
        if (!Script.normal(editor, count + "g_")) throw Context.fail();
        Pos end = editor.cursor();
        editor.marks().set('[', start);
        editor.marks().set(']', end);
        editor.setCursor(start);
        read(c, "char", false);
    }

    /** {@code <Plug>YSsurround}: g@ over {@code [count]_}, linewise, on lines of its own. */
    private static void linesOnTheirOwn(Context c) {
        Extensions.require(c, OPTION);
        VimEditor editor = c.editor;
        Pos cursor = editor.cursor();
        int last = cursor.line + c.count - 1;
        if (last >= editor.buffer().count()) throw Context.fail();
        editor.marks().set('[', new Pos(cursor.line, 0));
        editor.marks().set(']', new Pos(last, 0));
        read(c, "line", true);
    }

    /** Visual {@code S} and {@code gS}: {@code :<C-u>} leaves Visual mode; s:opfunc on the area, by visualmode(). */
    private static void visual(Context c, boolean other) {
        Extensions.require(c, OPTION);
        Mode mode = c.editor.mode();
        Visual.end(c.editor);
        String type = mode == Mode.VISUAL_LINE ? "V" : mode == Mode.VISUAL_BLOCK ? "\u0016" : "v";
        boolean special = mode == Mode.VISUAL_LINE != other;
        read(c, type, special);
    }

    /** s:opfunc's input: the replacement and its line, then the rest. */
    private static void read(Context c, String type, boolean special) {
        inputReplacement(c, ch -> {
            if (ch.isEmpty()) throw Context.fail();
            withInput(c, ch, input -> opfunc(c, type, special, ch, input));
        });
    }

    // ---------------------------------------------------------------- s:opfunc and s:dosurround

    /** s:opfunc: yank the text into the unnamed register, wrap it, and put it back over the text. */
    private static void opfunc(Context c, String aType, boolean special, String ch, String input) {
        VimEditor editor = c.editor;
        char reg = '"';
        Options options = editor.options();
        String clipboard = options.string("clipboard");
        withoutUnnamedClipboard(options);
        String regSave = Script.getreg(editor, reg), regType = Script.regtype(editor, reg);
        try {
            String type = aType;
            switch (aType) {
                case "char" -> {
                    Script.normal(editor, "v`[o`]\"" + reg + "y");
                    type = "v";
                }
                case "line" -> {
                    Script.normal(editor, "`[V`]\"" + reg + "y");
                    type = "V";
                }
                case "v", "V", "\u0016" -> Script.normal(editor, "gv\"" + reg + "y");
                default -> throw Context.fail();
            }
            String keeper = Script.getreg(editor, reg);
            String append = "";
            if (type.equals("v") && !aType.equals("v")) {
                // '\_s\@<!\s*$': the trailing blanks; Vim's \s is a space or a tab.
                Matcher m = Pattern.compile("(?<![ \\t\\n])[ \\t]*\\z").matcher(keeper);
                if (m.find()) {
                    append = m.group();
                    keeper = keeper.substring(0, m.start());
                }
            }
            Script.setreg(editor, reg, keeper, type);
            wrapreg(editor, reg, ch, "", special, input);
            if (type.equals("v") && !aType.equals("v") && !append.isEmpty())
                Script.setreg(editor, reg, Script.getreg(editor, reg) + append, "v");
            Script.normal(editor, "gvp`[");
        } finally {
            Script.setreg(editor, reg, regSave, regType.isEmpty() ? "v" : regType);
            options.set("clipboard", clipboard);
        }
    }

    private static void withoutUnnamedClipboard(Options options) {
        String kept = String.join(",", java.util.Arrays.stream(options.string("clipboard").split(","))
            .filter(item -> !item.equals("unnamed") && !item.equals("unnamedplus") && !item.isEmpty()).toList());
        options.set("clipboard", kept);
    }

    /**
     * s:dosurround: the text inside the target is deleted, then the target with it, and the inside is put back, wrapped
     * in the replacement when there is one.
     */
    private static void dosurround(Context c, String target, String newchar, String input, boolean special) {
        VimEditor editor = c.editor;
        Options options = editor.options();
        boolean solSave = options.flag("startofline");
        options.set("startofline", true);
        String clipboard = options.string("clipboard");
        withoutUnnamedClipboard(options);
        try {
            surroundBody(c, target, newchar, input, special);
        } finally {
            options.set("clipboard", clipboard);
            options.set("startofline", solSave);
        }
    }

    private static void surroundBody(Context c, String target, String newchar, String input, boolean special) {
        VimEditor editor = c.editor;
        Buffer b = editor.buffer();
        long scount = c.count;
        String ch = target;
        boolean spc = false;
        Matcher digits = Pattern.compile("^\\d+").matcher(ch);
        if (digits.find()) {
            scount *= Long.parseLong(digits.group());
            ch = ch.substring(digits.end());
        }
        if (ch.startsWith(" ")) {
            ch = ch.substring(1);
            spc = true;
        }
        if (ch.equals("a")) ch = ">";
        if (ch.equals("r")) ch = "]";
        if (newchar != null && newchar.isEmpty()) throw Context.fail();
        String original = Script.getreg(editor, '"'), otype = Script.regtype(editor, '"');
        Script.setreg(editor, '"', "", "v");
        String strcount = scount == 1 ? "" : String.valueOf(scount);
        boolean punct = ch.length() == 1 && isPunctOrSpace(ch.charAt(0));
        if (ch.equals("/")) {
            Script.normal(editor, strcount + "[/d" + strcount + "]/");
        } else if (punct && "[](){}<>\"'`".indexOf(ch.charAt(0)) < 0) {
            Script.normal(editor, "T" + key(ch));
            Pos p = editor.cursor();
            String line = b.line(p.line);
            if (p.col < line.length() && line.charAt(p.col) == ch.charAt(0)) Script.normal(editor, "l");
            Script.normal(editor, "dt" + key(ch));
        } else {
            Script.normal(editor, "d" + strcount + "i" + key(ch));
        }
        String keeper = Script.getreg(editor, '"');
        String okeeper = keeper;
        if (keeper.isEmpty()) {
            Script.setreg(editor, '"', original, otype.isEmpty() ? "v" : otype);
            return;
        }
        String oldline = b.line(editor.cursor().line);
        int oldlnum = editor.cursor().line;
        if (ch.equals("p")) Script.setreg(editor, '"', "", "V");
        else if (ch.equals("s") || ch.equals("w") || ch.equals("W")) Script.setreg(editor, '"', "", "v");
        else if (ch.length() == 1 && "\"'`".indexOf(ch.charAt(0)) >= 0) {
            Script.normal(editor, "i <Esc>d2i" + key(ch));
            Script.setreg(editor, '"', Script.getreg(editor, '"').replaceFirst(" ", ""), Script.regtype(editor, '"'));
        } else if (ch.equals("/")) {
            Script.normal(editor, "\"_x");
            Script.setreg(editor, '"', "/**/", "v");
            keeper = keeper.replaceFirst("\\A/\\*[ \\t]?", "").replaceFirst("[ \\t]?\\*\\z", "");
        } else if (punct && "[](){}<>".indexOf(ch.charAt(0)) < 0) {
            Script.normal(editor, "F" + key(ch));
            Script.normal(editor, "df" + key(ch));
        } else {
            backOneCharacter(editor);
            Script.normal(editor, "da" + key(ch));
        }
        String removed = Script.getreg(editor, '"');
        String rem2 = removed.replaceFirst("\n[^\n]*", "");
        String oldhead = oldline.substring(0, Math.max(0, oldline.length() - rem2.length()));
        String regtype = Script.regtype(editor, '"');
        if (ch.length() == 1 && "[({<T".indexOf(ch.charAt(0)) >= 0 || spc) keeper = keeper.replaceFirst("\\A[ \\t]+", "").replaceFirst("[ \\t]+\\z", "");
        // col("']") == col("$") && virtcol('.') + 1 == virtcol('$'): the delete ended the cursor's line.
        Pos markEnd = editor.marks().get(']');
        Pos cursor = editor.cursor();
        String cursorLine = b.line(cursor.line);
        String pcmd;
        boolean atEnd = markEnd != null && markEnd.col == cursorLine.length() && !cursorLine.isEmpty() && cursor.col == Text.last(cursorLine);
        if (atEnd) {
            if (oldhead.matches("[ \\t]*") && newchar == null)
                keeper = keeper.replaceFirst("\\A\\n" + Pattern.quote(oldhead) + "([ \\t]*[^\\n]*?)\\n[ \\t]*\\z", "$1");
            pcmd = "p";
        } else pcmd = "P";
        if (cursor.line + 1 < oldlnum && regtype.equals("V")) pcmd = "p";
        Script.setreg(editor, '"', keeper, regtype.isEmpty() ? "v" : regtype);
        if (newchar != null) wrapreg(editor, '"', newchar, removed, special, input);
        Script.normal(editor, "\"\"" + pcmd + "`[");
        String now = b.line(editor.cursor().line);
        if (now.matches("[ \\t]+") && keeper.matches("(?s)[ \\t]*\\n.*")) Script.normal(editor, "cc<Esc>");
        Script.setreg(editor, '"', original, otype.isEmpty() ? "v" : otype);
        lastdel = removed;
    }

    /** search('\m.', 'bW'): the character before the cursor, on an earlier line if need be. */
    private static void backOneCharacter(VimEditor editor) {
        Buffer b = editor.buffer();
        Pos p = editor.cursor();
        if (p.col > 0) {
            editor.setCursor(new Pos(p.line, Text.previous(b.line(p.line), p.col)), true);
            return;
        }
        for (int line = p.line - 1; line >= 0; line--) {
            String text = b.line(line);
            if (!text.isEmpty()) {
                editor.setCursor(new Pos(line, Text.last(text)), true);
                return;
            }
        }
    }

    /** [[:punct:][:space:]] for one ASCII character. */
    private static boolean isPunctOrSpace(char c) {
        return c < 128 && (Character.isWhitespace(c) || c > ' ' && c < 127 && !Character.isLetterOrDigit(c));
    }

    /** One character as keys: {@code <} is {@code <lt>}. */
    private static String key(String ch) { return ch.equals("<") ? "<lt>" : ch; }

    // ---------------------------------------------------------------- s:wrap

    private static void wrapreg(VimEditor editor, char reg, String ch, String removed, boolean special, String input) {
        String orig = Script.getreg(editor, reg);
        String type = Script.regtype(editor, reg).replaceFirst("\\d+$", "");
        String wrapped = wrap(editor, orig, ch, type, removed, special, input);
        Script.setreg(editor, reg, wrapped, type);
    }

    /** The bracket pairs: {@code b}, {@code (} and {@code )} stand for parentheses, and so on; an opening one adds spaces. */
    private static final String PAIRS = "b()B{}r[]a<>";

    /**
     * s:wrap: the text with the replacement's two sides around it; linewise (and with {@code special}) on lines of their
     * own, with the first line's indent, and blockwise around each line.
     */
    static String wrap(VimEditor editor, String string, String newcharIn, String type, String removed, boolean special, String input) {
        String keeper = string;
        String newchar = newcharIn;
        Buffer b = editor.buffer();
        String before = "", after = "";
        String initspaces;
        if (type.equals("V")) initspaces = leadingWhite(keeper);
        else initspaces = leadingWhite(b.line(editor.cursor().line));
        String extraspace = "";
        if (newchar.startsWith(" ")) {
            newchar = newchar.substring(1);
            extraspace = " ";
        }
        int idx = newchar.length() == 1 ? PAIRS.indexOf(newchar.charAt(0)) : -1;
        Object custom = newchar.length() == 1 ? editor.vim().variables().get("surround_" + (int) newchar.charAt(0)) : null;
        if (newchar.equals(" ")) {
            before = "";
            after = "";
        } else if (custom != null) {
            String all = custom.toString();
            before = extractBefore(all);
            after = extractAfter(all);
        } else if (newchar.equals("p")) {
            before = "\n";
            after = "\n\n";
        } else if (newchar.equals("s")) {
            before = " ";
            after = "";
        } else if (newchar.equals(":")) {
            before = ":";
            after = "";
        } else if (newchar.equals("t") || newchar.equals("T") || newchar.equals("\u0014") || newchar.equals("<")) {
            String tag = input == null ? "" : input;
            if (!tag.isEmpty()) {
                boolean keepAttributes = !tag.endsWith(">");
                tag = tag.replaceFirst(">*$", "");
                String attributes = "";
                if (keepAttributes) {
                    Matcher m = Pattern.compile("<[^ \\t\\n]+([\\s\\S]*?)>").matcher(removed);
                    if (m.find()) attributes = m.group(1);
                }
                if (tag.endsWith("/")) {
                    tag = tag.substring(0, tag.length() - 1);
                    before = "<" + tag + attributes + " />";
                    after = "";
                } else {
                    before = "<" + tag + attributes + ">";
                    after = "</" + tag.replaceFirst(" .*", "") + ">";
                }
                if (newchar.equals("\u0014")) {
                    if (type.equals("v") || type.equals("V")) before += "\n\t";
                    if (type.equals("v")) after = "\n" + after;
                }
            }
        } else if (newchar.equals("l") || newchar.equals("\\")) {
            String env = input == null ? "" : input;
            if (!env.isEmpty()) {
                env = "{" + env;
                env += closematch(env);
                before = "\\begin" + env;
                Matcher m = Pattern.compile("[^}]*").matcher(env);
                after = "\\end" + (m.find() ? m.group() : "") + "}";
            }
        } else if (newchar.equals("f") || newchar.equals("F")) {
            String fnc = input == null ? "" : input;
            if (!fnc.isEmpty()) {
                before = fnc.replaceFirst("\\($", "") + "(";
                after = ")";
                if (newchar.equals("F")) {
                    before += " ";
                    after = " " + after;
                }
            }
        } else if (newchar.equals("\u0006")) {
            String fnc = input == null ? "" : input;
            before = "(" + fnc + " ";
            after = ")";
        } else if (idx >= 0) {
            String spc = idx % 3 == 1 ? " " : "";
            int i = idx / 3 * 3;
            before = PAIRS.charAt(i + 1) + spc;
            after = spc + PAIRS.charAt(i + 2);
        } else if (newchar.equals(ESC) || newchar.equals("\u001d")) {
            before = "{\n\t";
            after = "\n}";
        } else if (!newchar.matches("\\p{Alpha}")) {
            before = newchar;
            after = newchar;
        }
        after = after.replace("\n", "\n" + initspaces);
        if (type.equals("V") || special && type.equals("v")) {
            before = before.replaceFirst(" +\\z", "");
            after = after.replaceFirst("^ +", "");
            if (!after.startsWith("\n")) after = initspaces + after;
            if (!keeper.endsWith("\n") && !after.startsWith("\n")) keeper += "\n";
            else if (keeper.endsWith("\n") && after.startsWith("\n")) after = after.substring(1);
            if (!keeper.startsWith("\n") && !endsWithBreak(before)) {
                before += "\n";
                if (special) before += "\t";
            } else if (keeper.startsWith("\n") && endsWithBreak(before)) keeper = keeper.substring(1);
            if (type.equals("V") && Pattern.compile("\\n[ \\t]*\\n\\z").matcher(keeper).find()) keeper = keeper.substring(0, keeper.length() - 1);
        }
        if (type.equals("V")) before = initspaces + before;
        if (endsWithBreak(before)) {
            if (type.equals("v")) keeper = initspaces + keeper;
            Matcher pad = Pattern.compile("\\n([ \\t]+)\\z").matcher(before);
            String padding = pad.find() ? pad.group(1) : "";
            before = before.replaceFirst("\\n[ \\t]+\\z", "\n");
            keeper = fixindent(editor, keeper, padding);
        }
        if (type.equals("V")) return before + keeper + after;
        if (type.startsWith("\u0016")) {
            String open = before.replace("\n", " "), close = after.replace("\n", " ");
            String[] lines = (keeper + "\n").split("\n", -1);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < lines.length - 1; i++) {
                if (i > 0) out.append('\n');
                out.append(open).append(lines[i]).append(close);
            }
            return out.toString();
        }
        return before + extraspace + keeper + extraspace + after;
    }

    /** {@code =~ '\n\s*$'}: a line break, then only blanks, at the end. */
    private static boolean endsWithBreak(String text) { return Pattern.compile("\\n[ \\t]*\\z").matcher(text).find(); }

    private static String extractBefore(String all) {
        int cr = all.indexOf('\r');
        int nl = all.indexOf('\n');
        int at = cr >= 0 ? cr : nl;
        return at < 0 ? "" : all.substring(0, at);
    }

    private static String extractAfter(String all) {
        int cr = all.indexOf('\r');
        int nl = all.indexOf('\n');
        int at = cr >= 0 ? cr : nl;
        return at < 0 ? "" : all.substring(at + 1);
    }

    /** s:closematch: the bracket that closes one left open at the end of a LaTeX environment's name. */
    private static String closematch(String str) {
        Matcher m = Pattern.compile(".[^\\[\\](){}<>]*$").matcher(str);
        String tail = m.find() ? m.group() : "";
        if (tail.matches("\\[.+")) return "]";
        if (tail.matches("\\(.+")) return ")";
        if (tail.matches("\\{.+")) return "}";
        return "";
    }

    /** s:fixindent: tabs as 'shiftwidth' spaces, each line after {@code spc}, and tabs back without 'expandtab'. */
    private static String fixindent(VimEditor editor, String str, String spc) {
        Options o = editor.options();
        String sw = " ".repeat(o.shiftwidth());
        String s = str.replace("\t", sw);
        String pad = spc.replace("\t", sw);
        s = Pattern.compile("(\\n|\\A)(?=.)").matcher(s).replaceAll(m -> Matcher.quoteReplacement(m.group(1) + pad));
        if (!o.flag("expandtab")) s = s.replaceAll("[ \\t]{" + o.number("tabstop") + "}", "\t");
        return s;
    }

    private static String leadingWhite(String text) {
        int i = 0;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i++;
        return text.substring(0, i);
    }
}
