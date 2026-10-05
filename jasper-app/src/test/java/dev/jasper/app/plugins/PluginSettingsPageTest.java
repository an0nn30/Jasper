package dev.jasper.app.plugins;

import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.settings.Setting;
import dev.jasper.app.settings.TomlDocument;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SettingsPage;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.settings.Choice;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginSettingsPageTest {
    private static final String ID = "test.pages";
    @TempDir Path data;
    private final List<ConfigDiagnostic> diagnostics = new CopyOnWriteArrayList<>();
    private final List<String> navigated = new ArrayList<>();
    private final int[] changes = {0};
    private PluginHost host;
    private final PageContext context = new PageContext(null, navigated::add, Runnable::run, (title, initial) -> Optional.empty(),
        () -> changes[0]++, () -> null, dev.jasper.app.testsupport.KitTestSupport::context);

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

    private Path file(String id) { return data.resolve(id).resolve(id + ".toml"); }

    private void write(String id, String text) throws Exception {
        Files.createDirectories(file(id).getParent());
        Files.writeString(file(id), text);
    }

    private void start(String id, String name, Consumer<PluginSettings> declare) {
        onEdt(() -> host.start(new HostedPlugin(new PluginInfo(id, name, "1.0.0", Set.of()), Set.of(), Set.of(), Set.of(),
            PluginSettingsPageTest.class.getClassLoader(), () -> started -> declare.accept(started.settings()))));
        assertThat(host.active(id)).isTrue();
    }

    private void reload() {
        onEdt(() -> {
            var changed = host.environment.settingsFiles().refresh(true);
            for (String id : changed) host.environment.clearReports().accept(host.environment.settingsFiles().file(id));
            host.settingsChanged(changed);
        });
    }

    private PluginSettingsPage page() {
        var pages = onEdtValue(() -> host.settingsPages(context));
        assertThat(pages).singleElement().isInstanceOf(PluginSettingsPage.class);
        return (PluginSettingsPage) pages.getFirst();
    }

    private static void declareEditor(PluginSettings settings) {
        settings.declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size")
            .description("Font size in points.").section("Editor"));
        settings.declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode").section("Editor"));
        settings.declare(SettingSpec.of("sftp.parallel", SettingKind.integer(1, 8), 2).label("Parallel files").section("SFTP"));
        settings.declare(SettingSpec.of("mode", SettingKind.choice(List.of(Choice.of("fast", "Fast", 1), Choice.of("safe", "Safe", 2))), 1)
            .label("Mode").section("SFTP"));
    }

    @Test void thePageFollowsTheDeclarationsUnderTools() {
        start(ID, "Pages", PluginSettingsPageTest::declareEditor);
        start("test.empty", "Empty", settings -> { });
        var page = page();
        onEdt(() -> {
            assertThat(page.id()).isEqualTo("plugin." + ID);
            assertThat(page.title()).isEqualTo("Pages");
            assertThat(page.parent()).contains("tools");
            assertThat(page.helpAnchor()).isEqualTo("plugins");
            assertThat(page.searchTerms()).contains("Pages", "Font size", "Font size in points.", "Editor", "SFTP", "Fast", "Safe");
            assertThat(page.component()).isNotNull();
            assertThat(page.modified()).isFalse();
            assertThat(page.valid()).isTrue();
        });
    }

    @Test @SuppressWarnings("unchecked") void applyWritesThePluginsFileAndTheDraftFollows() throws Exception {
        write(ID, "# Pages\nfont_size = 13\n");
        start(ID, "Pages", PluginSettingsPageTest::declareEditor);
        var page = page();
        var size = (Setting<Integer>) page.declared().handles().get(0).appSetting();
        var parallel = (Setting<Integer>) page.declared().handles().get(2).appSetting();
        onEdt(() -> {
            page.component();
            page.draft().stage(size, Optional.of(15));
            page.draft().stage(parallel, Optional.of(4));
            assertThat(page.modified()).isTrue();
            assertThat(changes[0]).as("the dialog hears about draft changes").isPositive();
        });
        var applied = new AtomicReference<java.util.concurrent.CompletableFuture<Void>>();
        onEdt(() -> applied.set(page.apply()));
        applied.get().get(5, TimeUnit.SECONDS);
        onEdt(() -> { });
        String text = Files.readString(file(ID));
        assertThat(text).startsWith("# Pages\n");
        assertThat(TomlDocument.leaves(text)).containsEntry(List.of("font_size"), 15L).containsEntry(List.of("sftp", "parallel"), 4L);
        onEdt(() -> assertThat(page.modified()).as("the draft follows the saved file").isFalse());
    }

    @Test @SuppressWarnings("unchecked") void aRejectedFileShowsItsMessageAndBlocksItsEditsUntilFixed() throws Exception {
        write(ID, "font_size = \"big\"\n");
        start(ID, "Pages", PluginSettingsPageTest::declareEditor);
        var page = page();
        var size = (Setting<Integer>) page.declared().handles().get(0).appSetting();
        onEdt(() -> {
            page.component();
            assertThat(page.valid()).as("an untouched page does not hold up the dialog").isTrue();
            assertThat(page.message().isVisible()).isTrue();
            assertThat(page.message().getText()).contains("has errors");
            page.draft().stage(size, Optional.of(15));
            assertThat(page.valid()).as("an edit cannot be saved to a rejected file").isFalse();
            page.reset();
            assertThat(page.valid()).isTrue();
        });
        write(ID, "font_size = 20\n");
        reload();
        onEdt(() -> {
            assertThat(page.valid()).isTrue();
            assertThat(page.message().isVisible()).isFalse();
            assertThat(page.draft().value(size)).isEqualTo(20);
        });
    }

    @Test @SuppressWarnings("unchecked") void resetDropsPendingEditsAndAChangeOnDiskMovesTheBaseline() throws Exception {
        write(ID, "font_size = 13\n");
        start(ID, "Pages", PluginSettingsPageTest::declareEditor);
        var page = page();
        var size = (Setting<Integer>) page.declared().handles().get(0).appSetting();
        var vim = (Setting<Boolean>) page.declared().handles().get(1).appSetting();
        onEdt(() -> {
            page.component();
            page.draft().stage(vim, Optional.of(true));
            page.reset();
            assertThat(page.modified()).isFalse();
            page.draft().stage(vim, Optional.of(true));
        });
        write(ID, "font_size = 20\n");
        reload();
        onEdt(() -> {
            assertThat(page.draft().baseline().get(size)).isEqualTo(20);
            assertThat(page.modified()).as("the pending edit stays").isTrue();
            page.dispose();
        });
    }
}
