package dev.jasper.remote;

import dev.jasper.editor.api.Editor;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.remote.client.LoopbackServer;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.trust.KnownHosts;
import dev.jasper.remote.ui.sftp.SftpPanel;
import dev.jasper.remote.ui.sftp.SftpUi;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.sftp.server.SftpSubsystem;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** The SFTP browser with and without the Editor, against a loopback SFTP server. POSIX paths and links. */
@DisabledOnOs(OS.WINDOWS)
class RemoteEditingPluginTest {
    static final PluginInfo EDITOR = new PluginInfo("dev.jasper.editor", "Editor", "0.1.0", Set.of());

    /** A stand-in Editor that records what Remote asks it to open. */
    static final class FakeEditor implements Plugin {
        final List<EditorDocument> documents = new CopyOnWriteArrayList<>();
        final List<java.util.function.Predicate<dev.jasper.editor.api.OpenRequest>> openers = new CopyOnWriteArrayList<>();
        volatile RuntimeException failure;
        @Override public void start(PluginContext context) {
            context.services().publish(Editor.class, new Editor() {
                @Override public void open(Path file) { throw new AssertionError("Remote opens documents, not local paths"); }
                @Override public void open(EditorDocument document) { if (failure != null) throw failure; documents.add(document); }
                @Override public dev.jasper.sdk.Subscription opener(java.util.function.Predicate<dev.jasper.editor.api.OpenRequest> handler) {
                    openers.add(handler);
                    return () -> openers.remove(handler);
                }
            });
        }
        @Override public void stop() { }
    }

    /** Connects a saved host whose SFTP root is {@code files} and shows its listing of {@code rows} entries. */
    static SftpPanel browse(FakePluginHost host, LoopbackServer server, Path files, Path dir, Optional<FakeEditor> editor, int rows) throws Exception {
        return browse(host, server, files, dir, editor, rows, new SftpSubsystemFactory());
    }
    /** As above, serving through {@code factory} instead of a plain {@link SftpSubsystemFactory}. */
    static SftpPanel browse(FakePluginHost host, LoopbackServer server, Path files, Path dir, Optional<FakeEditor> editor, int rows, SftpSubsystemFactory factory) throws Exception {
        return setUp(host, server, files, dir, editor, rows, factory).panel();
    }

    /** What {@link #setUp} leaves open: the SFTP panel, the terminal window and SSH pane, and Remote's context. */
    record Browsed(SftpPanel panel, UUID window, UUID pane, PluginContext context) { }

    /** As {@link #browse}, returning the window, the SSH pane and Remote's context too. */
    static Browsed setUp(FakePluginHost host, LoopbackServer server, Path files, Path dir, Optional<FakeEditor> editor, int rows, SftpSubsystemFactory factory) throws Exception {
        server.server.setFileSystemFactory(new VirtualFileSystemFactory(files.toRealPath()));
        server.server.setSubsystemFactories(List.of(factory));
        var vault = new FakeVault();
        host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
        editor.ifPresent(fake -> host.start(EDITOR, Set.of(), Set.of(), fake));
        RemotePlugin plugin = new TestRemotePlugin(Runnable::run, context -> Optional.empty(), (delay, task) -> () -> {}, dir.resolve("ssh"));
        var context = host.start(RemotePluginTest.INFO, Set.of(), Set.of("dev.jasper.vault", "dev.jasper.editor"), plugin);
        UUID credential = vault.password("deploy login", "deploy", "s3cret");
        RemoteHost prod = RemoteHost.create("prod", "127.0.0.1", server.port(), "", new Auth.Vault(credential), "", Optional.empty()).withFollowDirectory(false);
        plugin.store().put(prod);
        RemotePluginTest.settle(host);
        new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
        UUID window = host.addTerminalWindow();
        host.activateTerminalWindow(window);
        var panel = (SftpPanel) host.openPanel(SftpUi.PANEL, window);
        plugin.openHost(context.terminals().window(window).orElseThrow(), prod); RemotePluginTest.settle(host);
        UUID pane = host.terminalPanes().getFirst();
        host.focusTerminalPane(pane); host.flush();
        RemotePluginTest.awaitRowCount(host, panel, rows);
        return new Browsed(panel, window, pane, context);
    }

