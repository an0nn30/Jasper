package dev.jasper.app;

import dev.jasper.app.lifecycle.Subscription;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Window-owned scope roster; duplicate IDs fail and each registration removes only itself. EDT only. */
final class ScopeRegistry implements AutoCloseable {
    private final Map<String, PaletteScope> scopes = new LinkedHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private boolean closed;

    Subscription register(PaletteScope scope) {
        CommandRegistry.requireEdt();
        if (closed) throw new IllegalStateException("Registry is closed");
        String id = PaletteScope.requireValidId(scope.id());
        if (scopes.containsKey(id)) throw new IllegalArgumentException("Duplicate scope: " + id);
        if (scope.verbs().isEmpty()) throw new IllegalArgumentException("Scope needs at least one verb: " + id);
        scopes.put(id, scope);
        notifyListeners();
        return new Subscription(() -> {
            CommandRegistry.requireEdt();
            if (scopes.remove(id, scope)) notifyListeners();
        });
    }

    Subscription onChanged(Runnable listener) {
        CommandRegistry.requireEdt();
        if (closed) throw new IllegalStateException("Registry is closed");
        listeners.add(listener);
        return new Subscription(() -> { CommandRegistry.requireEdt(); listeners.remove(listener); });
    }

    List<PaletteScope> scopes() { CommandRegistry.requireEdt(); return List.copyOf(scopes.values()); }
    Optional<PaletteScope> find(String id) { CommandRegistry.requireEdt(); return Optional.ofNullable(scopes.get(id)); }
    boolean contains(PaletteScope scope) { CommandRegistry.requireEdt(); return scope != null && scopes.get(scope.id()) == scope; }

    private void notifyListeners() { List.copyOf(listeners).forEach(Runnable::run); }

    @Override public void close() {
        CommandRegistry.requireEdt();
        if (closed) return;
        closed = true; scopes.clear(); listeners.clear();
    }
}
