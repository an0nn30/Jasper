package dev.jasper.app.contributions;

import dev.jasper.app.lifecycle.Subscription;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import javax.swing.Icon;
import javax.swing.SwingUtilities;

/**
 * What extensions have contributed, in registration order. Windows subscribe and re-render the part
 * that changed. Everything here is EDT-only; callbacks into contributors are their concern to contain.
 */
public final class Contributions {
    /** Which part changed. */
    public enum Kind { ACTIONS, TOOLBAR, MENUS, STATUS }

    /** Where an action was invoked: the window, and the pane it concerns if the window has one. */
    public record Invocation(UUID windowId, Optional<UUID> paneId) {
        public Invocation {
            Objects.requireNonNull(windowId, "windowId");
            Objects.requireNonNull(paneId, "paneId");
        }
    }

    private static final System.Logger LOG = System.getLogger(Contributions.class.getName());
    private final Map<String, ActionEntry> actions = new LinkedHashMap<>();
    private final List<ToolbarEntry> toolbar = new ArrayList<>();
    private final List<MenuSection> menus = new ArrayList<>();
    private final List<StatusEntry> status = new ArrayList<>();
    private final List<Consumer<Kind>> listeners = new ArrayList<>();

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Contributions belong to the EDT");
    }

    public ActionEntry addAction(String id, String title, Icon iconOrNull, List<String> keywords,
                                 Optional<String> defaultBinding, Consumer<Invocation> handler) {
        requireEdt();
        Objects.requireNonNull(handler, "handler");
        if (actions.containsKey(id)) throw new IllegalArgumentException("Action already registered: " + id);
        var entry = new ActionEntry(this, id, title, iconOrNull, keywords, defaultBinding, handler);
        actions.put(id, entry);
        changed(Kind.ACTIONS);
        return entry;
    }

    public Subscription addToolbar(ToolbarEntry entry) {
        requireEdt();
        toolbar.add(Objects.requireNonNull(entry, "entry"));
        changed(Kind.TOOLBAR);
        // Identity removal: two equal records are still two toolbar controls.
        return new Subscription(() -> {
            for (int i = 0; i < toolbar.size(); i++) if (toolbar.get(i) == entry) { toolbar.remove(i); break; }
            changed(Kind.TOOLBAR);
        });
    }

    public MenuSection addMenuSection(MenuTarget target) {
        requireEdt();
        if (target.type() == MenuTarget.Type.TOP_LEVEL)
            for (MenuSection existing : menus)
                if (existing.target().type() == MenuTarget.Type.TOP_LEVEL && existing.target().key().equals(target.key()))
                    throw new IllegalArgumentException("Menu already exists: " + target.key());
        var section = new MenuSection(this, target);
        menus.add(section);
        changed(Kind.MENUS);
        return section;
    }

    public StatusEntry addStatus(String id, boolean left, int priority) {
        requireEdt();
        for (StatusEntry existing : status)
            if (existing.id().equals(id)) throw new IllegalArgumentException("Status item already exists: " + id);
        var entry = new StatusEntry(this, id, left, priority);
        status.add(entry);
        changed(Kind.STATUS);
        return entry;
    }

    public List<ActionEntry> actions() { return List.copyOf(actions.values()); }
    public Optional<ActionEntry> action(String id) { return Optional.ofNullable(actions.get(id)); }
    public List<ToolbarEntry> toolbar() { return List.copyOf(toolbar); }
    public List<MenuSection> menus() { return List.copyOf(menus); }

    /** Ascending priority; equal priorities keep registration order. */
    public List<StatusEntry> status() {
        return status.stream().sorted(Comparator.comparingInt(StatusEntry::priority)).toList();
    }

    public Subscription onChanged(Consumer<Kind> listener) {
        requireEdt();
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return new Subscription(() -> listeners.remove(listener));
    }

    void remove(ActionEntry entry) { if (actions.remove(entry.id(), entry)) changed(Kind.ACTIONS); }
    void remove(MenuSection section) { if (menus.remove(section)) changed(Kind.MENUS); }
    void remove(StatusEntry entry) { if (status.remove(entry)) changed(Kind.STATUS); }

    void changed(Kind kind) {
        for (Consumer<Kind> listener : List.copyOf(listeners)) {
            try { listener.accept(kind); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A contributions listener failed", failure); }
        }
    }
}
