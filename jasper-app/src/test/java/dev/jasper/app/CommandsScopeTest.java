package dev.jasper.app;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CommandsScopeTest {
    static Command command(String id, String title, KeyStroke stroke) {
        var action = new AbstractAction(title) {
            @Override public void actionPerformed(ActionEvent event) {}
        };
        if (stroke != null) action.putValue(Action.ACCELERATOR_KEY, stroke);
        return new Command(id, action, List.of());
    }

    @Test void emptyQueryShowsStartersThenRecentsAndSearchRowsCarryShortcuts() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var registry = new CommandRegistry(); var history = new CommandHistory()) {
                var dispatched = new ArrayList<String>();
                var scope = new CommandsScope(registry, history, true, command -> dispatched.add(command.id()), null);
                registry.register(command("new_tab", "New Tab", KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.META_DOWN_MASK)));
                registry.register(command("split_right", "Split Right", null));
                var context = new PaletteContext(true, PaletteTarget.none());
                assertThat(scope.id()).isEqualTo(PaletteScope.COMMANDS_ID);
                assertThat(scope.verbs()).containsExactly(CommandsScope.RUN);
                var starters = scope.search("", context);
                assertThat(starters.sectionLabel()).isEqualTo("Suggested");
                assertThat(starters.rows()).extracting(PaletteRow::id).containsExactly("new_tab", "split_right");
                assertThat(starters.rows().getFirst().tag()).isEqualTo("⌘T");
                assertThat(starters.rows().get(1).tag()).isNull();
                scope.execute(starters.rows().getFirst(), CommandsScope.RUN, context);
                assertThat(dispatched).containsExactly("new_tab");
                assertThat(history.recent()).containsExactly("new_tab");
                assertThat(scope.search("", context).sectionLabel()).isEqualTo("Recent");
                assertThat(scope.search("", context).rows()).extracting(PaletteRow::id).containsExactly("new_tab");
                var matches = scope.search("spl", context);
                assertThat(matches.sectionLabel()).isNull();
                assertThat(matches.rows()).extracting(PaletteRow::id).containsExactly("split_right");
                assertThat(CommandsScope.shortcutText(KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK), false))
                    .isEqualTo("Ctrl+K");
            }
        });
    }

    @Test void theContextCapsSearchResultsAndRecents() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var registry = new CommandRegistry(); var history = new CommandHistory()) {
                var scope = new CommandsScope(registry, history, true, command -> {}, null);
                for (int i = 0; i < 8; i++) registry.register(command("other_" + i, "Other " + i, null));
                var capped = new PaletteContext(true, PaletteTarget.none(), 3);
                assertThat(scope.search("other", capped).rows()).hasSize(3);
                assertThat(scope.search("other", new PaletteContext(true, PaletteTarget.none())).rows()).hasSize(5);
                for (int i = 0; i < 4; i++) history.record("other_" + i);
                assertThat(scope.search("", capped).rows()).hasSize(3);
                assertThat(scope.search("", new PaletteContext(true, PaletteTarget.none(), 2)).rows()).hasSize(2);
            }
        });
    }

    @Test void staleDisabledOrUnregisteredRowsAreUnavailableAndNeverDispatch() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var registry = new CommandRegistry(); var history = new CommandHistory()) {
                var calls = new AtomicInteger();
                var scope = new CommandsScope(registry, history, false, command -> calls.incrementAndGet(), null);
                var context = new PaletteContext(false, PaletteTarget.none());
                var stale = command("custom", "Custom", null);
                var registration = registry.register(stale);
                var staleRow = scope.rows(List.of(stale)).getFirst();
                registration.close();
                registry.register(command("custom", "Custom", null));
                assertThat(scope.available(staleRow, context)).isFalse();
                scope.execute(staleRow, CommandsScope.RUN, context);
                assertThat(calls.get()).isZero();
                var disabled = command("off", "Off", null);
                registry.register(disabled);
                var row = scope.rows(List.of(disabled)).getFirst();
                disabled.action().setEnabled(false);
                assertThat(scope.available(row, context)).isFalse();
                scope.execute(row, CommandsScope.RUN, context);
                assertThat(calls.get()).isZero();
                assertThat(history.recent()).isEmpty();
                assertThat(scope.search("off", context).rows()).isEmpty();
            }
        });
    }

    @Test void oneSubscriptionCoversRegistryAndHistoryChanges() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var registry = new CommandRegistry(); var history = new CommandHistory()) {
                var scope = new CommandsScope(registry, history, true, command -> {}, null);
                var changes = new AtomicInteger();
                var subscription = scope.onChanged(changes::incrementAndGet);
                registry.register(command("a", "A", null));
                history.record("a");
                assertThat(changes.get()).isEqualTo(2);
                subscription.close();
                history.record("a");
                assertThat(changes.get()).isEqualTo(2);
            }
        });
    }
}
