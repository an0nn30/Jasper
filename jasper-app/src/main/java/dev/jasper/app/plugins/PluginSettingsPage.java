package dev.jasper.app.plugins;

import dev.jasper.app.settings.Setting;
import dev.jasper.app.settingsui.Editors;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SchemaPage;
import dev.jasper.app.settingsui.SettingsDraft;
import dev.jasper.app.settingsui.SettingsPage;
import dev.jasper.sdk.settings.Choice;
import dev.jasper.sdk.settings.EditorHint;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.JComponent;
import javax.swing.JLabel;

/**
 * The page Settings generates for a running plugin's declared settings: rows in declaration order under their
 * sections, over the plugin's own draft, saved to the plugin's file on Apply. EDT only.
 */
final class PluginSettingsPage implements SettingsPage {
    private final String title;
    private final DeclaredSettings declared;
    private final SettingsDraft draft;
    private final SchemaPage inner;
    private final Runnable stopListening;
    private final dev.jasper.app.lifecycle.Subscription draftListening;
    private final JLabel message = PluginPageBody.message();
    private JComponent body;

    PluginSettingsPage(String pluginId, String title, DeclaredSettings declared, PageContext context) {
        this.title = title;
        this.declared = declared;
        draft = new SettingsDraft(declared.values());
        var own = new PageContext(draft, context.navigate(), context.closeThen(), context.chooseFile(), context.changed(), context.surface());
        inner = spec(PluginRuntime.settingsPageId(pluginId), title, declared.handles()).build(own);
        stopListening = declared.onReloaded(() -> {
            draft.rebase(declared.values());
            showMessage();
            context.changed().run();
        });
        draftListening = draft.onChanged(context.changed());
    }

    SettingsDraft draft() { return draft; }
    DeclaredSettings declared() { return declared; }
    JLabel message() { return message; }

    /** The rows of a plugin's page: declaration order, a heading wherever the section changes. */
    static SchemaPage.Spec spec(String id, String title, List<DeclaredSetting<?>> handles) {
        var spec = SchemaPage.page(id, title).parent(PluginRuntime.TOOLS).anchor("plugins");
        String section = "";
        for (DeclaredSetting<?> handle : handles) {
            String next = handle.spec().section();
            if (!next.equals(section)) {
                if (!next.isEmpty()) spec.section(next);
                section = next;
            }
            row(spec, handle);
        }
        return spec;
    }

    /** One declared setting's row, with its description as the row's note. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void row(SchemaPage.Spec spec, DeclaredSetting<?> handle) {
        Setting setting = handle.appSetting();
        spec.row(setting, editor(handle));
        if (!handle.spec().description().isEmpty()) spec.note(handle.spec().description());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Editors.Factory editor(DeclaredSetting<?> handle) {
        var spec = handle.spec();
        EditorHint hint = spec.editor().orElse(null);
        if (hint instanceof EditorHint.Font font) return Editors.font(font.monospaced());
        if (hint instanceof EditorHint.File) return Editors.path("Choose a file");
        return switch (spec.kind()) {
            case dev.jasper.sdk.settings.SettingKind.OneOf<?> oneOf -> Editors.choice(names(oneOf));
            case dev.jasper.sdk.settings.SettingKind.NumberRange range -> Editors.number(0.1, "");
            default -> Editors.byKind((Setting) handle.appSetting());
        };
    }

    private static Map<String, String> names(dev.jasper.sdk.settings.SettingKind.OneOf<?> oneOf) {
        var names = new LinkedHashMap<String, String>();
        for (Choice<?> choice : oneOf.choices()) names.put(choice.token(), choice.displayName());
        return names;
    }

    @Override public String id() { return inner.id(); }
    @Override public String title() { return title; }
    @Override public Optional<String> parent() { return Optional.of(PluginRuntime.TOOLS); }
    @Override public String helpAnchor() { return "plugins"; }
    @Override public List<String> searchTerms() { return inner.searchTerms(); }

    @Override public JComponent component() {
        if (body == null) {
            body = new PluginPageBody(message, inner.component());
            showMessage();
        }
        return body;
    }

    @Override public boolean modified() { return draft.modified(); }
    /** A rejected file holds up only this page's own edits, which could not be saved to it. */
    @Override public boolean valid() { return !(declared.rejected() && draft.modified()) && inner.valid(); }
    @Override public void reset() { inner.reset(); }

    /** Saves the draft; the plugin's reload moves the draft's baseline, which drops the edits now saved. */
    @Override public CompletableFuture<Void> apply() {
        return draft.modified() ? declared.save(draft.edit()) : CompletableFuture.completedFuture(null);
    }

    @Override public void highlight(String query) { inner.highlight(query); }

    @Override public void refresh() {
        draft.rebase(declared.values());
        inner.refresh();
        showMessage();
    }

    @Override public void dispose() {
        stopListening.run();
        draftListening.close();
        inner.dispose();
    }

    private void showMessage() { PluginPageBody.showMessage(message, declared); }
}