    static void select(SftpPanel panel, String name) {
        for (int row = 0; row < panel.table().getRowCount(); row++)
            if (name.equals(panel.table().getValueAt(row, 0))) { panel.table().setRowSelectionInterval(row, row); return; }
        throw new AssertionError("no row " + name);
    }
    static void enter(SftpPanel panel, String name) { select(panel, name); panel.table().getActionMap().get("browse").actionPerformed(null); }
    static void await(FakePluginHost host, BooleanSupplier condition, String what) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) { RemotePluginTest.settle(host); Thread.sleep(20); }
        assertThat(condition.getAsBoolean()).as(what).isTrue();
    }
    static <T> T done(FakePluginHost host, CompletableFuture<T> future) throws Exception { await(host, future::isDone, "the operation finishes"); return future.get(); }
    static List<String> labels(java.awt.Container root) {
        var texts = new ArrayList<String>();
        for (var child : root.getComponents()) {
            if (child instanceof javax.swing.JLabel label && label.getText() != null) texts.add(label.getText());
            if (child instanceof java.awt.Container container) texts.addAll(labels(container));
        }
        return texts;
    }
    /** Counts down {@code blocked} and waits on {@code release} the first time {@code path} itself is (l)stat-ed, then serves it normally. */
    static SftpSubsystemFactory blockingLstat(String path, CountDownLatch blocked, CountDownLatch release) {
        return new SftpSubsystemFactory() {
            @Override public Command createSubsystem(ChannelSession channel) {
                return new SftpSubsystem(channel, this) {
                    @Override protected java.util.Map<String, Object> doLStat(int id, String requested, int flags) throws java.io.IOException {
                        if (requested.equals(path)) { blocked.countDown(); try { release.await(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); } }
                        return super.doLStat(id, requested, flags);
                    }
                };
            }
        };
    }

    @Test void enterOpensFilesAndLinksToFilesAndALinkToAFolderNavigates(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        Files.writeString(files.resolve("notes.toml"), "a = 1\n");
        Files.createSymbolicLink(files.resolve("alias.toml"), Path.of("notes.toml"));
        Files.createDirectory(files.resolve("conf"));
        Files.createSymbolicLink(files.resolve("conf-link"), Path.of("conf"));
        var editor = new FakeEditor();
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var panel = browse(host, server, files, dir, Optional.of(editor), 4);
            enter(panel, "notes.toml");
            assertThat(editor.documents).singleElement().satisfies(document -> {
                assertThat(document.name()).isEqualTo("notes.toml");
                assertThat(document.location()).isEqualTo("deploy@prod:/notes.toml");
                assertThat(document.host()).contains("prod");
                assertThat(document.key()).startsWith("remote:").endsWith(":/notes.toml");
            });
            var notes = editor.documents.getFirst();
            assertThat(new String(done(host, notes.read(1 << 20)).bytes(), StandardCharsets.UTF_8)).isEqualTo("a = 1\n");
            done(host, notes.write("a = 2\n".getBytes(StandardCharsets.UTF_8)));
            assertThat(Files.readString(files.resolve("notes.toml"))).isEqualTo("a = 2\n");

            enter(panel, "alias.toml");
            await(host, () -> editor.documents.size() == 2, "the link to a file opens");
            var alias = editor.documents.get(1);
            assertThat(alias.name()).isEqualTo("alias.toml");
            done(host, alias.write("b = 3\n".getBytes(StandardCharsets.UTF_8)));
            assertThat(Files.readString(files.resolve("notes.toml"))).isEqualTo("b = 3\n");
            assertThat(Files.isSymbolicLink(files.resolve("alias.toml"))).isTrue();

            enter(panel, "conf-link");
            await(host, () -> panel.directory().equals("/conf"), "the link to a folder navigates");
            assertThat(editor.documents).hasSize(2);
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void openInEditorOpensTheSelectedFilesTwentyAtATime(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        for (int i = 0; i < 25; i++) Files.writeString(files.resolve(String.format("f%02d.txt", i)), "file " + i);
        Files.createSymbolicLink(files.resolve("alias.txt"), Path.of("f00.txt"));
        Files.createDirectory(files.resolve("conf"));
        Files.createSymbolicLink(files.resolve("conf-link"), Path.of("conf"));
        var editor = new FakeEditor();
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var panel = browse(host, server, files, dir, Optional.of(editor), 28);
            panel.table().selectAll();
            RemotePluginTest.menuItem(panel.table().getComponentPopupMenu(), "Open in Editor").doClick();
            await(host, () -> labels(panel).contains("Opened 20 of 26 files."), "the cap is reported");
            assertThat(editor.documents).hasSize(20).extracting(EditorDocument::name).doesNotContain("conf", "conf-link");
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void withoutTheEditorFilesStayPutAndOpenInEditorIsHidden(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        Files.writeString(files.resolve("notes.toml"), "a = 1\n");
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var panel = browse(host, server, files, dir, Optional.empty(), 1);
            assertThat(RemotePluginTest.menuItem(panel.table().getComponentPopupMenu(), "Open in Editor").isVisible()).isFalse();
            enter(panel, "notes.toml");
            RemotePluginTest.settle(host);
            assertThat(panel.directory()).isEqualTo("/");
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void anEditorThatThrowsBecomesANoticeAndThePanelKeepsWorking(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        Files.writeString(files.resolve("notes.toml"), "a = 1\n");
        var editor = new FakeEditor();
        editor.failure = new IllegalStateException("the Editor window is closing");
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var panel = browse(host, server, files, dir, Optional.of(editor), 1);
            enter(panel, "notes.toml");
            assertThat(host.notices()).containsExactly("dev.jasper.remote: Could not open /notes.toml in the Editor: the Editor window is closing");
            editor.failure = null;
            enter(panel, "notes.toml");
            assertThat(editor.documents).hasSize(1);
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    /** Finding 2 (review round 1): one link loop must not stop the regular files in the same selection from opening. */
    @Test void openInEditorStillOpensRegularFilesWhenALinkLoopFailsToResolve(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        Files.writeString(files.resolve("one.txt"), "one");
        Files.writeString(files.resolve("two.txt"), "two");
        Files.createSymbolicLink(files.resolve("a"), Path.of("b"));
        Files.createSymbolicLink(files.resolve("b"), Path.of("a"));
        var editor = new FakeEditor();
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var panel = browse(host, server, files, dir, Optional.of(editor), 4);
            panel.table().selectAll();
            RemotePluginTest.menuItem(panel.table().getComponentPopupMenu(), "Open in Editor").doClick();
            await(host, () -> editor.documents.size() == 2, "the regular files open despite the link loop");
            assertThat(editor.documents).extracting(EditorDocument::name).containsExactlyInAnyOrder("one.txt", "two.txt");
            await(host, () -> labels(panel).stream().anyMatch(text -> text.contains("Too many levels of symbolic links")), "the link loop failure is reported");
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    /** Finding 1 (review round 1): a link lookup that is still queued when the user navigates elsewhere must not pull the view back. */
    @Test void aLateLinkLookupDoesNotNavigateAViewThatHasMovedOn(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        Files.createDirectory(files.resolve("conf"));
        Files.createSymbolicLink(files.resolve("conf-link"), Path.of("conf"));
        Files.createDirectory(files.resolve("other"));
        var editor = new FakeEditor();
        var blocked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            try {
                var panel = browse(host, server, files, dir, Optional.of(editor), 3, blockingLstat("/conf-link", blocked, release));
                enter(panel, "conf-link");
                await(host, () -> blocked.getCount() == 0, "the link lookup reached the server and is now blocked there");
                enter(panel, "other");
                await(host, () -> panel.directory().equals("/other"), "navigating elsewhere while the lookup is still in flight");
                release.countDown();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (System.nanoTime() < deadline) {
                    RemotePluginTest.settle(host);
                    assertThat(panel.directory()).as("a late link result must not pull a view that moved on").isEqualTo("/other");
                    Thread.sleep(20);
                }
                host.stopAll();
                assertThat(host.failures()).isEmpty();
            } finally {
                // A failed assertion must not leave the server's SFTP thread parked on the latch.
                release.countDown();
            }
        }
    }
}
