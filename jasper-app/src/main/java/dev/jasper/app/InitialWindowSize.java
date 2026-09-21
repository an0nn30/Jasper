package dev.jasper.app;

import dev.jasper.terminal.rendering.FontSet;
import java.awt.Dimension;
import java.awt.Rectangle;

/** Initial terminal area and native frame constraints; reload never uses this sizing path. */
final class InitialWindowSize {
    private InitialWindowSize() {}

    static Dimension terminalArea(ConfigSnapshot snapshot) {
        FontConfig f = snapshot.font();
        FontSet fonts = new FontSet(f.family(), f.size(), f.fallback(), f.ligatures(), f.lineHeight());
        int panePadding = TerminalPane.PADDING * 2;
        return new Dimension(snapshot.columns() * fonts.cellWidth() + panePadding,
            snapshot.lines() * fonts.cellHeight() + panePadding);
    }

    static Dimension fit(Dimension packed, Dimension minimum, Rectangle usableBounds) {
        return new Dimension(Math.min(usableBounds.width, Math.max(minimum.width, packed.width)),
            Math.min(usableBounds.height, Math.max(minimum.height, packed.height)));
    }
}
