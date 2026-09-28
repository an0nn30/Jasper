package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Vim options (spec 3.8). The global instance holds global values; each editor has a local instance whose
 * buffer- and window-local options keep their own values. Setting an option on a local instance sets it there and
 * globally, as {@code :set} does. Defaults are Vim's with {@code -u NONE -N}, except where the Editor supplies them
 * ({@link EditorDefaults}) and 'showcmd', 'incsearch' and 'hlsearch', which are on (spec 3.8).
 */
public final class Options {
    enum Type { BOOLEAN, NUMBER, STRING }

    /**
     * @param name the full name
     * @param abbreviation the short name, or the full name when there is none
     * @param type the value type
     * @param fallback the default
     * @param local whether each editor keeps its own value
     */
    record Def(String name, String abbreviation, Type type, Object fallback, boolean local) { }

    /** Every option this version knows. */
    static final List<Def> DEFS = List.of(
        new Def("autoindent", "ai", Type.BOOLEAN, false, true),
        new Def("backspace", "bs", Type.STRING, "indent,eol,start", false),
        new Def("belloff", "bo", Type.STRING, "", false),
        new Def("clipboard", "cb", Type.STRING, "", false),
        new Def("comments", "com", Type.STRING, "s1:/*,mb:*,ex:*/,://,b:#,:%,:XCOMM,n:>,fb:-", true),
        new Def("compatible", "cp", Type.BOOLEAN, false, false),
        new Def("encoding", "enc", Type.STRING, "utf-8", false),
        new Def("expandtab", "et", Type.BOOLEAN, false, true),
        new Def("fileformat", "ff", Type.STRING, "unix", true),
        new Def("foldopen", "fdo", Type.STRING, "block,hor,mark,percent,quickfix,search,tag,undo", false),
        new Def("formatoptions", "fo", Type.STRING, "tcq", true),
        new Def("gdefault", "gd", Type.BOOLEAN, false, false),
        new Def("hlsearch", "hls", Type.BOOLEAN, true, false),
        new Def("ignorecase", "ic", Type.BOOLEAN, false, false),
        new Def("incsearch", "is", Type.BOOLEAN, true, false),
        new Def("iskeyword", "isk", Type.STRING, "@,48-57,_,192-255", true),
        new Def("joinspaces", "js", Type.BOOLEAN, true, false),
        new Def("laststatus", "ls", Type.NUMBER, 1, false),
        new Def("matchpairs", "mps", Type.STRING, "(:),{:},[:]", true),
        new Def("maxmapdepth", "mmd", Type.NUMBER, 1000, false),
        new Def("mouse", "mouse", Type.STRING, "", false),
        new Def("nrformats", "nf", Type.STRING, "bin,octal,hex", true),
        new Def("number", "nu", Type.BOOLEAN, true, true),
        new Def("relativenumber", "rnu", Type.BOOLEAN, false, true),
        new Def("report", "report", Type.NUMBER, 2, false),
        new Def("ruler", "ru", Type.BOOLEAN, false, false),
        new Def("scrolljump", "sj", Type.NUMBER, 1, false),
        new Def("scrolloff", "so", Type.NUMBER, 0, false),
        new Def("shiftround", "sr", Type.BOOLEAN, false, false),
        new Def("shiftwidth", "sw", Type.NUMBER, 8, true),
        new Def("showcmd", "sc", Type.BOOLEAN, true, false),
        new Def("showmode", "smd", Type.BOOLEAN, true, false),
        new Def("sidescroll", "ss", Type.NUMBER, 0, false),
        new Def("sidescrolloff", "siso", Type.NUMBER, 0, false),
        new Def("smartcase", "scs", Type.BOOLEAN, false, false),
        new Def("softtabstop", "sts", Type.NUMBER, 0, true),
        new Def("startofline", "sol", Type.BOOLEAN, true, false),
        new Def("syntax", "syn", Type.STRING, "", true),
        new Def("tabstop", "ts", Type.NUMBER, 8, true),
        new Def("textwidth", "tw", Type.NUMBER, 0, true),
        new Def("tildeop", "top", Type.BOOLEAN, false, false),
        new Def("timeout", "to", Type.BOOLEAN, true, false),
        new Def("timeoutlen", "tm", Type.NUMBER, 1000, false),
        new Def("ttimeout", "ttimeout", Type.BOOLEAN, false, false),
        new Def("ttimeoutlen", "ttm", Type.NUMBER, -1, false),
        new Def("undolevels", "ul", Type.NUMBER, 1000, false),
        new Def("visualbell", "vb", Type.BOOLEAN, false, false),
        new Def("whichwrap", "ww", Type.STRING, "b,s", false),
        new Def("wrap", "wrap", Type.BOOLEAN, true, true),
        new Def("wrapscan", "ws", Type.BOOLEAN, true, false));

