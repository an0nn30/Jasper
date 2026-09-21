package dev.jasper.terminal.internal.text;

import dev.jasper.terminal.config.CursorStyle;

public record CursorRequest(CursorStyle style, boolean blink) {
    public static CursorStyle effective(CursorRequest requested, CursorStyle configured) {
        return requested == null ? configured : requested.style();
    }
    public static boolean effectiveBlink(CursorRequest requested, boolean configured) {
        return requested == null ? configured : requested.blink();
    }
}
