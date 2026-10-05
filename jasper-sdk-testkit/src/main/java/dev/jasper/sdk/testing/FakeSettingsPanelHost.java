package dev.jasper.sdk.testing;

import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingsPanelHost;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JLabel;

/**
 * The fake's host for a plugin's own settings page: a labelled placeholder per row, and the values it staged.
 */
final class FakeSettingsPanelHost implements SettingsPanelHost {
    private final FakePluginSettings settings;
    final Map<String, Object> pending = new LinkedHashMap<>();
    int changes;
    boolean closed, applied;
    FakeKitView view;
    javax.swing.JComponent component;
    final KitSettingRows rows = new KitSettingRows(this, this::spec);
    private dev.jasper.sdk.settings.SettingSpec<?> spec(Setting<?> setting){return settings.handle(setting).spec();}
    private void guard(){if(closed)throw new IllegalStateException("Settings page is closed");}
    @Override public dev.jasper.sdk.ui.Nodes.Node rowNode(Setting<?> setting){guard();return rows.row(setting);}


    FakeSettingsPanelHost(FakePluginSettings settings) { this.settings = settings; }

    @Override public JComponent row(Setting<?> setting) {
        return new JLabel(settings.handle(setting).spec().label());
    }

    @SuppressWarnings("unchecked")
    @Override public <T> T pending(Setting<T> setting) {
        guard();settings.handle(setting);
        return pending.containsKey(setting.key()) ? (T) pending.get(setting.key()) : setting.get();
    }

    @SuppressWarnings("unchecked")
    @Override public <T> void stage(Setting<T> setting, T value) {
        guard();applied=false;var handle = (FakeSetting<T>) settings.handle(setting);
        Optional<String> problem = handle.spec().kind().check(value);
        if (problem.isPresent()) throw new IllegalArgumentException(problem.get());
        rows.staged(setting);pending.put(setting.key(), value);if(view!=null)view.update(view.spec());
    }

    @Override public void changed() { guard();applied=false;changes++; }
}