    private final Options global;
    private final Map<String, Object> values = new HashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private String keywordSpec;
    private Keywords keywords;

    /** A global instance with Vim's defaults. */
    public Options() { this(null); }

    private Options(Options global) { this.global = global; }

    /** @return a local instance for one editor, backed by this global one */
    Options local() {
        if (global != null) throw new IllegalStateException("Only the global options have local copies");
        return new Options(this);
    }

    /** @param listener runs after any value of this instance changes */
    void onChanged(Runnable listener) { listeners.add(listener); }

    boolean flag(String name) { return (Boolean) get(name); }

    int number(String name) { return (Integer) get(name); }

    String string(String name) { return (String) get(name); }

    /** @return 'shiftwidth', or 'tabstop' when it is 0, as Vim's get_sw_value */
    int shiftwidth() {
        int sw = number("shiftwidth");
        return sw == 0 ? number("tabstop") : sw;
    }

    /** @return whether a comma-separated option lists {@code item}, such as 'clipboard' holding {@code unnamed} */
    boolean has(String name, String item) { return List.of(string(name).split(",")).contains(item); }

    Keywords keywords() {
        String spec = string("iskeyword");
        if (!spec.equals(keywordSpec)) {
            keywords = Keywords.parse(spec);
            keywordSpec = spec;
        }
        return keywords;
    }

    Object get(String name) {
        Def def = def(name).orElseThrow(() -> new IllegalArgumentException("E518: Unknown option: " + name));
        if (global != null && def.local() && values.containsKey(def.name())) return values.get(def.name());
        Options root = global != null ? global : this;
        return root.values.getOrDefault(def.name(), def.fallback());
    }

    /** Sets a value here and, for a local instance, globally too (as {@code :set}). */
    void set(String name, Object value) {
        Def def = def(name).orElseThrow(() -> new IllegalArgumentException("E518: Unknown option: " + name));
        Object checked = check(def, value);
        if (global != null) {
            global.put(def, checked);
            if (def.local()) put(def, checked);
        } else put(def, checked);
    }

    /** Sets a value in this local instance only (as {@code :setlocal}); a global option is set globally. */
    void setLocal(String name, Object value) {
        Def def = def(name).orElseThrow(() -> new IllegalArgumentException("E518: Unknown option: " + name));
        Object checked = check(def, value);
        if (global != null && !def.local()) global.put(def, checked);
        else put(def, checked);
    }

    /** Which values {@code :set}, {@code :setlocal} and {@code :setglobal} read and write (Vim's opt_flags). */
    enum Scope { BOTH, LOCAL, GLOBAL }

    /** A comma-separated list (P_COMMA), whose items may not repeat (P_NODUP), or a list of one-letter flags (P_FLAGLIST). */
    private static final int COMMA = 1, ONECOMMA = 2, NODUP = 4, FLAGLIST = 8;

    private static int flags(String name) {
        return switch (name) {
            case "backspace", "clipboard", "comments", "foldopen", "matchpairs", "nrformats" -> COMMA | ONECOMMA | NODUP;
            case "iskeyword" -> COMMA | NODUP;
            case "formatoptions" -> FLAGLIST;
            case "whichwrap" -> COMMA | ONECOMMA | FLAGLIST;
            default -> 0;
        };
    }

    /**
     * Applies {@code :set} arguments as {@link #set(String, Scope, int, List)} does, dropping what they show.
     *
     * @throws IllegalArgumentException with Vim's message for an unknown option or a bad value
     */
    void apply(String arguments) { set(arguments, Scope.BOTH, 80, new ArrayList<>()); }

