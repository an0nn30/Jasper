package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.FileEndpoint;
import dev.jasper.remote.sftp.LocalEndpoint;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The SFTP dialogs as kit forms (Plan 2 Task 8c): the host chooser, New folder and Copy to host. Enter/Escape follow
 * baseline f16ae32f (ruling Task8 Enter): no primary anywhere, New folder submits only from its name field, Escape
 * closes or cancels. No networking: Copy to host lists a local folder through {@link LocalEndpoint}.
 */
class SftpDialogsTest {
    static final RemoteHost PROD = RemoteHost.create("<html>prod", "prod.invalid", 22, "deploy", Auth.AGENT, "", Optional.empty()),
        STAGE = RemoteHost.create("stage", "stage.invalid", 22, "deploy", Auth.AGENT, "", Optional.empty());

    @Test void theHostChooserBrowsesTheChosenHostAndEscapeCloses() throws Exception {
        edt(() -> {
            var browsed = new ArrayList<String>(); int[] closed = {0};
            var chooser = new HostChooser(new FakeKit("dev.jasper.remote"), List.of(PROD, STAGE), host -> browsed.add(host.name()), () -> closed[0]++);
            var hosts = node(chooser.view(), HostChooser.HOST, Nodes.Choice.class);
            assertThat(hosts.options()).extracting(option -> option.label()).containsExactly("<html>prod — prod.invalid", "stage — stage.invalid");
            assertThat(hosts.selectedId()).isEqualTo(PROD.id().toString());
            assertThat(primaries(chooser.view())).as("Enter browses nothing").isEmpty();
            emit(chooser.view(), new UiEvent.Selected(HostChooser.HOST, Set.of(STAGE.id().toString())));
            click(chooser.view(), HostChooser.BROWSE);
            press(chooser.view(), form(chooser.view()).cancelAction());
            assertThat(browsed).containsExactly("stage");
            assertThat(closed[0]).isEqualTo(1);
            var empty = new HostChooser(new FakeKit("dev.jasper.remote"), List.of(), host -> browsed.add("none"), () -> { });
            assertThat(button(empty.view(), HostChooser.BROWSE).state().enabled()).as("no saved hosts").isFalse();
            press(empty.view(), HostChooser.BROWSE);
            assertThat(browsed).hasSize(1);
            chooser.close();
            assertThat(closed(chooser.view())).isTrue();
        });
    }

    @Test void newFolderCreatesFromItsFieldAndShowsWhyANameWasRefused() throws Exception {
        edt(() -> {
            var attempts = new ArrayList<String>(); int[] closed = {0};
            var panel = new FolderNamePanel(new FakeKit("dev.jasper.remote"),
                name -> { attempts.add(name); return name.contains("/") ? Optional.of("Enter one folder name") : Optional.empty(); }, () -> closed[0]++);
            var field = node(panel.view(), FolderNamePanel.NAME, Nodes.Field.class);
            assertThat(field.label()).isEqualTo("Folder name");
            assertThat(field.trailingAction()).as("Enter in the name field creates, as its action listener did").isEqualTo(FolderNamePanel.CREATE);
            assertThat(button(panel.view(), FolderNamePanel.CREATE_BUTTON).action()).isEqualTo(FolderNamePanel.CREATE);
            assertThat(primaries(panel.view())).as("Create is no default button").isEmpty();
            type(panel.view(), FolderNamePanel.NAME, "a/b");
            press(panel.view(), field.trailingAction());
            assertThat(node(panel.view(), FolderNamePanel.ERROR, Nodes.Text.class).text()).isEqualTo("Enter one folder name");
            assertThat(node(panel.view(), FolderNamePanel.ERROR, Nodes.Text.class).state().visible()).isTrue();
            type(panel.view(), FolderNamePanel.NAME, "logs");
            click(panel.view(), FolderNamePanel.CREATE_BUTTON);
            press(panel.view(), form(panel.view()).cancelAction());
            assertThat(attempts).containsExactly("a/b", "logs");
            assertThat(closed[0]).isEqualTo(1);
        });
    }

