package dev.jasper.app.workspace;

import com.formdev.flatlaf.ui.FlatTabbedPaneUI;
import dev.jasper.app.platform.SwingAppearance;
import java.awt.Insets;
import javax.swing.JTabbedPane;

/** One retained selection/content model, with stock Metal tabs or the modern external strip. */
final class TerminalDeck extends JTabbedPane {
    TerminalDeck() {
        super(TOP, WRAP_TAB_LAYOUT);
        setTabLayoutPolicy(SwingAppearance.nativeChrome() ? SCROLL_TAB_LAYOUT : WRAP_TAB_LAYOUT);
        putClientProperty("html.disable", true);
    }
    @Override public void updateUI() {
        if (SwingAppearance.nativeChrome()) { super.updateUI(); return; }
        setUI(new FlatTabbedPaneUI() {
            @Override protected boolean hideTabArea() { return true; }
            @Override protected Insets getTabAreaInsets(int placement) { return new Insets(0, 0, 0, 0); }
        });
        putClientProperty("JTabbedPane.hasFullBorder", false);
        putClientProperty("JTabbedPane.tabAreaInsets", new Insets(0, 0, 0, 0));
    }
}