    /**
     * do_set: {@code :set}, {@code :setlocal} or {@code :setglobal} with its arguments. Each argument is
     * {@code name}, {@code noname}, {@code invname}, {@code name!}, {@code name&}, {@code name<},
     * {@code name=value}, {@code name:value}, {@code name+=value}, {@code name-=value}, {@code name^=value} or
     * {@code name?}; a number or string option without a value is shown. {@code all} shows every option,
     * {@code all&} resets them, and no argument shows those that differ from their default.
     *
     * @param columns the window's width, for the columns of a listing
     * @param shown receives the lines shown
     * @throws IllegalArgumentException with Vim's message at the first bad argument, after the earlier ones took effect
     */
    void set(String arguments, Scope scope, int columns, List<String> shown) {
        String arg = arguments;
        int i = skipWhite(arg, 0);
        if (i >= arg.length()) {
            show(false, scope, columns, shown);
            return;
        }
        while (i < arg.length()) {
            if (arg.startsWith("all", i) && !(i + 3 < arg.length() && isAlpha(arg.charAt(i + 3)))) {
                i += 3;
                if (i < arg.length() && arg.charAt(i) == '&') {
                    i++;
                    for (Def def : DEFS) setScoped(def, def.fallback(), scope);
                } else show(true, scope, columns, shown);
            } else {
                int start = i;
                String error = null;
                try {
                    i = one(arg, i, scope, shown);
                } catch (IllegalArgumentException bad) {
                    error = bad.getMessage();
                }
                // Advance to the next argument: skip to white space (over backslashes), then one "=val" after it.
                int next = start;
                for (int n = 0; n < 2; n++) {
                    while (next < arg.length() && !Character.isWhitespace(arg.charAt(next))) {
                        if (arg.charAt(next++) == '\\' && next < arg.length()) next++;
                    }
                    next = skipWhite(arg, next);
                    if (next >= arg.length() || arg.charAt(next) != '=') break;
                }
                if (error != null) throw new IllegalArgumentException(error + ": " + arg.substring(start, next));
                i = Math.max(i, next);
            }
            i = skipWhite(arg, i);
        }
    }

