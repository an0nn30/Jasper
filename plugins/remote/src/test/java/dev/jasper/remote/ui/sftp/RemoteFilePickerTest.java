package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/** The picker without a mouse: selecting a host only selects it; Connect, Enter, Esc and the initial focus. */
class RemoteFilePickerTest {
    @TempDir Path dir;
    final ExecutorService background = Executors.newCachedThreadPool();
    final List<UUID> resolved = new CopyOnWriteArrayList<>();
    final AtomicInteger cancelled = new AtomicInteger();
    final RemoteHost prod = RemoteHost.create("prod", "10.0.0.5", 22, "deploy", Auth.AGENT, "", Optional.empty());
    final RemoteHost stage = RemoteHost.create("stage", "10.0.0.6", 22, "deploy", Auth.AGENT, "", Optional.empty());
    RemoteFilePicker picker;

    @BeforeEach void create() {
        picker = new RemoteFilePicker(List.of(prod, stage), dir, background, Runnable::run, name -> null,
            id -> { resolved.add(id); return new CompletableFuture<>(); }, identity -> new CompletableFuture<>(),
            (capture, entry) -> { }, capture -> { }, cancelled::incrementAndGet);
    }

    @AfterEach void close() { picker.close(); background.shutdownNow(); }

    /** Runs {@code key}'s bindings as Swing would for a press in {@code target}: its focused map, then its ancestors'. */
    static void press(Component target, int key) {
        javax.swing.SwingUtilities.processKeyBindings(new KeyEvent(target, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, key, KeyEvent.CHAR_UNDEFINED));
    }

    @Test void choosingAHostOnlySelectsItUntilConnect() {
        assertThat(picker.connectButton().isEnabled()).isFalse();
        picker.hosts().setSelectedIndex(0);
        picker.hosts().setSelectedIndex(1);
        assertThat(resolved).isEmpty();
        assertThat(picker.connectButton().isEnabled()).isTrue();
        picker.connectButton().doClick();
        assertThat(resolved).containsExactly(stage.id());
    }

    @Test void enterInTheHostDropdownConnects() {
        picker.hosts().setSelectedIndex(0);
        press(picker.hosts(), KeyEvent.VK_ENTER);
        assertThat(resolved).containsExactly(prod.id());
    }

    @Test void escapeCancelsFromTheDropdownAndFromTheList() {
        press(picker.hosts(), KeyEvent.VK_ESCAPE);
        assertThat(cancelled).hasValue(1);
        press(picker.browser().table(), KeyEvent.VK_ESCAPE);
        assertThat(cancelled).hasValue(2);
    }

    @Test void enterPressesAFocusedButton() {
        press(picker.cancelButton(), KeyEvent.VK_ENTER);
        assertThat(cancelled).hasValue(1);
        picker.hosts().setSelectedIndex(1);
        press(picker.connectButton(), KeyEvent.VK_ENTER);
        assertThat(resolved).containsExactly(stage.id());
    }

    @Test void theHostDropdownIsWhereFocusStarts() {
        assertThat(picker.isFocusTraversalPolicyProvider()).isTrue();
        assertThat(picker.getFocusTraversalPolicy().getDefaultComponent(picker)).isSameAs(picker.hosts());
    }

    @Test void startingOnAConnectionSelectsItsHostWithoutSigningInAgain() {
        picker.start(new ConnectionIdentity(List.of(new ConnectionIdentity.Hop(stage, "deploy"))), "/srv");
        assertThat(picker.hosts().getSelectedItem()).isEqualTo(stage);
        assertThat(resolved).isEmpty();
        assertThat(picker.connectButton().isEnabled()).isTrue();
    }
}
