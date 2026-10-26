package dev.jasper.editor.vim;

import java.util.HashMap;
import java.util.Map;

/**
 * The handler table (spec 2.2): which keys go to Vim and which to the Editor, per mode, as {@code sethandler} sets
 * them (plan V3 reads the lines from the vimrc). A key with no entry follows the default rule.
 */
public final class KeyHandlers {
    /** Who gets a key. */
    public enum Handler { VIM, IDE, DEFAULT }

    /** The mode letters {@code sethandler} accepts; {@code a} is all of them. */
    static final String MODES = "nivxoc";

    private final Map<KeyInput, Map<Character, Handler>> table = new HashMap<>();

    /**
     * @param key a key
     * @param modes mode letters from {@link #MODES}, or {@code a}, joined with {@code -}: {@code n-v}
     * @param handler who gets the key in those modes
     * @throws IllegalArgumentException for an unknown mode letter
     */
    public void set(KeyInput key, String modes, Handler handler) {
        Map<Character, Handler> byMode = table.computeIfAbsent(key, k -> new HashMap<>());
        for (String part : modes.split("-")) {
            if (part.equals("a")) {
                for (char mode : MODES.toCharArray()) byMode.put(mode, handler);
            } else if (part.length() == 1 && MODES.indexOf(part.charAt(0)) >= 0) {
                byMode.put(part.charAt(0), handler);
            } else throw new IllegalArgumentException("Unknown sethandler mode: " + part);
        }
    }

    /**
     * @param key a key
     * @return whether a {@code sethandler} entry names it, for any mode
     */
    public boolean has(KeyInput key) { return table.containsKey(key); }

    /** Forgets every entry (the vimrc is being read again). */
    public void clear() { table.clear(); }

    /**
     * @param key a key
     * @param mode a letter from {@link #MODES}
     * @return who gets it there, or {@link Handler#DEFAULT}
     */
    public Handler handler(KeyInput key, char mode) {
        Map<Character, Handler> byMode = table.get(key);
        return byMode == null ? Handler.DEFAULT : byMode.getOrDefault(mode, Handler.DEFAULT);
    }
}
