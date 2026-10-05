package dev.jasper.app.pluginmanager;

import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.settingsui.FillingPage;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SearchText;
import dev.jasper.app.settingsui.SettingsPage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.JComponent;

/**
 * The Plugins page of Settings. Its operations apply at once and take effect after a restart, so it is never
 * modified and Apply has nothing to do for it. SettingsDialog owns each page; disposing it detaches the list. EDT only.
 */
public final class PluginsPage implements SettingsPage {
    /** The page id. */
    public static final String ID = "plugins";

    private final PluginManager manager;
    private final PageContext context;
    private PluginManagerPanel panel;
    private FillingPage body;

    PluginsPage(PluginManager manager, PageContext context) {
        this.manager = manager;
        this.context = context;
        manager.refresh();
    }

    @Override public String id() { return ID; }
    @Override public String title() { return "Plugins"; }
    @Override public Optional<String> parent() { return Optional.empty(); }
    @Override public String helpAnchor() { return "plugins"; }

    /** The page title and each listed plugin's name and id. */
    @Override public List<String> searchTerms() {
        var terms = new ArrayList<String>(List.of("Plugins", "extensions install enable disable remove safe mode"));
        for (PluginRuntime.Row row : manager.rows()) terms.add(row.name() + " " + row.id());
        return List.copyOf(terms);
    }

    @Override public JComponent component() {
        if (body != null) return body;
        panel = manager.attach(context.surface(), context.navigate(), context.kit());
        body = new FillingPage(panel);
        return body;
    }

    @Override public boolean modified() { return false; }
    @Override public void reset() { }
    @Override public CompletableFuture<Void> apply() { return CompletableFuture.completedFuture(null); }

    /** Selects the first plugin whose name or id matches. */
    @Override public void highlight(String query) {
        if (panel == null || query == null || query.isBlank()) return;
        manager.rows().stream().filter(row -> SearchText.matches(row.name() + " " + row.id(), query)).findFirst()
            .ifPresent(row -> panel.select(row.id()));
    }

    @Override public void selected() { manager.refresh(); }

    @Override public void dispose() { if (panel != null) manager.detach(panel); }
}
