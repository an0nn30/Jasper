package dev.jasper.app.workspace;

import com.formdev.flatlaf.ui.FlatTabbedPaneUI;
import java.awt.Insets;
import javax.swing.JTabbedPane;

/** Retains Swing's selection/content model; WindowTabs owns the only visible tab row. */
final class TerminalDeck extends JTabbedPane {
    TerminalDeck() { super(TOP, WRAP_TAB_LAYOUT); }

    @Override public void updateUI() {
        setUI(new FlatTabbedPaneUI() {
            @Override protected boolean hideTabArea() { return true; }
            @Override protected Insets getTabAreaInsets(int placement) { return new Insets(0, 0, 0, 0); }
        });
        putClientProperty("JTabbedPane.hasFullBorder", false);
        putClientProperty("JTabbedPane.tabAreaInsets", new Insets(0, 0, 0, 0));
    }
}
