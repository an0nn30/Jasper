package dev.jasper.app.plugins;

import dev.jasper.app.settings.Setting;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SchemaPage;
import dev.jasper.app.settingsui.SettingsDraft;
import dev.jasper.app.settingsui.SettingsPage;
import dev.jasper.sdk.settings.SettingsPanel;
import dev.jasper.sdk.settings.SettingsPanelHost;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

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
    private final JLabel message = PluginPageBody.message();
    private JComponent content;
    private JComponent body;
    /** Whether the page applied successfully and nothing changed since, so there is nothing for the panel to drop. */
    private boolean applied;
    private boolean disposed;
    Runnable onDisposed = () -> {};
    private HostedKit kit;
    private HostedKitView view;
    private final Host rowHost = new Host();
    private final KitSettingRows kitRows = new KitSettingRows(rowHost, this::settingSpec, rowHost::unset);
    private dev.jasper.sdk.settings.SettingSpec<?> settingSpec(dev.jasper.sdk.settings.Setting<?> setting){return declared.handle(setting).spec();}
    void kit(HostedKit value) { kit=value; }
    private void refreshRows(){if(view!=null && !disposed)view.update(view.spec());}


    PluginPanelPage(String pluginId, String title, DeclaredSettings declared, SettingsPanel panel, Containment containment,
                    PageContext context) {
        this.pluginId = pluginId; this.title = title; this.declared = declared; this.panel = panel;
        this.containment = containment; this.context = context;
        draft = new SettingsDraft(declared.values());
        own = new PageContext(draft, context.navigate(), context.closeThen(), context.chooseFile(), context.changed(), context.surface());
        stopListening = declared.onReloaded(() -> {
            draft.rebase(declared.values());
            refreshRows();
            PluginPageBody.showMessage(message, declared);
            context.changed().run();
        });
        draftListening = draft.onChanged(() -> {
            if (draft.modified()) applied = false;
            context.changed().run();
        });
    }

    /** The plugin's component, or the message saying why it could not be built; null until {@link #component}. */
    JComponent content() { return content; }
    JLabel message() { return message; }

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
        if(disposed)throw new IllegalStateException("Settings page is closed");
        if (body != null) return body;
        var made = new AtomicReference<JComponent>();
        Throwable failure = containment.attempt(pluginId, "settings page", () -> { var owned=panel instanceof SettingsPanel.View ? java.util.Objects.requireNonNull(((SettingsPanel.View)panel).createView(rowHost),"Settings view") : null;
            if(owned!=null){
                var next=kit.requireOwned(owned);next.mount(this);view=next;view.hostSpec=kitRows::refresh;
                view.hostEvents=event -> {boolean handled=kitRows.event(event);if(handled)refreshRows();return handled;};
                made.set(view.component());
            }else made.set(panel.create(rowHost)); return null; });
        content = failure == null && made.get() != null ? made.get() : failed(failure);
        body = new PluginPageBody(message, content);
        PluginPageBody.showMessage(message, declared);
        return body;
    }

    private static JComponent failed(Throwable failure) {
        String why = failure == null ? "it returned nothing."
            : failure.getMessage() != null ? failure.getMessage() : failure.toString();
        var label = new JLabel("This page could not be shown: " + why);
        label.putClientProperty("html.disable", true);
        label.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        return label;
    }

    @Override public boolean modified() {
        if(disposed)return false;
        if (draft.modified()) return true;
        boolean[] changed = {false};
        containment.attempt(pluginId, "settings page state", () -> { changed[0] = panel.modified(); return null; });
        return changed[0];
    }

    /** A rejected file holds up only this page's own edits to its declared settings, which could not be saved to it. */
    @Override public boolean valid() { return !disposed && !(declared.rejected() && draft.modified()) && kitRows.valid() && rows.stream().allMatch(SchemaPage::valid); }

    @Override public void reset() {
        if(disposed)return;
        kitRows.reset();
        rows.forEach(SchemaPage::reset);
        draft.reset(declared.handles().stream().<Setting<?>>map(DeclaredSetting::appSetting).toList());
        containment.run(pluginId, "settings page reset", panel::reset);
        refreshRows();
    }

    /** Saves the declared settings, then lets the plugin save its own state. */
    @Override public CompletableFuture<Void> apply() {
        if(disposed || !valid())return CompletableFuture.failedFuture(new IllegalStateException("Settings page unavailable or invalid"));
        CompletableFuture<Void> saved = draft.modified() ? declared.save(draft.edit()) : CompletableFuture.completedFuture(null);
        return saved.thenRun(() -> {
            if(disposed)return;
            Throwable failure = containment.attempt(pluginId, "settings page apply", () -> { panel.apply(); return null; });
            if (failure != null) throw new CompletionException(failure);
            applied = true;
        });
    }

    @Override public void highlight(String query) {
        if(disposed)return;
        rows.forEach(row -> row.highlight(query));
        containment.run(pluginId, "settings page highlight", () -> panel.highlight(query));
    }

    /** Lets a page that was shown drop state of its own that was not applied, then releases it. */
    @Override public void dispose() {
        if(disposed)return;disposed=true;
        stopListening.run();
        draftListening.close();
        rows.forEach(SchemaPage::dispose);
        if (body != null && !applied) containment.run(pluginId, "settings page reset", panel::reset);
        if(view!=null)view.close();
        containment.run(pluginId, "settings page dispose", panel::dispose);
        onDisposed.run();onDisposed=() -> {};
    }

    /** What the plugin's page may use: standard rows and the page's draft. */
    private final class Host implements SettingsPanelHost {
        private void guard(){if(disposed)throw new IllegalStateException("Settings page is closed");}
        @Override public dev.jasper.sdk.ui.Nodes.Node rowNode(dev.jasper.sdk.settings.Setting<?> setting){guard();return kitRows.row(setting);}

        @Override public JComponent row(dev.jasper.sdk.settings.Setting<?> setting) {
            guard();DeclaredSetting<?> handle = declared.handle(setting);
            var spec = SchemaPage.page(id() + "." + handle.key(), title);
            PluginSettingsPage.row(spec, handle);
            SchemaPage page = spec.build(own);
            rows.add(page);
            return new KeepsHeight(page.component());
        }

        @SuppressWarnings("unchecked")
        @Override public <T> T pending(dev.jasper.sdk.settings.Setting<T> setting) {
            guard();DeclaredSetting<T> handle = declared.handle(setting);
            return handle.sdkValue(draft.value((Setting<Object>) handle.appSetting()));
        }

        @SuppressWarnings("unchecked")
        @Override public <T> void stage(dev.jasper.sdk.settings.Setting<T> setting, T value) {
            guard();DeclaredSetting<T> handle = declared.handle(setting);
            handle.spec().kind().check(value).ifPresent(problem -> { throw new IllegalArgumentException(problem); });
            var app = (Setting<Object>) handle.appSetting();
            Object staged = handle.toApp(value);
            boolean unsetDefault = staged.equals(app.defaultValue()) && draft.saved(app).isEmpty();
            applied = false;
            kitRows.staged(setting);
            draft.stage(app, unsetDefault ? Optional.empty() : Optional.of(staged));
            refreshRows();
        }

        /** A standard Reset removes the saved override, unlike ordinary stage(default). */
        private void unset(dev.jasper.sdk.settings.Setting<?> setting) {
            guard();
            var handle = declared.handle(setting);
            kitRows.staged(setting);
            applied = false;
            draft.stage(handle.appSetting(), Optional.empty());
            refreshRows();
        }

        @Override public void changed() {
            guard();
            applied = false;
            context.changed().run();
        }
    }

    /** A standard row that keeps its preferred height, so a plugin's vertical box cannot stretch it. */
    private static final class KeepsHeight extends JPanel {
        KeepsHeight(JComponent row) {
            super(new BorderLayout());
            setOpaque(false);
            add(row, BorderLayout.CENTER);
        }

        @Override public Dimension getMaximumSize() {
            return isMaximumSizeSet() ? super.getMaximumSize() : new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }
}
