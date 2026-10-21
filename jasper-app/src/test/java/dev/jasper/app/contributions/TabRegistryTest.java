package dev.jasper.app.contributions;

import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.TabId;
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
        registry.addHost(new WorkspaceHost(main, mounted::add, mounted::remove, tab -> {}, () -> {}));
        registry.addHost(new WorkspaceHost(detached, mounted::add, mounted::remove, tab -> {}, () -> {}));
        registry.windows(windows(main, detached));
    }

    /** The window manager's part as a test plays it: {@code main} is the main window, and a new window is {@code separate}. */
    private TabRegistry.Windows windows(UUID main, UUID separate) {
        return new TabRegistry.Windows(() -> main, main::equals, tab -> registry.move(tab, separate));
    }

    /**
     * Workspace plan 8b Rulings 18: a tab reaches its window for a terminal beside it and for Move Pane to New Tab; the
     * registry reports whether the window split (its final review).
     */
    @Test void aTabReachesItsWindowForATerminalBesideItAndMovePaneToNewTab() {
        var heard = new ArrayList<String>();
        boolean[] shares = {true}, splits = {true};
        java.util.function.BiPredicate<Tab, Side> split = (tab, side) -> {
            heard.add("terminal " + side + " of " + tab.title());
            return splits[0];
        };
        registry.addHost(new WorkspaceHost(main, tab -> {}, tab -> {}, tab -> {}, () -> {}, tab -> Optional.empty(),
            (tab, group, side) -> {}, split::test, tab -> shares[0], tab -> heard.add("new tab " + tab.title())));
        Tab notes = registry.open("dev.example.editor", "notes", "Notes", main, new JPanel());
        assertThat(registry.splitTerminal(notes, Side.DOWN)).as("its window took it").isTrue();
        splits[0] = false;
        assertThat(registry.splitTerminal(notes, Side.RIGHT)).as("its window split nothing").isFalse();
        assertThat(registry.mayMoveToNewTab(notes)).isTrue();
        registry.moveToNewTab(notes);
        shares[0] = false;
        assertThat(registry.mayMoveToNewTab(notes)).as("its tab's only pane").isFalse();
        registry.moveToNewTab(notes);
        shares[0] = true;
        var pin = notes.pin();
        assertThat(registry.mayMoveToNewTab(notes)).as("a pinned tab").isFalse();
        pin.close();
        notes.close();
        assertThat(registry.splitTerminal(notes, Side.RIGHT)).as("a closed tab").isFalse();
        registry.moveToNewTab(notes);
        assertThat(heard).as("a closed tab reaches nothing")
            .containsExactly("terminal DOWN of Notes", "terminal RIGHT of Notes", "new tab Notes");
    }

    /**
     * Workspace plan 4a: the window a move leaves hears of it only after the move has reported itself, so a failed attach
     * can still go back to it; the window manager then closes a child window the move left empty.
     */
    @Test void theWindowAMoveLeavesHearsOfItAfterTheMoveAndAFailedMoveGoesBackToIt() {
        var heard = new ArrayList<String>();
        var child = UUID.randomUUID();
        var bad = UUID.randomUUID();
        registry.addHost(new WorkspaceHost(main, tab -> heard.add("attach main"), tab -> heard.add("detach main"), tab -> {}, () -> {}));
        registry.addHost(new WorkspaceHost(child, tab -> heard.add("attach child"), tab -> heard.add("detach child"), tab -> {}, () -> {}));
        registry.addHost(new WorkspaceHost(bad, tab -> { throw new IllegalStateException("destination failed"); }, tab -> {}, tab -> {}, () -> {}));
        registry.windows(new TabRegistry.Windows(() -> main, main::equals, tab -> {},
            window -> heard.add("moved out of " + (window.equals(child) ? "child" : "main"))));
        var tab = registry.open("plugin.file", "one", "One", child, new JPanel());
        heard.clear();
        tab.moveToMainWindow();
        assertThat(heard).containsExactly("detach child", "attach main", "moved out of child");
        registry.move(tab, child);
        heard.clear();
        assertThatThrownBy(() -> registry.move(tab, bad)).isInstanceOf(IllegalStateException.class).hasMessage("destination failed");
        assertThat(tab.window()).as("back in the window it left, which a move never closes midway").isEqualTo(child);
        assertThat(heard).containsExactly("detach child", "attach child", "moved out of child");
    }

    @Test void aPlacementAttachesBesideTheActiveGroupOrInANewWindowAndLeavesTheOwnerAlone() {
        var heard = new ArrayList<String>();
        registry.addHost(new WorkspaceHost(main, tab -> heard.add("attach " + tab.title()), mounted::remove,
            tab -> heard.add("select " + tab.title()), () -> {}, tab -> Optional.empty(),
            (tab, group, side) -> heard.add("beside " + tab.title() + " " + side + (group == null ? " of the active group" : ""))));
        registry.addHost(new WorkspaceHost(detached, tab -> heard.add("new window " + tab.title()), mounted::remove, tab -> {}, () -> {}));
        registry.windows(new TabRegistry.Windows(() -> main, main::equals, tab -> registry.move(tab, detached), window -> {}, () -> detached));
        registry.open("plugin.file", "right", "Right", main, new JPanel(), TabRegistry.Placement.SPLIT_RIGHT);
        registry.open("plugin.file", "down", "Down", main, new JPanel(), TabRegistry.Placement.SPLIT_DOWN);
        var here = registry.open("plugin.file", "here", "Here", main, new JPanel());
        assertThat(heard).containsExactly("beside Right RIGHT of the active group", "select Right",
            "beside Down DOWN of the active group", "select Down", "attach Here", "select Here");
        heard.clear();
        var away = registry.open("plugin.file", "away", "Away", main, new JPanel(), TabRegistry.Placement.NEW_WINDOW);
        assertThat(away.window()).isEqualTo(detached);
        assertThat(heard).as("opened in the new window; the owner's window is not touched").containsExactly("new window Away");
        assertThat(here.window()).isEqualTo(main);
        assertThat(registry.open("plugin.file", "right", "Again", null, new JPanel(), TabRegistry.Placement.NEW_WINDOW).window())
            .as("an open key is selected where it is").isEqualTo(main);
    }

    /** A tab placed in a new window that fails to show it goes, and the window's close keeps no failure from the caller. */
    @Test void aNewWindowThatFailsToShowItsPlacedTabClosesAndKeepsTheFailure() {
        var heard = new ArrayList<String>();
        registry.addHost(new WorkspaceHost(main, mounted::add, mounted::remove, tab -> {}, () -> {}));
        registry.addHost(new WorkspaceHost(detached, tab -> { throw new IllegalStateException("show failed"); }, tab -> {}, tab -> {}, () -> {}));
        registry.windows(new TabRegistry.Windows(() -> main, main::equals, tab -> {}, window -> {
            heard.add(window.equals(detached) ? "the new window closes" : "main");
            throw new IllegalStateException("close failed");
        }, () -> detached));
        assertThatThrownBy(() -> registry.open("plugin.file", "away", "Away", main, new JPanel(), TabRegistry.Placement.NEW_WINDOW))
            .hasMessage("show failed")
            .satisfies(failure -> assertThat(failure.getSuppressed()).extracting(Throwable::getMessage).contains("close failed"));
        assertThat(heard).containsExactly("the new window closes");
        assertThat(registry.tabs()).as("no tab without a window").isEmpty();
    }

    /**
     * Workspace plan 4a's re-review: a tab that waited for a main window that closed again while an earlier waiting tab
     * opened there is shown by the next main window, not by the closed one.
     */
    @Test void aWaitingTabWhoseMainWindowClosedMeanwhileWaitsForTheNextOne() {
        UUID[] up = {null};
        var next = UUID.randomUUID();
        registry.windows(new TabRegistry.Windows(() -> up[0], id -> id.equals(up[0]), tab -> {}));
        var first = registry.open("plugin.file", "first", "First", null, new JPanel());
        var second = registry.open("plugin.file", "second", "Second", null, new JPanel(), TabRegistry.Placement.SPLIT_RIGHT);
        var registration = new ArrayList<dev.jasper.app.lifecycle.Subscription>();
        // The main window closes as its first waiting tab opens there.
        registration.add(registry.addHost(new WorkspaceHost(main, tab -> { if (tab == first) registration.getFirst().close(); },
            tab -> {}, tab -> {}, () -> {})));
        up[0] = main;
        registry.mainOpened(main);
        assertThat(first.closed()).as("it went with the main window").isTrue();
        assertThat(second.window()).as("not shown in a closed window").isNull();
        assertThat(second.closed()).isFalse();
        registry.addHost(new WorkspaceHost(next, mounted::add, mounted::remove, tab -> {}, () -> {}, tab -> Optional.empty(),
            (tab, group, side) -> { throw new AssertionError("a tab that waited opens in the active group"); }));
        up[0] = next;
        registry.mainOpened(next);
        assertThat(second.window()).as("the next main window shows it").isEqualTo(next);
        assertThat(mounted).as("at the end of its active group, as a tab that waited opens").containsExactly(second);
    }

    /** Workspace plan 4a's re-review: a move a change listener refuses leaves the tab where it was, as active as it was. */
    @Test void aRefusedMoveLeavesTheTabAsActiveAsItWas() {
        hosts();
        var tab = registry.open("plugin.file", "one", "One", main, new JPanel());
        tab.active(true);
        tab.onChanged(() -> { if (detached.equals(tab.window())) throw new IllegalStateException("refused"); });
        assertThatThrownBy(() -> registry.move(tab, detached)).hasMessage("refused");
        assertThat(tab.window()).isEqualTo(main);
        assertThat(tab.active()).as("as active as before the move").isTrue();
    }

    @Test void withoutTheWindowManagerTheFirstWorkspaceIsTheMainWindowAndNothingMovesToANewWindow() {
        var other = UUID.randomUUID();
        registry.addHost(new WorkspaceHost(main, mounted::add, mounted::remove, tab -> {}, () -> {}));
        registry.addHost(new WorkspaceHost(other, mounted::add, mounted::remove, tab -> {}, () -> {}));
        var tab = registry.open("plugin.file", "one", "One", null, new JPanel());
        assertThat(tab.window()).as("a view without a window goes to the main window").isEqualTo(main);
        assertThat(registry.isMain(tab.window())).isTrue();
        tab.moveToNewWindow();
        assertThat(tab.window()).isEqualTo(main);
        registry.move(tab, other);
        assertThat(registry.isMain(tab.window())).isFalse();
        tab.moveToMainWindow();
        assertThat(tab.window()).isEqualTo(main);
    }

    /**
     * Workspace plan 4a's final review: the tabs that waited for the main window open there in the order they were
     * asked for once it is up, and the last one is selected; one that fails to open there goes and keeps no other out.
     */
    @Test void theTabsThatWaitedForTheMainWindowOpenThereInOrderAndAFailureKeepsNoneOut() {
        var heard = new ArrayList<String>();
        UUID[] up = {null};
        registry.addHost(new WorkspaceHost(main, tab -> {
            heard.add("attach " + tab.key());
            if (tab.key().equals("bad")) throw new IllegalStateException("attach failed");
        }, tab -> heard.add("detach " + tab.key()), tab -> heard.add("select " + tab.key()), () -> {}));
        registry.windows(new TabRegistry.Windows(() -> up[0], main::equals, tab -> {}));
        var first = registry.open("plugin.file", "first", "First", null, new JPanel());
        var bad = registry.open("plugin.file", "bad", "Bad", null, new JPanel());
        var last = registry.open("plugin.file", "last", "Last", null, new JPanel());
        assertThat(heard).as("they wait for it").isEmpty();
        up[0] = main;
        registry.mainOpened(main);
        assertThat(heard).containsExactly("attach first", "attach bad", "detach bad", "attach last", "select last");
        assertThat(first.window()).isEqualTo(main);
        assertThat(last.window()).isEqualTo(main);
        assertThat(bad.closed()).as("removed, so disposed").isTrue();
        assertThat(registry.tabs()).containsExactly(first, last);
    }

    /** Workspace plan 4a: a window that fails to let a moving tab go still loses it to the move, which then rethrows. */
    @Test void aMoveWhoseSourceFailsToLetGoStillArrivesAndRethrows() {
        var heard = new ArrayList<String>();
        var source = UUID.randomUUID();
        var target = UUID.randomUUID();
        registry.addHost(new WorkspaceHost(source, tab -> heard.add("attach source"),
            tab -> { heard.add("detach source"); throw new IllegalStateException("detach failed"); }, tab -> {}, () -> {}));
        registry.addHost(new WorkspaceHost(target, tab -> heard.add("attach target"), tab -> heard.add("detach target"),
            tab -> heard.add("select target"), () -> {}));
        registry.windows(new TabRegistry.Windows(() -> source, source::equals, tab -> {},
            window -> heard.add("moved out of " + (window.equals(source) ? "source" : "target"))));
        var tab = registry.open("plugin.file", "one", "One", source, new JPanel());
        heard.clear();
        assertThatThrownBy(() -> registry.move(tab, target)).isInstanceOf(IllegalStateException.class).hasMessage("detach failed")
            .satisfies(failure -> assertThat(failure.getSuppressed()).as("nothing else failed").isEmpty());
        assertThat(tab.window()).as("shown where it is said to be").isEqualTo(target);
        assertThat(heard).as("and selected there").containsExactly("detach source", "attach target", "moved out of source", "select target");
        assertThat(tab.moving()).as("the move is over").isFalse();
    }

    /**
     * Workspace plan 4a: a failure as the window a move left hears of it is kept behind the move's own, and never keeps the
     * tab that arrived from being selected.
     */
    @Test void aFailureAsTheWindowAMoveLeftHearsOfItIsKeptBehindTheMovesOwn() {
        var heard = new ArrayList<String>();
        var source = UUID.randomUUID();
        var target = UUID.randomUUID();
        boolean[] failDetach = {false}, failAttach = {false}, failClose = {true};
        registry.addHost(new WorkspaceHost(source, tab -> heard.add("attach source"), tab -> {
            heard.add("detach source");
            if (failDetach[0]) throw new IllegalStateException("detach failed");
        }, tab -> heard.add("select source"), () -> {}));
        registry.addHost(new WorkspaceHost(target, tab -> {
            heard.add("attach target");
            if (failAttach[0]) throw new IllegalStateException("destination failed");
        }, tab -> heard.add("detach target"), tab -> heard.add("select target"), () -> {}));
        registry.windows(new TabRegistry.Windows(() -> source, source::equals, tab -> {}, window -> {
            if (failClose[0]) throw new IllegalStateException("close failed");
        }));
        var tab = registry.open("plugin.file", "one", "One", source, new JPanel());
        Runnable back = () -> { failClose[0] = false; registry.move(tab, source); failClose[0] = true; };

        heard.clear();
        assertThatThrownBy(() -> registry.move(tab, target)).isInstanceOf(IllegalStateException.class).hasMessage("close failed");
        assertThat(tab.window()).as("a clean move arrives").isEqualTo(target);
        assertThat(heard).as("and is selected there").containsExactly("detach source", "attach target", "select target");
        assertThat(tab.moving()).as("the move is over").isFalse();

        back.run();
        failDetach[0] = true;
        heard.clear();
        assertThatThrownBy(() -> registry.move(tab, target)).hasMessage("detach failed").satisfies(failure ->
            assertThat(failure.getSuppressed()).extracting(Throwable::getMessage).containsExactly("close failed"));
        assertThat(tab.window()).isEqualTo(target);
        assertThat(heard).as("a source that failed to let go still loses the tab").containsExactly("detach source", "attach target", "select target");
        failDetach[0] = false;

        back.run();
        failAttach[0] = true;
        heard.clear();
        assertThatThrownBy(() -> registry.move(tab, target)).hasMessage("destination failed").satisfies(failure ->
            assertThat(failure.getSuppressed()).extracting(Throwable::getMessage).containsExactly("close failed"));
        assertThat(tab.window()).as("back in the source").isEqualTo(source);
        assertThat(heard).as("and not selected again").containsExactly("detach source", "attach target", "detach target", "attach source");

        failClose[0] = false;
        failDetach[0] = true;
        heard.clear();
        assertThatThrownBy(() -> registry.move(tab, target)).hasMessage("detach failed").satisfies(failure ->
            assertThat(failure.getSuppressed()).extracting(Throwable::getMessage).containsExactly("destination failed"));
        assertThat(tab.window()).isEqualTo(source);
    }

    /**
     * Workspace plan 4a: a tab being closed, or closed, reaches no window when moved (a pinned tab, one already there and an
     * unknown window are {@code anyTabMovesToANamedWindowAndIsSelectedThere}'s).
     */
    @Test void aTabBeingClosedOrClosedReachesNoWindowWhenMoved() {
        var heard = new ArrayList<String>();
        var other = UUID.randomUUID();
        registry.addHost(new WorkspaceHost(main, tab -> heard.add("attach main"), tab -> heard.add("detach main"), tab -> {}, () -> {}));
        registry.addHost(new WorkspaceHost(other, tab -> heard.add("attach other"), tab -> heard.add("detach other"), tab -> {}, () -> {}));
        var tab = registry.open("plugin.file", "one", "One", main, new JPanel());
        var answer = new CompletableFuture<Boolean>();
        registry.guard("plugin.file", (tabs, batch) -> answer);
        registry.requestClose(List.of(tab));
        heard.clear();
        registry.move(tab, other);
        assertThat(heard).as("being closed: it stays").isEmpty();
        answer.complete(true);
        assertThat(tab.closed()).isTrue();
        heard.clear();
        registry.move(tab, other);
        assertThat(heard).as("closed: it goes nowhere").isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> registry.move(tab, UUID.randomUUID())).withMessageStartingWith("No workspace");
    }
    /** Workspace tabs spec 12: a tab dropped on a group's edge in another window lands beside that group, as a move. */
    @Test void aTabMovesIntoANewGroupBesideAGroupOfAnotherWindow() {
        var beside = new ArrayList<String>();
        var group = GroupId.random();
        registry.addHost(new WorkspaceHost(main, mounted::add, mounted::remove, tab -> {}, () -> {}));
        registry.addHost(new WorkspaceHost(detached, mounted::add, mounted::remove, tab -> {}, () -> {}, tab -> Optional.empty(),
            (tab, at, side) -> { beside.add(tab.title() + " " + at.equals(group) + " " + side + " moving " + tab.moving()); mounted.add(tab); }));
        registry.windows(windows(main, detached));
        var tab = registry.open("plugin.file", "notes", "Notes", main, new JPanel());
        registry.move(tab, detached, group, Side.DOWN);
        assertThat(beside).containsExactly("Notes true DOWN moving true");
        assertThat(tab.window()).isEqualTo(detached);
        assertThat(tab.moving()).isFalse();
        registry.move(tab, detached, group, Side.UP);
        assertThat(beside).as("a tab already there stays").hasSize(1);
    }

    @Test void failedOpenLeavesNoEntryAndSameKeyCanMountAgain() {
        var fail = new boolean[] {true};
        registry.addHost(new WorkspaceHost(main, tab -> {
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
        registry.addHost(new WorkspaceHost(bad, t -> { throw new IllegalStateException("destination failed"); },
            t -> {}, t -> {}, () -> {}));
        registry.windows(windows(main, bad));
        assertThatThrownBy(() -> registry.moveToNewWindow(tab)).isInstanceOf(IllegalStateException.class)
            .hasMessage("destination failed");
        assertThat(tab.window()).isEqualTo(main);
        assertThat(tab.closed()).isFalse();
        assertThat(tab.dirty()).isTrue();
        assertThat(tab.component()).isSameAs(panel);
        assertThat(mounted).containsExactly(tab);
    }

    @Test void failedDestinationAfterSourceUnregistersLeavesRecoverableUnmountedEntry() {
        var source = new dev.jasper.app.lifecycle.Subscription[1];
        source[0] = registry.addHost(new WorkspaceHost(main, mounted::add, tab -> {
            mounted.remove(tab);
            source[0].close();
        }, tab -> {}, () -> {}));
        var bad = UUID.randomUUID();
        registry.addHost(new WorkspaceHost(bad, tab -> { throw new IllegalStateException("destination failed"); },
            tab -> {}, tab -> {}, () -> {}));
        registry.windows(windows(main, bad));
        var panel = new JTextArea("unsaved");
        var tab = registry.open("plugin.file", "one", "One", main, panel);
        tab.presentation("One", "", null, true, "", "");
        // Model the source window unregistering synchronously when its final tab leaves.
        assertThatThrownBy(() -> registry.moveToNewWindow(tab)).isInstanceOf(IllegalStateException.class)
            .hasMessage("destination failed");
        assertThat(tab.window()).isNull();
        assertThat(tab.closed()).isFalse();
        assertThat(tab.dirty()).isTrue();
        assertThat(registry.hosted()).containsExactly(tab);
        var replacement = UUID.randomUUID();
        registry.addHost(new WorkspaceHost(replacement, mounted::add, mounted::remove, t -> {}, () -> {}));
        assertThat(registry.open("plugin.file", "one", "One", replacement, new JPanel())).isSameAs(tab);
        assertThat(tab.component()).isSameAs(panel);
        assertThat(tab.window()).isEqualTo(replacement);
        assertThat(mounted).containsExactly(tab);
    }

    @Test void failedDestinationAfterSourceUnregistersUsesAnotherMainHost() {
        var source = new dev.jasper.app.lifecycle.Subscription[1];
        source[0] = registry.addHost(new WorkspaceHost(main, mounted::add, tab -> {
            mounted.remove(tab);
            source[0].close();
        }, tab -> {}, () -> {}));
        var fallback = UUID.randomUUID();
        registry.addHost(new WorkspaceHost(fallback, mounted::add, mounted::remove, t -> {}, () -> {}));
        var bad = UUID.randomUUID();
        registry.addHost(new WorkspaceHost(bad, tab -> { throw new IllegalStateException("destination failed"); },
            tab -> {}, tab -> {}, () -> {}));
        registry.windows(windows(fallback, bad));
        var panel = new JTextArea("unsaved");
        var tab = registry.open("plugin.file", "one", "One", main, panel);
        tab.presentation("One", "", null, true, "", "");
        assertThatThrownBy(() -> registry.moveToNewWindow(tab)).isInstanceOf(IllegalStateException.class)
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
        registry.moveToNewWindow(tab);
        assertThat(tab.window()).isEqualTo(detached);
        assertThat(tab.component()).isSameAs(panel);
        assertThat(disposed[0]).isZero();
        registry.moveToMainWindow(tab);
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
        registry.moveToNewWindow(a);
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
        tab.close(); answer.complete(true); registry.moveToNewWindow(tab); tab.select();
        assertThat(closing.join()).isTrue();
        assertThat(disposed[0]).isEqualTo(1);
        assertThat(mounted).isEmpty();
    }
    @Test void modalOwnerCannotMoveOrCloseUntilReleased() {
        hosts();
        var tab = registry.open("plugin.file", "a", "A", main, new JPanel());
        var pin = tab.pin();
        tab.moveToNewWindow();
        assertThat(tab.window()).isEqualTo(main);
        assertThat(tab.requestClose().join()).isFalse();
        pin.close();
        tab.moveToNewWindow();
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

    /** Workspace tabs spec 13: a plugin's restorer rebuilds a saved tab, which takes the saved id and window, not yet shown. */
    @Test void aRestorerRebuildsASavedTabWithItsIdAndWindowOrSaysWhyNot() {
        hosts();
        TabId saved = TabId.random();
        var state = new dev.jasper.app.layout.TabRestore("note", Map.of("name", "todo"));
        var registration = registry.restorer("plugin.notes", "note", restoring -> {
            registry.open("plugin.notes", "note:todo", restoring.title(), null, new JPanel());
            return Optional.empty();
        });
        assertThat(registry.restores("plugin.notes", "note")).isTrue();
        TabRegistry.Restored restored = registry.restore(saved, "plugin.notes", new TabRegistry.Restoring(detached, "Todo", state));
        Tab tab = restored.tab().orElseThrow();
        assertThat(tab.id()).isEqualTo(saved);
        assertThat(tab.window()).as("the window being restored, whatever the plugin named").isEqualTo(detached);
        assertThat(tab.title()).isEqualTo("Todo");
        assertThat(mounted).as("that window places it").isEmpty();
        assertThat(registry.tab(saved)).contains(tab);
        assertThat(registry.slot("plugin.notes", detached)).as("taken").isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> registry.restorer("plugin.notes", "note", restoring -> Optional.empty()));
        var other = new TabRegistry.Restoring(main, "Other", new dev.jasper.app.layout.TabRestore("other", Map.of()));
        assertThat(registry.restore(TabId.random(), "plugin.notes", other).problem()).isEqualTo("it does not restore other tabs");
        registry.restorer("plugin.notes", "broken", restoring -> Optional.of("the file is gone"));
        assertThat(registry.restore(TabId.random(), "plugin.notes",
            new TabRegistry.Restoring(main, "x", new dev.jasper.app.layout.TabRestore("broken", Map.of()))).problem()).isEqualTo("the file is gone");
        registry.restorer("plugin.notes", "again", restoring -> {
            assertThat(registry.restore(TabId.random(), "plugin.notes", restoring).problem()).as("one restore at a time")
                .isEqualTo("another tab is being restored");
            return Optional.of("declined");
        });
        assertThat(registry.restore(TabId.random(), "plugin.notes",
            new TabRegistry.Restoring(main, "x", new dev.jasper.app.layout.TabRestore("again", Map.of()))).problem()).isEqualTo("declined");
        assertThat(registry.restore(saved, "plugin.notes", new TabRegistry.Restoring(main, "x", state)).problem())
            .as("an id already open is never taken twice").isEqualTo("a tab with its id is already open");
        registry.restorer("plugin.notes", "nothing", restoring -> {
            assertThat(registry.slot("plugin.notes", main)).as("a session opened in that window would take it").isPresent();
            assertThat(registry.slot("plugin.other", main)).isEmpty();
            return Optional.empty();
        });
        assertThat(registry.restore(TabId.random(), "plugin.notes",
            new TabRegistry.Restoring(main, "x", new dev.jasper.app.layout.TabRestore("nothing", Map.of()))).problem()).isEqualTo("it opened no tab");
        // Workspace plan 6b Rulings 24: while it runs, the window being restored takes only the saved tab.
        Tab[] extra = new Tab[1];
        registry.restorer("plugin.notes", "pair", restoring -> {
            assertThat(registry.restoring(detached)).isTrue();
            registry.open("plugin.notes", "pair:one", "One", restoring.window(), new JPanel());
            extra[0] = registry.open("plugin.notes", "pair:two", "Two", restoring.window(), new JPanel());
            return Optional.empty();
        });
        Tab pair = registry.restore(TabId.random(), "plugin.notes",
            new TabRegistry.Restoring(detached, "Pair", new dev.jasper.app.layout.TabRestore("pair", Map.of()))).tab().orElseThrow();
        assertThat(pair.window()).isEqualTo(detached);
        assertThat(extra[0].window()).as("where an owner-less tab goes: the main window").isEqualTo(main);
        assertThat(mounted).containsExactly(extra[0]);
        assertThat(registry.restoring(detached)).as("only while the restorer runs").isFalse();
        // So does a tab of the same key that waits for its window: it keeps waiting, out of the window being restored.
        registry.windows(new TabRegistry.Windows(() -> null, main::equals, ignored -> { }));
        Tab waiting = registry.open("plugin.notes", "wait", "Wait", null, new JPanel());
        registry.restorer("plugin.notes", "wait", restoring -> {
            registry.open("plugin.notes", "wait", "Wait", restoring.window(), new JPanel());
            return Optional.empty();
        });
        assertThat(registry.restore(TabId.random(), "plugin.notes",
            new TabRegistry.Restoring(detached, "Wait", new dev.jasper.app.layout.TabRestore("wait", Map.of()))).problem())
            .as("its open key is selected as always: no tab opened").isEqualTo("it opened no tab");
        assertThat(waiting.window()).as("still waiting, not in the window being restored").isNull();
        assertThat(mounted).doesNotContain(waiting);
        registry.windows(windows(main, detached));
        registration.close();
        assertThat(registry.restores("plugin.notes", "note")).isFalse();
        int[] changed = {0};
        registry.onRestoreChanged(() -> changed[0]++);
        tab.restore(Optional.of(state));
        tab.restore(Optional.of(state));
        assertThat(changed[0]).as("told once per change").isEqualTo(1);
        assertThat(tab.restore()).contains(state);
    }

    /**
     * Workspace plan 6b Rulings 30 (6b Task 1's review): while its restore runs, a restorer's item stays in the window being
     * restored, whatever move it asks for, and the restore gives it back; once the restore ends it moves as any item does.
     */
    @Test void aRestorersItemStaysInTheWindowBeingRestoredWhateverMoveItAsksFor() {
        hosts();
        var movable = new ArrayList<Boolean>();
        registry.restorer("plugin.notes", "note", restoring -> {
            Tab opened = registry.open("plugin.notes", "note:" + restoring.title(), restoring.title(), null, new JPanel());
            movable.add(registry.movable(opened));
            opened.moveToNewWindow();
            opened.moveToMainWindow();
            registry.move(opened, restoring.window().equals(main) ? detached : main);
            registry.moveToNewTab(opened);
            return Optional.empty();
        });
        for (UUID window : List.of(main, detached)) {
            TabId saved = TabId.random();
            TabRegistry.Restored restored = registry.restore(saved, "plugin.notes",
                new TabRegistry.Restoring(window, window.equals(main) ? "Main" : "Detached", new dev.jasper.app.layout.TabRestore("note", Map.of())));
            assertThat(restored.problem()).as("restored").isEmpty();
            Tab tab = restored.tab().orElseThrow();
            assertThat(tab.id()).isEqualTo(saved);
            assertThat(tab.window()).as("the window being restored").isEqualTo(window);
            assertThat(registry.movable(tab)).as("once its restore ends").isTrue();
        }
        assertThat(movable).as("not while its restore runs").containsExactly(false, false);
        assertThat(mounted).as("each window being restored places its item; no move attached one").isEmpty();
    }

    /**
     * Workspace plan 6b's final review I1: a plugin's tab asked for while no main window is open restores the main window
     * first, before the registry looks for that (plugin, key): the item that restore brought back is selected, never
     * opened a second time.
     */
    @Test void aRestoreInsideAnOpenForTheMainWindowLeavesOneItemPerPluginAndKey() {
        TabId saved = TabId.random();
        UUID[] open = {null};
        registry.addHost(new WorkspaceHost(detached, mounted::add, mounted::remove, tab -> {}, () -> {}));
        registry.windows(new TabRegistry.Windows(() -> {
            if (open[0] == null) {
                // The window manager restores the main window, whose saved item comes back through its plugin.
                registry.addHost(new WorkspaceHost(main, mounted::add, mounted::remove, tab -> {}, () -> {}));
                open[0] = main;
                registry.restore(saved, "plugin.notes", new TabRegistry.Restoring(main, "Todo", new dev.jasper.app.layout.TabRestore("note", Map.of())));
            }
            return open[0];
        }, window -> window.equals(open[0]), tab -> {}));
        registry.restorer("plugin.notes", "note", restoring -> {
            registry.open("plugin.notes", "note:todo", restoring.title(), restoring.window(), new JPanel());
            return Optional.empty();
        });
        Tab opened = registry.open("plugin.notes", "note:todo", "Todo", null, new JPanel());
        assertThat(registry.hosted()).as("one item for the key").containsExactly(opened);
        assertThat(opened.id()).as("the restored one").isEqualTo(saved);
        assertThat(opened.window()).isEqualTo(main);
    }

    /** 6b Task 1's review: an Error a restorer lets out, which no containment holds, takes the item it opened with it. */
    @Test void aRestorerThatOpensItsItemAndThrowsAnErrorLeavesNoTab() {
        hosts();
        TabId saved = TabId.random();
        Tab[] opened = new Tab[1];
        registry.restorer("plugin.notes", "fatal", restoring -> {
            opened[0] = registry.open("plugin.notes", "fatal", restoring.title(), null, new JPanel());
            throw new InternalError("the restorer broke");
        });
        assertThatThrownBy(() -> registry.restore(saved, "plugin.notes",
            new TabRegistry.Restoring(main, "Fatal", new dev.jasper.app.layout.TabRestore("fatal", Map.of()))))
            .isInstanceOf(InternalError.class).hasMessage("the restorer broke");
        assertThat(opened[0].closed()).as("closed").isTrue();
        assertThat(registry.tab(saved)).as("no tab under the saved id").isEmpty();
        assertThat(registry.restoring(main)).as("the restore ended").isFalse();
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
        registry.addHost(new WorkspaceHost(main, tab -> seen.add("attach main " + tab.moving()),
            tab -> seen.add("detach main " + tab.moving()), tab -> {}, () -> {}));
        registry.addHost(new WorkspaceHost(detached, tab -> seen.add("attach detached " + tab.moving()),
            tab -> seen.add("detach detached " + tab.moving()), tab -> {}, () -> {}));
        registry.windows(windows(main, detached));
        var tab = registry.open("plugin.file", "one", "One", main, new JPanel());
        registry.moveToNewWindow(tab);
        assertThat(tab.moving()).as("only during the move").isFalse();
        tab.close();
        assertThat(seen).containsExactly("attach main false", "detach main true", "attach detached true", "detach detached false");
    }

    @Test void theHostSaysWhichGroupHoldsATab() {
        var group = GroupId.random();
        registry.addHost(new WorkspaceHost(main, mounted::add, mounted::remove, tab -> {}, () -> {},
            id -> Optional.of(group)));
        var tab = registry.open("plugin.file", "one", "One", main, new JPanel());
        assertThat(tab.group()).contains(group);
        assertThat(registry.groupOf(tab)).contains(group);
        tab.close();
        assertThat(tab.group()).as("a closed tab is in no group").isEmpty();
    }

    @Test void aTabItsWindowPlacesIsRegisteredWithoutCallingTheHost() {
        hosts();
        var panel = new JPanel();
        int[] disposed = {0};
        var content = new TabContent() {
            @Override public JComponent component() { return panel; }
            @Override public void dispose() { disposed[0]++; }
        };
        var id = TabId.random();
        var tab = registry.add(id, Tab.Kind.TERMINAL, content, main);
        assertThat(mounted).as("the window placed it itself").isEmpty();
        assertThat(tab.id()).isEqualTo(id);
        assertThat(tab.kind()).isEqualTo(Tab.Kind.TERMINAL);
        assertThat(tab.plugin()).isEmpty();
        assertThat(tab.window()).isEqualTo(main);
        assertThat(registry.inWindow(main)).containsExactly(tab);
        assertThat(registry.hosted()).as("not a plugin's tab").isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> registry.add(id, Tab.Kind.TERMINAL, content, main));
        assertThat(tab.requestClose().join()).as("no guard to ask").isTrue();
        assertThat(disposed[0]).isEqualTo(1);
        assertThat(registry.tabs()).isEmpty();
    }

    @Test void aJoiningWindowsTabsMoveIntoTheRegistryItJoins() {
        var own = new TabRegistry();
        var window = UUID.randomUUID();
        var left = new ArrayList<Tab>();
        var registration = own.addHost(new WorkspaceHost(window, tab -> {}, left::add, tab -> {}, () -> {}));
        var tab = own.add(TabId.random(), Tab.Kind.TERMINAL, new HostedContent(new JPanel()), window);
        int[] closed = {0};
        tab.onClosed(() -> closed[0]++);
        registry.adopt(own, window);
        registration.close();
        assertThat(left).as("leaving the window's own registry closes nothing").isEmpty();
        assertThat(closed[0]).isZero();
        assertThat(own.tabs()).isEmpty();
        assertThat(registry.tabs()).containsExactly(tab);
        registry.addHost(new WorkspaceHost(window, mounted::add, mounted::remove, t -> {}, () -> {}));
        tab.close();
        assertThat(closed[0]).isEqualTo(1);
        assertThat(registry.tabs()).isEmpty();
    }

    private Tab counted(int[] disposed, UUID window) {
        var panel = new JPanel();
        return registry.add(TabId.random(), Tab.Kind.TERMINAL, new TabContent() {
            @Override public JComponent component() { return panel; }
            @Override public void dispose() { disposed[0]++; }
        }, window);
    }

    @Test void aFailingListenerNeverKeepsTheContentOrTheWindowFromLettingGoOfATab() {
        hosts();
        int[] disposed = {0};
        var tab = counted(disposed, main);
        mounted.add(tab);
        var heard = new ArrayList<String>();
        tab.onActive(active -> { if (!active) throw new IllegalStateException("activation failed"); });
        tab.onActive(active -> { if (!active) heard.add("second activation"); });
        tab.active(true);
        tab.onClosed(() -> { throw new IllegalStateException("close failed"); });
        tab.onClosed(() -> heard.add("second close"));
        assertThatThrownBy(() -> registry.remove(tab)).isInstanceOf(IllegalStateException.class).hasMessage("activation failed")
            .satisfies(failure -> assertThat(failure.getSuppressed()).extracting(Throwable::getMessage).containsExactly("close failed"));
        assertThat(heard).as("every other listener was still told").containsExactly("second activation", "second close");
        assertThat(disposed[0]).as("the content was released").isEqualTo(1);
        assertThat(mounted).as("the window let go of the tab").isEmpty();
        assertThat(registry.tabs()).isEmpty();
        assertThat(tab.closed()).isTrue();
    }

    @Test void aFailingDetachDoesNotHideTheFailureThatCameFirst() {
        var window = UUID.randomUUID();
        registry.addHost(new WorkspaceHost(window, tab -> {}, tab -> { throw new IllegalStateException("detach failed"); }, tab -> {}, () -> {}));
        int[] disposed = {0};
        var tab = counted(disposed, window);
        tab.onClosed(() -> { throw new IllegalStateException("close failed"); });
        assertThatThrownBy(() -> registry.remove(tab)).isInstanceOf(IllegalStateException.class).hasMessage("close failed")
            .satisfies(failure -> assertThat(failure.getSuppressed()).extracting(Throwable::getMessage).containsExactly("detach failed"));
        assertThat(disposed[0]).isEqualTo(1);
        assertThat(registry.tabs()).isEmpty();
    }

    @Test void closingAHostRemovesEveryTabItHoldsAndForgetsItWhateverFails() {
        var window = UUID.randomUUID();
        var detachedTabs = new ArrayList<Tab>();
        var registration = registry.addHost(new WorkspaceHost(window, tab -> {}, detachedTabs::add, tab -> {}, () -> {}));
        int[] disposed = {0};
        var first = counted(disposed, window);
        var second = counted(disposed, window);
        first.onClosed(() -> { throw new IllegalStateException("close failed"); });
        assertThat(registry.isMain(window)).as("registered").isTrue();
        assertThatThrownBy(registration::close).isInstanceOf(IllegalStateException.class).hasMessage("close failed");
        assertThat(disposed[0]).as("the other tab was removed too").isEqualTo(2);
        assertThat(detachedTabs).containsExactly(first, second);
        assertThat(registry.tabs()).isEmpty();
        assertThat(registry.isMain(window)).as("the host is forgotten").isFalse();
        assertThatCode(() -> registry.addHost(new WorkspaceHost(window, tab -> {}, tab -> {}, tab -> {}, () -> {})))
            .as("its id is free again").doesNotThrowAnyException();
    }

    @Test void keepingTheSameFailureTwiceDoesNotSuppressItUnderItself() {
        var failure = new IllegalStateException("shared");
        assertThat(Tab.keep(failure, failure)).isSameAs(failure);
        assertThat(failure.getSuppressed()).isEmpty();
    }

    @Test void aRollbackThatThrowsTheOpeningFailureAgainRethrowsIt() {
        var failure = new IllegalStateException("mount failed");
        registry.addHost(new WorkspaceHost(main, tab -> { throw failure; }, tab -> { throw failure; }, tab -> {}, () -> {}));
        assertThatThrownBy(() -> registry.open("plugin.file", "one", "One", main, new JPanel())).isSameAs(failure);
        assertThat(registry.hosted()).isEmpty();
    }

    @Test void aFailingRemovalNeitherStopsABatchCloseNorKeepsTheNextOneWaiting() {
        hosts();
        int[] disposed = {0};
        var terminal = counted(disposed, main);
        mounted.add(terminal);
        terminal.onClosed(() -> { throw new IllegalStateException("close failed"); });
        var view = registry.open("plugin.file", "a", "A", main, new JPanel());
        var later = registry.open("plugin.file", "b", "B", main, new JPanel());
        var logger = java.util.logging.Logger.getLogger(TabRegistry.class.getName());
        var records = new ArrayList<java.util.logging.LogRecord>();
        var handler = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        boolean parents = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        try {
            var closing = registry.requestClose(List.of(terminal, view));
            assertThat(closing).as("the decision completes").isCompletedWithValue(true);
            assertThat(terminal.closed()).isTrue();
            assertThat(view.closed()).as("a tab after the failing one still closes").isTrue();
            assertThat(mounted).containsExactly(later);
            assertThat(registry.requestClose(List.of(later))).as("the next close is not refused").isCompletedWithValue(true);
            assertThat(later.closed()).isTrue();
        } finally {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(parents);
        }
        assertThat(records).as("the failure is logged, not swallowed").singleElement().satisfies(record -> {
            assertThat(record.getLevel()).isEqualTo(java.util.logging.Level.WARNING);
            assertThat(record.getThrown()).hasMessage("close failed");
        });
    }

    @Test void aMoveTellsBothWindowsWhereTheTabComesFromUntilItEnds() {
        var seen = new ArrayList<String>();
        var first = UUID.randomUUID(); var second = UUID.randomUUID(); var failing = UUID.randomUUID();
        registry.addHost(new WorkspaceHost(first, tab -> seen.add("attach first " + tab.movingFrom().orElse(null)),
            tab -> seen.add("detach first " + tab.movingFrom().orElse(null)), tab -> { }, () -> { }));
        registry.addHost(new WorkspaceHost(second, tab -> seen.add("attach second " + tab.movingFrom().orElse(null)),
            tab -> seen.add("detach second " + tab.movingFrom().orElse(null)), tab -> { }, () -> { }));
        registry.addHost(new WorkspaceHost(failing, tab -> { throw new IllegalStateException("attach failed"); },
            tab -> { }, tab -> { }, () -> { }));
        var tab = registry.open("plugin.file", "one", "One", first, new JPanel());
        registry.move(tab, second);
        assertThat(seen).containsExactly("attach first null", "detach first " + first, "attach second " + first);
        assertThat(tab.movingFrom()).as("the move is over").isEmpty();
        assertThat(tab.moving()).isFalse();
        assertThatIllegalStateException().isThrownBy(() -> registry.move(tab, failing)).withMessage("attach failed");
        assertThat(tab.window()).as("back where it was").isEqualTo(second);
        assertThat(tab.movingFrom()).as("a failed move is over too").isEmpty();
        seen.clear();
        tab.close();
        assertThat(seen).containsExactly("detach second null");
    }

    @Test void anyTabMovesToANamedWindowAndIsSelectedThere() {
        hosts();
        var other = UUID.randomUUID();
        var arrived = new ArrayList<Tab>();
        var selected = new ArrayList<Tab>();
        registry.addHost(new WorkspaceHost(other, arrived::add, arrived::remove, selected::add, () -> {}));
        var tab = registry.add(TabId.random(), Tab.Kind.TERMINAL, new HostedContent(new JPanel()), main);
        mounted.add(tab);
        registry.move(tab, other);
        assertThat(tab.window()).isEqualTo(other);
        assertThat(mounted).as("detached from the old window").isEmpty();
        assertThat(arrived).containsExactly(tab);
        assertThat(selected).containsExactly(tab);
        registry.move(tab, other);
        assertThat(arrived).as("a move to its own window does nothing").containsExactly(tab);
        assertThatIllegalArgumentException().isThrownBy(() -> registry.move(tab, UUID.randomUUID()));
        var pin = tab.pin();
        registry.move(tab, main);
        assertThat(tab.window()).as("a pinned tab stays").isEqualTo(other);
        pin.close();
    }
}
