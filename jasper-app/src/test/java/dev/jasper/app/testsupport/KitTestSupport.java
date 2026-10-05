package dev.jasper.app.testsupport;

import dev.jasper.app.appearance.AppearanceBindings;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.theme.ThemeRoute;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.text.JTextComponent;

/** App-native kit contexts and lookups for headless tests of kit-composed app surfaces. */
public final class KitTestSupport {
    private static AppearanceBindings shared;

    private KitTestSupport() { }

    /** A chrome context over the generated Jasper Dark pair, shared by the test JVM; EDT only. */
    public static KitContext context() {
        if (!EventQueue.isDispatchThread()) throw new IllegalStateException("Kit contexts are EDT confined");
        if (shared == null) shared = bindings(true);
        return shared.context(ThemeRoute.CHROME);
    }

    /** Fresh bindings a test owns and closes, for example to publish a theme switch; EDT only. */
    public static AppearanceBindings bindings(boolean dark) {
        return new AppearanceBindings(ThemeTestSupport.pair(dark), failure -> { throw new AssertionError(failure); }, () -> false);
    }

    /** The mounted component named {@code name} (kit components are named by node id), or null. */
    public static <T extends Component> T find(Component root, String name, Class<T> type) {
        if (type.isInstance(root) && name.equals(root.getName())) return type.cast(root);
        if (root instanceof Container container) for (Component child : container.getComponents()) {
            T found = find(child, name, type);
            if (found != null) return found;
        }
        return null;
    }

    /** As {@link #find}, failing when absent. */
    public static <T extends Component> T require(Component root, String name, Class<T> type) {
        T found = find(root, name, type);
        if (found == null) throw new AssertionError("No " + type.getSimpleName() + " named " + name);
        return found;
    }

    /** Every label and text component's text under {@code root}, in tree order. */
    public static List<String> texts(Component root) {
        var out = new ArrayList<String>();
        if (root instanceof JLabel label && label.getText() != null) out.add(label.getText());
        if (root instanceof JTextComponent text) out.add(text.getText());
        if (root instanceof Container container) for (Component child : container.getComponents()) out.addAll(texts(child));
        return out;
    }

    /** Lays out a component tree as a shown window would. */
    public static void layout(Component component) {
        component.doLayout();
        if (component instanceof Container container) for (Component child : container.getComponents()) layout(child);
    }
}
