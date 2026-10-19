package dev.jasper.app.contributions;

import dev.jasper.app.layout.GroupId;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class TabRegistryTest {
    private final TabRegistry registry = new TabRegistry();
    private final List<Tab> mounted = new ArrayList<>();
    private final UUID main = UUID.randomUUID(), detached = UUID.randomUUID();
    private void hosts() {
        registry.addHost(new WorkspaceHost(main, false, mounted::add, mounted::remove, tab -> {}, () -> {}));
        registry.addHost(new WorkspaceHost(detached, true, mounted::add, mounted::remove, tab -> {}, () -> {}));
        registry.setFactory(separate -> separate ? detached : main);
    }
    @Test void failedOpenLeavesNoEntryAndSameKeyCanMountAgain() {
        var fail = new boolean[] {true};
        registry.addHost(new WorkspaceHost(main, false, tab -> {
            if (fail[0]) { fail[0] = false; throw new IllegalStateException("mount failed"); }
            mounted.add(tab);
        }, mounted::remove, tab -> {}, () -> {}));
        assertThatThrownBy(() -> registry.open("plugin.file", "one", "One", main, new JPanel()))
            .isInstanceOf(IllegalStateException.class).hasMessage("mount failed");
        assertThat(registry.hosted()).isEmpty();
        var tab = registry.open("plugin.file", "one", "One", main, new JPanel());
        assertThat(registry.hosted()).containsExactly(tab);
        assertThat(mounted).containsExactly(tab);
    }

    @Test void failedDestinationMountRestoresSourceAndKeepsDirtyDocument() {
        hosts();
        var panel = new JTextArea("unsaved");
        var tab = registry.open("plugin.file", "one", "One", main, panel);
        tab.presentation("One", "", null, true, "", "");
        var bad = UUID.randomUUID();
        registry.addHost(new WorkspaceHost(bad, true, t -> { throw new IllegalStateException("destination failed"); },
            t -> {}, t -> {}, () -> {}));
        registry.setFactory(separate -> separate ? bad : main);
        assertThatThrownBy(() -> registry.move(tab, true)).isInstanceOf(IllegalStateException.class)
            .hasMessage("destination failed");
        assertThat(tab.window()).isEqualTo(main);
        assertThat(tab.closed()).isFalse();
        assertThat(tab.dirty()).isTrue();
        assertThat(tab.component()).isSameAs(panel);
        assertThat(mounted).containsExactly(tab);
    }

    @Test void failedDestinationAfterSourceUnregistersLeavesRecoverableUnmountedEntry() {
        var source = new dev.jasper.app.lifecycle.Subscription[1];
        source[0] = registry.addHost(new WorkspaceHost(main, false, mounted::add, tab -> {
            mounted.remove(tab);
            source[0].close();
        }, tab -> {}, () -> {}));
        var bad = UUID.randomUUID();
        registry.addHost(new WorkspaceHost(bad, true, tab -> { throw new IllegalStateException("destination failed"); },
            tab -> {}, tab -> {}, () -> {}));
        registry.setFactory(separate -> separate ? bad : main);
        var panel = new JTextArea("unsaved");
        var tab = registry.open("plugin.file", "one", "One", main, panel);
        tab.presentation("One", "", null, true, "", "");
        // Model the source window unregistering synchronously when its final tab leaves.
        assertThatThrownBy(() -> registry.move(tab, true)).isInstanceOf(IllegalStateException.class)
            .hasMessage("destination failed");
        assertThat(tab.window()).isNull();
        assertThat(tab.closed()).isFalse();
        assertThat(tab.dirty()).isTrue();
        assertThat(registry.hosted()).containsExactly(tab);
        var replacement = UUID.randomUUID();
        registry.addHost(new WorkspaceHost(replacement, false, mounted::add, mounted::remove, t -> {}, () -> {}));
        assertThat(registry.open("plugin.file", "one", "One", replacement, new JPanel())).isSameAs(tab);
        assertThat(tab.component()).isSameAs(panel);
        assertThat(tab.window()).isEqualTo(replacement);
        assertThat(mounted).containsExactly(tab);
    }

    @Test void failedDestinationAfterSourceUnregistersUsesAnotherMainHost() {
        var source = new dev.jasper.app.lifecycle.Subscription[1];
        source[0] = registry.addHost(new WorkspaceHost(main, false, mounted::add, tab -> {
            mounted.remove(tab);
            source[0].close();
        }, tab -> {}, () -> {}));
        var fallback = UUID.randomUUID();
        registry.addHost(new WorkspaceHost(fallback, false, mounted::add, mounted::remove, t -> {}, () -> {}));
        var bad = UUID.randomUUID();
        registry.addHost(new WorkspaceHost(bad, true, tab -> { throw new IllegalStateException("destination failed"); },
            tab -> {}, tab -> {}, () -> {}));
        registry.setFactory(separate -> separate ? bad : fallback);
        var panel = new JTextArea("unsaved");
        var tab = registry.open("plugin.file", "one", "One", main, panel);
        tab.presentation("One", "", null, true, "", "");
        assertThatThrownBy(() -> registry.move(tab, true)).isInstanceOf(IllegalStateException.class)
            .hasMessage("destination failed");
        assertThat(tab.window()).isEqualTo(fallback);
        assertThat(tab.closed()).isFalse();
        assertThat(tab.dirty()).isTrue();
        assertThat(tab.component()).isSameAs(panel);
        assertThat(mounted).containsExactly(tab);
    }
    @Test void openingAndMovingRetainIdentityAndNeverDisposeContent() {
        hosts();
        var panel = new JPanel();
        var tab = registry.open("plugin.file", "one", "One", main, panel);
        int[] disposed = {0}; tab.onClosed(() -> disposed[0]++);
        assertThat(registry.open("plugin.file", "one", "Other", detached, new JPanel())).isSameAs(tab);
        registry.move(tab, true);
        assertThat(tab.window()).isEqualTo(detached);
        assertThat(tab.component()).isSameAs(panel);
        assertThat(disposed[0]).isZero();
        registry.move(tab, false);
        assertThat(tab.window()).isEqualTo(main);
        tab.close(); tab.close();
        assertThat(disposed[0]).isEqualTo(1);
        assertThat(registry.hosted()).isEmpty();
    }
    @Test void pendingBatchCloseIsSharedAndCancellationRemovesNothing() {
        hosts();
        var a=registry.open("plugin.file", "a", "A", main, new JPanel());
        var b=registry.open("plugin.file", "b", "B", main, new JPanel());
        var answer=new CompletableFuture<Boolean>();
        registry.guard("plugin.file", (tabs,batch) -> { assertThat(tabs).containsExactly(a,b); return answer; });
        var first=registry.requestClose(List.of(a,b));
        assertThat(registry.requestClose(List.of(a,b))).isSameAs(first);
        registry.move(a,true);
        assertThat(a.window()).isEqualTo(main);
        answer.complete(false);
        assertThat(first.join()).isFalse();
        assertThat(mounted).containsExactly(a,b);
        registry.guard("plugin.file", (tabs,batch) -> CompletableFuture.completedFuture(true));
        assertThat(registry.requestClose(List.of(a,b)).join()).isTrue();
        assertThat(mounted).isEmpty();
    }
    @Test void lateDecisionAfterDisposalCannotReattachOrDoubleClose() {
        hosts(); var tab=registry.open("plugin.file","a","A", main,new JPanel());
        var answer=new CompletableFuture<Boolean>();
        registry.guard("plugin.file", (tabs,batch) -> answer);
        int[] disposed={0}; tab.onClosed(() -> disposed[0]++);
        var closing=registry.requestClose(List.of(tab));
        tab.close(); answer.complete(true); registry.move(tab,true); tab.select();
        assertThat(closing.join()).isTrue();
        assertThat(disposed[0]).isEqualTo(1);
        assertThat(mounted).isEmpty();
    }
    @Test void modalOwnerCannotMoveOrCloseUntilReleased() {
        hosts();
        var tab = registry.open("plugin.file", "a", "A", main, new JPanel());
        var pin = tab.pin();
        tab.move(true);
        assertThat(tab.window()).isEqualTo(main);
        assertThat(tab.requestClose().join()).isFalse();
        pin.close();
        tab.move(true);
        assertThat(tab.window()).isEqualTo(detached);
        assertThat(tab.requestClose().join()).isTrue();
    }
    @Test void reentrantWindowCloseDoesNotWaitOnItsOwnPendingGuard() {
        hosts();
        var tab = registry.open("plugin.file", "a", "A", main, new JPanel());
        var answer = new CompletableFuture<Boolean>();
        registry.guard("plugin.file", (tabs, batch) -> answer);
        registry.approve(List.of(tab));
        var timer = new javax.swing.Timer(30, event -> answer.complete(true));
        timer.setRepeats(false); timer.start();
        try {
            assertThat(registry.mayClose(List.of(tab))).as("nested close is refused immediately").isFalse();
            assertThat(answer).isNotDone();
        } finally { timer.stop(); answer.complete(false); }
    }
    @Test void windowCloseRefusesNewDocumentsOpenedDuringApproval() {
        hosts();
        registry.open("plugin.file", "a", "A", main, new JPanel());
        registry.guard("plugin.file", (tabs, batch) -> {
            registry.open("plugin.file", "b", "B", main, new JPanel());
            return CompletableFuture.completedFuture(true);
        });
        assertThat(registry.mayClose(() -> registry.inWindow(main))).isFalse();
        assertThat(registry.hosted()).hasSize(2);
    }

    @Test void aHostedTabHasAnIdItsKindAndItsContent() {
        hosts();
        var panel = new JPanel();
        var tab = registry.open("plugin.file", "one", "One", main, panel);
        assertThat(tab.kind()).isEqualTo(Tab.Kind.HOSTED);
        assertThat(tab.content()).isEqualTo(new HostedContent(panel));
        assertThat(tab.component()).isSameAs(panel);
        assertThat(registry.tab(tab.id())).contains(tab);
        assertThat(registry.tabs()).containsExactly(tab);
        assertThat(registry.inWindow(main)).containsExactly(tab);
        var other = registry.open("plugin.file", "two", "Two", main, new JPanel());
        assertThat(other.id()).isNotEqualTo(tab.id());
        tab.close();
        assertThat(registry.tab(tab.id())).isEmpty();
        assertThat(registry.tabs()).containsExactly(other);
    }

    @Test void aTabSaysItIsMovingWhileItsWindowsDetachAndAttachIt() {
        var seen = new ArrayList<String>();
        registry.addHost(new WorkspaceHost(main, false, tab -> seen.add("attach main " + tab.moving()),
            tab -> seen.add("detach main " + tab.moving()), tab -> {}, () -> {}));
        registry.addHost(new WorkspaceHost(detached, true, tab -> seen.add("attach detached " + tab.moving()),
            tab -> seen.add("detach detached " + tab.moving()), tab -> {}, () -> {}));
        registry.setFactory(separate -> separate ? detached : main);
        var tab = registry.open("plugin.file", "one", "One", main, new JPanel());
        registry.move(tab, true);
        assertThat(tab.moving()).as("only during the move").isFalse();
        tab.close();
        assertThat(seen).containsExactly("attach main false", "detach main true", "attach detached true", "detach detached false");
    }

    @Test void theHostSaysWhichGroupHoldsATab() {
        var group = GroupId.random();
        registry.addHost(new WorkspaceHost(main, false, mounted::add, mounted::remove, tab -> {}, () -> {},
            id -> Optional.of(group)));
        var tab = registry.open("plugin.file", "one", "One", main, new JPanel());
        assertThat(tab.group()).contains(group);
        assertThat(registry.groupOf(tab)).contains(group);
        tab.close();
        assertThat(tab.group()).as("a closed tab is in no group").isEmpty();
    }
}
