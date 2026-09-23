package dev.jasper.app.platform;

import javax.swing.ImageIcon;

/** Captured retro selection; ImageIcon keeps Metal's native disabled rendering. */
final class SkinIcon extends ImageIcon {
    private final String name;
    SkinIcon(String name) {
        super(OldGnomeCatalog.icon(name, 16).getImage());
        this.name = name;
    }
    ImageIcon toolbar() { return OldGnomeCatalog.icon(name, 28); }
}
