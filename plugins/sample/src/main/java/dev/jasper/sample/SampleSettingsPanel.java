package dev.jasper.sample;

import dev.jasper.sdk.settings.SettingsPanel;
import dev.jasper.sdk.settings.SettingsPanelHost;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.util.List;

/**
 * The sample Settings page uses host-owned draft rows, including its 0–5000 ms step delay, composed as every Settings
 * page is: a settings page of sections, each a label above a settings card of rows.
 */
final class SampleSettingsPanel implements SettingsPanel {
    static final String PAGE = "sample.settings", DEMOS = "sample.settings.demos", TIMING = "sample.settings.timing";
    private final UiKit kit;
    private final SampleSettings settings;

    SampleSettingsPanel(UiKit kit, SampleSettings settings) { this.kit = kit; this.settings = settings; }

    @Override public UiView create(SettingsPanelHost host) {
        return kit.create(new ViewSpec(new Nodes.Stack(PAGE, ControlState.READY, Nodes.LayoutRole.SETTINGS_PAGE, List.of(
            section(DEMOS, "Demos", List.of(host.row(settings.activity), host.row(settings.ui), host.row(settings.terminal),
                host.row(settings.session), host.row(settings.scope))),
            section(TIMING, "Timing", List.of(host.row(settings.stepMillis)))))), event -> { });
    }

    private static Nodes.Section section(String id, String title, List<Nodes.Node> rows) {
        return new Nodes.Section(id, ControlState.READY, title, new Nodes.Card(id + ".card", ControlState.READY, Nodes.CardKind.SETTINGS, rows));
    }

    @Override public List<String> keywords() { return List.of("sample", "demo", "step delay", "milliseconds"); }
}
