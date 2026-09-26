package dev.jasper.app.workspace;

import com.formdev.flatlaf.ui.FlatTabbedPaneUI;
import java.awt.Insets;
import javax.swing.JTabbedPane;

/** One retained selection/content model; the external WindowTabs strip draws the tabs. */
final class TerminalDeck extends JTabbedPane {
    TerminalDeck() {
        super(TOP, WRAP_TAB_LAYOUT);
        putClientProperty("html.disable", true);
    }
    @Override public void updateUI() {
        setUI(new FlatTabbedPaneUI() {
            @Override protected boolean hideTabArea() { return true; }
            @Override protected Insets getTabAreaInsets(int placement) { return new Insets(0, 0, 0, 0); }
        });
        putClientProperty("JTabbedPane.hasFullBorder", false);
        putClientProperty("JTabbedPane.tabAreaInsets", new Insets(0, 0, 0, 0));
    }
}
