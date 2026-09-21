package dev.jasper.app.commands;

import dev.jasper.app.lifecycle.Subscription;

import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.SwingUtilities;

/** EDT registry of real Swing actions with a cached search index. Registration handles and registry close remove property listeners exactly once. */
public final class CommandRegistry implements AutoCloseable {
    private record Registered(Command command, PropertyChangeListener listener) {}

    private final Map<String, Registered> commands = new LinkedHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private List<CommandSearch.Entry> index = List.of();
    private boolean closed;

    public Subscription register(Command command) {
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

    public Subscription onChanged(Runnable listener) {
        requireEdt();
        if (closed) throw new IllegalStateException("Registry is closed");
        listeners.add(listener);
        return new Subscription(() -> {
            requireEdt();
            listeners.remove(listener);
        });
    }

    public List<CommandSearch.Entry> entries() {
        requireEdt();
        return index;
    }

    public boolean contains(Command command) {
        requireEdt();
        Registered current = commands.get(command.id());
        return current != null && current.command() == command;
    }

    public Optional<Command> find(String id) {
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

    public static void requireEdt() {
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
