package dev.jasper.sdk.palette;

import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.Objects;
import java.util.Optional;

/**
 * What a scope is told about each query.
 *
 * @param window     the window whose palette is open
 * @param target     the pane the palette was opened from, when there is one; the handle is bound to
 *                   the scope's plugin, so pasting into it needs {@code terminal.inject}
 * @param maxResults how many rows the palette will show, 1 to 200; the All tab asks for one more than
 *                   it shows, to learn whether to offer a "More in Scope..." row, so a scope should
 *                   return up to this many rows and not assume exactly that many are displayed
 * @param macOs      whether to show macOS shortcut glyphs and wording
 */
public record PaletteQuery(WindowHandle window, Optional<PaneHandle> target, int maxResults, boolean macOs) {
    /** Validates the window and the bound. */
    public PaletteQuery {
        if (window == null) throw new IllegalArgumentException("A query needs a window");
        Objects.requireNonNull(target, "target");
        if (maxResults < 1 || maxResults > PaletteResults.MAX_ROWS) throw new IllegalArgumentException("maxResults must be 1 to " + PaletteResults.MAX_ROWS);
    }
}
