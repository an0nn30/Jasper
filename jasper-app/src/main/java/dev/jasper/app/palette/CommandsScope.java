package dev.jasper.app.palette;

import dev.jasper.app.commands.Command;
import dev.jasper.app.commands.CommandRegistry;
import dev.jasper.app.commands.CommandSearch;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.lifecycle.Subscription;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.KeyStroke;

/** The Commands scope: the existing registry, search, recents and starters behind the scope contract. */
public final class CommandsScope implements PaletteScope {
    static final PaletteVerb RUN = new PaletteVerb("run", "Run");
    private static final List<String> STARTERS = List.of("new_tab", "split_right", "open_settings", "new_window");

    private final CommandRegistry registry;
    private final CommandHistory history;
    private final boolean macOs;
    private final Consumer<Command> dispatch;
    private final Icon icon;

    public CommandsScope(CommandRegistry registry, CommandHistory history, boolean macOs, Consumer<Command> dispatch) {
        this(registry, history, macOs, dispatch, AppIcons.icon("command"));
    }

    public CommandsScope(CommandRegistry registry, CommandHistory history, boolean macOs, Consumer<Command> dispatch, Icon icon) {
        this.registry = Objects.requireNonNull(registry);
        this.history = Objects.requireNonNull(history);
        this.macOs = macOs;
        this.dispatch = Objects.requireNonNull(dispatch);
        this.icon = icon;
    }

    @Override public String id() { return COMMANDS_ID; }
    @Override public String label() { return "Commands"; }
    @Override public Icon icon() { return icon; }
    @Override public String description() { return "Run an application command"; }
    @Override public String placeholder() { return "Type a command, or > to switch scope"; }
    @Override public List<String> aliases() { return List.of("cmd", "commands", "actions"); }
    @Override public List<PaletteVerb> verbs() { return List.of(RUN); }

    @Override public PaletteResults search(String query, PaletteContext context) {
        if (CommandSearch.normalize(query).isEmpty()) {
            int limit = Math.min(3, context.maxResults());
            List<Command> recent = available(history.recent(), limit);
            boolean suggested = recent.isEmpty();
            if (suggested) recent = available(STARTERS, limit);
            return new PaletteResults(rows(recent), suggested ? "Suggested" : "Recent", null);
        }
        return new PaletteResults(
            rows(CommandSearch.find(registry.entries(), query, history.recent(), context.maxResults())), null, null);
    }

    @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteContext context) {
        return row.token() instanceof Command command && registry.contains(command) && command.action().isEnabled();
    }

    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) {
        if (!available(row, verb, context)) return;
        Command command = (Command) row.token();
        dispatch.accept(command);
        history.record(command.id());
    }

    @Override public Subscription onChanged(Runnable listener) {
        var registryListener = registry.onChanged(listener);
        var historyListener = history.onChanged(listener);
        return new Subscription(() -> { registryListener.close(); historyListener.close(); });
    }

    private List<Command> available(List<String> ids, int limit) {
        return ids.stream().flatMap(id -> registry.find(id).stream())
            .filter(command -> command.action().isEnabled()).limit(limit).toList();
    }

    List<PaletteRow> rows(List<Command> commands) {
        var rows = new ArrayList<PaletteRow>(commands.size());
        for (Command command : commands)
            rows.add(new PaletteRow(command.id(), command.title(), null,
                shortcutText(command.action().getValue(Action.ACCELERATOR_KEY), macOs), command.icon(),
                command.action().isEnabled(), command));
        return rows;
    }

    /** Moved from the palette renderer: "⌘K" on macOS, "Ctrl+K" elsewhere, "" for anything but a KeyStroke. */
    public static String shortcutText(Object value, boolean macOs) {
        if (!(value instanceof KeyStroke stroke)) return "";
        int modifiers = stroke.getModifiers();
        String key = stroke.getKeyCode() == 0
            ? String.valueOf(stroke.getKeyChar()) : KeyEvent.getKeyText(stroke.getKeyCode());
        if (macOs) {
            var text = new StringBuilder();
            if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) text.append("⌃");
            if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) text.append("⌥");
            if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) text.append("⇧");
            if ((modifiers & InputEvent.META_DOWN_MASK) != 0) text.append("⌘");
            return text.append(key).toString();
        }
        var names = new ArrayList<String>();
        if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) names.add("Ctrl");
        if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) names.add("Alt");
        if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) names.add("Shift");
        if ((modifiers & InputEvent.META_DOWN_MASK) != 0) names.add("Meta");
        names.add(key);
        return String.join("+", names);
    }
}
