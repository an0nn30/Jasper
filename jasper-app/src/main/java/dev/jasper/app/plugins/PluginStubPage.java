package dev.jasper.app.plugins;

import dev.jasper.app.settingsui.SettingsPage;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.swing.JComponent;

/** Settings' page for an installed plugin that is not running: why, and a way to edit its file. EDT only. */
final class PluginStubPage implements SettingsPage {
    private final String pluginId, title, text, reason;
    private final Runnable openFile;
    private final Supplier<KitContext> kit;
    private KitView view;
    private boolean disposed;

    private final Optional<dev.jasper.ui.icons.EncodedArtwork> artwork;
    @Override public Optional<dev.jasper.ui.icons.EncodedArtwork> artwork(){return artwork;}
    @Override public Optional<String> icon(){return Optional.of("plugin");}
    PluginStubPage(String pluginId, String title, String why, Runnable openFile, Supplier<KitContext> kit) { this(pluginId, title, why, openFile, kit, Optional.empty()); }
    PluginStubPage(String pluginId, String title, String why, Runnable openFile, Supplier<KitContext> kit, Optional<dev.jasper.ui.icons.EncodedArtwork> artwork) {
        this.artwork=java.util.Objects.requireNonNull(artwork);
        this.pluginId = pluginId; this.title = title; this.openFile = openFile; this.kit = kit;
        text = "Start " + title + " to edit its settings.";
        reason = why.isBlank() ? "" : "Not running: " + why;
    }
    String text() { return text; }
    String reason() { return reason; }
    @Override public String id() { return PluginRuntime.settingsPageId(pluginId); }
    @Override public String title() { return title; }
    @Override public Optional<String> parent() { return Optional.of(PluginRuntime.TOOLS); }
    @Override public String helpAnchor() { return "plugins"; }
    @Override public List<String> searchTerms() { return List.of(title); }
    @Override public JComponent component() {
        if (disposed) throw new IllegalStateException("Settings page is closed");
        if (view == null) view = new KitView(kit.get(), new ViewSpec(new Nodes.Stack("plugin-stub", ControlState.READY,
            Nodes.LayoutRole.DETAIL, List.of(
                new Nodes.Text("plugin-stub.message", ControlState.READY, text, Nodes.TextRole.BODY),
                new Nodes.Text("plugin-stub.reason", new ControlState(!reason.isEmpty(), true, false, ""), reason, Nodes.TextRole.DESCRIPTION),
                new Nodes.Button("plugin-stub.open", ControlState.READY, "Open in Editor", "", Nodes.ButtonKind.SECONDARY, "open-file", "")))),
            event -> { if (!disposed && event instanceof UiEvent.Action action && action.id().equals("open-file")) openFile.run(); });
        return view;
    }
    @Override public boolean modified() { return false; }
    @Override public void reset() { }
    @Override public CompletableFuture<Void> apply() { return CompletableFuture.completedFuture(null); }
    @Override public void highlight(String query) { }
    @Override public void dispose() { if (disposed) return; disposed = true; if (view != null) view.close(); }
}
