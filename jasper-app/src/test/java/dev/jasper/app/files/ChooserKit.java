package dev.jasper.app.files;

import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.swing.KitView;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/** Drives the chooser's kit views as a user does; EDT only. */
final class ChooserKit {
    private ChooserKit() { }

    /** Sizes and lays out the panel as a shown dialog would, so the visible rows are mounted. */
    static void layout(FileChooserPanel panel) {
        if (panel.getWidth() == 0) panel.setSize(900, 560);
        KitTestSupport.layout(panel);
    }

    static KitView view(FileChooserPanel panel, String node) {
        var component = KitTestSupport.require(panel, node, JComponent.class);
        return (KitView) SwingUtilities.getAncestorOfClass(KitView.class, component);
    }

    static Nodes.Table table(FileChooserPanel panel) { return (Nodes.Table) view(panel, FileChooserPanel.FILES).spec().root(); }

    /** The listed names, in order, as the kit table shows them. */
    static List<String> rows(FileChooserPanel panel) { return table(panel).rows().stream().map(row -> row.cells().get("name")).toList(); }

    static JScrollPane scroll(FileChooserPanel panel) { return KitTestSupport.require(panel, FileChooserPanel.FILES, JScrollPane.class); }

    static JComponent canvas(FileChooserPanel panel) { return (JComponent) scroll(panel).getViewport().getView(); }

    /** A plain left click on the row's name. */
    static void selectRow(FileChooserPanel panel, int row) {
        layout(panel);
        var id = table(panel).rows().get(row).id();
        var cell = KitTestSupport.require(panel, "@table/" + FileChooserPanel.FILES + "/" + id + "/name", JLabel.class);
        for (var listener : cell.getMouseListeners())
            listener.mouseClicked(new MouseEvent(cell, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 2, 2, 1, false, MouseEvent.BUTTON1));
    }

    /** The table's own Enter, as the focused listing receives it. */
    static void enter(FileChooserPanel panel) {
        var canvas = canvas(panel);
        canvas.getActionMap().get(canvas.getInputMap().get(KeyStroke.getKeyStroke("ENTER"))).actionPerformed(null);
    }

    /** Backspace while the listing has focus. */
    static void backspace(FileChooserPanel panel) {
        var view = view(panel, FileChooserPanel.FILES);
        var name = view.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0));
        view.getActionMap().get(name).actionPerformed(null);
    }

    /** A key typed into the focused listing. */
    static void type(FileChooserPanel panel, char c) {
        var canvas = canvas(panel);
        for (var listener : canvas.getKeyListeners())
            listener.keyTyped(new KeyEvent(canvas, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, c));
    }

    /** A plain left click on a place in the places tree. */
    static void choosePlace(FileChooserPanel panel, Places.Place place) {
        layout(panel);
        var title = KitTestSupport.require(panel, "@tree/" + FileChooserPanel.PLACES + "/" + panel.placeId(place) + "/title", JLabel.class);
        for (var listener : title.getMouseListeners())
            listener.mouseClicked(new MouseEvent(title, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 2, 2, 1, false, MouseEvent.BUTTON1));
    }

    /** Cmd/Ctrl+L, typing a path into the crumbs' field and Enter. */
    static void editPath(FileChooserPanel panel, String text) {
        panel.editPath();
        var field = KitTestSupport.require(panel, "@crumbs/" + FileChooserPanel.PATH + "/field", JTextField.class);
        field.setText(text);
        field.getActionMap().get(field.getInputMap().get(KeyStroke.getKeyStroke("ENTER"))).actionPerformed(null);
    }

    static AbstractButton previewToggle(FileChooserPanel panel) {
        return KitTestSupport.require(panel, FileChooserPanel.PREVIEW_TOGGLE, AbstractButton.class);
    }

    /** The crumbs' field-shaped surface. */
    static JComponent pathBox(FileChooserPanel panel) { return KitTestSupport.require(panel, "@crumbs/" + FileChooserPanel.PATH + "/box", JComponent.class); }

    /** The crumbs and their separators. */
    static JComponent crumbs(FileChooserPanel panel) { return KitTestSupport.require(panel, "@crumbs/" + FileChooserPanel.PATH + "/crumbs", JComponent.class); }

    static <T extends Component> T kit(FileChooserPanel panel, String name, Class<T> type) { return KitTestSupport.require(panel, name, type); }

    /**
     * Right-clicks {@code target} as a popup trigger and presses the item labelled {@code label} in the kit context menu
     * it built. Headless, the menu is built but never shown; its items are the real kit items.
     */
    static void chooseFromMenu(JComponent target, String label) {
        var press = new MouseEvent(target, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), java.awt.event.InputEvent.BUTTON3_DOWN_MASK,
            2, 2, 1, true, MouseEvent.BUTTON3);
        for (var listener : target.getMouseListeners()) listener.mousePressed(press);
        javax.swing.JPopupMenu menu = null;
        for (java.awt.Container c = target; c != null && menu == null; c = c.getParent())
            if (c instanceof JComponent owner && owner.getClientProperty(dev.jasper.ui.swing.KitOwnership.CONTEXT_MENU) instanceof javax.swing.JPopupMenu found) menu = found;
        if (menu == null) throw new AssertionError("no kit context menu above " + target.getName());
        for (var item : menu.getComponents())
            if (item instanceof javax.swing.JMenuItem entry && entry.getText().equals(label)) { entry.doClick(0); return; }
        throw new AssertionError("no menu item " + label);
    }
}
