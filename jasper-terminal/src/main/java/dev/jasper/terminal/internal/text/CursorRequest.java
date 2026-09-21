package dev.jasper.terminal.internal.text;

import dev.jasper.terminal.config.CursorStyle;

/**
 * Unsupported program cursor override; null at the call site means use the configured default.
 * @param style program-requested shape
 * @param blink program-requested blinking
 */
public record CursorRequest(CursorStyle style, boolean blink) {
    public static CursorStyle effective(CursorRequest requested, CursorStyle configured) {
        return requested == null ? configured : requested.style();
    }
    public static boolean effectiveBlink(CursorRequest requested, boolean configured) {
        return requested == null ? configured : requested.blink();
    }
}
