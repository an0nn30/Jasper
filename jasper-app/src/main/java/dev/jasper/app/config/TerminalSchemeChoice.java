package dev.jasper.app.config;

/** {@code terminal.scheme}: the terminal panes' colour scheme; {@code MATCH} follows the UI variant. */
public enum TerminalSchemeChoice {
    MATCH("match"), SLATE_STEEL_DARK("slate-steel-dark"), SLATE_STEEL_LIGHT("slate-steel-light"), HIGH_CONTRAST("high-contrast");

    private final String key;
    TerminalSchemeChoice(String key) { this.key = key; }

    /** The value in config.toml, which is also the bundled theme file's scheme key. */
    public String key() { return key; }
}
