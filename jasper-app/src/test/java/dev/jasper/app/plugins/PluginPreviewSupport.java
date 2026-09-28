package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SettingsPage;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.settings.EditorHint;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import java.awt.BorderLayout;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;

/** For previews: two in-memory plugins, Editor with a generated page and Sample with a page of its own. UI thread. */
public final class PluginPreviewSupport implements AutoCloseable {
    private final PluginHost host;

    public PluginPreviewSupport(Path data) {
        host = AppContractTest.host(data, Map.of(), Duration.ofMillis(200), new Contributions(), new AtomicBoolean(true),
            AppContractTest.headlessWindows(), new TerminalRegistry(), new CopyOnWriteArrayList<>(), new CopyOnWriteArrayList<>());
        start("dev.example.editor", "Editor", context -> {
            var settings = context.settings();
            settings.declare(SettingSpec.of("font_family", SettingKind.text(), "JetBrains Mono").label("Font")
                .editor(EditorHint.font(true)).section("Editor"));
            settings.declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size")
                .description("Points.").section("Editor"));
            settings.declare(SettingSpec.of("tab_size", SettingKind.integer(1, 16), 4).label("Tab size").section("Editor"));
            settings.declare(SettingSpec.of("soft_wrap", SettingKind.bool(), false).label("Wrap long lines")
                .description("Applies to new tabs.").section("Editor"));
            settings.declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode").section("Editor"));
            settings.declare(SettingSpec.of("max_file_mib", SettingKind.integer(1, 256), 10).label("Largest file")
                .description("Larger files are refused.").section("Files"));
        });
        start("dev.example.sample", "Sample", context -> {
            var settings = context.settings();
            var ui = settings.declare(SettingSpec.of("demo_ui", SettingKind.bool(), false).label("Show the sample action"));
            var delay = settings.declare(SettingSpec.of("demo_step_millis", SettingKind.integer(0, 5000), 300).label("Step delay"));
            settings.page(rows -> {
                var page = new JPanel();
                page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
                page.setOpaque(false);
                JComponent row = rows.row(ui);
                row.setAlignmentX(0);
                page.add(row);
                var slider = new JSlider(0, 5000, rows.pending(delay));
                var own = new JPanel(new BorderLayout(8, 0));
                own.setOpaque(false);
                own.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
                own.setAlignmentX(0);
                own.add(new JLabel("Step delay"), BorderLayout.WEST);
                own.add(slider, BorderLayout.CENTER);
                page.add(own);
                return page;
            });
        });
    }

    private void start(String id, String name, Plugin plugin) {
        host.start(new HostedPlugin(new PluginInfo(id, name, "1.0.0", Set.of()), Set.of(), Set.of(), Set.of(),
            PluginPreviewSupport.class.getClassLoader(), () -> plugin));
    }

    /** Settings pages for the two plugins. */
    public Function<PageContext, List<SettingsPage>> pages() { return host::settingsPages; }

    /** Stops the plugins; call off the UI thread. */
    @Override public void close() {
        var pending = new AtomicReference<List<CompletableFuture<?>>>(List.of());
        AppContractTest.onEdt(() -> pending.set(host.stop()));
        CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).join();
    }
}
