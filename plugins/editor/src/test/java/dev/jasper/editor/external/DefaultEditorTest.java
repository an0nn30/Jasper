package dev.jasper.editor.external;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.editor.testsupport.EdtTests;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.testing.FakeNotifications;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(EdtTests.class)
class DefaultEditorTest {
    private static final String MAC = "Mac OS X", WINDOWS = "Windows 11";
    private static final PluginInfo INFO = new PluginInfo("dev.jasper.editor", "Editor", "0.9.0", Set.of(Capabilities.PALETTE_CONTRIBUTE,
        Capabilities.TERMINAL_ENVIRONMENT, Capabilities.EDITOR_PROVIDE, Capabilities.TERMINAL_OPEN));

    /** One call to the Jasper Editor's own file editor. */
    private record Opened(Path file, OptionalInt line, Optional<WindowOwner> window) { }

    @TempDir Path home;
    private FakePluginHost host;
    private PluginContext context;
    private Path bin, file, client;
    private final Set<Path> programs = new HashSet<>();
    private final AtomicReference<DefaultEditor.Preference> preference =
        new AtomicReference<>(new DefaultEditor.Preference("jasper", "", true, true));
    private final AtomicBoolean failing = new AtomicBoolean();
    private final List<Opened> opened = new ArrayList<>();
    private final List<List<String>> launched = new ArrayList<>();
    /** The program each launch names, for a failure's notice. */
    private final List<String> named = new ArrayList<>();
    /** Work {@link DefaultEditor} hands to the UI thread; the tests run it when the UI thread would. */
    private final List<Runnable> uiQueue = new ArrayList<>();
    private final AtomicInteger probed = new AtomicInteger();
    private boolean starts = true;

    @BeforeEach void start() throws Exception {
        host = new FakePluginHost(home.resolve("plugins"));
        context = host.start(INFO, Set.of(), Set.of(), c -> { });
        bin = Files.createDirectories(home.resolve("bin"));
        file = Files.writeString(Files.createDirectories(home.resolve("work")).resolve("config.toml"), "[window]\n");
        client = home.resolve("Jasper Beta.app").resolve("bin").resolve("jasper");
    }

    @AfterEach void close() { host.close(); }

    /** The default editor over a machine whose programs are {@link #programs}; the launcher records instead of starting. */
    private DefaultEditor editor(String osName, Optional<Path> jasperCommand) {
        return new DefaultEditor(preference::get, () -> {
                probed.incrementAndGet();
                if (failing.get()) throw new IllegalStateException("PATH could not be read");
                return new Detection.Probe(osName, List.of(bin), home, Map.of(), programs::contains);
            },
            context.background(), uiQueue::add, context.terminals(),
            (path, line, window) -> { opened.add(new Opened(path, line, window)); return true; },
            (command, program) -> { launched.add(command); named.add(program); return starts; }, jasperCommand, osName, context.log());
    }

    /** The default editor once its first detection has run. */
    private DefaultEditor detected(String osName) {
        DefaultEditor editor = editor(osName, Optional.of(client));
        editor.detect();
        drain();
        return editor;
    }

    /** Runs the background executor's tasks, as its thread would; the UI queue still waits. */
    private void drainBackground() { while (host.runBackground() > 0) { } }

    /** Runs what the UI thread was handed, until it has nothing more. */
    private void runUi() {
        while (!uiQueue.isEmpty()) uiQueue.removeFirst().run();
    }

    /** Both executors, background first: a detection from its start to its published result. */
    private void drain() {
        drainBackground();
        runUi();
    }

    /** A program detection finds in {@code bin}. */
    private Path install(String name) {
        Path program = bin.resolve(name).toAbsolutePath().normalize();
        programs.add(program);
        return program;
    }

    private void choose(String editor) { preference.set(new DefaultEditor.Preference(editor, "", true, true)); }

    /** What a shell gets when Jasper's own environment has no VISUAL to replace. */
    private static Map<String, String> editorOnly(String value) { return Map.of("EDITOR", value); }

