package dev.jasper.app.shortcuthelp;

import dev.jasper.app.testsupport.KitTestSupport;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.text.JTextComponent;

/** Drives the Keymap page's kit views as a user does; EDT only. */
final class ShortcutKit {
    private ShortcutKit() { }

    static ShortcutPanel panel(boolean macOs, Function<Map<String, Optional<String>>, CompletableFuture<Void>> writer) {
        return new ShortcutPanel(KitTestSupport.context(), macOs, writer);
    }

    static ShortcutPanel panel(Function<Map<String, Optional<String>>, CompletableFuture<Void>> writer) { return panel(true, writer); }

    /** The mounted search field's text component. */
    static JTextComponent search(ShortcutPanel panel) {
        return KitTestSupport.require(panel, ShortcutPanel.SEARCH + ".input", JTextComponent.class);
    }

    static JButton button(ShortcutPanel panel, String id) { return KitTestSupport.require(panel, id, JButton.class); }

    static String message(ShortcutPanel panel) { return KitTestSupport.require(panel, "editor.message", JLabel.class).getText(); }

    static AbstractButton reassign(ShortcutPanel panel) { return KitTestSupport.require(panel, "editor.reassign", AbstractButton.class); }

    /** The tree's focusable row canvas, which owns its keyboard bindings. */
    static JComponent canvas(ShortcutPanel panel) {
        var scroll = KitTestSupport.require(panel, ShortcutTree.ID, JScrollPane.class);
        return (JComponent) scroll.getViewport().getView();
    }

    /** Presses a key bound on the tree, as the focused tree receives it. */
    static void key(ShortcutPanel panel, String stroke) {
        var canvas = canvas(panel);
        canvas.getActionMap().get(canvas.getInputMap().get(javax.swing.KeyStroke.getKeyStroke(stroke))).actionPerformed(null);
    }

    /** Selects the row on screen with the keyboard, which never starts an edit. */
    static void selectRow(ShortcutPanel panel, int row) {
        for (int i = 0; i <= panel.tree.rowCount(); i++) key(panel, "UP");
        for (int i = 0; i < row; i++) key(panel, "DOWN");
    }

    /** The mounted title label of the row on screen. */
    static JLabel title(ShortcutPanel panel, int row) {
        layout(panel);
        return KitTestSupport.require(panel, "@tree/" + ShortcutTree.ID + "/" + panel.tree.visibleIds().get(row) + "/title", JLabel.class);
    }

    /** Clicks the row on screen through the event queue, as a real click is delivered. */
    static void clickRow(ShortcutPanel panel, int row) { click(title(panel, row), 1); }

    /** Delivers a left click through the system event queue and waits for it, so the click is the event being dispatched. */
    static void click(Component target, int count) {
        var queue = Toolkit.getDefaultToolkit().getSystemEventQueue();
        var loop = queue.createSecondaryLoop();
        queue.postEvent(new MouseEvent(target, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 3, 3, count, false, MouseEvent.BUTTON1));
        EventQueue.invokeLater(loop::exit);
        if (!loop.enter()) throw new AssertionError("could not wait for the click");
    }

    static void layout(Component root) {
        if (root.getWidth() == 0) root.setSize(900, 600);
        KitTestSupport.layout(root);
    }
}
