package dev.jasper.app.plugins;

import dev.jasper.sdk.ui.WorkspacePalette;
import java.awt.Color;
import java.awt.Component;

/** JDK-only test boundary for verifying SDK zone lookup without leaking SDK types into workspace. */
public final class WorkspacePaletteTestBridge {
    private WorkspacePaletteTestBridge() {}
    public static Color text(Component component) { return dev.jasper.app.platform.ThemeZone.color(component, dev.jasper.ui.theme.SemanticColor.TEXT_PRIMARY); }
}