    private static Map<String, String> both(String value) { return Map.of("EDITOR", value, "VISUAL", value); }

    private String tab(UUID window, String command) { return "tab|" + window + "|" + file.getParent() + "|run:" + command; }

    /** A custom command's {@code sh -c} line on macOS: detection's extra folders added to PATH, then the command and the file. */
    private String customLine(String command) {
        return "export PATH=\"$PATH\":" + EditorCommands.quote(Path.of("/opt/homebrew/bin").toString(), MAC) + ":"
            + EditorCommands.quote(Path.of("/usr/local/bin").toString(), MAC) + ":"
            + EditorCommands.quote(home.resolve(".local").resolve("bin").toString(), MAC) + "; " + command + " " + EditorCommands.quote(file.toString(), MAC);
    }

    @Test void theJasperEditorOpensTheFileItself() {
        DefaultEditor defaults = detected(MAC);
        assertThat(defaults.choice()).isEqualTo(new Choice.Jasper(Optional.of(client)));
        assertThat(defaults.edit(file, OptionalInt.of(3), Optional.empty())).isTrue();
        assertThat(opened).containsExactly(new Opened(file, OptionalInt.of(3), Optional.empty()));
        assertThat(launched).isEmpty();
        assertThat(host.openRequests()).isEmpty();
    }

    @Test void aTerminalEditorRunsAtTheLineInATabOfTheCallsWindow() {
        Path nvim = install("nvim");
        choose("nvim");
        DefaultEditor defaults = detected(MAC);
        UUID window = host.addTerminalWindow();
        WindowHandle handle = context.terminals().window(window).orElseThrow();
        assertThat(defaults.edit(file, OptionalInt.of(42), Optional.of(handle))).isTrue();
        assertThat(host.openRequests()).containsExactly(tab(window, nvim + " +42 " + file));
        assertThat(opened).isEmpty();
    }

    @Test void withoutATerminalWindowATerminalEditorUsesTheActiveOneOrLeavesTheFileToTheDesktop() {
        Path emacs = install("emacs");
        choose("emacs");
        DefaultEditor defaults = detected(MAC);
        assertThat(defaults.edit(file, OptionalInt.empty(), Optional.empty())).as("no terminal window is open").isFalse();
        UUID active = host.addTerminalWindow();
        host.activateTerminalWindow(active);
        assertThat(defaults.edit(file, OptionalInt.empty(), Optional.of(new WindowOwner() { }))).as("not a terminal window").isTrue();
        assertThat(host.openRequests()).containsExactly(tab(active, emacs + " -nw " + file));
    }

    @Test void anAppEditorStartsOnItsOwnAtTheLineWithoutWaiting() {
        Path code = install("code");
        choose("code");
        DefaultEditor defaults = detected(MAC);
        assertThat(defaults.edit(file, OptionalInt.of(7), Optional.empty())).isTrue();
        assertThat(launched).containsExactly(List.of(code.toString(), "-g", file + ":7"));
        starts = false;
        assertThat(defaults.edit(file, OptionalInt.empty(), Optional.empty())).as("it did not start").isFalse();
        assertThat(launched.getLast()).containsExactly(code.toString(), file.toString());
        assertThat(named).as("a failure names the program's file name").containsExactly("code", "code");
        assertThat(host.openRequests()).isEmpty();
    }

