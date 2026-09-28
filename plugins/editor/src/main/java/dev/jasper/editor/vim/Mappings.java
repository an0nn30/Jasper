package dev.jasper.editor.vim;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The key mappings (spec 7.2), global as Vim's maphash: each has the modes it applies in, a left-hand side of keys
 * and a right-hand side of keys, as Vim's mapblock_T in map.c. The typeahead ({@link Typeahead}) looks mappings up
 * here; {@link ExMaps} defines, lists and removes them.
 */
final class Mappings {
    /** Vim's MODE_* bits for mappings: Normal, Visual, Select (only through {@code :vmap} and {@code :map}), Operator-pending, Insert, Command-line. */
    static final int NORMAL = 1, VISUAL = 2, SELECT = 4, OP_PENDING = 8, INSERT = 16, CMDLINE = 32;
    /** The modes of {@code :map} and of {@code :map!}. */
    static final int MAP = NORMAL | VISUAL | SELECT | OP_PENDING, MAP_BANG = INSERT | CMDLINE;

    /** One mapping; {@link ExMaps} changes its modes and right-hand side as Vim's do_map does. */
    static final class Mapping {
        int modes;
        final List<KeyInput> lhs;
        List<KeyInput> rhs;
        boolean noremap, nowait;
        /** Newer mappings are tried and listed first (Vim adds each in front of its hash list). */
        final long order;

        Mapping(int modes, List<KeyInput> lhs, List<KeyInput> rhs, boolean noremap, boolean nowait, long order) {
            this.modes = modes;
            this.lhs = List.copyOf(lhs);
            this.rhs = List.copyOf(rhs);
            this.noremap = noremap;
            this.nowait = nowait;
            this.order = order;
        }
    }

    private final List<Mapping> all = new ArrayList<>();
    private long next;

    /** @return every mapping, in Vim's listing order: by the hash of the first key, newest first */
    List<Mapping> all() {
        List<Mapping> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparingInt(Mappings::hash).thenComparing(Comparator.comparingLong((Mapping m) -> m.order).reversed()));
        return sorted;
    }

    /**
     * @param state the mode being typed in, one of the mode bits
     * @param first the first key of the typeahead
     * @return the mappings for that mode that start with that key, newest first (the order handle_mapping tries them)
     */
    List<Mapping> starting(int state, KeyInput first) {
        List<Mapping> found = new ArrayList<>();
        for (Mapping m : all) if ((m.modes & state) != 0 && m.lhs.getFirst().equals(first)) found.add(m);
        found.sort(Comparator.comparingLong((Mapping m) -> m.order).reversed());
        return found;
    }

    boolean isEmpty() { return all.isEmpty(); }

    /**
     * @param modes the mode bits it applies in
     * @param lhs its left-hand side
     * @param rhs its right-hand side, empty for {@code <Nop>}
     * @param noremap whether the right-hand side is not mapped again
     * @param nowait whether it runs without waiting for a longer mapping
     * @return the new mapping, the newest
     */
    Mapping add(int modes, List<KeyInput> lhs, List<KeyInput> rhs, boolean noremap, boolean nowait) {
        Mapping m = new Mapping(modes, lhs, rhs, noremap, nowait, next++);
        all.add(m);
        return m;
    }

    void remove(Mapping m) { all.remove(m); }

    /** map_clear_mode: the modes go from every mapping; one left with none goes. */
    void clear(int modes) {
        for (Mapping m : List.copyOf(all)) {
            m.modes &= ~modes;
            if (m.modes == 0) all.remove(m);
        }
    }

    /** MAP_HASH: Normal-ish mappings by their first byte, Insert and Command-line ones by it with the top bit flipped. */
    private static int hash(Mapping m) {
        int c = firstByte(m.lhs.getFirst());
        return (m.modes & MAP) != 0 ? c : c ^ 0x80;
    }

    /**
     * The first byte Vim stores for a key: an ASCII character or control character is itself, other characters their
     * first UTF-8 byte, and a special key K_SPECIAL (0x80). CTRL with a letter written as {@code <C-a>} is listed
     * from its unsimplified form, which starts with K_SPECIAL.
     */
    static int firstByte(KeyInput key) {
        if (key.modifiers() == 0 && key.isChar()) return key.key().getBytes(StandardCharsets.UTF_8)[0] & 0xff;
        if (key.modifiers() == 0) {
            switch (key.key()) {
                case "Esc" -> { return 0x1b; }
                case "CR" -> { return 0x0d; }
                case "Tab" -> { return 0x09; }
                default -> { }
            }
        }
        return 0x80;
    }
}
