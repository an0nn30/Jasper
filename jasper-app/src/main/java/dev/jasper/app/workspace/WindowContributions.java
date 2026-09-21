package dev.jasper.app.workspace;

import dev.jasper.app.commands.Command;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.ActionEntry;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.lifecycle.Subscription;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.AbstractAction;
import javax.swing.Action;

/**
 * This window's Swing side of the application-wide contributions model: one {@link Action} per
 * contributed action, registered as a palette command and bound to its shortcut, kept in step with the
 * model. Chrome and status rendering hang off the same change notifications. EDT only.
 */
final class WindowContributions implements AutoCloseable {
    private final WindowContent owner;
    private final Contributions model;
    private final Map<String, Action> actions = new LinkedHashMap<>();
    private final Map<String, Subscription> commands = new LinkedHashMap<>();
    private Subscription listening;

    WindowContributions(WindowContent owner, Contributions model) {
        this.owner = owner;
        this.model = model;
        syncActions();
        owner.chrome().connect(this);
        renderStatus();
        listening = model.onChanged(this::changed);
    }

    Contributions model() { return model; }

    /** The window's action for a contributed id, or null when the contributor removed it. */
    Action action(String id) { return actions.get(id); }

    /** Contributed actions in registration order, which is plugin load order: the keybinding precedence. */
    List<KeyBindings.Extension> extensions() {
        List<KeyBindings.Extension> result = new ArrayList<>();
        for (ActionEntry entry : model.actions())
            if (KeyBindings.extensionId(entry.id())) result.add(new KeyBindings.Extension(entry.id(), entry.defaultBinding()));
        return result;
    }

    void applyAccelerators() {
        actions.forEach((id, action) -> action.putValue(Action.ACCELERATOR_KEY, owner.bindings().strokeFor(id).orElse(null)));
    }

    /** From a shortcut, the palette, a menu, the toolbar or a status item. */
    void invoke(String id) {
        if (owner.closed() || owner.commandPalette() != null && owner.commandPalette().isOpen()) return;
        Optional<java.util.UUID> pane = Optional.ofNullable(owner.currentPane()).map(TerminalPane::id);
        model.action(id).ifPresent(entry -> entry.invoke(new Contributions.Invocation(owner.id(), pane)));
    }

    private void renderStatus() { owner.status().setContributed(model.status(), this::action); }

    private void changed(Contributions.Kind kind) {
        switch (kind) {
            case ACTIONS -> {
                if (syncActions()) owner.rebind();
                // Titles label toolbar buttons, and a vanished or disabled action changes every placement of it.
                owner.chrome().renderContributedToolbar();
                owner.chrome().renderContributedMenus();
                renderStatus();
            }
            case TOOLBAR -> owner.chrome().renderContributedToolbar();
            case MENUS -> owner.chrome().renderContributedMenus();
            case STATUS -> renderStatus();
        }
    }

    /** Returns whether the set of actions changed, which is when shortcuts must be recomputed. */
    private boolean syncActions() {
        boolean structural = false;
        Map<String, ActionEntry> current = new LinkedHashMap<>();
        for (ActionEntry entry : model.actions()) current.put(entry.id(), entry);
        for (String id : List.copyOf(actions.keySet())) {
            if (current.containsKey(id)) continue;
            commands.remove(id).close();
            actions.remove(id).setEnabled(false);
            structural = true;
        }
        for (ActionEntry entry : current.values()) {
            Action action = actions.get(entry.id());
            if (action == null) {
                String id = entry.id();
                action = new AbstractAction(entry.title()) {
                    @Override public void actionPerformed(ActionEvent event) { invoke(id); }
                };
                if (entry.icon() != null) action.putValue(Command.ICON, entry.icon());
                actions.put(id, action);
                List<String> keywords = new ArrayList<>(List.of(id.replace('.', ' ').replace('_', ' ')));
                keywords.addAll(entry.keywords());
                // The name is set before registration: a command needs a non-blank Action.NAME.
                commands.put(id, owner.commands().register(new Command(id, action, keywords)));
                structural = true;
            }
            action.putValue(Action.NAME, entry.title());
            action.putValue(Command.TITLE, entry.title());
            action.setEnabled(entry.enabled());
        }
        return structural;
    }

    @Override public void close() {
        if (listening == null) return;
        listening.close();
        listening = null;
        commands.values().forEach(Subscription::close);
        commands.clear();
        actions.values().forEach(action -> action.setEnabled(false));
        actions.clear();
        owner.chrome().connect(null);
        owner.status().setContributed(List.of(), id -> null);
    }
}
