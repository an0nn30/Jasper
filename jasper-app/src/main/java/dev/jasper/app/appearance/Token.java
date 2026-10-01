package dev.jasper.app.appearance;

import java.util.Locale;
import java.util.Optional;

/**
 * Jasper's semantic colours (a theme's {@code [tokens]}). Every token is derivable from the seeds;
 * tokens map onto FlatLaf, IntelliJ and {@code Jasper.*} keys.
 */
public enum Token {
    SURFACE, SURFACE_RAISED, SURFACE_SUNKEN, TITLE_BAR, STATUS_BAR, SIDEBAR,
    TEXT, TEXT_MUTED, TEXT_DISABLED, TEXT_ON_ACCENT, LINK,
    BORDER, SEPARATOR, DIVIDER, FOCUS_RING,
    HOVER, PRESSED, SELECTION, SELECTION_TEXT, SELECTION_INACTIVE, TEXT_SELECTION, TEXT_SELECTION_TEXT,
    TAB_BAR, TAB_ACTIVE, TAB_ACTIVE_TEXT, TAB_ACTIVE_BORDER, TAB_HOVER, TAB_UNDERLINE, TAB_UNDERLINE_INACTIVE,
    INPUT_BACKGROUND, BUTTON_BACKGROUND, BUTTON_BORDER, BUTTON_TEXT, BUTTON_DEFAULT, SCROLLBAR_THUMB, SCROLLBAR_TRACK,
    ACCENT, SUCCESS, WARNING, ERROR, ERROR_BACKGROUND,
    EDITOR_BACKGROUND, EDITOR_FOREGROUND, EDITOR_CARET, EDITOR_SELECTION, EDITOR_CURRENT_LINE, EDITOR_GUTTER,
    EDITOR_LINE_NUMBERS;

    /** The TOML key, for example {@code tab_active}. */
    public String key() { return name().toLowerCase(Locale.ROOT); }

    /** The token with this TOML key. */
    public static Optional<Token> of(String key) {
        for (Token token : values()) if (token.key().equals(key)) return Optional.of(token);
        return Optional.empty();
    }
}
