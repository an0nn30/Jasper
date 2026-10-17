package dev.jasper.app.workspace;

import dev.jasper.app.config.AnimationSettings;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;

/**
 * A window's editor tab strip over its {@code TerminalDeck} (Enterprise presentation spec 10.5): today's equal tabs or
 * the classic tabs. Both drive the window's own select, close, menu and reorder callbacks. EDT only.
 */
sealed interface TabStrip extends AutoCloseable permits WindowTabs, ClassicTabs {
    /** The component mounted above the deck. */
    JComponent component();
    /** Takes the deck's tabs, titles, selection and states. */
    void refresh();
    /** Whether the window is active. */
    void setActive(boolean active);
    /** The configured animations; classic tabs never move. */
    void setAnimations(AnimationSettings settings);
    /** Every tab, as a menu. */
    JPopupMenu tabList();
    /** Releases the tabs. */
    @Override void close();
}
