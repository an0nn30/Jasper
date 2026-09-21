package dev.jasper.app;

import dev.jasper.app.lifecycle.Subscription;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandRegistryTest {
    @Test void registrationRemovalInvalidatesPreviouslyReturnedCommand() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var registry = new CommandRegistry();
            var action = new AbstractAction("Connect Session") {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) {}
            };
            var command = new Command("sessions.connect", action, List.of("ssh", "host"));
            var registration = registry.register(command);
            assertThat(registry.entries()).extracting(e -> e.command().id()).containsExactly("sessions.connect");
            assertThatThrownBy(() -> registry.register(command)).isInstanceOf(IllegalArgumentException.class);
            registration.close();
            assertThat(registry.contains(command)).isFalse();
            assertThat(registry.entries()).isEmpty();
            registry.close();
        });
    }

    @Test void actionPropertiesRebuildMetadataAndEnabledStateIsReflectedInSearch() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var registry = new CommandRegistry();
            var action = action("Original");
            var command = new Command("test.action", action, List.of("terminal"));
            var changes = new AtomicInteger();
            var registration = registry.register(command);
            var changeSubscription = registry.onChanged(changes::incrementAndGet);
            assertThat(registry.entries().getFirst().title()).isEqualTo("original");

            action.putValue(Command.TITLE, "Custom title");
            assertThat(registry.entries().getFirst().title()).isEqualTo("custom title");
            action.putValue(Command.TITLE, null);
            action.putValue(Action.NAME, "Renamed");
            assertThat(registry.entries().getFirst().title()).isEqualTo("renamed");
            assertThat(CommandSearch.find(registry.entries(), "renamed", List.of(), 5)).containsExactly(command);
            action.setEnabled(false);
            assertThat(CommandSearch.find(registry.entries(), "renamed", List.of(), 5)).isEmpty();
            assertThat(changes).hasValue(4);

            changeSubscription.close();
            registration.close();
            registry.close();
        });
    }

    @Test void duplicateIdsAreRejectedAndIdentitySurvivesIdReuse() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var registry = new CommandRegistry();
            var action = action("Same");
            var first = new Command("test.same", action, List.of("one"));
            var firstRegistration = registry.register(first);
            assertThatThrownBy(() -> registry.register(new Command("test.same", action, List.of("one"))))
                .isInstanceOf(IllegalArgumentException.class);
            firstRegistration.close();

            var replacement = new Command("test.same", action, List.of("one"));
            var replacementRegistration = registry.register(replacement);
            firstRegistration.close();
            assertThat(registry.contains(first)).isFalse();
            assertThat(registry.contains(replacement)).isTrue();
            replacementRegistration.close();
            registry.close();
        });
    }

    @Test void subscriptionsAreIdempotentAndListenerRemovalIsSafeDuringNotification() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var registry = new CommandRegistry();
            var action = action("Change");
            var command = new Command("test.change", action, List.of());
            var registration = registry.register(command);
            var notifications = new AtomicInteger();
            Subscription[] self = new Subscription[1];
            self[0] = registry.onChanged(() -> {
                notifications.incrementAndGet();
                self[0].close();
            });
            action.putValue(Action.NAME, "Changed");
            assertThat(notifications).hasValue(1);
            action.putValue(Action.NAME, "Changed again");
            assertThat(notifications).hasValue(1);
            self[0].close();
            registration.close();
            registration.close();
            registry.close();
            registry.close();
        });
    }

    @Test void closingRegistryRemovesActionListenersAndReturnedEntriesAreImmutable() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var registry = new CommandRegistry();
            var action = action("Close");
            var registration = registry.register(new Command("test.close", action, List.of()));
            var entries = registry.entries();
            assertThatThrownBy(entries::clear).isInstanceOf(UnsupportedOperationException.class);
            assertThat(action.getPropertyChangeListeners()).hasSize(1);
            registry.close();
            assertThat(action.getPropertyChangeListeners()).isEmpty();
            registration.close();
        });
    }

    @Test void invalidCommandsAreRejectedAndKeywordsAreCopied() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var action = action("Valid");
            for (String id : List.of("", "1bad", "Upper", "bad id", "bad/part")) {
                assertThatIllegalArgumentException().isThrownBy(() -> new Command(id, action, List.of()));
            }
            assertThatIllegalArgumentException().isThrownBy(() -> new Command("test.invalid", action(""), List.of()));
            var keywords = new java.util.ArrayList<>(List.of("ssh"));
            var command = new Command("test.immutable", action, keywords);
            keywords.add("mutated");
            assertThat(command.keywords()).containsExactly("ssh");
            assertThatThrownBy(() -> command.keywords().add("mutated"))
                .isInstanceOf(UnsupportedOperationException.class);
        });
    }

    private static AbstractAction action(String title) {
        return new AbstractAction(title) {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {}
        };
    }
}
