package dev.jasper.terminal.config;

/** Which macOS Option key sends Meta (ESC-prefixed keys) instead of composing characters. */
public enum OptionAsMeta {
    /** Left Option sends Meta. */
    LEFT,
    /** Right Option sends Meta. */
    RIGHT,
    /** Either Option sends Meta. */
    BOTH,
    /** Both Option keys retain composition behavior. */
    NONE
}
