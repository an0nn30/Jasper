package dev.jasper.app.workspace;

import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.FontConfig;
import dev.jasper.terminal.rendering.FontSet;
import java.awt.Dimension;
import java.awt.Rectangle;

/** Initial terminal area and native frame constraints; reload never uses this sizing path. */
final class InitialWindowSize {
    private InitialWindowSize() {}

    static Dimension terminalArea(ConfigSnapshot snapshot) {
        dev.jasper.app.platform.BundledFonts.register();
        FontConfig f = snapshot.font();
        FontSet fonts = new FontSet(f.family(), com.formdev.flatlaf.util.UIScale.scale(f.size()), f.fallback(), f.ligatures(), f.lineHeight());
        int panePadding = com.formdev.flatlaf.util.UIScale.scale(TerminalPane.PADDING) * 2;
        return new Dimension(snapshot.columns() * fonts.cellWidth() + panePadding,
            snapshot.lines() * fonts.cellHeight() + panePadding);
    }

    static Dimension fit(Dimension packed, Dimension minimum, Rectangle usableBounds) {
        return new Dimension(Math.min(usableBounds.width, Math.max(minimum.width, packed.width)),
            Math.min(usableBounds.height, Math.max(minimum.height, packed.height)));
    }
}