    /** The browser speaks SFTP's POSIX paths; LocalEndpoint stands in for SFTP only where local paths are POSIX too. */
    @Test @DisabledOnOs(OS.WINDOWS) void copyToHostListsTheChosenHostsFoldersAndCopiesThereOnly(@TempDir Path temp) throws Exception {
        Path root = temp.toRealPath();
        Path folders = Files.createDirectory(root.resolve("dest"));
        Files.createDirectory(folders.resolve("inbox")); Files.writeString(folders.resolve("readme.txt"), "x");
        var identity = new CompletableFuture<ConnectionIdentity>();
        var chosen = new ArrayList<SftpController.Capture>(); int[] cancelled = {0};
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var panel = value(() -> new DestinationPanel(new FakeKit("dev.jasper.remote"), "2 items from prod:/srv", List.of(PROD, STAGE), root.resolve("cache"), workers, UI,
                id -> identity, connection -> CompletableFuture.completedFuture(homeAt(folders)), chosen::add, capture -> { }, () -> { }, () -> cancelled[0]++));
            try {
                edt(() -> {
                    assertThat(texts(panel.view())).contains("2 items from prod:/srv");
                    assertThat(button(panel.view(), DestinationPanel.COPY).state().enabled()).as("no host yet").isFalse();
                    assertThat(primaries(panel.view())).as("Enter copies nothing").isEmpty();
                    assertThat(node(panel.view(), DestinationPanel.HOST, Nodes.Choice.class).options()).extracting(option -> option.label())
                        .containsExactly("<html>prod — prod.invalid", "stage — stage.invalid");
                    emit(panel.view(), new UiEvent.Selected(DestinationPanel.HOST, Set.of(PROD.id().toString())));
                    assertThat(texts(panel.view())).contains("Connecting…");
                });
                identity.complete(new ConnectionIdentity(List.of(new ConnectionIdentity.Hop(PROD, "deploy"))));
                await(() -> panel.browser().rowNames().equals(List.of("inbox")));
                edt(() -> {
                    assertThat(SftpTestAccess.table(panel.view()).selectionMode()).isEqualTo(Nodes.SelectionMode.SINGLE);
                    assertThat(button(panel.view(), DestinationPanel.COPY).state().enabled()).isTrue();
                    click(panel.view(), DestinationPanel.COPY);
                    press(panel.view(), form(panel.view()).cancelAction());
                });
                assertThat(chosen).singleElement().satisfies(capture -> {
                    assertThat(capture.identity().host().id()).isEqualTo(PROD.id());
                    assertThat(capture.directory()).isEqualTo(folders.toString());
                });
                assertThat(cancelled[0]).as("Escape cancels").isEqualTo(1);
            } finally { edt(panel::close); }
            edt(() -> assertThat(closed(panel.view())).isTrue());
        }
    }

    @Test void aClosedCopyToHostIgnoresALateConnection(@TempDir Path root) throws Exception {
        var identity = new CompletableFuture<ConnectionIdentity>();
        var kit = value(() -> new RecordingKit("dev.jasper.remote"));
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var panel = value(() -> new DestinationPanel(kit, "1 items from prod:/srv", List.of(PROD), root.resolve("cache"), workers, UI,
                id -> identity, connection -> new CompletableFuture<FileEndpoint>(), capture -> { throw new AssertionError("late copy"); }, capture -> { }, () -> { }, () -> { }));
            edt(() -> { emit(panel.view(), new UiEvent.Selected(DestinationPanel.HOST, Set.of(PROD.id().toString()))); panel.close(); });
            identity.complete(new ConnectionIdentity(List.of(new ConnectionIdentity.Hop(PROD, "deploy"))));
            edt(() -> { panel.status(true, "late"); panel.refresh(); });
            edt(() -> assertThat(kit.lateCalls).isEmpty());
        }
    }

    /** A local endpoint whose home is {@code home}, so a destination opens there rather than in the user's home. */
    static FileEndpoint homeAt(Path home) {
        var local = new LocalEndpoint();
        return (FileEndpoint) java.lang.reflect.Proxy.newProxyInstance(FileEndpoint.class.getClassLoader(), new Class<?>[] {FileEndpoint.class}, (proxy, method, args) -> {
            if (method.getName().equals("home") && method.getParameterCount() == 0) return home.toString();
            try { return method.invoke(local, args); } catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
        });
    }

    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!value(condition::getAsBoolean)) {
            if (System.nanoTime() > deadline) throw new AssertionError("timed out");
            Thread.sleep(10);
        }
    }
}
