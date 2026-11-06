package dev.jasper.sdk.palette;

import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.TextTarget;
import java.util.Objects;
import java.util.Optional;

/**
 * What a scope is told about each query.
 *
 * @param window     the window whose palette is open
 * @param target     the pane the command palette was opened from, or for the Run window that window's current pane,
 *                   read again before each search and run; empty when there is none. The handle is bound to the
 *                   scope's plugin, so pasting into it needs {@code terminal.inject}
 * @param maxResults how many rows the scope may return, 1 to 200; the host drops any beyond it, and the list holds
 *                   every row and scrolls. With the scope chosen it is 200. With no scope chosen the palette shares
 *                   200 rows across the scopes in All and asks each for one more than it has room for, at most 200,
 *                   to learn whether to offer a "More in &lt;label&gt;…" row; finding a recently used row again asks
 *                   for 50
 * @param macOs      whether to show macOS shortcut glyphs and wording
 * @param textTarget (since 0.29.0) with no {@code target}, the content tab the palette was opened from, or for the
 *                   Run window that window's current item, while it takes inserted text
 *                   ({@code ContentTab.setTextInsertion}), read again before each search and run; empty otherwise. The
 *                   handle is bound to the scope's plugin, so inserting into it needs {@code terminal.inject}
 */
public record PaletteQuery(WindowHandle window, Optional<PaneHandle> target, int maxResults, boolean macOs,
                           Optional<TextTarget> textTarget) {
    /** Validates the window and the bound, and that a query with a target pane names no text target. */
    public PaletteQuery {
        if (window == null) throw new IllegalArgumentException("A query needs a window");
        Objects.requireNonNull(target, "target");
        if (maxResults < 1 || maxResults > PaletteResults.MAX_ROWS) throw new IllegalArgumentException("maxResults must be 1 to " + PaletteResults.MAX_ROWS);
        Objects.requireNonNull(textTarget, "textTarget");
        if (target.isPresent() && textTarget.isPresent()) throw new IllegalArgumentException("A query with a target pane has no text target");
    }
}
