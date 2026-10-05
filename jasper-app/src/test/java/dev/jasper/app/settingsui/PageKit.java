package dev.jasper.app.settingsui;

import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.Nodes;
import java.awt.Component;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Drives Settings pages through their mounted kit views, as a user does; EDT only. */
final class PageKit {
    private PageKit() { }

    /** A page context with the shared test kit context. */
    static PageContext context(SettingsDraft draft, Consumer<String> navigate, Runnable changed) {
        return new PageContext(draft, navigate, Runnable::run, (title, initial) -> Optional.empty(), changed, () -> null, KitTestSupport::context);
    }

    static PageContext context(SettingsDraft draft, java.util.function.BiFunction<String, Optional<Path>, Optional<Path>> choose) {
        return new PageContext(draft, id -> { }, Runnable::run, choose, () -> { }, () -> null, KitTestSupport::context);
    }

    /** The descriptor node {@code id} of a built page. */
    static Nodes.Node node(SchemaPage page, String id) {
        page.component();
        var pending = new ArrayDeque<Nodes.Node>(List.of(page.view().spec().root()));
        while (!pending.isEmpty()) {
            var next = pending.removeFirst();
            if (next.id().equals(id)) return next;
            switch (next) {
                case Nodes.Stack n -> pending.addAll(n.children());
                case Nodes.Card n -> pending.addAll(n.children());
                case Nodes.Section n -> pending.add(n.body());
                case Nodes.SettingRow n -> pending.add(n.control());
                case Nodes.FormRow n -> pending.add(n.control());
                default -> { }
            }
        }
        throw new AssertionError("No node " + id);
    }

    /** The page's section titles, in order, as rendered. */
    static List<String> sectionTitles(SettingsPage page) {
        var titles = new ArrayList<String>();
        collect(page.component(), titles);
        return titles;
    }

    private static void collect(Component component, List<String> titles) {
        if (component instanceof javax.swing.JLabel label && component.getName() != null && component.getName().matches("section-\\d+\\.title"))
            titles.add(label.getText());
        if (component instanceof java.awt.Container container) for (var child : container.getComponents()) collect(child, titles);
    }

    /** The mounted kit component {@code name} on a page. */
    static <T extends Component> T kit(SettingsPage page, String name, Class<T> type) {
        return KitTestSupport.require(page.component(), name, type);
    }
}
