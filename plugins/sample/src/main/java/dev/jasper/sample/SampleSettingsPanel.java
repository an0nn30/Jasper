package dev.jasper.sample;

import dev.jasper.sdk.settings.SettingsPanel;
import dev.jasper.sdk.settings.SettingsPanelHost;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.util.List;
import java.util.Optional;

/** The sample Settings page uses host-owned draft rows, including its 0–5000 ms step delay. */
final class SampleSettingsPanel implements SettingsPanel {
    private final UiKit kit;
    private final SampleSettings settings;

    SampleSettingsPanel(UiKit kit, SampleSettings settings) { this.kit = kit; this.settings = settings; }

    @Override public UiView create(SettingsPanelHost host) {
        return kit.create(new ViewSpec(new Nodes.Form("sample.settings", ControlState.READY, Nodes.FormKind.SETTINGS,
            "Sample", Optional.empty(), List.of(
                host.row(settings.activity), host.row(settings.ui), host.row(settings.terminal),
                host.row(settings.session), host.row(settings.scope), host.row(settings.stepMillis)),
            Optional.empty(), List.of())), event -> { });
    }

    @Override public List<String> keywords() { return List.of("sample", "demo", "step delay", "milliseconds"); }
}
