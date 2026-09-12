package dev.moray.app;

import dev.moray.terminal.FontSet;
import java.awt.Dimension;
import java.awt.Rectangle;

/** Initial terminal area and native frame constraints; reload never uses this sizing path. */
final class InitialWindowSize {
    private InitialWindowSize() {}

    static Dimension terminalArea(ConfigSnapshot snapshot) {
        FontConfig f = snapshot.font();
        FontSet fonts = new FontSet(f.family(), f.size(), f.fallback(), f.ligatures(), f.lineHeight());
        return new Dimension(snapshot.columns() * fonts.cellWidth() + 48,
            snapshot.lines() * fonts.cellHeight() + 48);
    }

    static Dimension fit(Dimension packed, Dimension minimum, Rectangle usableBounds) {
        return new Dimension(Math.min(usableBounds.width, Math.max(minimum.width, packed.width)),
            Math.min(usableBounds.height, Math.max(minimum.height, packed.height)));
    }
}
