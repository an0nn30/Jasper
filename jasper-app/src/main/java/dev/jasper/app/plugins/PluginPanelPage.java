package dev.jasper.app.plugins;

import dev.jasper.app.settings.Setting;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SchemaPage;
import dev.jasper.app.settingsui.SettingsDraft;
import dev.jasper.app.settingsui.SettingsPage;
import dev.jasper.sdk.settings.SettingsPanel;
import dev.jasper.sdk.settings.SettingsPanelHost;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;

/**
 * A plugin's own page in Settings: its component, standard rows for its declared settings over the plugin's own
 * draft, and the plugin's own state. EDT only; every call into the plugin runs contained.
 */
final class PluginPanelPage implements SettingsPage {
    private final String pluginId;
    private final String title;
    private final DeclaredSettings declared;
    private final SettingsPanel panel;
    private final Containment containment;
    private final PageContext context;
    private final SettingsDraft draft;
    private final PageContext own;
    private final List<SchemaPage> rows = new ArrayList<>();
    private final Runnable stopListening;
    private final dev.jasper.app.lifecycle.Subscription draftListening;
    private JComponent body;

    PluginPanelPage(String pluginId, String title, DeclaredSettings declared, SettingsPanel panel, Containment containment,
                    PageContext context) {
        this.pluginId = pluginId; this.title = title; this.declared = declared; this.panel = panel;
        this.containment = containment; this.context = context;
        draft = new SettingsDraft(declared.values());
        own = new PageContext(draft, context.navigate(), context.closeThen(), context.chooseFile(), context.changed(), context.surface());
        stopListening = declared.onReloaded(() -> { draft.rebase(declared.values()); context.changed().run(); });
        draftListening = draft.onChanged(context.changed());
    }

    @Override public String id() { return PluginRuntime.settingsPageId(pluginId); }
    @Override public String title() { return title; }
    @Override public Optional<String> parent() { return Optional.of(PluginRuntime.TOOLS); }
    @Override public String helpAnchor() { return "plugins"; }

    @Override public List<String> searchTerms() {
        var terms = new ArrayList<String>();
        terms.add(title);
        for (DeclaredSetting<?> handle : declared.handles()) {
            terms.add(handle.spec().label());
            terms.add(handle.spec().description());
            terms.addAll(handle.spec().keywords());
        }
        var extra = new AtomicReference<List<String>>(List.of());
        containment.attempt(pluginId, "settings page keywords", () -> { extra.set(List.copyOf(panel.keywords())); return null; });
        terms.addAll(extra.get());
        return List.copyOf(terms);
    }

    @Override public JComponent component() {
        if (body != null) return body;
        var made = new AtomicReference<JComponent>();
        Throwable failure = containment.attempt(pluginId, "settings page", () -> { made.set(panel.create(new Host())); return null; });
        body = failure == null && made.get() != null ? made.get() : failed(failure);
        return body;
    }

    private static JComponent failed(Throwable failure) {
        var label = new JLabel("This page could not be shown: " + (failure == null ? "it returned nothing." : failure.getMessage()));
        label.putClientProperty("html.disable", true);
        label.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        return label;
    }

    @Override public boolean modified() {
        if (draft.modified()) return true;
        boolean[] changed = {false};
        containment.attempt(pluginId, "settings page state", () -> { changed[0] = panel.modified(); return null; });
        return changed[0];
    }

    @Override public boolean valid() { return !declared.rejected() && rows.stream().allMatch(SchemaPage::valid); }

    @Override public void reset() {
        rows.forEach(SchemaPage::reset);
        draft.reset(declared.handles().stream().<Setting<?>>map(DeclaredSetting::appSetting).toList());
        containment.run(pluginId, "settings page reset", panel::reset);
    }

    /** Saves the declared settings, then lets the plugin save its own state. */
    @Override public CompletableFuture<Void> apply() {
        CompletableFuture<Void> saved = draft.modified() ? declared.save(draft.edit()) : CompletableFuture.completedFuture(null);
        return saved.thenRun(() -> {
            Throwable failure = containment.attempt(pluginId, "settings page apply", () -> { panel.apply(); return null; });
            if (failure != null) throw new CompletionException(failure);
        });
    }

    @Override public void highlight(String query) {
        rows.forEach(row -> row.highlight(query));
        containment.run(pluginId, "settings page highlight", () -> panel.highlight(query));
    }

    @Override public void dispose() {
        stopListening.run();
        draftListening.close();
        rows.forEach(SchemaPage::dispose);
        containment.run(pluginId, "settings page dispose", panel::dispose);
    }

    /** What the plugin's page may use: standard rows and the page's draft. */
    private final class Host implements SettingsPanelHost {
        @Override public JComponent row(dev.jasper.sdk.settings.Setting<?> setting) {
            DeclaredSetting<?> handle = declared.handle(setting);
            var spec = SchemaPage.page(id() + "." + handle.key(), title);
            PluginSettingsPage.row(spec, handle);
            SchemaPage page = spec.build(own);
            rows.add(page);
            return page.component();
        }

        @SuppressWarnings("unchecked")
        @Override public <T> T pending(dev.jasper.sdk.settings.Setting<T> setting) {
            DeclaredSetting<T> handle = declared.handle(setting);
            return handle.sdkValue(draft.value((Setting<Object>) handle.appSetting()));
        }

        @SuppressWarnings("unchecked")
        @Override public <T> void stage(dev.jasper.sdk.settings.Setting<T> setting, T value) {
            DeclaredSetting<T> handle = declared.handle(setting);
            handle.spec().kind().check(value).ifPresent(problem -> { throw new IllegalArgumentException(problem); });
            draft.stage((Setting<Object>) handle.appSetting(), Optional.of(handle.toApp(value)));
        }

        @Override public void changed() { context.changed().run(); }
    }
}
