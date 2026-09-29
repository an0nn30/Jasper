package dev.jasper.remote;

import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.api.OpenRequest;
import dev.jasper.remote.client.LoopbackServer;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.ui.sftp.RemoteFilePicker;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.remote.RemoteEditingPluginTest.*;
import static org.assertj.core.api.Assertions.assertThat;

/** File › Open Remote File…: the picker, against a loopback SFTP server. POSIX paths and links. */
@DisabledOnOs(OS.WINDOWS)
class RemoteOpenFileTest {
    static final String ACTION = "dev.jasper.remote.open-file";

    static RemoteFilePicker picker(FakePluginHost host) throws Exception {
        await(host, () -> host.windowContent("dev.jasper.remote", "Open Remote File").isPresent(), "the picker is shown");
        return (RemoteFilePicker) host.windowContent("dev.jasper.remote", "Open Remote File").orElseThrow();
    }

    @Test void itIsInTheFileAndSshMenusOnlyWithTheEditor(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        Files.writeString(files.resolve("notes.toml"), "a = 1\n");
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            setUp(host, server, files, dir, Optional.of(new FakeEditor()), 1, new SftpSubsystemFactory());
            assertThat(host.actions()).anySatisfy(line -> assertThat(line).startsWith(ACTION + "|Open Remote File…|"));
            assertThat(host.menu("FILE")).contains("item:" + ACTION);
            assertThat(host.menu("top:dev.jasper.remote.menu")).contains("item:" + ACTION);
            host.stopAll();
        }
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            setUp(host, server, files, dir, Optional.empty(), 1, new SftpSubsystemFactory());
            assertThat(host.actions()).noneSatisfy(line -> assertThat(line).startsWith(ACTION + "|"));
            assertThat(host.menu("FILE")).doesNotContain("item:" + ACTION);
            host.stopAll();
        }
    }

    @Test void fromAnSshPaneItStartsOnThatConnectionAndOpensTheSelectedFiles(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        Files.writeString(files.resolve("a.txt"), "a\n");
        Files.writeString(files.resolve("b.txt"), "b\n");
        Files.createDirectory(files.resolve("conf"));
        var editor = new FakeEditor();
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var set = setUp(host, server, files, dir, Optional.of(editor), 3, new SftpSubsystemFactory());
            assertThat(host.invoke(ACTION, set.window(), set.pane())).isTrue();
            var picker = picker(host);
            assertThat(((RemoteHost) picker.hosts().getSelectedItem()).name()).isEqualTo("prod");
            RemotePluginTest.awaitRowCount(host, picker.browser(), 3);
            picker.browser().table().selectAll();
            assertThat(picker.openButton().isEnabled()).isTrue();
            picker.openButton().doClick();
            await(host, () -> editor.documents.size() == 2, "both files open");
            assertThat(editor.documents).extracting(EditorDocument::name).containsExactlyInAnyOrder("a.txt", "b.txt");
            await(host, () -> host.windowContent("dev.jasper.remote", "Open Remote File").isEmpty(), "the picker closes");
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void foldersAreEnteredAndALinkToAFileOpens(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        Files.createDirectory(files.resolve("conf"));
        Files.writeString(files.resolve("conf/app.toml"), "x = 1\n");
        Files.createSymbolicLink(files.resolve("alias.toml"), Path.of("conf/app.toml"));
        var editor = new FakeEditor();
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var set = setUp(host, server, files, dir, Optional.of(editor), 2, new SftpSubsystemFactory());
            host.invoke(ACTION, set.window(), set.pane());
            var picker = picker(host);
            RemotePluginTest.awaitRowCount(host, picker.browser(), 2);
            enter(picker.browser(), "conf");
            await(host, () -> picker.browser().directory().equals("/conf"), "the folder is entered");
            assertThat(editor.documents).isEmpty();
            picker.navigate("/");
            RemotePluginTest.awaitRowCount(host, picker.browser(), 2);
            enter(picker.browser(), "alias.toml");
            await(host, () -> editor.documents.size() == 1, "the link to a file opens");
            await(host, () -> host.windowContent("dev.jasper.remote", "Open Remote File").isEmpty(), "the picker closes");
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void withoutAnSshPaneTheHostIsChosenInThePickerAndCancelCloses(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        Files.writeString(files.resolve("notes.toml"), "a = 1\n");
        var editor = new FakeEditor();
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var set = setUp(host, server, files, dir, Optional.of(editor), 1, new SftpSubsystemFactory());
            assertThat(host.invoke(ACTION, set.window(), null)).isTrue();
            var picker = picker(host);
            assertThat(picker.hosts().getSelectedIndex()).isEqualTo(-1);
            assertThat(picker.openButton().isEnabled()).isFalse();
            picker.hosts().setSelectedIndex(0);
            RemotePluginTest.awaitRowCount(host, picker.browser(), 1);
            picker.cancelButton().doClick();
            assertThat(host.windowContent("dev.jasper.remote", "Open Remote File")).isEmpty();
            assertThat(editor.documents).isEmpty();
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void theEditorsOpenInEditorOpensThePickerFromAnSshPane(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        Files.writeString(files.resolve("notes.toml"), "a = 1\n");
        var editor = new FakeEditor();
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var set = setUp(host, server, files, dir, Optional.of(editor), 1, new SftpSubsystemFactory());
            assertThat(editor.openers).hasSize(1);
            WindowHandle window = set.context().terminals().window(set.window()).orElseThrow();
            var pane = set.context().terminals().pane(set.pane()).orElseThrow();
            assertThat(editor.openers.getFirst().test(new OpenRequest(window, Optional.of(pane), Optional.empty()))).isTrue();
            var picker = picker(host);
            assertThat(((RemoteHost) picker.hosts().getSelectedItem()).name()).isEqualTo("prod");
            picker.cancelButton().doClick();
            assertThat(editor.openers.getFirst().test(new OpenRequest(window, Optional.empty(), Optional.of("local:/tmp/a.txt")))).isFalse();
            host.stopAll();
            assertThat(editor.openers).as("stopping Remote removes its opener").isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    /** The Editor hands over its own pane handle, gated by its own capabilities; the Editor has no terminal.observe. */
    @Test void aPaneHandleFromAPluginWithoutTerminalObserveStillOpensThePicker(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        Files.writeString(files.resolve("notes.toml"), "a = 1\n");
        var editor = new FakeEditor();
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var set = setUp(host, server, files, dir, Optional.of(editor), 1, new SftpSubsystemFactory());
            var stranger = new java.util.concurrent.atomic.AtomicReference<dev.jasper.sdk.plugin.PluginContext>();
            host.start(new dev.jasper.sdk.PluginInfo("test.stranger", "Stranger", "1.0.0", java.util.Set.of()), java.util.Set.of(), java.util.Set.of(), stranger::set);
            WindowHandle window = stranger.get().terminals().window(set.window()).orElseThrow();
            var pane = stranger.get().terminals().pane(set.pane()).orElseThrow();
            assertThat(editor.openers.getFirst().test(new OpenRequest(window, Optional.of(pane), Optional.empty()))).isTrue();
            assertThat(((RemoteHost) picker(host).hosts().getSelectedItem()).name()).isEqualTo("prod");
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }
}