    /** do_set_option: one argument from {@code i}; returns where it ends. */
    private int one(String arg, int i, Scope scope, List<String> shown) {
        char prefix = 0;
        if (arg.startsWith("no", i) && !arg.startsWith("novice", i)) {
            prefix = 'n';
            i += 2;
        } else if (arg.startsWith("inv", i)) {
            prefix = 'i';
            i += 3;
        }
        int len = 0;
        while (i + len < arg.length() && (isAlnum(arg.charAt(i + len)) || arg.charAt(i + len) == '_')) len++;
        Def def = def(arg.substring(i, i + len)).orElse(null);
        char afterchar = i + len < arg.length() ? arg.charAt(i + len) : 0;
        while (i + len < arg.length() && isWhite(arg.charAt(i + len))) len++;
        char op = 0;
        if (i + len + 1 < arg.length() && "+^-".indexOf(arg.charAt(i + len)) >= 0 && arg.charAt(i + len + 1) == '=') {
            op = arg.charAt(i + len);
            len++;
        }
        char nextchar = i + len < arg.length() ? arg.charAt(i + len) : 0;
        if (def == null) throw new IllegalArgumentException("E518: Unknown option");
        if ("?=:!&<".indexOf(nextchar) >= 0 && nextchar != 0) {
            i += len;
            if (nextchar == '&' && arg.startsWith("vi", i + 1)) i += arg.startsWith("vim", i + 1) ? 3 : 2;
            if ("?!&<".indexOf(nextchar) >= 0 && i + 1 < arg.length() && !isWhite(arg.charAt(i + 1)))
                throw new IllegalArgumentException("E488: Trailing characters");
        }
        if (nextchar == '?' || prefix == 0 && "=:&<".indexOf(nextchar) < 0 && def.type() != Type.BOOLEAN) {
            shown.add(showOne(def, scope));
            if (nextchar != '?' && nextchar != 0 && !isWhite(afterchar)) throw new IllegalArgumentException("E488: Trailing characters");
            // After "opt?" the index is already on the "?".
            return nextchar == '?' ? i + 1 : i + len;
        }
        Object old = value(def, scope);
        if (def.type() == Type.BOOLEAN) {
            if (nextchar == '=' || nextchar == ':') throw new IllegalArgumentException("E474: Invalid argument");
            boolean value;
            if (nextchar == '!') value = !(Boolean) old;
            else if (nextchar == '&') value = (Boolean) def.fallback();
            else if (nextchar == '<') value = (Boolean) global().get(def.name());
            else {
                if (nextchar != 0 && !isWhite(afterchar)) throw new IllegalArgumentException("E488: Trailing characters");
                value = prefix == 'i' ? !(Boolean) old : prefix != 'n';
            }
            setScoped(def, value, scope);
            return nextchar == 0 ? i + len : i + 1;
        }
        if ("=:&<".indexOf(nextchar) < 0 || nextchar == 0 || prefix != 0) throw new IllegalArgumentException("E474: Invalid argument");
        int p = i + 1;
        if (def.type() == Type.NUMBER) {
            int value;
            if (nextchar == '&') value = (Integer) def.fallback();
            else if (nextchar == '<') value = (Integer) global().get(def.name());
            else {
                int end = p;
                while (end < arg.length() && !isWhite(arg.charAt(end))) end++;
                Long parsed = parseNumber(arg.substring(p, end));
                if (parsed == null) throw new IllegalArgumentException("E521: Number required after =");
                long v = parsed, current = (Integer) old;
                v = switch (op) { case '+' -> current + v; case '^' -> current * v; case '-' -> current - v; default -> v; };
                value = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, v));
                p = end;
            }
            setScoped(def, checkNumber(def, value), scope);
            return p;
        }
        String value;
        if (nextchar == '&') value = (String) def.fallback();
        else if (nextchar == '<') value = (String) global().get(def.name());
        else {
            StringBuilder copied = new StringBuilder();
            while (p < arg.length() && !isWhite(arg.charAt(p))) {
                if (arg.charAt(p) == '\\' && p + 1 < arg.length()) p++;
                copied.append(arg.charAt(p++));
            }
            value = combine((String) old, copied.toString(), op, flags(def.name()));
        }
        setScoped(def, checkString(def, value), scope);
        return p;
    }

    /** stropt_get_newval: adding, prepending or removing an item, as the option's list flags say. */
    private static String combine(String origval, String newval, char op, int flags) {
        if (op == 0) return (flags & FLAGLIST) != 0 ? dedupFlags(newval, flags) : newval;
        int s = -1;
        if (op == '-' || (flags & NODUP) != 0) {
            s = findDup(origval, newval, flags);
            if ((op == '+' || op == '^') && s >= 0) return origval;
            if (s < 0) s = origval.length();
        }
        String result;
        if (op == '+' || op == '^') {
            boolean comma = (flags & COMMA) != 0 && !origval.isEmpty() && !newval.isEmpty();
            if (op == '+') {
                String o = origval;
                if (comma && o.length() > 1 && (flags & ONECOMMA) != 0 && o.endsWith(",") && o.charAt(o.length() - 2) != '\\')
                    o = o.substring(0, o.length() - 1);
                result = o + (comma ? "," : "") + newval;
            } else result = newval + (comma ? "," : "") + origval;
        } else {
            result = origval;
            if (s < origval.length()) {
                int from = s, len = newval.length();
                if ((flags & COMMA) != 0) {
                    if (from == 0) {
                        if (len < origval.length() && origval.charAt(len) == ',') len++;
                    } else {
                        from--;
                        len++;
                    }
                }
                result = origval.substring(0, from) + origval.substring(Math.min(origval.length(), from + len));
            }
        }
        return (flags & FLAGLIST) != 0 ? dedupFlags(result, flags) : result;
    }

    /** find_dup_item: where {@code item} is in the list, or -1. */
    private static int findDup(String origval, String item, int flags) {
        int bs = 0;
        boolean comma = (flags & COMMA) != 0;
        for (int s = 0; s < origval.length(); s++) {
            if ((!comma || s == 0 || origval.charAt(s - 1) == ',' && (bs & 1) == 0) && origval.startsWith(item, s)
                && (!comma || s + item.length() == origval.length() || origval.charAt(s + item.length()) == ','))
                return s;
            if (s > 1 && origval.charAt(s - 1) == '\\' && origval.charAt(s - 2) != ',' || s == 1 && origval.charAt(0) == '\\') bs++;
            else bs = 0;
        }
        return -1;
    }

    /** stropt_remove_dupflags: a flag that appears again later goes. */
    private static String dedupFlags(String value, int flags) {
        StringBuilder s = new StringBuilder(value);
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if ((flags & ONECOMMA) != 0) {
                if (c != ',' && i + 1 < s.length() && s.charAt(i + 1) == ',' && s.indexOf(String.valueOf(c), i + 2) >= 0) {
                    s.delete(i, i + 2);
                    continue;
                }
            } else if (((flags & COMMA) == 0 || c != ',') && s.indexOf(String.valueOf(c), i + 1) >= 0) {
                s.deleteCharAt(i);
                continue;
            }
            i++;
        }
        return s.toString();
    }

    /** vim_str2nr with STR2NR_ALL: decimal, {@code 0x} hex, {@code 0b} binary, {@code 0o} or leading-zero octal. */
    private static Long parseNumber(String text) {
        String t = text;
        boolean negative = t.startsWith("-");
        if (negative) t = t.substring(1);
        if (t.isEmpty() || !Character.isDigit(t.charAt(0))) return null;
        int radix = 10;
        String digits = t;
        if (t.length() > 2 && t.charAt(0) == '0' && "xX".indexOf(t.charAt(1)) >= 0) { radix = 16; digits = t.substring(2); }
        else if (t.length() > 2 && t.charAt(0) == '0' && "bB".indexOf(t.charAt(1)) >= 0) { radix = 2; digits = t.substring(2); }
        else if (t.length() > 2 && t.charAt(0) == '0' && "oO".indexOf(t.charAt(1)) >= 0) { radix = 8; digits = t.substring(2); }
        else if (t.length() > 1 && t.charAt(0) == '0' && t.chars().allMatch(ch -> ch >= '0' && ch <= '7')) radix = 8;
        try {
            long v = Long.parseLong(digits, radix);
            return negative ? -v : v;
        } catch (NumberFormatException bad) {
            return null;
        }
    }

    /** @return the value {@code :setlocal} or {@code :setglobal} reads: the local one, the global one, or the effective one */
    private Object value(Def def, Scope scope) {
        if (scope == Scope.GLOBAL) return global().get(def.name());
        return get(def.name());
    }

    private Options global() { return global != null ? global : this; }

    /** Sets a value as {@code :set} (both), {@code :setlocal} (this editor's) or {@code :setglobal} (the global one). */
    private void setScoped(Def def, Object value, Scope scope) {
        switch (scope) {
            case BOTH -> set(def.name(), value);
            case LOCAL -> setLocal(def.name(), value);
            case GLOBAL -> {
                // The editor keeps its own value of a local option (each Vim window and buffer has a copy).
                if (global != null && def.local() && !values.containsKey(def.name())) values.put(def.name(), get(def.name()));
                global().put(def, check(def, value));
            }
        }
    }

    /** The number options Vim lets go below zero ('ttimeoutlen' -1 is "use 'timeoutlen'", 'undolevels' -1 no undo). */
    private static final List<String> NEGATIVE = List.of("laststatus", "maxmapdepth", "ttimeoutlen", "undolevels");

    private static int checkNumber(Def def, int value) {
        if (value < 0 && !NEGATIVE.contains(def.name()) || def.name().equals("tabstop") && value == 0)
            throw new IllegalArgumentException("E487: Argument must be positive");
        return value;
    }

    /** did_set_string_option's checks for the string options Jasper knows. */
    private static String checkString(Def def, String value) {
        switch (def.name()) {
            case "iskeyword" -> {
                try { Keywords.parse(value); }
                catch (IllegalArgumentException bad) { throw new IllegalArgumentException("E474: Invalid argument"); }
            }
            case "formatoptions" -> flagList(value, "tcro/q2vlb1mMBn,]:wjp");
            case "whichwrap" -> flagList(value, "bshl<>[],~");
            case "backspace" -> {
                if (!(value.length() == 1 && value.charAt(0) >= '0' && value.charAt(0) <= '3')) items(value, List.of("indent", "eol", "start", "nostop"));
            }
            case "clipboard" -> items(value, List.of("unnamed", "unnamedplus", "autoselect", "autoselectplus", "autoselectml", "html", "exclude"));
            case "foldopen" -> items(value, List.of("all", "block", "hor", "mark", "percent", "quickfix", "search", "tag", "insert", "undo", "jump"));
            case "nrformats" -> items(value, List.of("alpha", "octal", "hex", "bin", "unsigned", "blank"));
            case "matchpairs" -> {
                for (String pair : value.split(",", -1)) {
                    if (value.isEmpty()) break;
                    if (pair.codePointCount(0, pair.length()) != 3 || pair.charAt(pair.offsetByCodePoints(0, 1)) != ':')
                        throw new IllegalArgumentException("E474: Invalid argument");
                }
            }
            default -> { }
        }
        return value;
    }

    private static void flagList(String value, String allowed) {
        for (int i = 0; i < value.length(); i++)
            if (allowed.indexOf(value.charAt(i)) < 0) throw new IllegalArgumentException("E539: Illegal character <" + value.charAt(i) + ">");
    }

    private static void items(String value, List<String> allowed) {
        if (value.isEmpty()) return;
        for (String item : value.split(",", -1))
            if (!allowed.contains(item) && !item.startsWith("exclude:")) throw new IllegalArgumentException("E474: Invalid argument");
    }

    /** showoneopt: {@code noname}, {@code   name} or {@code   name=value}. */
    private String showOne(Def def, Scope scope) {
        Object value = value(def, scope);
        if (def.type() == Type.BOOLEAN) return ((Boolean) value ? "  " : "no") + def.name();
        return "  " + def.name() + "=" + value;
    }

    /**
     * showoptions: the options that differ from their default ({@code all}: every option), the short ones in
     * columns of 20 characters and then the long ones one to a line.
     */
    private void show(boolean all, Scope scope, int columns, List<String> shown) {
        shown.add(switch (scope) {
            case GLOBAL -> "--- Global option values ---";
            case LOCAL -> "--- Local option values ---";
            case BOTH -> "--- Options ---";
        });
        List<Def> items = new ArrayList<>();
        for (Def def : DEFS) if (all || !value(def, scope).equals(def.fallback())) items.add(def);
        List<String> shortItems = new ArrayList<>(), longItems = new ArrayList<>();
        for (Def def : items) {
            String text = showOne(def, scope);
            int len = def.type() == Type.BOOLEAN ? 1 : def.name().length() + String.valueOf(value(def, scope)).length() + 1;
            (len <= 17 ? shortItems : longItems).add(text);
        }
        int cols = Math.max(1, columns / 20);
        int rows = (shortItems.size() + cols - 1) / cols;
        for (int row = 0; row < rows; row++) {
            StringBuilder line = new StringBuilder();
            for (int i = row; i < shortItems.size(); i += rows) {
                int col = (i - row) / rows * 20;
                while (line.length() < col) line.append(' ');
                line.append(shortItems.get(i));
            }
            shown.add(line.toString());
        }
        shown.addAll(longItems);
    }

    private static int skipWhite(String s, int i) {
        while (i < s.length() && isWhite(s.charAt(i))) i++;
        return i;
    }

    private static boolean isWhite(char c) { return c == ' ' || c == '\t'; }

    private static boolean isAlpha(char c) { return c < 128 && Character.isLetter(c); }

    private static boolean isAlnum(char c) { return c < 128 && Character.isLetterOrDigit(c); }

    static Optional<Def> def(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return DEFS.stream().filter(def -> def.name().equals(lower) || def.abbreviation().equals(lower)).findFirst();
    }

    /** Reload vimrc: every global value goes back to its default (spec 7.1); local instances keep theirs. */
    void resetGlobal() {
        if (global != null) throw new IllegalStateException("Only the global options are reset");
        if (values.isEmpty()) return;
        values.clear();
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    private void put(Def def, Object value) {
        Object old = values.put(def.name(), value);
        if (!value.equals(old)) for (Runnable listener : List.copyOf(listeners)) listener.run();
    }



    private static Object check(Def def, Object value) {
        return switch (def.type()) {
            case BOOLEAN -> (Boolean) value;
            case NUMBER -> {
                int number = (Integer) value;
                if (number < 0 && !NEGATIVE.contains(def.name()) || def.name().equals("tabstop") && number == 0)
                    throw new IllegalArgumentException("E487: Argument must be positive: " + def.name() + "=" + number);
                yield number;
            }
            case STRING -> {
                String text = (String) value;
                if (def.name().equals("iskeyword")) Keywords.parse(text);
                if (def.name().equals("fileformat") && !List.of("unix", "dos", "mac").contains(text))
                    throw new IllegalArgumentException("E474: Invalid argument");
                yield text;
            }
        };
    }

}
