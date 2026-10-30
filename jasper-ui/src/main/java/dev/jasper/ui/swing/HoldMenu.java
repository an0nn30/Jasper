package dev.jasper.ui.swing;

import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import java.awt.Container;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * A button's hold menu (SDK 0.27.0; {@link Nodes.Button#holdMenu()}), as a browser's Back button holds its history: a
 * left press held for {@link #HOLD_NANOS} opens it under the button, and the press's release then reports nothing,
 * while a shorter press is the button's own. A popup trigger (a right-click; a Control-click on macOS) and the menu
 * keys (Shift+F10, the context-menu key) open it at once. It shows in the menu's card, placed as a menu button's menu
 * is, and choosing an enabled item reports its action through the button's own events; a submenu's children are
 * reachable as in every kit menu. The button's {@link KitOwnership#CONTEXT_MENU} holds it while there are items. The
 * button makes it the first time it has a hold menu. EDT only.
 */
final class HoldMenu implements AutoCloseable {
    /** How long a left press is held before the menu opens: 500 ms, as browsers hold their Back and Forward buttons. */
    static final long HOLD_NANOS = 500_000_000L;
    private static final List<KeyStroke> MENU_KEYS = List.of(KeyStroke.getKeyStroke("shift F10"), KeyStroke.getKeyStroke("CONTEXT_MENU"));
    private static final String OPEN = "Jasper.holdMenu.open";

    final Buttons.ButtonControl owner;
    final Buttons.KitButton button;
    final Menus.Popup popup;
    /** Checks a held press: {@link #elapsed()} once {@link #HOLD_NANOS} has passed by the wall clock. */
    final Timer timer;
    /** The clock a held press is measured by, in nanoseconds; a test sets its own and runs {@link #elapsed()}. */
    LongSupplier clock = System::nanoTime;
    private final List<Runnable> cleanup = new ArrayList<>();
    private List<MenuItem> items = List.of();
    /** When the left press now held began, by {@link #clock}; meaningful while {@link #holding}. */
    private long pressedAt;
    private boolean holding, keys;
    private final MouseAdapter presses = new MouseAdapter() {
        @Override public void mousePressed(MouseEvent event) { pressed(event); }
        @Override public void mouseReleased(MouseEvent event) { released(event); }
    };

    HoldMenu(Buttons.ButtonControl owner) {
        this.owner = owner;
        button = owner.button;
        popup = new Menus.Popup(owner.context());
        timer = new Timer(millis(HOLD_NANOS), event -> elapsed());
        timer.setRepeats(false);
        // After the button's own listener, which arms it: a press that opens the menu disarms it again.
        button.addMouseListener(presses);
        button.getActionMap().put(OPEN, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { open(); }
        });
    }

    /** Shows {@code next} as the menu's items, rebuilt only when they change, and hides the menu while the button takes no input. */
    void update(List<MenuItem> next) {
        popup.restyle();
        if (!next.equals(items)) {
            clear();
            items = next;
            Menus.fill(owner.context(), popup, items, this::chosen, cleanup::add);
            keys(!items.isEmpty());
            button.putClientProperty(KitOwnership.CONTEXT_MENU, items.isEmpty() ? null : popup);
            if (items.isEmpty()) dismiss();
        } else Menus.describe(popup.getComponents());
        if (!owner.interactive()) { holding = false; timer.stop(); dismiss(); }
        popup.revalidate();
        popup.repaint();
    }

    private void pressed(MouseEvent event) {
        if (items.isEmpty() || !owner.interactive()) return;
        if (event.isPopupTrigger()) {
            disarm();
            open();
            return;
        }
        if (!SwingUtilities.isLeftMouseButton(event)) return;
        holding = true;
        pressedAt = clock.getAsLong();
        timer.setInitialDelay(millis(HOLD_NANOS));
        timer.restart();
    }

    private void released(MouseEvent event) {
        holding = false;
        timer.stop();
        if (!items.isEmpty() && owner.interactive() && event.isPopupTrigger()) open();
    }

    /**
     * The held press's check, which the timer runs: once the press has been held for {@link #HOLD_NANOS} by
     * {@link #clock} it disarms the button, so that the release reports nothing, and opens the menu; before that it
     * waits for the rest.
     */
    void elapsed() {
        if (!holding) return;
        if (items.isEmpty() || !owner.interactive()) { holding = false; return; }
        long left = HOLD_NANOS - (clock.getAsLong() - pressedAt);
        if (left > 0) {
            timer.setInitialDelay(Math.max(1, millis(left)));
            timer.restart();
            return;
        }
        holding = false;
        disarm();
        open();
    }

    /**
     * The press in progress shows released, and reports nothing when it is released; the button reports no click while
     * the menu shows either, should its own listener arm it after this one ran.
     */
    private void disarm() {
        var model = button.getModel();
        model.setArmed(false);
        model.setPressed(false);
    }

    /** Opens the menu under the button, as a menu button opens its own, unless it shows already or has no items. */
    void open() {
        if (items.isEmpty() || !owner.interactive() || popup.isVisible()) return;
        holding = false;
        timer.stop();
        popup.setInvoker(button);
        if (button.isShowing()) {
            Container host = SwingUtilities.getRootPane(button);
            if (host == null) host = owner.view;
            Rectangle anchor = SwingUtilities.convertRectangle(button.getParent(), button.getBounds(), host);
            Point point = Menus.anchor(anchor, popup.getPreferredSize(), new Rectangle(0, 0, host.getWidth(), host.getHeight()));
            Point local = SwingUtilities.convertPoint(host, point, button);
            popup.show(button, local.x, local.y);
        } else popup.setVisible(true);
    }

    /** Hides the menu; whether it was showing. */
    boolean dismiss() {
        boolean visible = popup.isVisible();
        popup.setVisible(false);
        return visible;
    }

    private void chosen(MenuItem item) {
        if (!item.enabled() || !owner.interactive() || !Menus.holds(items, item)) return;
        dismiss();
        owner.act(new UiEvent.Action(item.action()));
    }

    /** Binds the menu keys while there are items; a key bound to nothing would hide the look and feel's own binding. */
    private void keys(boolean on) {
        if (keys == on) return;
        keys = on;
        var map = button.getInputMap(JComponent.WHEN_FOCUSED);
        for (KeyStroke stroke : MENU_KEYS) {
            if (on) map.put(stroke, OPEN); else map.remove(stroke);
        }
    }

    private void clear() {
        cleanup.forEach(Runnable::run);
        cleanup.clear();
        popup.removeAll();
    }

    private static int millis(long nanos) { return (int) ((nanos + 999_999) / 1_000_000); }

    @Override public void close() {
        holding = false;
        timer.stop();
        dismiss();
        clear();
        items = List.of();
        keys(false);
        button.removeMouseListener(presses);
        button.getActionMap().remove(OPEN);
        button.putClientProperty(KitOwnership.CONTEXT_MENU, null);
    }
}
