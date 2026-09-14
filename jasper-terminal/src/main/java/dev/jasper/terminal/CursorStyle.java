package dev.jasper.terminal;

import com.jediterm.terminal.CursorShape;

public enum CursorStyle {
    BLOCK, BEAM, UNDERLINE;

    /** The application's DECSCUSR request if there is one, otherwise the configured style. */
    static CursorStyle effective(CursorShape requested, CursorStyle configured) {
        if (requested == null) {
            return configured;
        }
        return switch (requested) {
            case BLINK_BLOCK, STEADY_BLOCK -> BLOCK;
            case BLINK_UNDERLINE, STEADY_UNDERLINE -> UNDERLINE;
            case BLINK_VERTICAL_BAR, STEADY_VERTICAL_BAR -> BEAM;
        };
    }

    static boolean effectiveBlink(CursorShape requested, boolean configured) {
        if (requested == null) {
            return configured;
        }
        return switch (requested) {
            case BLINK_BLOCK, BLINK_UNDERLINE, BLINK_VERTICAL_BAR -> true;
            case STEADY_BLOCK, STEADY_UNDERLINE, STEADY_VERTICAL_BAR -> false;
        };
    }
}
