package dev.jasper.app.terminals;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class TerminalRegistryTest {
    private final TerminalRegistry registry = new TerminalRegistry();
    private final List<TerminalEvent> events = new ArrayList<>();

    /** A window whose tabs and panes are plain lists the test mutates. */
    private static final class Window {
        final UUID id = UUID.randomUUID();
        final List<TabEntry> tabs = new ArrayList<>();
        TabEntry selected;
        final WindowEntry entry = new WindowEntry(id, () -> List.copyOf(tabs), () -> Optional.ofNullable(selected), () -> true, () -> { },
            directory -> Optional.empty());
    }

    private static final class Tab {
        final UUID id = UUID.randomUUID();
        final List<PaneEntry> panes = new ArrayList<>();
        PaneEntry focused;
        final TabEntry entry;
        Tab(Window window) { entry = new TabEntry(id, window.id, () -> List.copyOf(panes), () -> Optional.ofNullable(focused), () -> "tab", () -> { }); }
        PaneEntry pane() {
            var snapshot = new PaneSnapshot("sh", Optional.empty(), 80, 24, false, PaneSnapshot.State.RUNNING, OptionalInt.empty());
            var created = new PaneEntry(UUID.randomUUID(), id, () -> snapshot, () -> CompletableFuture.completedFuture(Optional.empty()),
                bytes -> { }, text -> { }, Optional::empty, () -> { }, (axis, directory) -> Optional.empty());
            panes.add(created);
            return created;
        }
    }

    @Test void windowsAreFoundByIdAndTheirTabsAndPanesThroughThem() {
        var window = new Window();
        var tab = new Tab(window);
        PaneEntry pane = tab.pane();
        window.tabs.add(tab.entry);
        var registration = registry.addWindow(window.entry);
        assertThat(registry.windows()).containsExactly(window.entry);
        assertThat(registry.window(window.id)).contains(window.entry);
        assertThat(registry.tab(tab.id)).contains(tab.entry);
        assertThat(registry.pane(pane.id())).contains(pane);
        assertThat(registry.pane(UUID.randomUUID())).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> registry.addWindow(window.entry));
        registration.close();
        registration.close();
        assertThat(registry.windows()).isEmpty();
        assertThat(registry.pane(pane.id())).isEmpty();
    }

    @Test void theActivePaneFollowsActivationSelectionFocusAndClosing() {
        registry.onEvent(events::add);
        var first = new Window(); var second = new Window();
        var tab = new Tab(first); var other = new Tab(second);
        PaneEntry left = tab.pane(), right = tab.pane(), lone = other.pane();
        first.tabs.add(tab.entry); first.selected = tab.entry; tab.focused = left;
        second.tabs.add(other.entry); second.selected = other.entry; other.focused = lone;
        var closeFirst = registry.addWindow(first.entry);
        registry.addWindow(second.entry);
        assertThat(registry.activeWindow()).as("nothing was activated yet").isEmpty();
        assertThat(registry.activePane()).isEmpty();

        registry.windowActivated(first.id);
        assertThat(registry.activePane()).contains(left);
        tab.focused = right;
        registry.publish(new TerminalEvent.PaneFocused(tab.id, right.id()));
        registry.publish(new TerminalEvent.PaneFocused(tab.id, right.id()));
        registry.windowActivated(second.id);
        registry.windowActivated(UUID.randomUUID());
        closeFirst.close();
        assertThat(events).containsExactly(
            new TerminalEvent.WindowOpened(first.id), new TerminalEvent.WindowOpened(second.id),
            new TerminalEvent.WindowActivated(first.id), new TerminalEvent.ActivePaneChanged(Optional.of(left.id())),
            new TerminalEvent.PaneFocused(tab.id, right.id()), new TerminalEvent.ActivePaneChanged(Optional.of(right.id())),
            new TerminalEvent.PaneFocused(tab.id, right.id()),
            new TerminalEvent.WindowActivated(second.id), new TerminalEvent.ActivePaneChanged(Optional.of(lone.id())),
            new TerminalEvent.WindowClosed(first.id));

        events.clear();
        registry.windows().get(0);
        registry.window(second.id).orElseThrow();
        // Closing the active window leaves no active window until the user turns to another.
        registry.atomically(() -> { });
        assertThat(events).isEmpty();
    }

    @Test void anAtomicChangeReportsTheActivePaneOnceAndRefreshNoticesAQuietChange() {
        var window = new Window();
        var one = new Tab(window); var two = new Tab(window);
        PaneEntry a = one.pane(), b = two.pane();
        one.focused = a; two.focused = b;
        window.tabs.add(one.entry); window.tabs.add(two.entry); window.selected = one.entry;
        registry.addWindow(window.entry);
        registry.windowActivated(window.id);
        registry.onEvent(events::add);
        registry.atomically(() -> {
            one.panes.clear(); one.focused = null;
            registry.publish(new TerminalEvent.PaneClosed(one.id, a.id()));
            window.tabs.remove(one.entry);
            registry.publish(new TerminalEvent.TabClosed(window.id, one.id));
            window.selected = two.entry;
            registry.publish(new TerminalEvent.TabSelected(window.id, two.id));
        });
        assertThat(events).as("no transient 'no active pane' in the middle").containsExactly(
            new TerminalEvent.PaneClosed(one.id, a.id()), new TerminalEvent.TabClosed(window.id, one.id),
            new TerminalEvent.TabSelected(window.id, two.id), new TerminalEvent.ActivePaneChanged(Optional.of(b.id())));
        events.clear();
        PaneEntry c = two.pane();
        two.focused = c;
        registry.refresh();
        registry.refresh();
        assertThat(events).containsExactly(new TerminalEvent.ActivePaneChanged(Optional.of(c.id())));
    }

    @Test void aFailingListenerDoesNotStopTheOthersAndClosedSubscriptionsHearNothing() {
        List<String> heard = new ArrayList<>();
        registry.onEvent(event -> { throw new IllegalStateException("listener failure"); });
        var second = registry.onEvent(event -> heard.add("second"));
        registry.onEvent(event -> heard.add("third"));
        registry.publish(new TerminalEvent.Bell(UUID.randomUUID(), UUID.randomUUID()));
        second.close();
        registry.publish(new TerminalEvent.Bell(UUID.randomUUID(), UUID.randomUUID()));
        assertThat(heard).containsExactly("second", "third", "third");
    }
}