    @Test void aCustomCommandRunsInATabOrOnItsOwnWithTheQuotedFileAdded() {
        UUID window = host.addTerminalWindow();
        host.activateTerminalWindow(window);
        preference.set(new DefaultEditor.Preference("custom", "subl -w", true, true));
        DefaultEditor defaults = detected(MAC);
        assertThat(defaults.choice()).isEqualTo(new Choice.Custom("subl -w", true));
        assertThat(defaults.edit(file, OptionalInt.of(9), Optional.empty())).isTrue();
        assertThat(host.openRequests()).containsExactly(tab(window, "/bin/sh -c " + customLine("subl -w")));
        preference.set(new DefaultEditor.Preference("custom", "subl -w", false, true));
        assertThat(defaults.edit(file, OptionalInt.of(9), Optional.empty())).isTrue();
        assertThat(launched).containsExactly(List.of("/bin/sh", "-c", customLine("subl -w")));
        assertThat(detected(WINDOWS).edit(file, OptionalInt.empty(), Optional.empty())).isTrue();
        assertThat(launched.getLast()).containsExactly("cmd.exe", "/d", "/s", "/c", "\"subl -w \"" + file + "\"\"");
        assertThat(named).as("a failure names the command's first word, never the file").containsExactly("subl", "subl");
    }

    /** Controller Ruling 12: a program started on its own that exits with an error is logged and posted, by its name only. */
    @Test void aDetachedProgramThatExitsWithAnErrorIsPostedByItsNameOnly() {
        FakeNotifications notices = new FakeNotifications();
        NotificationGroup group = notices.group("dev.jasper.editor");
        var failing = new CompletableFuture<Integer>();
        var clean = new CompletableFuture<Integer>();
        DefaultEditor.watch(failing, "subl", context.log(), group);
        DefaultEditor.watch(clean, "code", context.log(), group);
        assertThat(notices.lines()).as("nothing before the programs exit; watching never blocks").isEmpty();
        clean.complete(0);
        assertThat(notices.lines()).as("a clean exit says nothing").isEmpty();
        failing.complete(127);
        assertThat(notices.lines()).containsExactly("dev.jasper.editor|ERROR|Could not open a file in your default editor|subl exited with status 127.");
    }

