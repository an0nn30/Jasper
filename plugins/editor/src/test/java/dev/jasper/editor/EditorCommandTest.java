package dev.jasper.editor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.editor.command.EditorArguments;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.testsupport.EdtTests;
import dev.jasper.editor.ui.EditorEnvironment;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakeCommandCall;
import dev.jasper.sdk.testing.FakePluginHost;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(EdtTests.class)
class EditorCommandTest {
    @TempDir Path home;
    private FakePluginHost host;

    private static EditorPlugin plugin() {
        return new EditorPlugin(Runnable::run, (context, settings) -> new EditorEnvironment(context, Runnable::run,
            owner -> Question::dismissed, owner -> (message, saves) -> saves.getNow(false), settings::current, settings::saveVim, false));
    }

    @BeforeEach void startTheEditor() {
        host = new FakePluginHost(home.resolve("plugins"));
        host.declareSubcommand("dev.jasper.editor", "editor", "Open files in Jasper's Editor");
        host.start(new PluginInfo("dev.jasper.editor", "Editor", "0.8.0", Set.of("palette.contribute")), Set.of(), Set.of(), plugin());
    }

    @AfterEach void closeTheHost() { host.close(); }

    private Path file(String name, String text) throws IOException { return Files.writeString(home.resolve(name), text); }

    private FakeCommandCall editor(String... arguments) { return host.call("editor", List.of(arguments), home); }

    private void load() { while (host.runBackground() > 0) { } }

    @Test void opensEachFileAndExitsOnceTheyHaveLoaded() throws IOException {
        file("a.txt", "a\n");
        file("notes \u00fc.txt", "n\n");
        FakeCommandCall call = editor("a.txt", "notes \u00fc.txt");
        load();
        assertThat(call.exit()).isCompletedWithValue(0);
        assertThat(call.err()).isEmpty();
        assertThat(host.contentTabs()).hasSize(2);
    }

    @Test void waitReturnsOnlyOnceEveryTabItOpenedHasClosed() throws IOException {
        file("a.txt", "a\n");
        file("b.txt", "b\n");
        FakeCommandCall call = editor("--wait", "a.txt", "b.txt");
        load();
        assertThat(call.exit()).isNotDone();
        host.contentTabs().get(0).close();
        assertThat(call.exit()).isNotDone();
        host.contentTabs().get(0).close();
        assertThat(call.exit()).isCompletedWithValue(0);
    }

    @Test void waitingOnAFileThatIsAlreadyOpenWaitsForItsTab() throws IOException {
        file("COMMIT_EDITMSG", "\n");
        FakeCommandCall first = editor("COMMIT_EDITMSG");
        load();
        assertThat(first.exit()).isCompletedWithValue(0);
        FakeCommandCall waiting = editor("--wait", "COMMIT_EDITMSG");
        load();
        assertThat(host.contentTabs()).hasSize(1);
        assertThat(waiting.exit()).isNotDone();
        host.contentTabs().getFirst().close();
        assertThat(waiting.exit()).isCompletedWithValue(0);
    }

    @Test void aMissingFileOpensAnEmptyTabAndAMissingFolderIsReported() {
        FakeCommandCall call = editor("new.txt", "nowhere/x.txt");
        load();
        assertThat(call.exit()).isCompletedWithValue(1);
        assertThat(call.err()).isEqualTo("jasper editor: " + home.resolve("nowhere") + " does not exist\n");
        assertThat(host.contentTabs()).hasSize(1);
        assertThat(home.resolve("new.txt")).doesNotExist();
        assertThat(editor("new.txt").exit()).isCompletedWithValue(0);
        assertThat(host.contentTabs()).as("the same new file's tab again").hasSize(1);
    }

    @Test void aFolderIsReportedAndTheOtherFilesStillOpen() throws IOException {
        file("a.txt", "a\n");
        Files.createDirectory(home.resolve("folder"));
        FakeCommandCall call = editor("folder", "a.txt");
        load();
        assertThat(call.exit()).isCompletedWithValue(1);
        assertThat(call.err()).isEqualTo("jasper editor: " + home.resolve("folder") + " is a folder\n");
        assertThat(host.contentTabs()).hasSize(1);
    }

    @Test @DisabledOnOs(OS.WINDOWS) void aFileThatCannotBeReadIsReportedAndNotWaitedFor() throws IOException {
        Path locked = file("locked.txt", "secret\n");
        Files.setPosixFilePermissions(locked, Set.of());
        FakeCommandCall call = editor("--wait", "locked.txt");
        load();
        assertThat(call.exit()).isCompletedWithValue(1);
        assertThat(call.err()).startsWith("jasper editor: ");
    }

    @Test void cancellingStopsTheWaitAndLeavesTheTabsOpen() throws IOException {
        file("a.txt", "a\n");
        FakeCommandCall call = editor("--wait", "a.txt");
        load();
        call.cancel();
        assertThat(call.exit()).isCompletedWithValue(0);
        assertThat(host.contentTabs()).hasSize(1);
    }

    @Test void stoppingTheEditorEndsTheWait() throws IOException {
        file("a.txt", "a\n");
        FakeCommandCall call = editor("--wait", "a.txt");
        load();
        host.stopAll();
        assertThat(call.exit()).isCompletedWithValue(0);
    }

    @Test void usageErrorsHelpAndNoFiles() {
        FakeCommandCall bogus = editor("--bogus");
        assertThat(bogus.exit()).isCompletedWithValue(2);
        assertThat(bogus.err()).isEqualTo("jasper editor: unknown option --bogus\n" + EditorArguments.USAGE);
        FakeCommandCall help = editor("--help");
        assertThat(help.exit()).isCompletedWithValue(0);
        assertThat(help.out()).isEqualTo(EditorArguments.USAGE);
        assertThat(editor().exit()).isCompletedWithValue(0);
        assertThat(host.contentTabs()).as("Show Editor opens an untitled tab").hasSize(1);
    }
}
