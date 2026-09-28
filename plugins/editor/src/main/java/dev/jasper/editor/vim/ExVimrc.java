package dev.jasper.editor.vim;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The vimrc's commands (spec 7.1), which also run typed after {@code :}: {@code :let} for variables
 * ({@code mapleader}), registers and options, {@code :source}, {@code :messages}, IdeaVim's {@code :sethandler},
 * {@code :action} and {@code :actionlist}, and {@code :syntax} and {@code :filetype}, which a vimrc may say and which
 * do nothing (the Editor highlights and indents by language).
 */
final class ExVimrc {
    private ExVimrc() { }

    private static final String NOT_AVAILABLE = "E319: Sorry, the command is not available in this version";

    /** {@code :let {name} = {value}}, where the name is a variable, {@code @r} or {@code &option}. */
    private static final Pattern LET = Pattern.compile("(g:)?([A-Za-z_][A-Za-z0-9_#.]*|@.|&(?:[lg]:)?[a-z]+)\\s*=\\s*(.*)");

    /**
     * ex_let for the forms spec 7.1 lists: a global variable ({@code mapleader} is {@code g:mapleader}), a register
     * ({@code let @a = "…"}; a value ending with a line break is linewise, as setreg makes it) or an option
     * ({@code let &tabstop = 4}), set to one string or number. Other Vim script fails with E319.
     */
    static void let(ExCommand cmd) {
        String arg = cmd.arg.strip();
        Matcher m = LET.matcher(arg);
        if (!m.matches()) throw new VimFailure(NOT_AVAILABLE);
        String name = m.group(2);
        Object value = value(m.group(3));
        VimEditor editor = cmd.editor;
        Vim vim = editor.vim();
        if (name.startsWith("@")) {
            if (m.group(1) != null) throw new VimFailure(NOT_AVAILABLE);
            char register = name.charAt(1);
            String text = value.toString();
            if (register == '/') {
                vim.search().save(Search.SEARCH, text);
                return;
            }
            if (!Registers.valid(register, true) || register == '_') throw new VimFailure("E354: Invalid register name: '" + register + "'");
            vim.registers().set(register, Registers.Content.of(text, text.endsWith("\n") ? Registers.Type.LINE : Registers.Type.CHAR));
            return;
        }
        if (name.startsWith("&")) {
            if (m.group(1) != null) throw new VimFailure(NOT_AVAILABLE);
            String option = name.substring(1);
            boolean local = option.startsWith("l:"), global = option.startsWith("g:");
            if (local || global) option = option.substring(2);
            Options.Def def = Options.def(option).orElseThrow(() -> new VimFailure("E355: Unknown option: " + name.substring(1)));
            Object set = switch (def.type()) {
                case BOOLEAN -> value instanceof Long n ? n != 0 : Boolean.valueOf(!value.toString().isEmpty() && !value.toString().equals("0"));
                case NUMBER -> value instanceof Long n ? (Object) (int) (long) n : number(value.toString());
                case STRING -> value.toString();
            };
            try {
                if (local) editor.options().setLocal(def.name(), set);
                else editor.options().set(def.name(), set);
            } catch (IllegalArgumentException bad) {
                throw new VimFailure(bad.getMessage());
            }
            return;
        }
        vim.variables().put(name, value);
    }

    private static int number(String text) {
        try { return Integer.parseInt(text.strip()); }
        catch (NumberFormatException notNumber) { return 0; }
    }

    /**
     * One literal value: a single-quoted string ({@code ''} is a quote), a double-quoted one with Vim's escapes
     * ({@code \n}, {@code \t}, {@code \e}, {@code \<Esc>}, {@code \x41}, {@code €}), or a decimal or hex number.
     * A trailing {@code "} comment is allowed; anything else after the value is an expression Jasper does not have.
     *
     * @return a String or a Long
     */
    static Object value(String text) {
        String t = text.strip();
        if (t.isEmpty()) throw new VimFailure("E15: Invalid expression: \"\"");
        int[] end = { 0 };
        Object value;
        char first = t.charAt(0);
        if (first == '\'') value = single(t, end);
        else if (first == '"') value = doubled(t, end);
        else {
            Matcher n = Pattern.compile("-?(0[xX][0-9a-fA-F]+|\\d+)").matcher(t);
            if (!n.lookingAt()) throw new VimFailure(NOT_AVAILABLE);
            String digits = n.group();
            boolean negative = digits.startsWith("-");
            String body = negative ? digits.substring(1) : digits;
            long v = body.startsWith("0x") || body.startsWith("0X") ? Long.parseLong(body.substring(2), 16) : Long.parseLong(body);
            value = negative ? -v : v;
            end[0] = n.end();
        }
        String rest = t.substring(end[0]).strip();
        if (!rest.isEmpty() && !rest.startsWith("\"")) throw new VimFailure(NOT_AVAILABLE);
        return value;
    }

