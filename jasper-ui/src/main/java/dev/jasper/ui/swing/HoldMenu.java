package dev.jasper.ui.swing;

import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * A button's hold menu (SDK 0.27.0; {@link Nodes.Button#holdMenu()}), as a browser's Back button holds its history: a
 * left press held on the button for {@link #HOLD_NANOS} opens it under the button, and the press's release then reports
 * nothing, while a shorter press is the button's own. A press that ends without its release reaching the button, or
 * whose pointer leaves the button, opens nothing, and nothing opens while the button is not on screen. A popup trigger (a right-click; a Control-click on macOS) and the menu
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
    /**
     * Puts the menu on screen under the button, as a menu button's menu opens ({@link Menus#showUnder}), and says whether
     * it did: never while the button is not on screen. A headless test, where nothing is, sets its own.
     */
    Predicate<JPopupMenu> show;
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
        show = menu -> Menus.showUnder(menu, button, owner.view);
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
            // A history that changes while its menu shows: the menu takes its new size where it is.
            Menus.repack(popup);
        } else Menus.describe(popup.getComponents());
        if (!owner.interactive()) { holding = false; timer.stop(); dismiss(); }
        popup.revalidate();
        popup.repaint();
    }

    private void pressed(MouseEvent event) {
        if (items.isEmpty() || !owner.interactive()) return;
        if (event.isPopupTrigger()) {
            if (open()) disarm();
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
     * {@link #clock} it opens the menu and disarms the button, so that the release reports nothing; before that it waits
     * for the rest. A press that has ended without its release reaching the button (a focus loss clears its pressed
     * state), one whose pointer has left the button (it is no longer armed) and a button not on screen open nothing.
     */
    void elapsed() {
        if (!holding) return;
        var model = button.getModel();
        if (items.isEmpty() || !owner.interactive() || !model.isPressed() || !model.isArmed()) { holding = false; return; }
        long left = HOLD_NANOS - (clock.getAsLong() - pressedAt);
        if (left > 0) {
            timer.setInitialDelay(Math.max(1, millis(left)));
            timer.restart();
            return;
        }
        holding = false;
        if (open()) disarm();
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

    /**
     * Opens the menu under the button ({@link #show}) unless it shows already, has no items or the button takes no input;
     * whether it opened now.
     */
    boolean open() {
        if (items.isEmpty() || !owner.interactive() || popup.isVisible()) return false;
        holding = false;
        timer.stop();
        popup.setInvoker(button);
        return show.test(popup);
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
