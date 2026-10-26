package dev.jasper.app.settingsui;

import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.Option;

/** Test-only access to the mounted Appearance composite for application-owner integration tests. */
public final class DesktopPageProbe {
    private DesktopPageProbe() { }
    public static Option.Palette preview(SchemaPage page, boolean dark) {
        var tiles = (Nodes.Choice) PageKit.node(page, "ui.appearance:policy");
        return tiles.options().get(dark ? 0 : 1).preview().orElseThrow().primary();
    }
    public static String status(SchemaPage page) {
        return ((Nodes.Text) PageKit.node(page, "ui.appearance:desktop-status")).text();
    }
}