    private static String single(String t, int[] end) {
        StringBuilder out = new StringBuilder();
        int i = 1;
        while (i < t.length()) {
            char c = t.charAt(i);
            if (c == '\'') {
                if (i + 1 < t.length() && t.charAt(i + 1) == '\'') {
                    out.append('\'');
                    i += 2;
                    continue;
                }
                end[0] = i + 1;
                return out.toString();
            }
            out.append(c);
            i++;
        }
        throw new VimFailure("E115: Missing single quote: " + t);
    }

    /** eval_string: a double-quoted string and its backslash escapes. */
    private static String doubled(String t, int[] end) {
        StringBuilder out = new StringBuilder();
        int i = 1;
        while (i < t.length()) {
            char c = t.charAt(i);
            if (c == '"') {
                end[0] = i + 1;
                return out.toString();
            }
            if (c != '\\' || i + 1 >= t.length()) {
                out.append(c);
                i++;
                continue;
            }
            char e = t.charAt(++i);
            i++;
            switch (e) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'e' -> out.append('\033');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'x', 'X', 'u', 'U' -> {
                    int max = e == 'u' ? 4 : e == 'U' ? 8 : 2;
                    int start = i;
                    while (i < t.length() && i - start < max && Character.digit(t.charAt(i), 16) >= 0) i++;
                    if (i == start) out.append(e);
                    else out.appendCodePoint(Integer.parseInt(t.substring(start, i), 16));
                }
                case '<' -> {
                    int close = t.indexOf('>', i);
                    List<KeyInput> key = close < 0 ? List.of() : KeyInput.parse("<" + t.substring(i, close + 1));
                    if (key.size() == 1 && !key.getFirst().equals(KeyInput.of('<'))) {
                        out.append(KeyCodes.text(key));
                        i = close + 1;
                    } else out.append('<');
                }
                default -> {
                    if (e >= '0' && e <= '7') {
                        int start = i - 1;
                        while (i < t.length() && i - start < 3 && t.charAt(i) >= '0' && t.charAt(i) <= '7') i++;
                        out.append((char) Integer.parseInt(t.substring(start, i), 8));
                    } else out.append(e);
                }
            }
        }
        throw new VimFailure("E114: Missing double quote: " + t);
    }

    /**
     * ex_source: {@code :source {file}} runs the file's lines; the file is relative to the file being read, or typed,
     * to the current file's folder. Typed, what failed is shown as Vim shows it; inside a vimrc it joins the vimrc's
     * report.
     */
    static void source(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        String name = ExFiles.fileArg(cmd);
        if (name == null) throw new VimFailure("E471: Argument required");
        Vim vim = editor.vim();
        Vimrc.Reading reading = vim.reading();
        Path file;
        if (reading != null) {
            String home = System.getProperty("user.home");
            String expanded = name.equals("~") ? home : name.startsWith("~/") ? home + name.substring(1) : name;
            Path path = Path.of(expanded);
            file = path.isAbsolute() ? path.normalize() : reading.file.toAbsolutePath().getParent().resolve(path).normalize();
        } else file = ExFiles.resolve(name, ExFiles.current(ExFiles.buffers(cmd)));
        Vimrc.Reading into = reading != null ? reading : new Vimrc.Reading();
        try {
            Vimrc.source(editor, file, into);
        } catch (NoSuchFileException missing) {
            throw new VimFailure("E484: Can't open file " + name);
        } catch (IOException unreadable) {
            throw new VimFailure("E484: Can't open file " + name);
        }
        if (reading == null) for (String line : into.report) editor.message(line, true);
    }

    /**
     * ex_messages: the message history under Vim's "Messages maintainer" line ({@code :messages clear} empties it;
     * {@code :3messages} shows the last three, without that line).
     */
    static void messages(ExCommand cmd) {
        List<String> history = cmd.editor.vim().messages();
        String arg = cmd.arg.strip();
        if (arg.equals("clear")) {
            history.clear();
            return;
        }
        if (!arg.isEmpty()) throw new VimFailure("E474: Invalid argument");
        if (cmd.addrCount == 0) cmd.editor.say("Messages maintainer: The Vim Project");
        int from = cmd.addrCount > 0 ? Math.max(0, history.size() - cmd.line2) : 0;
        for (String line : List.copyOf(history.subList(from, history.size()))) cmd.editor.say(line);
    }

    /** {@code :syntax} and {@code :filetype}: accepted, as vimrcs say them, and nothing happens. */
    static void ignored(ExCommand cmd) { }
}
