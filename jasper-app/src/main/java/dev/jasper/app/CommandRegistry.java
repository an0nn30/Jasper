package dev.jasper.app;

import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.SwingUtilities;

final class CommandRegistry implements AutoCloseable {
    static final class Subscription implements AutoCloseable {
        private Runnable removal;

        Subscription(Runnable removal) {
            this.removal = removal;
        }

        @Override public void close() {
            if (removal == null) return;
            Runnable once = removal;
            removal = null;
            once.run();
        }
    }

    private record Registered(Command command, PropertyChangeListener listener) {}

    private final Map<String, Registered> commands = new LinkedHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private List<CommandSearch.Entry> index = List.of();
    private boolean closed;

    Subscription register(Command command) {
        requireEdt();
        if (closed) throw new IllegalStateException("Registry is closed");
        if (commands.containsKey(command.id()))
            throw new IllegalArgumentException("Duplicate command: " + command.id());
        PropertyChangeListener listener = event -> rebuild();
        Registered registered = new Registered(command, listener);
        commands.put(command.id(), registered);
        command.action().addPropertyChangeListener(listener);
        rebuild();
        return new Subscription(() -> {
            requireEdt();
            if (commands.remove(command.id(), registered)) {
                command.action().removePropertyChangeListener(listener);
                rebuild();
            }
        });
    }

    Subscription onChanged(Runnable listener) {
        requireEdt();
        if (closed) throw new IllegalStateException("Registry is closed");
        listeners.add(listener);
        return new Subscription(() -> {
            requireEdt();
            listeners.remove(listener);
        });
    }

    List<CommandSearch.Entry> entries() {
        requireEdt();
        return index;
    }

    boolean contains(Command command) {
        requireEdt();
        Registered current = commands.get(command.id());
        return current != null && current.command() == command;
    }

    Optional<Command> find(String id) {
        requireEdt();
        Registered current = commands.get(id);
        return current == null ? Optional.empty() : Optional.of(current.command());
    }

    private void rebuild() {
        requireEdt();
        if (closed) return;
        index = commands.values().stream().map(r -> CommandSearch.entry(r.command())).toList();
        List.copyOf(listeners).forEach(Runnable::run);
    }

    static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("EDT required");
    }

    @Override public void close() {
        requireEdt();
        if (closed) return;
        closed = true;
        commands.values().forEach(r -> r.command().action().removePropertyChangeListener(r.listener()));
        commands.clear();
        index = List.of();
        listeners.clear();
    }
}
