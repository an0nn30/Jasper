package dev.jasper.remote.ui;

import javax.swing.BorderFactory;
import javax.swing.JToolBar;

/**
 * A fixed, borderless, transparent toolbar. Buttons inside it take the running look and feel's flat toolbar style:
 * FlatLaf draws them transparent with a hover highlight, Metal (retro) as flat rollover buttons.
 */
public final class FlatToolBar extends JToolBar {
    public FlatToolBar() {
        setFloatable(false);
        setRollover(true);
        setBorderPainted(false);
        setBorder(BorderFactory.createEmptyBorder());
        setOpaque(false);
    }
}
