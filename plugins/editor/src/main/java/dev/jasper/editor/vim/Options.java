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
 * ({@link EditorDefaults}) and 'showcmd' and 'incsearch', which are on (spec 3.8; 'hlsearch' stays off until plan V2
 * brings {@code :noh}).
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
        new Def("clipboard", "cb", Type.STRING, "", false),
        new Def("comments", "com", Type.STRING, "s1:/*,mb:*,ex:*/,://,b:#,:%,:XCOMM,n:>,fb:-", true),
        new Def("expandtab", "et", Type.BOOLEAN, false, true),
        new Def("formatoptions", "fo", Type.STRING, "tcq", true),
        new Def("hlsearch", "hls", Type.BOOLEAN, false, false),
        new Def("ignorecase", "ic", Type.BOOLEAN, false, false),
        new Def("incsearch", "is", Type.BOOLEAN, true, false),
        new Def("iskeyword", "isk", Type.STRING, "@,48-57,_,192-255", true),
        new Def("joinspaces", "js", Type.BOOLEAN, true, false),
        new Def("matchpairs", "mps", Type.STRING, "(:),{:},[:]", true),
        new Def("nrformats", "nf", Type.STRING, "bin,octal,hex", true),
        new Def("report", "report", Type.NUMBER, 2, false),
        new Def("shiftround", "sr", Type.BOOLEAN, false, false),
        new Def("shiftwidth", "sw", Type.NUMBER, 8, true),
        new Def("showcmd", "sc", Type.BOOLEAN, true, false),
        new Def("showmode", "smd", Type.BOOLEAN, true, false),
        new Def("smartcase", "scs", Type.BOOLEAN, false, false),
        new Def("softtabstop", "sts", Type.NUMBER, 0, true),
        new Def("startofline", "sol", Type.BOOLEAN, true, false),
        new Def("tabstop", "ts", Type.NUMBER, 8, true),
        new Def("textwidth", "tw", Type.NUMBER, 0, true),
        new Def("tildeop", "top", Type.BOOLEAN, false, false),
        new Def("whichwrap", "ww", Type.STRING, "b,s", false),
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

    /**
     * Applies {@code :set} arguments: {@code name}, {@code noname}, {@code invname}, {@code name!}, {@code name&},
     * {@code name=value} and {@code name:value}, separated by spaces.
     *
     * @throws IllegalArgumentException with Vim's message for an unknown option or a bad value
     */
    void apply(String arguments) {
        for (String argument : arguments.trim().split("\\s+")) {
            if (argument.isEmpty()) continue;
            int equals = indexOfAny(argument, "=:");
            if (equals > 0) {
                char how = "+-^".indexOf(argument.charAt(equals - 1)) >= 0 && argument.charAt(equals) == '=' ? argument.charAt(equals - 1) : '=';
                String name = argument.substring(0, how == '=' ? equals : equals - 1);
                String text = argument.substring(equals + 1);
                Def def = required(name);
                switch (def.type()) {
                    case NUMBER -> {
                        int number;
                        try { number = Integer.parseInt(text); }
                        catch (NumberFormatException bad) { throw new IllegalArgumentException("E521: Number required after =: " + argument); }
                        int old = number(def.name());
                        set(def.name(), switch (how) { case '+' -> old + number; case '-' -> old - number; case '^' -> old * number; default -> number; });
                    }
                    case STRING -> set(def.name(), combine(string(def.name()), text, how));
                    case BOOLEAN -> throw new IllegalArgumentException("E474: Invalid argument: " + argument);
                }
            } else if (argument.endsWith("&")) {
                Def def = required(argument.substring(0, argument.length() - 1));
                set(def.name(), def.fallback());
            } else if (argument.endsWith("!")) {
                Def def = required(argument.substring(0, argument.length() - 1));
                booleanOnly(def, argument);
                set(def.name(), !flag(def.name()));
            } else if (def(argument).isPresent()) {
                Def def = def(argument).get();
                booleanOnly(def, argument);
                set(def.name(), true);
            } else if (argument.startsWith("no") && def(argument.substring(2)).isPresent()) {
                Def def = def(argument.substring(2)).get();
                booleanOnly(def, argument);
                set(def.name(), false);
            } else if (argument.startsWith("inv") && def(argument.substring(3)).isPresent()) {
                Def def = def(argument.substring(3)).get();
                booleanOnly(def, argument);
                set(def.name(), !flag(def.name()));
            } else throw new IllegalArgumentException("E518: Unknown option: " + argument);
        }
    }

    /** {@code :set opt+=}, {@code -=} and {@code ^=} on a comma-separated option (option.c's add, remove and prepend). */
    private static String combine(String old, String item, char how) {
        List<String> items = new ArrayList<>(old.isEmpty() ? List.of() : List.of(old.split(",", -1)));
        switch (how) {
            case '+' -> {
                if (items.contains(item)) return old;
                items.add(item);
            }
            case '^' -> {
                if (items.contains(item)) return old;
                items.addFirst(item);
            }
            case '-' -> items.remove(item);
            default -> { return item; }
        }
        return String.join(",", items);
    }

    static Optional<Def> def(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return DEFS.stream().filter(def -> def.name().equals(lower) || def.abbreviation().equals(lower)).findFirst();
    }

    private void put(Def def, Object value) {
        Object old = values.put(def.name(), value);
        if (!value.equals(old)) for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    private static Def required(String name) {
        return def(name).orElseThrow(() -> new IllegalArgumentException("E518: Unknown option: " + name));
    }

    private static void booleanOnly(Def def, String argument) {
        if (def.type() != Type.BOOLEAN) throw new IllegalArgumentException("E474: Invalid argument: " + argument);
    }

    private static Object check(Def def, Object value) {
        return switch (def.type()) {
            case BOOLEAN -> (Boolean) value;
            case NUMBER -> {
                int number = (Integer) value;
                if (number < 0 || def.name().equals("tabstop") && number == 0) throw new IllegalArgumentException("E487: Argument must be positive: " + def.name() + "=" + number);
                yield number;
            }
            case STRING -> {
                String text = (String) value;
                if (def.name().equals("iskeyword")) Keywords.parse(text);
                yield text;
            }
        };
    }

    private static int indexOfAny(String text, String characters) {
        for (int i = 0; i < text.length(); i++) if (characters.indexOf(text.charAt(i)) >= 0) return i;
        return -1;
    }
}
