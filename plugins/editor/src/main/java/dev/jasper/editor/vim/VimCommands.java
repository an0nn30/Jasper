package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every key Vim mode knows (spec 11.1): the parser looks keys up here, the coverage guard checks each entry has a
 * corpus case, and the reference document is generated from it. Keys are Vim notation, as {@link KeyInput#format}
 * writes them.
 */
public final class VimCommands {
    /** What an entry does. */
    public enum Kind { MOTION, OPERATOR, ACTION, OBJECT, INSERT, VISUAL }

    @FunctionalInterface interface Motion { Target move(Context context); }

    @FunctionalInterface interface TextObject { Span select(Context context); }

    @FunctionalInterface interface Operator { void apply(Context context, Span span); }

    @FunctionalInterface interface Action { void run(Context context); }

    @FunctionalInterface interface InsertKey { void run(InsertMode insert, KeyInput key); }

    /**
     * One command.
     *
     * @param kind what it is
     * @param keys its keys in Vim notation
     * @param description what it does, for the reference
     * @param needsChar whether it reads one more key as its argument ({@code f}, {@code r})
     * @param change whether it changes text, so {@code .} repeats it and it is one undo step
     * @param handler a {@link Motion}, {@link TextObject}, {@link Operator}, {@link Action} or {@link InsertKey}
     */
    public record Entry(Kind kind, String keys, String description, boolean needsChar, boolean change, Object handler) {
        /** @return the coverage id, such as {@code motion w} */
        public String id() { return kind.name().toLowerCase(java.util.Locale.ROOT) + " " + keys; }
    }

    private final Map<String, Entry> normal = new LinkedHashMap<>();
    private final Map<String, Entry> objects = new LinkedHashMap<>();
    private final Map<String, Entry> insert = new LinkedHashMap<>();
    private final Map<String, Entry> visual = new LinkedHashMap<>();
    private final Set<String> normalPrefixes = new HashSet<>(), pendingPrefixes = new HashSet<>(), visualPrefixes = new HashSet<>();

    private VimCommands() { }

    /** @return the standard commands */
    public static VimCommands standard() {
        var commands = new VimCommands();
        Motions.register(commands);
        TextObjects.register(commands);
        Operators.register(commands);
        NormalCommands.register(commands);
        InsertMode.register(commands);
        Visual.register(commands);
        Search.register(commands);
        MarkCommands.register(commands);
        Numbers.register(commands);
        Format.register(commands);
        ViewCommands.register(commands);
        Ex.register(commands);
        Macros.register(commands);
        ReplaceWithRegister.register(commands);
        Commentary.register(commands);
        Surround.register(commands);
        Exchange.register(commands);
        ParagraphMotion.register(commands);
        TextobjEntire.register(commands);
        return commands;
    }

    /** @return every entry, in registration order */
    public List<Entry> entries() {
        List<Entry> all = new ArrayList<>(normal.values());
        all.addAll(objects.values());
        all.addAll(visual.values());
        all.addAll(insert.values());
        return Collections.unmodifiableList(all);
    }

    void motion(String keys, String description, Motion motion) { add(normal, new Entry(Kind.MOTION, keys, description, false, false, motion)); }

    /** A motion that reads a command line first ({@code /} and {@code ?}); the line is in {@link Context#line}. */
    void motionWithLine(String keys, String description, Motion motion) {
        motion(keys, description, motion);
        lineCommands.add(keys);
    }

    /** @return whether the entry reads a command line before it runs */
    boolean needsLine(Entry entry) { return entry.kind() == Kind.MOTION && lineCommands.contains(entry.keys()); }

    private final java.util.Set<String> lineCommands = new java.util.HashSet<>();

    void motionWithChar(String keys, String description, Motion motion) { add(normal, new Entry(Kind.MOTION, keys, description, true, false, motion)); }

    void operator(String keys, String description, Operator operator) { add(normal, new Entry(Kind.OPERATOR, keys, description, false, !keys.equals("y"), operator)); }

    void action(String keys, String description, boolean change, Action action) { add(normal, new Entry(Kind.ACTION, keys, description, false, change, action)); }

    void actionWithChar(String keys, String description, boolean change, Action action) { add(normal, new Entry(Kind.ACTION, keys, description, true, change, action)); }

    void object(String keys, String description, TextObject object) { add(objects, new Entry(Kind.OBJECT, keys, description, false, false, object)); }

    void insert(String keys, String description, InsertKey key) { add(insert, new Entry(Kind.INSERT, keys, description, false, false, key)); }

    /** A Visual-mode command; {@code change} commands are repeated by {@code .} on an area of the same size. */
    void visual(String keys, String description, boolean needsChar, boolean change, Action action) {
        add(visual, new Entry(Kind.VISUAL, keys, description, needsChar, change, action));
    }

    /** @return the Normal-mode command with exactly these keys, or null */
    Entry normal(String keys) { return normal.get(keys); }

    /** @return the motion or text object with exactly these keys after an operator, or null */
    Entry pending(String keys) {
        Entry entry = normal.get(keys);
        if (entry != null && entry.kind() == Kind.MOTION) return entry;
        return objects.get(keys);
    }

    /** @return the Insert-mode key, or null */
    Entry insertKey(String keys) { return insert.get(keys); }

    /** @return in Visual mode: a Visual command, else a motion or operator, else a text object; or null */
    Entry visualKey(String keys) {
        Entry entry = visual.get(keys);
        if (entry != null) return entry;
        entry = normal.get(keys);
        if (entry != null && (entry.kind() == Kind.MOTION || entry.kind() == Kind.OPERATOR)) return entry;
        return objects.get(keys);
    }

    /** @return whether more keys could complete a Visual-mode command */
    boolean visualPrefix(String keys) { return visualPrefixes.contains(keys); }

    /** @return whether more keys could complete a command */
    boolean prefix(String keys, boolean pending) { return (pending ? pendingPrefixes : normalPrefixes).contains(keys); }

    private void add(Map<String, Entry> map, Entry entry) {
        if (map.putIfAbsent(entry.keys(), entry) != null) throw new IllegalStateException("Duplicate command " + entry.keys());
        if (entry.kind() == Kind.INSERT) return;
        List<KeyInput> keys = KeyInput.parse(entry.keys());
        for (int n = 1; n < keys.size(); n++) {
            String prefix = KeyInput.format(keys.subList(0, n));
            if (entry.kind() != Kind.OBJECT && entry.kind() != Kind.VISUAL) normalPrefixes.add(prefix);
            if (entry.kind() == Kind.MOTION || entry.kind() == Kind.OBJECT) pendingPrefixes.add(prefix);
            if (entry.kind() != Kind.ACTION) visualPrefixes.add(prefix);
        }
    }
}
