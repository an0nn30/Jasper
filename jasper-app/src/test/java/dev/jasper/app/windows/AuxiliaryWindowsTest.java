package dev.jasper.app.windows;

import dev.jasper.app.persistence.UiState;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class AuxiliaryWindowsTest {
    private final List<String> shellEvents = new ArrayList<>();
    private final UiState state = UiState.inMemory();
    private final AuxiliaryWindows windows = new AuxiliaryWindows(state, surface -> {
        shellEvents.add("create:" + surface.title());
        return new AuxiliarySurface.Shell(() -> shellEvents.add("show"), () -> shellEvents.add("front"),
            () -> shellEvents.add("dispose"), title -> shellEvents.add("title:" + title), () -> new Rectangle(10, 20, 640, 480), (title, initial) -> java.util.Optional.empty());
    });

    @Test void reportsWhenASurfaceOpensWhileNoneIsOpen() {
        List<String> events = new ArrayList<>();
        windows.onFirstOpened = () -> events.add("first");
        windows.window("dev.x.a", "A", new Dimension(10, 10), false);
        windows.window("dev.x.b", "B", new Dimension(10, 10), false);
        assertThat(events).containsExactly("first");
        windows.close();
        windows.window("dev.x.c", "C", new Dimension(10, 10), false);
        assertThat(events).containsExactly("first", "first");
    }

    /** Spec 13: File › Open File… asks for one existing local file over the terminal window, from its pane's folder. */
    @Test void openFileAsksForOneLocalFileOverTheTerminalWindow() {
        var asked = new ArrayList<PathChoice>();
        var file = java.nio.file.Path.of("notes.txt").toAbsolutePath();
        var folder = java.nio.file.Path.of("project").toAbsolutePath();
        var host = new AuxiliaryWindows(UiState.inMemory(), surface -> new AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { },
            title -> { }, Rectangle::new, (title, initial) -> java.util.Optional.empty()),
            (choice, cancellation) -> { asked.add(choice); return List.of(dev.jasper.app.files.Location.local(file)); });
        UUID window = UUID.randomUUID();
        assertThat(host.openFile(window, java.util.Optional.of(folder))).contains(file);
        assertThat(asked).singleElement().satisfies(choice -> {
            assertThat(choice.terminalOwner()).isEqualTo(window);
            assertThat(choice.auxiliaryOwner()).isNull();
            assertThat(choice.title()).isEqualTo("Open File");
            assertThat(choice.mode()).isEqualTo(PathChoice.Mode.OPEN);
            assertThat(choice.multiple()).isFalse();
            assertThat(choice.initial()).isEmpty();
            assertThat(choice.terminalFolder()).contains(dev.jasper.app.files.Location.local(folder));
            assertThat(choice.sources()).isEmpty();
        });
        var cancelled = new AuxiliaryWindows(UiState.inMemory(), surface -> null, (choice, cancellation) -> List.of());
        assertThat(cancelled.openFile(window, java.util.Optional.empty())).isEmpty();
    }

    @Test void onlyOneOverlayCanOwnATerminalWindow() {
        UUID owner = UUID.randomUUID();
        var first = windows.overlay("Connecting", owner);
        assertThat(first.kind()).isEqualTo(AuxiliarySurface.Kind.OVERLAY);
        assertThatIllegalStateException().isThrownBy(() -> windows.overlay("Another", owner));
        assertThat(first.requestClose()).isFalse();
        windows.closeOwned(owner);
        assertThat(first.closed()).isTrue();
        assertThat(windows.overlay("Retry", owner).closed()).isFalse();
    }

    @Test void chooserUsesItsSurfaceAndDiscardsResultsWhenTheOwnerCloses() {
        var chosen = java.nio.file.Path.of("selected").toAbsolutePath();
        var initial = java.util.Optional.of(java.nio.file.Path.of("original").toAbsolutePath());
        boolean[] closeDuringChoice = {false};
        var hosted = new AuxiliaryWindows(UiState.inMemory(), surface -> new AuxiliarySurface.Shell(
            () -> { }, () -> { }, () -> { }, title -> { }, Rectangle::new, (title, path) -> {
                assertThat(title).isEqualTo("Choose key");
                assertThat(path).isEqualTo(initial);
                if (closeDuringChoice[0]) surface.close();
                return java.util.Optional.of(chosen);
            }));
        var window = hosted.window("test", "Manager", new Dimension(100, 100), false);
        assertThatIllegalStateException().isThrownBy(() -> window.chooseFile("Choose key", initial));
        window.show();
        var dialog = hosted.dialog("Editor", true, window);
        dialog.show();
        assertThat(dialog.chooseFile("Choose key", initial)).contains(chosen);
        closeDuringChoice[0] = true;
        assertThat(dialog.chooseFile("Choose key", initial)).isEmpty();
        assertThatIllegalStateException().isThrownBy(() -> dialog.chooseFile("Choose key", initial));
        hosted.close();
    }

    @Test void theShellIsCreatedOnFirstShowAndFollowsTheSurface() {
        AuxiliarySurface manager = windows.window("dev.x.manager", "Manager", new Dimension(640, 480), true);
        var content = new JLabel("content");
        manager.setContent(content);
        manager.setTitle("Manager (2)");
        assertThat(shellEvents).as("nothing native before show").isEmpty();
        assertThat(content.getParent()).isSameAs(manager.holder());
        manager.show(); manager.show(); manager.toFront();
        manager.setTitle("Manager (3)");
        assertThat(shellEvents).containsExactly("create:Manager (2)", "show", "show", "front", "title:Manager (3)");
        assertThat(manager.shown()).isTrue();
        assertThatIllegalArgumentException().isThrownBy(() -> manager.setTitle(" "));
    }

    @Test void singletonsAreReusedWhileOpenAndBoundsAreRememberedOnClose() {
        AuxiliarySurface first = windows.window("dev.x.manager", "Manager", new Dimension(640, 480), true);
        assertThat(windows.window("dev.x.manager", "Manager", new Dimension(1, 1), true)).isSameAs(first);
        AuxiliarySurface log = windows.window("dev.x.log", "Log", new Dimension(640, 480), false);
        assertThat(windows.window("dev.x.log", "Log", new Dimension(640, 480), false)).as("non-singletons are never reused").isNotSameAs(log);
        first.show();
        first.close(); first.close();
        assertThat(shellEvents).containsOnlyOnce("dispose");
        assertThat(state.window("dev.x.manager")).hasValue(new UiState.Bounds(10, 20, 640, 480));
        assertThat(windows.window("dev.x.manager", "Manager", new Dimension(640, 480), true)).isNotSameAs(first);
        assertThat(windows.open()).as("two logs and the reopened manager").hasSize(3);
    }

    @Test void guardsVetoAUserCloseButNotAProgrammaticOne() {
        AuxiliarySurface manager = windows.window("dev.x.manager", "Manager", new Dimension(640, 480), true);
        List<String> events = new ArrayList<>();
        var veto = manager.onClosing(() -> false);
        manager.onClosing(() -> { throw new IllegalStateException("guard failure"); });
        manager.onClosed(() -> events.add("closed"));
        assertThat(manager.requestClose()).isFalse();
        assertThat(manager.closed()).isFalse();
        veto.close();
        assertThat(manager.requestClose()).as("a throwing guard cannot trap the window open").isTrue();
        assertThat(manager.requestClose()).isTrue();
        assertThat(events).containsExactly("closed");

        AuxiliarySurface stubborn = windows.window("dev.x.stubborn", "Stubborn", new Dimension(10, 10), false);
        stubborn.onClosing(() -> false);
        stubborn.close();
        assertThat(stubborn.closed()).isTrue();
    }

    @Test void dialogsNeedALiveOwnerAndCloseAllClosesEverything() {
        AuxiliarySurface manager = windows.window("dev.x.manager", "Manager", new Dimension(640, 480), true);
        AuxiliarySurface prompt = windows.dialog("Unlock", true, manager);
        UUID terminalWindow = UUID.randomUUID();
        AuxiliarySurface trust = windows.dialog("Trust host?", false, terminalWindow);
        assertThat(prompt.kind()).isEqualTo(AuxiliarySurface.Kind.DIALOG);
        assertThat(prompt.modal()).isTrue();
        assertThat(prompt.ownerSurface()).containsSame(manager);
        assertThat(trust.ownerWindow()).contains(terminalWindow);
        assertThat(windows.open()).containsExactly(manager, prompt, trust);

        manager.onClosing(() -> false);
        windows.close();
        assertThat(windows.open()).isEmpty();
        assertThat(manager.closed() && prompt.closed() && trust.closed()).isTrue();
        assertThatIllegalArgumentException().isThrownBy(() -> windows.dialog("Late", true, manager));
    }

    @Test void reportsWhenTheLastSurfaceCloses() {
        List<String> events = new ArrayList<>();
        windows.onAllClosed = () -> events.add("empty");
        AuxiliarySurface first = windows.window("dev.x.a", "A", new Dimension(10, 10), false);
        AuxiliarySurface second = windows.window("dev.x.b", "B", new Dimension(10, 10), false);
        first.close();
        assertThat(events).isEmpty();
        second.close();
        assertThat(events).containsExactly("empty");
    }

    @Test void activationReachesListenersUntilTheyUnsubscribeOrTheWindowCloses() {
        AuxiliarySurface manager = windows.window("dev.x.manager", "Manager", new Dimension(640, 480), true);
        List<String> events = new ArrayList<>();
        var first = manager.onActivated(() -> events.add("first"));
        manager.onActivated(() -> { throw new IllegalStateException("listener failure"); });
        manager.onActivated(() -> events.add("third"));
        manager.notifyActivated();
        assertThat(events).as("a failing listener does not stop the others").containsExactly("first", "third");
        first.close();
        manager.notifyActivated();
        assertThat(events).containsExactly("first", "third", "third");
        manager.close();
        manager.notifyActivated();
        assertThat(events).hasSize(3);
    }

    @Test void theCloseCommandAsksHandlersNewestFirstThenCloses() {
        AuxiliarySurface editor = windows.window("dev.x.editor", "Editor", new Dimension(640, 480), true);
        List<String> asked = new ArrayList<>();
        boolean[] handled = {true};
        editor.onCloseCommand(() -> { asked.add("older"); return false; });
        editor.onCloseCommand(() -> { asked.add("newer"); return handled[0]; });
        editor.onCloseCommand(() -> { throw new IllegalStateException("boom"); });
        editor.show();
        assertThat(editor.closeCommand()).isFalse();
        assertThat(asked).containsExactly("newer");
        handled[0] = false;
        assertThat(editor.closeCommand()).as("nothing handled it and no guard refused").isTrue();
        assertThat(asked).containsExactly("newer", "newer", "older");
        assertThat(editor.closed()).isTrue();
    }

    @Test void quitAsksOnlyWindowGuardsInCreationOrderAndClosesNothing() {
        List<String> asked = new ArrayList<>();
        boolean[] allow = {false};
        AuxiliarySurface first = windows.window("dev.x.first", "First", new Dimension(100, 100), false);
        first.onClosing(() -> { asked.add("first"); return true; });
        AuxiliarySurface second = windows.window("dev.x.second", "Second", new Dimension(100, 100), false);
        second.onClosing(() -> { asked.add("second"); return allow[0]; });
        second.onClosing(() -> { throw new IllegalStateException("boom"); });
        windows.dialog("Details", false, second).onClosing(() -> { asked.add("dialog"); return false; });
        windows.overlay("Connecting", UUID.randomUUID());
        assertThat(windows.mayQuit()).isFalse();
        assertThat(asked).containsExactly("first", "second");
        allow[0] = true;
        asked.clear();
        assertThat(windows.mayQuit()).isTrue();
        assertThat(asked).containsExactly("first", "second");
        assertThat(windows.open()).hasSize(4).noneMatch(AuxiliarySurface::closed);
    }
    @Test void reentrantCloseDuringConstructionNeverShowsAndDisposesReturnedShellOnce() {
        var events = new ArrayList<String>();
        var host = new AuxiliaryWindows(UiState.inMemory(), surface -> {
            surface.close();
            return new AuxiliarySurface.Shell(() -> events.add("show"), () -> {}, () -> events.add("dispose"),
                title -> {}, Rectangle::new, (title, initial) -> java.util.Optional.empty());
        });
        var surface = host.window("test", "Reentrant", new Dimension(200, 100), false);
        surface.show(); surface.close();
        assertThat(events).containsExactly("dispose"); assertThat(host.open()).isEmpty();
    }
    @Test void failedShowClosesTheSurfaceAndItsChildrenAndDoesNotRemainShown() {
        var disposed = new java.util.concurrent.atomic.AtomicInteger();
        var host = new AuxiliaryWindows(UiState.inMemory(), surface -> new AuxiliarySurface.Shell(
            () -> { throw new IllegalStateException("native show failed"); }, () -> {}, disposed::incrementAndGet,
            title -> {}, Rectangle::new, (title, initial) -> java.util.Optional.empty()));
        var surface = host.window("test", "Failed", new Dimension(200, 100), false);
        var child = host.dialog("Child", true, surface);
        assertThatThrownBy(surface::show).isInstanceOf(IllegalStateException.class);
        assertThat(surface.shown()).isFalse(); assertThat(child.closed()).isTrue(); assertThat(disposed).hasValue(1); assertThat(host.open()).isEmpty();
    }
    @Test void reentrantCloseDuringBodyRemovalNeverMountsTheReplacement() {
        var host = new AuxiliaryWindows(UiState.inMemory(), surface -> new AuxiliarySurface.Shell(
            () -> {}, () -> {}, () -> {}, title -> {}, Rectangle::new, (title, initial) -> java.util.Optional.empty()));
        var surface = host.window("test", "Reentrant", new Dimension(200, 100), false);
        var old = new javax.swing.JPanel(); surface.setContent(old);
        surface.holder().addContainerListener(new java.awt.event.ContainerAdapter() {
            @Override public void componentRemoved(java.awt.event.ContainerEvent event) { surface.close(); }
        });
        var next = new javax.swing.JPanel(); surface.setContent(next);
        assertThat(surface.closed()).isTrue(); assertThat(surface.holder().getComponents()).isEmpty();
        assertThat(next.getParent()).isNull(); assertThat(host.open()).isEmpty();
    }

}
