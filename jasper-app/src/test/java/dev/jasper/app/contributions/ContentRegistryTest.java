package dev.jasper.app.contributions;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class ContentRegistryTest {
    private final ContentRegistry registry = new ContentRegistry();
    private final List<ContentEntry> mounted = new ArrayList<>();
    private final UUID main = UUID.randomUUID(), detached = UUID.randomUUID();
    private void hosts() {
        registry.addHost(new ContentHost(main, false, mounted::add, mounted::remove, entry -> {}, () -> {}));
        registry.addHost(new ContentHost(detached, true, mounted::add, mounted::remove, entry -> {}, () -> {}));
        registry.setFactory(separate -> separate ? detached : main);
    }
    @Test void failedOpenLeavesNoEntryAndSameKeyCanMountAgain() {
        var fail = new boolean[] {true};
        registry.addHost(new ContentHost(main, false, entry -> {
            if (fail[0]) { fail[0] = false; throw new IllegalStateException("mount failed"); }
            mounted.add(entry);
        }, mounted::remove, entry -> {}, () -> {}));
        assertThatThrownBy(() -> registry.open("plugin.file", "one", "One", main, new JPanel()))
            .isInstanceOf(IllegalStateException.class).hasMessage("mount failed");
        assertThat(registry.entries()).isEmpty();
        var entry = registry.open("plugin.file", "one", "One", main, new JPanel());
        assertThat(registry.entries()).containsExactly(entry);
        assertThat(mounted).containsExactly(entry);
    }

    @Test void failedDestinationMountRestoresSourceAndKeepsDirtyDocument() {
        hosts();
        var panel = new JTextArea("unsaved");
        var entry = registry.open("plugin.file", "one", "One", main, panel);
        entry.presentation("One", "", null, true, "", "");
        var bad = UUID.randomUUID();
        registry.addHost(new ContentHost(bad, true, e -> { throw new IllegalStateException("destination failed"); },
            e -> {}, e -> {}, () -> {}));
        registry.setFactory(separate -> separate ? bad : main);
        assertThatThrownBy(() -> registry.move(entry, true)).isInstanceOf(IllegalStateException.class)
            .hasMessage("destination failed");
        assertThat(entry.window()).isEqualTo(main);
        assertThat(entry.closed()).isFalse();
        assertThat(entry.dirty()).isTrue();
        assertThat(entry.component()).isSameAs(panel);
        assertThat(mounted).containsExactly(entry);
    }

    @Test void failedDestinationAfterSourceUnregistersLeavesRecoverableUnmountedEntry() {
        var source = new dev.jasper.app.lifecycle.Subscription[1];
        source[0] = registry.addHost(new ContentHost(main, false, mounted::add, entry -> {
            mounted.remove(entry);
            source[0].close();
        }, entry -> {}, () -> {}));
        var bad = UUID.randomUUID();
        registry.addHost(new ContentHost(bad, true, entry -> { throw new IllegalStateException("destination failed"); },
            entry -> {}, entry -> {}, () -> {}));
        registry.setFactory(separate -> separate ? bad : main);
        var panel = new JTextArea("unsaved");
        var entry = registry.open("plugin.file", "one", "One", main, panel);
        entry.presentation("One", "", null, true, "", "");
        // Model the source window unregistering synchronously when its final tab leaves.
        assertThatThrownBy(() -> registry.move(entry, true)).isInstanceOf(IllegalStateException.class)
            .hasMessage("destination failed");
        assertThat(entry.window()).isNull();
        assertThat(entry.closed()).isFalse();
        assertThat(entry.dirty()).isTrue();
        assertThat(registry.entries()).containsExactly(entry);
        var replacement = UUID.randomUUID();
        registry.addHost(new ContentHost(replacement, false, mounted::add, mounted::remove, e -> {}, () -> {}));
        assertThat(registry.open("plugin.file", "one", "One", replacement, new JPanel())).isSameAs(entry);
        assertThat(entry.component()).isSameAs(panel);
        assertThat(entry.window()).isEqualTo(replacement);
        assertThat(mounted).containsExactly(entry);
    }

    @Test void failedDestinationAfterSourceUnregistersUsesAnotherMainHost() {
        var source = new dev.jasper.app.lifecycle.Subscription[1];
        source[0] = registry.addHost(new ContentHost(main, false, mounted::add, entry -> {
            mounted.remove(entry);
            source[0].close();
        }, entry -> {}, () -> {}));
        var fallback = UUID.randomUUID();
        registry.addHost(new ContentHost(fallback, false, mounted::add, mounted::remove, e -> {}, () -> {}));
        var bad = UUID.randomUUID();
        registry.addHost(new ContentHost(bad, true, entry -> { throw new IllegalStateException("destination failed"); },
            entry -> {}, entry -> {}, () -> {}));
        registry.setFactory(separate -> separate ? bad : fallback);
        var panel = new JTextArea("unsaved");
        var entry = registry.open("plugin.file", "one", "One", main, panel);
        entry.presentation("One", "", null, true, "", "");
        assertThatThrownBy(() -> registry.move(entry, true)).isInstanceOf(IllegalStateException.class)
            .hasMessage("destination failed");
        assertThat(entry.window()).isEqualTo(fallback);
        assertThat(entry.closed()).isFalse();
        assertThat(entry.dirty()).isTrue();
        assertThat(entry.component()).isSameAs(panel);
        assertThat(mounted).containsExactly(entry);
    }
    @Test void openingAndMovingRetainIdentityAndNeverDisposeContent() {
        hosts();
        var panel = new JPanel();
        var entry = registry.open("plugin.file", "one", "One", main, panel);
        int[] disposed = {0}; entry.onClosed(() -> disposed[0]++);
        assertThat(registry.open("plugin.file", "one", "Other", detached, new JPanel())).isSameAs(entry);
        registry.move(entry, true);
        assertThat(entry.window()).isEqualTo(detached);
        assertThat(entry.component()).isSameAs(panel);
        assertThat(disposed[0]).isZero();
        registry.move(entry, false);
        assertThat(entry.window()).isEqualTo(main);
        entry.close(); entry.close();
        assertThat(disposed[0]).isEqualTo(1);
        assertThat(registry.entries()).isEmpty();
    }
    @Test void pendingBatchCloseIsSharedAndCancellationRemovesNothing() {
        hosts();
        var a=registry.open("plugin.file", "a", "A", main, new JPanel());
        var b=registry.open("plugin.file", "b", "B", main, new JPanel());
        var answer=new CompletableFuture<Boolean>();
        registry.guard("plugin.file", (entries,batch) -> { assertThat(entries).containsExactly(a,b); return answer; });
        var first=registry.requestClose(List.of(a,b));
        assertThat(registry.requestClose(List.of(a,b))).isSameAs(first);
        registry.move(a,true);
        assertThat(a.window()).isEqualTo(main);
        answer.complete(false);
        assertThat(first.join()).isFalse();
        assertThat(mounted).containsExactly(a,b);
        registry.guard("plugin.file", (entries,batch) -> CompletableFuture.completedFuture(true));
        assertThat(registry.requestClose(List.of(a,b)).join()).isTrue();
        assertThat(mounted).isEmpty();
    }
    @Test void lateDecisionAfterDisposalCannotReattachOrDoubleClose() {
        hosts(); var entry=registry.open("plugin.file","a","A", main,new JPanel());
        var answer=new CompletableFuture<Boolean>();
        registry.guard("plugin.file", (entries,batch) -> answer);
        int[] disposed={0}; entry.onClosed(() -> disposed[0]++);
        var closing=registry.requestClose(List.of(entry));
        entry.close(); answer.complete(true); registry.move(entry,true); entry.select();
        assertThat(closing.join()).isTrue();
        assertThat(disposed[0]).isEqualTo(1);
        assertThat(mounted).isEmpty();
    }
    @Test void modalOwnerCannotMoveOrCloseUntilReleased() {
        hosts();
        var entry = registry.open("plugin.file", "a", "A", main, new JPanel());
        var pin = entry.pin();
        entry.move(true);
        assertThat(entry.window()).isEqualTo(main);
        assertThat(entry.requestClose().join()).isFalse();
        pin.close();
        entry.move(true);
        assertThat(entry.window()).isEqualTo(detached);
        assertThat(entry.requestClose().join()).isTrue();
    }
    @Test void reentrantWindowCloseDoesNotWaitOnItsOwnPendingGuard() {
        hosts();
        var entry = registry.open("plugin.file", "a", "A", main, new JPanel());
        var answer = new CompletableFuture<Boolean>();
        registry.guard("plugin.file", (entries, batch) -> answer);
        registry.approve(List.of(entry));
        var timer = new javax.swing.Timer(30, event -> answer.complete(true));
        timer.setRepeats(false); timer.start();
        try {
            assertThat(registry.mayClose(List.of(entry))).as("nested close is refused immediately").isFalse();
            assertThat(answer).isNotDone();
        } finally { timer.stop(); answer.complete(false); }
    }
    @Test void windowCloseRefusesNewDocumentsOpenedDuringApproval() {
        hosts();
        registry.open("plugin.file", "a", "A", main, new JPanel());
        registry.guard("plugin.file", (entries, batch) -> {
            registry.open("plugin.file", "b", "B", main, new JPanel());
            return CompletableFuture.completedFuture(true);
        });
        assertThat(registry.mayClose(() -> registry.inWindow(main))).isFalse();
        assertThat(registry.entries()).hasSize(2);
    }
}
