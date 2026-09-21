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
            () -> shellEvents.add("dispose"), title -> shellEvents.add("title:" + title), () -> new Rectangle(10, 20, 640, 480));
    });

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
}
