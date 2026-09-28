package dev.jasper.app.plugins;

import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.settings.TomlDocument;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SettingsPage;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import dev.jasper.sdk.settings.SettingsPanel;
import dev.jasper.sdk.settings.SettingsPanelHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginPanelPageTest {
    private static final String ID = "test.panel";
    @TempDir Path data;
    private final List<ConfigDiagnostic> diagnostics = new CopyOnWriteArrayList<>();
    private final int[] changes = {0};
    private PluginHost host;
    private final PageContext context = new PageContext(null, id -> { }, Runnable::run, (title, initial) -> Optional.empty(),
        () -> changes[0]++);

    @BeforeEach void open() {
        host = AppContractTest.host(data, Duration.ofMillis(200), onEdtValue(Contributions::new), new AtomicBoolean(true),
            onEdtValue(AppContractTest::headlessWindows), onEdtValue(TerminalRegistry::new), new CopyOnWriteArrayList<>(),
            new CopyOnWriteArrayList<>(), diagnostics);
    }

    @AfterEach void stop() {
        var pending = new AtomicReference<List<java.util.concurrent.CompletableFuture<?>>>(List.of());
        onEdt(() -> pending.set(host.stop()));
        java.util.concurrent.CompletableFuture.allOf(pending.get().toArray(java.util.concurrent.CompletableFuture[]::new)).join();
    }

    private void start(Consumer<PluginSettings> declare) {
        onEdt(() -> host.start(new HostedPlugin(new PluginInfo(ID, "Panel", "1.0.0", Set.of()), Set.of(), Set.of(), Set.of(),
            PluginPanelPageTest.class.getClassLoader(), () -> started -> declare.accept(started.settings()))));
        assertThat(host.active(ID)).isTrue();
    }

    /** A plugin page with one standard row, one control of its own for {@code size}, and its own state. */
    private static final class Panel implements SettingsPanel {
        final Setting<Boolean> vim;
        final Setting<Integer> size;
        final List<String> calls = new ArrayList<>();
        boolean ownModified;
        SettingsPanelHost host;
        Panel(Setting<Boolean> vim, Setting<Integer> size) { this.vim = vim; this.size = size; }
        @Override public JComponent create(SettingsPanelHost host) {
            this.host = host;
            var page = new JPanel();
            page.add(host.row(vim));
            page.add(new JLabel("size " + host.pending(size)));
            return page;
        }
        @Override public List<String> keywords() { return List.of("panel", "custom"); }
        @Override public void highlight(String query) { calls.add("highlight " + query); }
        @Override public boolean modified() { return ownModified; }
        @Override public void apply() { calls.add("apply"); ownModified = false; }
        @Override public void reset() { calls.add("reset"); ownModified = false; }
        @Override public void dispose() { calls.add("dispose"); }
    }

    private Panel startWithPanel() {
        var panel = new AtomicReference<Panel>();
        start(settings -> {
            var vim = settings.declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode").keywords("modal"));
            var size = settings.declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size"));
            panel.set(new Panel(vim, size));
            settings.page(panel.get());
        });
        return panel.get();
    }

    @Test void theOwnPageGetsRowsStagesValuesAndAppliesBothKindsOfState() throws Exception {
        Files.createDirectories(data.resolve(ID));
        Files.writeString(data.resolve(ID).resolve(ID + ".toml"), "font_size = 13\n");
        var panel = startWithPanel();
        var pages = onEdtValue(() -> host.settingsPages(context));
        assertThat(pages).singleElement().isInstanceOf(PluginPanelPage.class);
        SettingsPage page = pages.getFirst();
        onEdt(() -> {
            assertThat(page.id()).isEqualTo("plugin." + ID);
            assertThat(page.parent()).contains("tools");
            assertThat(page.searchTerms()).contains("Panel", "Vim mode", "modal", "panel", "custom");
            page.component();
            assertThat(panel.host).isNotNull();
            panel.host.stage(panel.size, 20);
            assertThat(panel.host.pending(panel.size)).isEqualTo(20);
            assertThatIllegalArgumentException().isThrownBy(() -> panel.host.stage(panel.size, 99));
            assertThat(page.modified()).isTrue();
            panel.ownModified = true;
            page.highlight("vim");
            assertThat(panel.calls).containsExactly("highlight vim");
        });
        var applied = new AtomicReference<java.util.concurrent.CompletableFuture<Void>>();
        onEdt(() -> applied.set(page.apply()));
        applied.get().get(5, TimeUnit.SECONDS);
        onEdt(() -> { });
        assertThat(TomlDocument.leaves(Files.readString(data.resolve(ID).resolve(ID + ".toml")))).containsEntry(List.of("font_size"), 20L);
        onEdt(() -> {
            assertThat(panel.calls).contains("apply");
            assertThat(page.modified()).isFalse();
            page.dispose();
            assertThat(panel.calls).contains("dispose");
        });
    }

    @Test void aPageThatFailsToBuildShowsWhyAndSettingsCarriesOn() {
        start(settings -> {
            settings.declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode"));
            settings.page(rows -> { throw new IllegalStateException("boom"); });
        });
        var page = (PluginPanelPage) onEdtValue(() -> host.settingsPages(context)).getFirst();
        onEdt(() -> {
            assertThat(page.component()).isNotNull();
            assertThat(page.content()).isInstanceOf(JLabel.class);
            assertThat(((JLabel) page.content()).getText()).isEqualTo("This page could not be shown: boom");
        });
        assertThat(host.failures(ID)).isPositive();
    }

    @Test void aFailureWithoutAMessageShowsWhatItWas() {
        start(settings -> {
            settings.declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode"));
            settings.page(rows -> { throw new IllegalStateException(); });
        });
        var page = (PluginPanelPage) onEdtValue(() -> host.settingsPages(context)).getFirst();
        onEdt(() -> {
            page.component();
            assertThat(((JLabel) page.content()).getText())
                .isEqualTo("This page could not be shown: java.lang.IllegalStateException");
        });
    }

    @Test void aRejectedFileShowsItsMessageAndBlocksOnlyThisPagesEdits() throws Exception {
        write("font_size = \"big\"\n");
        var panel = startWithPanel();
        var page = (PluginPanelPage) onEdtValue(() -> host.settingsPages(context)).getFirst();
        onEdt(() -> {
            page.component();
            assertThat(page.message().isVisible()).isTrue();
            assertThat(page.message().getText()).contains("has errors");
            assertThat(page.valid()).as("an untouched page does not hold up the dialog").isTrue();
            panel.host.stage(panel.size, 20);
            assertThat(page.valid()).as("an edit cannot be saved to a rejected file").isFalse();
            page.reset();
            assertThat(page.valid()).isTrue();
        });
        write("font_size = 20\n");
        reload();
        onEdt(() -> {
            assertThat(page.message().isVisible()).isFalse();
            assertThat(panel.host.pending(panel.size)).isEqualTo(20);
            page.dispose();
        });
    }

    /** A plugin page laid out like the Sample's: standard rows in a vertical box. */
    private static final class BoxPanel implements SettingsPanel {
        final Setting<Boolean> vim;
        final Setting<Integer> size;
        final List<JComponent> rows = new ArrayList<>();
        BoxPanel(Setting<Boolean> vim, Setting<Integer> size) { this.vim = vim; this.size = size; }
        @Override public JComponent create(SettingsPanelHost host) {
            var page = new JPanel();
            page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
            for (var setting : List.<Setting<?>>of(vim, size)) {
                JComponent row = host.row(setting);
                row.setAlignmentX(0);
                rows.add(row);
                page.add(row);
            }
            return page;
        }
    }

    @Test void rowsStayTogetherAtTheTopOfATallPage() {
        var panel = new AtomicReference<BoxPanel>();
        start(settings -> {
            var vim = settings.declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode"));
            var size = settings.declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size"));
            panel.set(new BoxPanel(vim, size));
            settings.page(panel.get());
        });
        var page = onEdtValue(() -> host.settingsPages(context)).getFirst();
        onEdt(() -> {
            var scroll = new JScrollPane(page.component());
            scroll.setSize(700, 1400);
            LayoutTestSupport.layoutTree(scroll);
            JComponent first = panel.get().rows.get(0), second = panel.get().rows.get(1);
            assertThat(first.getHeight()).isEqualTo(first.getPreferredSize().height);
            assertThat(second.getY()).as("the second row follows the first")
                .isLessThan(first.getY() + first.getPreferredSize().height + 100);
            assertThat(second.getHeight()).isEqualTo(second.getPreferredSize().height);
            page.dispose();
        });
    }

    @Test void closingWithoutApplyResetsThePanelAndClosingAfterApplyDoesNot() throws Exception {
        var panel = startWithPanel();
        var cancelled = onEdtValue(() -> host.settingsPages(context)).getFirst();
        onEdt(() -> {
            cancelled.component();
            panel.ownModified = true;
            cancelled.dispose();
            assertThat(panel.calls).containsExactly("reset", "dispose");
            panel.calls.clear();
        });
        var applied = onEdtValue(() -> host.settingsPages(context)).getFirst();
        onEdt(() -> {
            applied.component();
            panel.host.stage(panel.size, 20);
            panel.ownModified = true;
        });
        var saving = new AtomicReference<java.util.concurrent.CompletableFuture<Void>>();
        onEdt(() -> saving.set(applied.apply()));
        saving.get().get(5, TimeUnit.SECONDS);
        onEdt(() -> { });
        onEdt(() -> {
            applied.dispose();
            assertThat(panel.calls).containsExactly("apply", "dispose");
        });
    }

    @Test void stagingTheDefaultOfAnUnsetKeyLeavesThePageUnmodified() {
        var panel = startWithPanel();
        var page = onEdtValue(() -> host.settingsPages(context)).getFirst();
        onEdt(() -> {
            page.component();
            panel.host.stage(panel.size, 20);
            assertThat(page.modified()).isTrue();
            panel.host.stage(panel.size, 13);
            assertThat(page.modified()).isFalse();
            page.dispose();
        });
    }

    @Test void eachPluginGetsOnePageAndASupersededCopyGetsNone() {
        assertThat(PluginRuntime.stubbed(status("dev.example.x", PluginStatus.State.SKIPPED,
            "superseded by version 2.0.0 from DEVELOPMENT"))).isFalse();
        var running = new PluginStubPage("dev.example.run", "Run", "", () -> { });
        var statuses = List.of(
            status("dev.example.run", PluginStatus.State.ACTIVE, ""),
            status("dev.example.run", PluginStatus.State.DISABLED, "safe mode"),
            status("dev.example.quiet", PluginStatus.State.ACTIVE, ""),
            status("dev.example.quiet", PluginStatus.State.DISABLED, "safe mode"),
            status("dev.example.old", PluginStatus.State.SKIPPED, "superseded by version 2.0.0 from USER"),
            status("dev.example.two", PluginStatus.State.NEEDS_CONSENT, "not reviewed yet"),
            status("dev.example.two", PluginStatus.State.NEEDS_CONSENT, "not reviewed yet"),
            status("dev.example.gap", PluginStatus.State.SKIPPED, "requires dev.example.none, which is not installed"));
        var pages = PluginRuntime.withStubs(List.of(running), statuses,
            status -> new PluginStubPage(status.id(), status.name(), status.reason(), () -> { }));
        assertThat(pages).map(SettingsPage::id).doesNotHaveDuplicates()
            .containsExactlyInAnyOrder("plugin.dev.example.run", "plugin.dev.example.two", "plugin.dev.example.gap");
        assertThat(pages).contains(running);
    }

    @Test void pluginsThatAreNotRunningGetAStubAndOnlyWhenTheUserMeantThemToRun() {
        var opened = new ArrayList<String>();
        var stub = new PluginStubPage("dev.example.off", "Off", "not reviewed yet", () -> opened.add("open"));
        onEdt(() -> {
            assertThat(stub.id()).isEqualTo("plugin.dev.example.off");
            assertThat(stub.parent()).contains("tools");
            stub.component();
            assertThat(stub.text()).isEqualTo("Start Off to edit its settings.");
            assertThat(stub.reason()).isEqualTo("Not running: not reviewed yet");
            stub.open.doClick();
        });
        assertThat(opened).containsExactly("open");
        assertThat(PluginRuntime.stubbed(status(PluginStatus.State.NEEDS_CONSENT, "not reviewed yet"))).isTrue();
        assertThat(PluginRuntime.stubbed(status(PluginStatus.State.FAILED, "boom"))).isTrue();
        assertThat(PluginRuntime.stubbed(status(PluginStatus.State.SKIPPED, "requires x"))).isTrue();
        assertThat(PluginRuntime.stubbed(status(PluginStatus.State.DISABLED, "safe mode"))).isTrue();
        assertThat(PluginRuntime.stubbed(status(PluginStatus.State.DISABLED, "disabled"))).isFalse();
        assertThat(PluginRuntime.stubbed(status(PluginStatus.State.ACTIVE, ""))).isFalse();
    }

    private static PluginStatus status(PluginStatus.State state, String reason) { return status("dev.example.x", state, reason); }

    private static PluginStatus status(String id, PluginStatus.State state, String reason) {
        return new PluginStatus(id, id.substring(id.lastIndexOf('.') + 1), "1.0.0", PluginCandidate.Origin.USER, state, reason);
    }

    private void write(String text) throws Exception {
        Files.createDirectories(data.resolve(ID));
        Files.writeString(data.resolve(ID).resolve(ID + ".toml"), text);
    }

    private void reload() {
        onEdt(() -> {
            var changed = host.environment.settingsFiles().refresh(true);
            for (String id : changed) host.environment.clearReports().accept(host.environment.settingsFiles().file(id));
            host.settingsChanged(changed);
        });
    }
}
