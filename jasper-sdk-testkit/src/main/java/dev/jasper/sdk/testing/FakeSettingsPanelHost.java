package dev.jasper.sdk.testing;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingsPanelHost;
import dev.jasper.sdk.ui.*;
import java.awt.EventQueue;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JLabel;

/** Independent in-memory settings draft. Empty edits are Reset tombstones, not explicit defaults. */
final class FakeSettingsPanelHost implements SettingsPanelHost {
    private final FakePluginSettings settings;
    private final Map<FakeSetting<?>, Optional<Object>> draft = new LinkedHashMap<>();
    private final Map<String, Row> rows = new LinkedHashMap<>();
    private final Subscription reloads;
    int changes;
    private String query = "";
    boolean closed, applied;
    FakeKitView view;
    JComponent component;

    private static final class Row {
        final FakeSetting<?> setting;
        String invalidText;
        String error = "";
        Row(FakeSetting<?> setting) { this.setting = setting; }
        void clear() { invalidText = null; error = ""; }
    }

    FakeSettingsPanelHost(FakePluginSettings settings) {
        this.settings = settings;
        reloads = settings.onReloaded(() -> {
            if (EventQueue.isDispatchThread()) rebase(); else EventQueue.invokeLater(this::rebase);
        });
    }

    private void guard() { if (closed) throw new IllegalStateException("Settings page is closed"); }


    @Override public Nodes.Node row(Setting<?> setting) {
        guard();
        var handle = settings.handle(setting);
        var row = rows.computeIfAbsent("settings:" + handle.key(), ignored -> new Row(handle));
        return KitSettingRows.row(handle.spec(), pending(handle), row.invalidText, row.error);
    }

    @SuppressWarnings("unchecked")
    @Override public <T> T pending(Setting<T> setting) {
        guard();
        var handle = settings.handle(setting);
        var edit = draft.get(handle);
        return edit == null ? setting.get() : (T) edit.orElse(handle.spec().defaultValue());
    }

    @Override public <T> void stage(Setting<T> setting, T value) {
        guard();
        var handle = settings.handle(setting);
        Object checked = handle.checked(value);
        Optional<Object> edit = checked.equals(handle.spec().defaultValue()) && !settings.raw().containsKey(handle.key())
            ? Optional.empty() : Optional.of(checked);
        draft.put(handle, edit);
        var row = rows.get("settings:" + handle.key());
        if (row != null) row.clear();
        applied = false;
        rebase();
    }

    /** Refresh saved values while retaining edits until the saved representation matches them. */
    private void rebase() {
        if (closed) return;
        draft.entrySet().removeIf(entry -> entry.getValue().isEmpty()
            ? !settings.raw().containsKey(entry.getKey().key())
            : settings.raw().containsKey(entry.getKey().key()) && entry.getValue().get().equals(entry.getKey().get()));
        refresh();
    }

    private void refresh() { if (!closed && view != null) view.update(view.sourceSpec()); }
    void highlight(String value) { guard(); query = java.util.Objects.requireNonNull(value); refresh(); }
    boolean valid() { return rows.values().stream().allMatch(row -> row.error.isEmpty()); }

    /** The fake receives typed intent directly and uses FakeSetting's own conversion/validation. */
    boolean event(UiEvent event) {
        guard();
        String id = event.id();
        Row row = rows.get(id);
        if (event instanceof UiEvent.Action) {
            row = rows.values().stream().filter(value -> id.equals("settings:" + value.setting.key() + ":reset")).findFirst().orElse(null);
            if (row == null) return false;
            row.clear();
            draft.put(row.setting, Optional.empty());
            rebase();
        } else {
            if (row == null || !(event instanceof UiEvent.Edited || event instanceof UiEvent.Toggled || event instanceof UiEvent.Selected)) return false;
            try { stageValue(row.setting, row.setting.edited(event)); }
            catch (RuntimeException invalid) {
                row.error = invalid.getMessage() == null ? "Enter a valid value." : invalid.getMessage();
                if (event instanceof UiEvent.Edited edit) row.invalidText = edit.text();
            }
        }
        changed();
        refresh();
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void stageValue(FakeSetting setting, Object value) { stage(setting, value); }

    ViewSpec descriptor(ViewSpec spec) {
        return KitSettingRows.project(spec, node -> {
            if (node instanceof Nodes.SettingRow row) {
                var owned = rows.values().stream().filter(value -> row.id().equals("settings:" + value.setting.key() + ":row")).findFirst();
                if (owned.isPresent()) {
                    // Rebuilt from the draft, keeping the presentation the plugin asked for (its search highlight).
                    var fresh = (Nodes.SettingRow) row(owned.get().setting);
                    return new Nodes.SettingRow(fresh.id(), fresh.state(), fresh.title(), fresh.description(), fresh.control(), fresh.dependent(),
                        row.highlighted() || KitSettingRows.matches(owned.get().setting.spec(), query));
                }
            }
            return node;
        });
    }

    Map<String, Object> pendingValues() {
        var result = new LinkedHashMap<String, Object>();
        draft.forEach((setting, value) -> result.put(setting.key(), value.orElse(setting.spec().defaultValue())));
        return Map.copyOf(result);
    }

    void saveDraft() {
        guard();
        if (!valid()) throw new IllegalStateException("Settings page is invalid");
        // Publish one snapshot, then one reload, rather than invalidating the draft during iteration.
        Map.copyOf(draft).forEach(FakeSetting::writeDraft);
        settings.reload();
    }

    void closeDraft() {
        if (closed) return;
        closed = true;
        reloads.close();
        draft.clear();
        rows.clear();
    }

    @Override public void changed() { guard(); applied = false; changes++; }
}
