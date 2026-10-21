package dev.jasper.app.plugins;

import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.config.DefaultSource;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.settings.TomlDocument;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.settings.Choice;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeclaredSettingsTest {
    private static final String ID = "test.declared";
    @TempDir Path data;
    private final List<ConfigDiagnostic> diagnostics = new CopyOnWriteArrayList<>();
    private PluginHost host;

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

    private final List<String> lookProblems = new CopyOnWriteArrayList<>();

    private Path file() { return data.resolve(ID).resolve(ID + ".toml"); }

    /** The look gives this plugin {@code table} as its defaults (a kit's {@code [defaults."test.declared"]}). */
    private void look(Map<String, Object> table) {
        host.defaults = new DefaultSource(Map.of(), Map.of(ID, table));
        host.lookDefaultProblem = lookProblems::add;
    }

    private void write(String text) throws Exception {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), text);
    }

    /** Starts the plugin; {@code declare} runs inside its start. */
    private PluginSettings start(Consumer<PluginSettings> declare) {
        var context = new AtomicReference<PluginContext>();
        onEdt(() -> host.start(new HostedPlugin(new PluginInfo(ID, "Declared", "1.0.0", Set.of()), Set.of(), Set.of(), Set.of(),
            DeclaredSettingsTest.class.getClassLoader(), () -> started -> { context.set(started); declare.accept(started.settings()); })));
        assertThat(host.active(ID)).as("started").isTrue();
        return context.get().settings();
    }

    private void reload() {
        onEdt(() -> {
            var changed = host.environment.settingsFiles().refresh(true);
            for (String id : changed) host.environment.clearReports().accept(host.environment.settingsFiles().file(id));
            host.settingsChanged(changed);
        });
    }

    private List<String> reports() {
        return diagnostics.stream().filter(diagnostic -> diagnostic.file().equals(file()))
            .map(diagnostic -> diagnostic.severity() + " " + diagnostic.key() + " " + diagnostic.line() + " " + diagnostic.message()).toList();
    }

    @Test void valuesComeFromTheFileAndProblemsArePlaced() throws Exception {
        write("font_size = 99\ntab_size = 6\nmystery = 1\n[sftp]\nmax_parallel_files = 3\n");
        var size = new AtomicReference<Setting<Integer>>();
        var tab = new AtomicReference<Setting<Integer>>();
        var parallel = new AtomicReference<Setting<Integer>>();
        start(settings -> {
            size.set(settings.declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size")));
            tab.set(settings.declare(SettingSpec.of("tab_size", SettingKind.integer(1, 16), 4).label("Tab size")));
            parallel.set(settings.declare(SettingSpec.of("sftp.max_parallel_files", SettingKind.integer(1, 8), 2).label("Parallel files")));
        });
        assertThat(size.get().get()).isEqualTo(13);
        assertThat(tab.get().get()).isEqualTo(6);
        assertThat(parallel.get().get()).isEqualTo(3);
        assertThat(reports()).containsExactly(
            "ERROR font_size 1 Use an integer from 8–48; using the default.",
            "WARNING mystery 3 Unknown setting or table; ignored.");
    }

    @Test void aPluginThatDeclaresNothingHasItsKeysReportedAsUnknown() throws Exception {
        write("greeting = \"hello\"\n");
        start(settings -> { });
        assertThat(reports()).containsExactly("WARNING greeting 1 Unknown setting or table; ignored.");
    }

    @Test void everyKindTranslates() throws Exception {
        write("wrap = true\nratio = 1.25\nshell = \"/bin/zsh\"\nmode = \"safe\"\nwords = [\"ls\", \"cd\"]\n");
        var wrap = new AtomicReference<Setting<Boolean>>();
        var ratio = new AtomicReference<Setting<Double>>();
        var shell = new AtomicReference<Setting<String>>();
        var mode = new AtomicReference<Setting<Integer>>();
        var words = new AtomicReference<Setting<List<String>>>();
        start(settings -> {
            wrap.set(settings.declare(SettingSpec.of("wrap", SettingKind.bool(), false).label("Wrap")));
            ratio.set(settings.declare(SettingSpec.of("ratio", SettingKind.number(0.5, 3), 1.0).label("Ratio")));
            shell.set(settings.declare(SettingSpec.of("shell", SettingKind.text(text -> text.startsWith("/"), "Use an absolute path."), "/bin/sh").label("Shell")));
            mode.set(settings.declare(SettingSpec.of("mode",
                SettingKind.choice(List.of(Choice.of("fast", "Fast", 1), Choice.of("safe", "Safe", 2))), 1).label("Mode")));
            words.set(settings.declare(SettingSpec.of("words", SettingKind.stringList(), List.of()).label("Words")));
        });
        assertThat(wrap.get().get()).isTrue();
        assertThat(ratio.get().get()).isEqualTo(1.25);
        assertThat(shell.get().get()).isEqualTo("/bin/zsh");
        assertThat(mode.get().get()).isEqualTo(2);
        assertThat(words.get().get()).containsExactly("ls", "cd");
        assertThat(reports()).isEmpty();
    }

    @Test void aThrowingRuleCountsAsRejectingTheValue() throws Exception {
        write("name = \"x\"\n");
        var name = new AtomicReference<Setting<String>>();
        start(settings -> name.set(settings.declare(SettingSpec.of("name",
            SettingKind.text(text -> { if (text.equals("x")) throw new IllegalStateException("boom"); return true; }, "Bad name."), "ok").label("Name"))));
        assertThat(name.get().get()).isEqualTo("ok");
        assertThat(reports()).containsExactly("ERROR name 1 Bad name.");
        assertThat(host.failures(ID)).isPositive();
    }

    /** Plan 15a (spec 5.8): saved, then the look's, then the plugin's own default. */
    @Test void aLooksKitDefaultReplacesTheDeclaredDefaultAndASavedValueStillWins() throws Exception {
        look(Map.of("font_family", "IBM Plex Mono"));
        var family = new AtomicReference<Setting<String>>();
        start(settings -> family.set(settings.declare(SettingSpec.of("font_family", SettingKind.text(), "JetBrains Mono").label("Font"))));
        assertThat(family.get().get()).isEqualTo("IBM Plex Mono");
        write("font_family = \"Menlo\"\n");
        reload();
        assertThat(family.get().get()).isEqualTo("Menlo");
        write("");
        reload();
        assertThat(family.get().get()).as("with nothing saved, the look's default again").isEqualTo("IBM Plex Mono");
        assertThat(lookProblems).isEmpty();
        assertThat(reports()).isEmpty();
    }

    @Test void aKitDefaultTheKindRejectsLeavesThePluginsDefaultAndIsReported() {
        look(Map.of("size", "big", "name", "x"));
        var size = new AtomicReference<Setting<Integer>>();
        var name = new AtomicReference<Setting<String>>();
        start(settings -> {
            size.set(settings.declare(SettingSpec.of("size", SettingKind.integer(8, 48), 13).label("Size")));
            name.set(settings.declare(SettingSpec.of("name",
                SettingKind.text(text -> { if (text.equals("x")) throw new IllegalStateException("boom"); return true; }, "Bad name."), "ok").label("Name")));
        });
        assertThat(size.get().get()).isEqualTo(13);
        assertThat(name.get().get()).isEqualTo("ok");
        assertThat(lookProblems).containsExactly(
            ID + ": the look's default for size is not used: Expected an integer.",
            ID + ": the look's default for name is not used: Bad name.");
        assertThat(host.failures(ID)).as("the plugin's rule ran contained").isPositive();
        assertThat(reports()).as("a look default is not the settings file").isEmpty();
    }

    @Test void aKitKeyThePluginNeverDeclaresIsReportedOnce() throws Exception {
        look(Map.of("missing", true, "size", 20L));
        start(settings -> settings.declare(SettingSpec.of("size", SettingKind.integer(8, 48), 13).label("Size")));
        reload();
        write("size = 14\n");
        reload();
        assertThat(lookProblems).containsExactly(ID + ": the look gives a default for missing, which the plugin does not declare");
    }

    @Test void aStringListKitDefaultIsReadAsTheFileWouldBe() {
        look(Map.of("names", org.tomlj.Toml.parse("names = [\"a\", \"b\"]\n").getArray("names")));
        var names = new AtomicReference<Setting<List<String>>>();
        start(settings -> names.set(settings.declare(SettingSpec.of("names", SettingKind.stringList(), List.of()).label("Names"))));
        assertThat(names.get().get()).containsExactly("a", "b");
        assertThat(lookProblems).isEmpty();
    }

    @Test void declarationsCloseWhenStartReturnsAndKeysAreUnique() {
        var settings = start(declared -> {
            declared.declare(SettingSpec.of("a", SettingKind.bool(), false).label("A"));
            assertThatIllegalArgumentException().isThrownBy(() -> declared.declare(SettingSpec.of("a", SettingKind.bool(), true).label("Again")));
            assertThatIllegalArgumentException().isThrownBy(() -> declared.declare(SettingSpec.of("a.b", SettingKind.bool(), true).label("Inside")));
            assertThatIllegalArgumentException().isThrownBy(() -> declared.declare(SettingSpec.of("b", SettingKind.bool(), true)));
        });
        onEdt(() -> assertThatIllegalStateException()
            .isThrownBy(() -> settings.declare(SettingSpec.of("late", SettingKind.bool(), false).label("Late")))
            .withMessage("Declare settings while the plugin starts: late"));
        assertThat(settings.file()).isEqualTo(file());
    }

    @Test void fileChangesReachListenersOnTheUiThreadAndAWrongTypeKeepsTheLastGoodValue() throws Exception {
        write("vim = false\n");
        var vim = new AtomicReference<Setting<Boolean>>();
        var seen = new CopyOnWriteArrayList<String>();
        int[] any = {0};
        start(settings -> {
            vim.set(settings.declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode")));
            vim.get().onChanged(value -> seen.add(value + " " + SwingUtilities.isEventDispatchThread()));
            settings.onChanged(() -> any[0]++);
        });
        write("vim = true\n");
        reload();
        assertThat(vim.get().get()).isTrue();
        assertThat(seen).containsExactly("true true");
        assertThat(any[0]).isEqualTo(1);
        write("vim = true\n# a comment\n");
        reload();
        assertThat(seen).as("an unchanged value is quiet").hasSize(1);
        write("vim = \"yes\"\n");
        reload();
        assertThat(vim.get().get()).isTrue();
        assertThat(reports()).containsExactly("ERROR vim 1 Expected a boolean; configuration was not applied.");
    }

    @Test void setWritesTheFileKeepingItsCommentsAndTheHandleHearsIt() throws Exception {
        write("# Editor settings\nfont_size = 13\n");
        var size = new AtomicReference<Setting<Integer>>();
        var parallel = new AtomicReference<Setting<Integer>>();
        var seen = new CopyOnWriteArrayList<Integer>();
        start(settings -> {
            size.set(settings.declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size")));
            parallel.set(settings.declare(SettingSpec.of("sftp.max_parallel_files", SettingKind.integer(1, 8), 2).label("Parallel files")));
            size.get().onChanged(seen::add);
        });
        size.get().set(15).get(5, TimeUnit.SECONDS);
        parallel.get().set(4).get(5, TimeUnit.SECONDS);
        onEdt(() -> { });
        String text = Files.readString(file());
        assertThat(text).startsWith("# Editor settings\n");
        assertThat(TomlDocument.leaves(text)).containsEntry(List.of("font_size"), 15L)
            .containsEntry(List.of("sftp", "max_parallel_files"), 4L);
        assertThat(size.get().get()).isEqualTo(15);
        assertThat(parallel.get().get()).isEqualTo(4);
        assertThat(seen).containsExactly(15);
    }

    @Test void setRefusesAnInvalidValueAChangeOnDiskAndABrokenFile() throws Exception {
        write("font_size = 99\n");
        var size = new AtomicReference<Setting<Integer>>();
        start(settings -> size.set(settings.declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size"))));
        assertThat(reports()).hasSize(1);
        assertThatThrownBy(() -> size.get().set(99).get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
            .cause().isInstanceOf(IllegalArgumentException.class).hasMessage("Use a whole number from 8–48.");
        size.get().set(14).get(5, TimeUnit.SECONDS);
        onEdt(() -> { });
        assertThat(reports()).as("a save clears the file's old reports").isEmpty();
        write("font_size = 20\n");
        assertThatThrownBy(() -> size.get().set(15).get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
            .cause().hasMessage("font_size changed on disk. Review the new value and try again.");
        write("font_size = \"big\"\n");
        reload();
        assertThatThrownBy(() -> size.get().set(16).get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
            .cause().hasMessage(ID + ".toml has errors; fix them before saving settings.");
    }
}
