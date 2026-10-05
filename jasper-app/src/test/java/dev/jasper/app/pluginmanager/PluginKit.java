package dev.jasper.app.pluginmanager;

import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.Nodes;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;

/**
 * Drives the Plugins page's kit views as the user does: the real switches, buttons and menu items the kit mounted
 * (kit components are named by node id). Headless, a menu is built but never shown; its items are the real kit items.
 */
final class PluginKit {
    private PluginKit() { }

    /** Sizes and lays out a component as a shown page would, so list rows are mounted. */
    static <T extends Component> T laidOut(T component) {
        if (component.getWidth() == 0) component.setSize(900, 600);
        KitTestSupport.layout(component);
        KitTestSupport.layout(component);
        return component;
    }

    /** The row's enable switch. */
    static JButton toggle(PluginManagerPanel panel, String id) { return KitTestSupport.require(laidOut(panel), PluginCard.TOGGLE + id, JButton.class); }

    /** The row's enable switch in a bare list view. */
    static JButton toggle(PluginCardList list, String id) { return KitTestSupport.require(laidOut(list.view()), PluginCard.TOGGLE + id, JButton.class); }

    /** Clicks the kit button named {@code name}; a disabled button does nothing, as for the user. */
    static void press(Component root, String name) { KitTestSupport.require(laidOut(root), name, AbstractButton.class).doClick(0); }

    static JButton button(Component root, String name) { return KitTestSupport.require(laidOut(root), name, JButton.class); }

    /** The items of the kit menu button named {@code name}. */
    static List<AbstractButton> menuItems(Component root, String name) {
        JPopupMenu menu = KitTestSupport.require(laidOut(root), name, JComponent.class).getComponentPopupMenu();
        return Arrays.stream(menu.getComponents()).filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast).toList();
    }

    /** Presses the item labelled {@code label} of the kit menu button named {@code name}. */
    static void chooseFromMenuButton(Component root, String name, String label) {
        menuItems(root, name).stream().filter(item -> item.getText().equals(label)).findFirst()
            .orElseThrow(() -> new AssertionError("no menu item " + label)).doClick(0);
    }

    /** The details pane's More actions item. */
    static void clickMenu(PluginManagerPanel panel, String label) {
        chooseFromMenuButton(panel.detailsPane().header(), PluginDetails.MORE, label);
    }

    /** The gear menu's item. */
    static void clickGear(PluginManagerPanel panel, String label) { chooseFromMenuButton(panel.gearView(), PluginManagerPanel.GEAR, label); }

    /** Right-clicks the plugin's row (a popup trigger) and returns the kit context menu it built. */
    static JPopupMenu rowMenu(PluginManagerPanel panel, String id) { return rowMenu((Component) laidOut(panel), "name:" + id); }

    static JPopupMenu rowMenu(Component root, String titleName) {
        var target = KitTestSupport.require(laidOut(root), titleName, JComponent.class);
        var press = new MouseEvent(target, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), InputEvent.BUTTON3_DOWN_MASK, 2, 2, 1, true, MouseEvent.BUTTON3);
        for (var listener : target.getMouseListeners()) listener.mousePressed(press);
        for (Container c = target; c != null; c = c.getParent())
            if (c instanceof JComponent owner && owner.getClientProperty(dev.jasper.ui.swing.KitOwnership.CONTEXT_MENU) instanceof JPopupMenu found) return found;
        throw new AssertionError("no kit context menu above " + titleName);
    }

    /** The list's kit context menu (built on a right click or the menu keys; headless it is never shown). */
    static JPopupMenu rowMenuOf(Component root) {
        if (root instanceof JComponent owner && owner.getClientProperty(dev.jasper.ui.swing.KitOwnership.CONTEXT_MENU) instanceof JPopupMenu found) return found;
        if (root instanceof Container container) for (var child : container.getComponents()) {
            var found = rowMenuOf(child);
            if (found != null) return found;
        }
        return null;
    }

    static List<String> labels(JPopupMenu menu) {
        return Arrays.stream(menu.getComponents()).map(c -> c instanceof AbstractButton item ? item.getText() : "-").toList();
    }

    static void choose(JPopupMenu menu, String label) {
        Arrays.stream(menu.getComponents()).filter(c -> c instanceof AbstractButton item && item.getText().equals(label)).findFirst()
            .map(AbstractButton.class::cast).orElseThrow(() -> new AssertionError("no menu item " + label)).doClick(0);
    }

    /** Runs the list's key binding for {@code stroke} on its focused row. */
    static void key(PluginManagerPanel panel, String stroke) { key(panel.listView(), stroke); }

    static void key(Component listView, String stroke) {
        var canvas = canvas(laidOut(listView));
        var name = canvas.getInputMap().get(KeyStroke.getKeyStroke(stroke));
        if (name == null) throw new AssertionError("no binding for " + stroke);
        canvas.getActionMap().get(name).actionPerformed(new java.awt.event.ActionEvent(canvas, 0, ""));
    }

    /** The row canvas: the focusable component of the list that binds the row keys. */
    static JComponent canvas(Component root) {
        if (root instanceof JComponent c && c.isFocusable() && c.getInputMap().get(KeyStroke.getKeyStroke("DOWN")) != null) return c;
        if (root instanceof Container container) for (var child : container.getComponents()) {
            var found = canvas(child);
            if (found != null) return found;
        }
        return null;
    }

    /** The empty state's title as shown (its accessible name is its literal text), or null when the list has rows. */
    static String emptyTitle(Component listView) {
        var title = KitTestSupport.find(laidOut(listView), "plugins-empty.title", JComponent.class);
        return title == null ? null : title.getAccessibleContext().getAccessibleName();
    }

    /** The text input of the kit field named {@code id}. */
    static javax.swing.text.JTextComponent field(Component root, String id) {
        var owner = KitTestSupport.require(laidOut(root), id, JComponent.class);
        var found = first(owner, javax.swing.text.JTextComponent.class);
        if (found == null) throw new AssertionError("no input in " + id);
        return found;
    }

    static <T extends Component> T first(Component root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Container container) for (var child : container.getComponents()) {
            T found = first(child, type);
            if (found != null) return found;
        }
        return null;
    }

    /** The plugin list's current descriptor. */
    static Nodes.ItemList list(PluginManagerPanel panel) { return (Nodes.ItemList) panel.listView().spec().root(); }

    /** Every label and text under the root, in tree order. */
    static List<String> texts(Component root) { return KitTestSupport.texts(laidOut(root)); }
}
