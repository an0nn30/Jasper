package dev.jasper.app.platform;

import javax.swing.ImageIcon;

/** Captured GTK artwork for a semantic name; ImageIcon lets the LAF derive its disabled form. */
final class DesktopSkinIcon extends ImageIcon {
    private final String name;
    DesktopSkinIcon(String name) {
        super(DesktopIcons.themed(FreedesktopNames.of(name), DesktopIcons.COMPACT).map(ImageIcon::getImage)
            .orElseGet(() -> OldGnomeCatalog.icon(name, DesktopIcons.COMPACT).getImage()));
        this.name = name;
    }
    ImageIcon toolbar() {
        return DesktopIcons.themed(FreedesktopNames.of(name), DesktopIcons.TOOLBAR)
            .orElseGet(() -> OldGnomeCatalog.icon(name, DesktopIcons.TOOLBAR));
    }
}
