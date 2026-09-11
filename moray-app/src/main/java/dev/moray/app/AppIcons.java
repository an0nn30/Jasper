package dev.moray.app;

import com.formdev.flatlaf.extras.FlatSVGIcon;

/** Bundled Tabler icons; no network access is needed to render chrome. */
final class AppIcons {
    private AppIcons() {}
    static FlatSVGIcon icon(String name) {
        return new FlatSVGIcon("dev/moray/app/icons/" + name + ".svg", 20, 20);
    }
}