    /** Controller Ruling 12, through the production launcher: runs only {@code /bin/sh -c 'exit 3'}. */
    @Test @DisabledOnOs(value = OS.WINDOWS, disabledReason = "runs /bin/sh")
    void theDetachedLauncherPostsAProgramThatExitsWithAnError() throws Exception {
        DefaultEditor.Launcher launcher = DefaultEditor.Launcher.detached(context.log(), context.notifications().group());
        assertThat(launcher.start(List.of("/bin/sh", "-c", "exit 3"), "subl")).as("it started").isTrue();
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
        while (host.notifications().lines().isEmpty() && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(host.notifications().lines()).containsExactly("dev.jasper.editor|ERROR|Could not open a file in your default editor|subl exited with status 3.");
    }

    @Test void theDetachedLauncherReportsAProgramThatCannotStartAsNotStartedAndPostsNothing() {
        DefaultEditor.Launcher launcher = DefaultEditor.Launcher.detached(context.log(), context.notifications().group());
        assertThat(launcher.start(List.of(home.resolve("no such program").toString()), "no such program")).isFalse();
        assertThat(host.notifications().lines()).isEmpty();
    }

    @Test void theSystemsAppAndABlankCustomCommandLeaveTheFileToTheDesktop() {
        host.activateTerminalWindow(host.addTerminalWindow());
        choose("system");
        DefaultEditor defaults = detected(MAC);
        assertThat(defaults.edit(file, OptionalInt.of(1), Optional.empty())).isFalse();
        preference.set(new DefaultEditor.Preference("custom", "  ", true, true));
        assertThat(defaults.choice()).isEqualTo(new Choice.System());
        assertThat(defaults.edit(file, OptionalInt.of(1), Optional.empty())).isFalse();
        assertThat(opened).isEmpty();
        assertThat(launched).isEmpty();
        assertThat(host.openRequests()).isEmpty();
    }

    /** Controller Ruling 11: EDITOR only, so a VISUAL or EDITOR exported in a shell startup file still wins. */
    @Test void editorForEachChoice() {
        Path nvim = install("nvim"), emacs = install("emacs"), code = install("code");
        DefaultEditor defaults = detected(MAC);
        assertThat(defaults.variables(Map.of())).isEqualTo(editorOnly(EditorCommands.quote(client.toString(), MAC) + " editor --wait"));
        assertThat(editor(MAC, Optional.empty()).variables(Map.of())).as("no jasper command").isEmpty();
        choose("nvim");
        assertThat(defaults.variables(Map.of())).isEqualTo(editorOnly(EditorCommands.quote(nvim.toString(), MAC)));
        choose("emacs");
        assertThat(defaults.variables(Map.of())).isEqualTo(editorOnly(EditorCommands.quote(emacs.toString(), MAC) + " -nw"));
        choose("code");
        assertThat(defaults.variables(Map.of())).isEqualTo(editorOnly(EditorCommands.quote(code.toString(), MAC) + " --wait"));
        preference.set(new DefaultEditor.Preference("custom", "subl -w", true, true));
        assertThat(defaults.variables(Map.of())).isEqualTo(editorOnly("subl -w"));
        preference.set(new DefaultEditor.Preference("custom", "", true, true));
        assertThat(defaults.variables(Map.of())).isEmpty();
        choose("system");
        assertThat(defaults.variables(Map.of())).isEmpty();
    }

    /** Review Focus 2: Jasper started from a terminal with EDITOR=vim sets neither variable until the setting is off. */
    @Test void editorOrVisualInJaspersEnvironmentKeepsBothUntilTheSettingIsOff() {
        DefaultEditor defaults = detected(MAC);
        String jasper = EditorCommands.quote(client.toString(), MAC) + " editor --wait";
        assertThat(defaults.variables(Map.of("EDITOR", "vim"))).isEmpty();
        assertThat(defaults.variables(Map.of("VISUAL", "vim"))).isEmpty();
        assertThat(defaults.variables(Map.of("EDITOR", "", "VISUAL", " "))).as("blank values: no VISUAL to replace").isEqualTo(editorOnly(jasper));
        preference.set(new DefaultEditor.Preference("jasper", "", true, false));
        assertThat(defaults.variables(Map.of("EDITOR", "vim", "VISUAL", "nano"))).as("the setting off").isEqualTo(both(jasper));
    }

    /** Controller Ruling 11: with the setting off, a VISUAL Jasper was started with is replaced along with EDITOR. */
    @Test void withTheSettingOffAVisualInJaspersEnvironmentIsReplacedToo() {
        preference.set(new DefaultEditor.Preference("jasper", "", true, false));
        DefaultEditor defaults = detected(MAC);
        assertThat(defaults.variables(Map.of("VISUAL", "nano"))).isEqualTo(both(EditorCommands.quote(client.toString(), MAC) + " editor --wait"));
    }

    /** Controller Ruling 11: with the setting off, an EDITOR alone in Jasper's environment gets EDITOR alone. */
    @Test void withTheSettingOffAnEditorAloneInJaspersEnvironmentIsReplacedAlone() {
        preference.set(new DefaultEditor.Preference("jasper", "", true, false));
        DefaultEditor defaults = detected(MAC);
        assertThat(defaults.variables(Map.of("EDITOR", "vim"))).isEqualTo(editorOnly(EditorCommands.quote(client.toString(), MAC) + " editor --wait"));
    }

    /** Review Focus 5: the saved editor was uninstalled. */
    @Test void aSavedEditorThatIsNotInstalledIsTheSystemsApp() {
        host.activateTerminalWindow(host.addTerminalWindow());
        choose("nvim");
        DefaultEditor defaults = detected(MAC);
        assertThat(defaults.detected()).isEmpty();
        assertThat(defaults.choice()).isEqualTo(new Choice.System());
        assertThat(defaults.variables(Map.of())).isEmpty();
        assertThat(defaults.edit(file, OptionalInt.of(2), Optional.empty())).isFalse();
        assertThat(host.openRequests()).isEmpty();
        assertThat(launched).isEmpty();
    }

    @Test void detectionRunsInTheBackgroundAndTellsItsListenersOnTheUiThread() {
        Path nvim = install("nvim");
        DefaultEditor defaults = editor(MAC, Optional.of(client));
        var heard = new AtomicInteger();
        Subscription listening = defaults.onDetected(heard::incrementAndGet);
        defaults.detect();
        assertThat(defaults.detected()).as("before the background task ran").isEmpty();
        assertThat(heard).hasValue(0);
        drainBackground();
        assertThat(defaults.detected()).as("the search ran, but its result waits for the UI thread").isEmpty();
        assertThat(heard).as("the listener waits for the UI thread").hasValue(0);
        assertThat(uiQueue).as("the result was handed to the UI thread").hasSize(1);
        runUi();
        assertThat(defaults.detected()).extracting(found -> found.editor().id() + "=" + found.executable()).containsExactly("nvim=" + nvim);
        assertThat(heard).hasValue(1);
        listening.close();
        defaults.detect();
        drain();
        assertThat(heard).hasValue(1);
    }

    /** The first shell of a launch starts in the same UI turn as the plugin, before any background task could have run. */
    @Test void theSavedEditorIsFoundOnTheCallingThreadSoTheFirstShellAlreadyGetsItsEditor() {
        Path nvim = install("nvim");
        install("vim");
        choose("nvim");
        DefaultEditor defaults = editor(MAC, Optional.of(client));
        defaults.findSaved();
        assertThat(defaults.choice()).isEqualTo(new Choice.Installed(new Detected(KnownEditors.byId("nvim").orElseThrow(), nvim)));
        assertThat(defaults.variables(Map.of())).isEqualTo(editorOnly(EditorCommands.quote(nvim.toString(), MAC)));
        assertThat(defaults.detected()).extracting(found -> found.editor().id()).as("only the saved editor").containsExactly("nvim");
        assertThat(uiQueue).as("no listener or UI work was needed").isEmpty();
    }

    @Test void findSavedTellsNoListenerAndKeepsWhatDetectionAlreadyFound() {
        install("nvim");
        install("vim");
        choose("nvim");
        DefaultEditor defaults = detected(MAC);
        var heard = new AtomicInteger();
        defaults.onDetected(heard::incrementAndGet);
        defaults.findSaved();
        assertThat(defaults.detected()).extracting(found -> found.editor().id()).containsExactly("nvim", "vim");
        DefaultEditor fresh = editor(MAC, Optional.of(client));
        fresh.onDetected(heard::incrementAndGet);
        fresh.findSaved();
        assertThat(heard).as("the page is not open at start").hasValue(0);
    }

    @Test void findSavedDoesNotLookWhenTheChoiceIsNotAKnownEditorsId() {
        install("nvim");
        DefaultEditor defaults = editor(MAC, Optional.of(client));
        for (String value : List.of("jasper", "system", "custom", "emacsclient", "")) {
            choose(value);
            defaults.findSaved();
        }
        assertThat(probed).as("the probe supplier was never called").hasValue(0);
        assertThat(defaults.detected()).isEmpty();
    }

    @Test void findSavedLeavesNothingFoundWhenTheSavedEditorIsNotInstalledOrTheProbeFails() {
        install("vim");
        choose("nvim");
        DefaultEditor defaults = editor(MAC, Optional.of(client));
        defaults.findSaved();
        assertThat(probed).hasValue(1);
        assertThat(defaults.detected()).isEmpty();
        assertThat(defaults.choice()).isEqualTo(new Choice.System());
        failing.set(true);
        defaults.findSaved();
        assertThat(probed).as("it looked again and failed").hasValue(2);
        assertThat(defaults.detected()).isEmpty();
        assertThat(defaults.variables(Map.of())).isEmpty();
    }

    @Test void aFailedDetectionKeepsWhatWasFound() {
        install("vim");
        DefaultEditor defaults = detected(MAC);
        var heard = new AtomicInteger();
        defaults.onDetected(heard::incrementAndGet);
        failing.set(true);
        defaults.detect();
        drain();
        assertThat(defaults.detected()).extracting(found -> found.editor().id()).containsExactly("vim");
        assertThat(heard).as("nothing new to tell").hasValue(0);
    }
}
