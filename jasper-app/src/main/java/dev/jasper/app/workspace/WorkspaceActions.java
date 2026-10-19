package dev.jasper.app.workspace;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.layout.Side;
import dev.jasper.app.palette.PaletteScope;
import dev.jasper.terminal.view.TerminalAction;
import dev.jasper.terminal.view.TerminalView;
import java.awt.event.ActionEvent;
import java.util.EnumMap;
import javax.swing.*;

/** Window-local action table and dispatch; all lookups resolve the current pane on the EDT. */
final class WorkspaceActions {
    private final WindowContent owner;
    private final EnumMap<ActionId, Action> actions = new EnumMap<>(ActionId.class);
    private boolean updatingActions;
    WorkspaceActions(WindowContent owner) {
        this.owner = owner;
        for (ActionId id : ActionId.values()) {
            Action action = new AbstractAction(id.label()) {
                @Override public void actionPerformed(ActionEvent event) { invoke(id); }
            };
            owner.bindings().strokeFor(id).ifPresent(stroke -> action.putValue(Action.ACCELERATOR_KEY, stroke));
            actions.put(id, action);
        }
        String unavailable = "Configuration is not connected";
        action(ActionId.OPEN_SETTINGS).putValue(Action.SHORT_DESCRIPTION, unavailable);
        action(ActionId.RELOAD_CONFIG).putValue(Action.SHORT_DESCRIPTION, unavailable);
        action(ActionId.OPEN_FILE).putValue(Action.SHORT_DESCRIPTION, "Open a file with the default editor");
    }
    Action action(ActionId id) { return actions.get(id); }
    boolean updating() { return updatingActions; }
    void disable() { actions.values().forEach(action -> action.setEnabled(false)); }

    void invoke(ActionId id) {
        if (dev.jasper.app.platform.WindowInput.blocked(owner) && id != ActionId.QUIT) return;
        if (owner.commandPalette() != null && owner.commandPalette().blocksActions()
            && id != ActionId.COMMAND_PALETTE) return;
        update();
        if (!action(id).isEnabled()) return;
        var content = owner.selectedContent();
        if (content != null) {
            if (id == ActionId.CLOSE_TAB) { content.requestClose(); return; }
            String key = contentCommand(id);
            if (key != null) { content.commands().stream().filter(c -> c.id().equals(key) && c.enabled()).findFirst().ifPresent(c -> c.run().run()); return; }
        }
        TerminalContent terminal = owner.currentTerminal();
        TerminalPane pane = owner.currentPane();
        TerminalView view = pane == null ? null : pane.view();
        switch (id) {
            case COMMAND_PALETTE -> owner.commandPalette().open(PaletteScope.ALL_ID);
            case NEW_TAB -> owner.newTab(owner.directory());
            case NEW_WINDOW -> owner.requestNewWindow();
            case OPEN_FILE -> owner.openFile();
            case QUIT -> owner.requestQuit();
            case CLOSE_TAB -> owner.closeTab(owner.selectedTab());
            case CLOSE_GROUP -> owner.closeGroup(owner.selectedTab());
            case SPLIT_RIGHT -> owner.split(Side.RIGHT);
            case SPLIT_DOWN -> owner.split(Side.DOWN);
            case MAXIMIZE_GROUP -> owner.toggleMaximize();
            case TOGGLE_ZEN_MODE -> owner.toggleZenMode();
            case FOCUS_GROUP_LEFT -> owner.focusGroup(Side.LEFT);
            case FOCUS_GROUP_RIGHT -> owner.focusGroup(Side.RIGHT);
            case FOCUS_GROUP_UP -> owner.focusGroup(Side.UP);
            case FOCUS_GROUP_DOWN -> owner.focusGroup(Side.DOWN);
            case NEXT_TAB -> owner.selectRelative(1);
            case PREVIOUS_TAB -> owner.selectRelative(-1);
            case SELECT_TAB_1, SELECT_TAB_2, SELECT_TAB_3, SELECT_TAB_4, SELECT_TAB_5,
                 SELECT_TAB_6, SELECT_TAB_7, SELECT_TAB_8, SELECT_TAB_9 ->
                owner.selectInGroup(id.ordinal() - ActionId.SELECT_TAB_1.ordinal());
            case RENAME_TAB -> {
                String name = owner.askTabName(terminal.title());
                if (name != null) terminal.rename(name);
            }
            case FIND -> pane.findBar().open();
            case FIND_NEXT -> pane.findBar().next();
            case FIND_PREVIOUS -> pane.findBar().previous();
            case PREVIOUS_PROMPT -> view.execute(TerminalAction.PREVIOUS_PROMPT);
            case NEXT_PROMPT -> view.execute(TerminalAction.NEXT_PROMPT);
            case COPY -> view.execute(TerminalAction.COPY_SELECTION);
            case PASTE -> view.execute(TerminalAction.PASTE_CLIPBOARD);
            case CLEAR_SCROLLBACK -> view.execute(TerminalAction.CLEAR_SCROLLBACK);
            case CLEAR_TERMINAL -> view.execute(TerminalAction.CLEAR_TERMINAL);
            case FONT_BIGGER -> view.setFontSize(view.fontSize() + 1);
            case FONT_SMALLER -> view.setFontSize(view.fontSize() - 1);
            case FONT_RESET -> view.setFontSize(owner.configuredFontSize());
            case OPEN_SETTINGS -> owner.openSettings();
            case RELOAD_CONFIG -> owner.reloadConfiguration();
        }
        owner.update();
    }

    private static String contentCommand(ActionId id) {
        return switch (id) {
            case FIND -> "find"; case FIND_NEXT -> "find-next"; case FIND_PREVIOUS -> "find-previous";
            case COPY -> "copy"; case PASTE -> "paste";
            default -> null;
        };
    }

    void update() {
        updatingActions = true;
        try {
            TerminalPane pane = owner.currentPane();
            boolean present = pane != null;
            var content = owner.selectedContent();
            boolean ready = present && pane.view() != null;
            boolean running = present && pane.running();
            for (ActionId id : ActionId.values()) {
                String contentKey = contentCommand(id);
                boolean contentEnabled = content != null && (id == ActionId.CLOSE_TAB
                    || id == ActionId.NEXT_TAB || id == ActionId.PREVIOUS_TAB || contentKey != null && content.commands().stream().anyMatch(c -> c.id().equals(contentKey) && c.enabled()));
                boolean enabled = !owner.closed() && (contentEnabled || switch (id) {
                    case OPEN_SETTINGS -> owner.configurationConnected();
                    case RELOAD_CONFIG -> owner.configurationConnected();
                    case COMMAND_PALETTE, NEW_TAB, NEW_WINDOW, OPEN_FILE, QUIT, SPLIT_RIGHT, SPLIT_DOWN -> true;
                    case TOGGLE_ZEN_MODE -> owner.isMain();
                    case PASTE -> running;
                    case COPY -> ready && pane.view().hasSelection();
                    case CLEAR_TERMINAL -> ready && !pane.session().usingAlternateBuffer();
                    case FIND, FIND_NEXT, FIND_PREVIOUS, PREVIOUS_PROMPT, NEXT_PROMPT,
                         CLEAR_SCROLLBACK, FONT_BIGGER, FONT_SMALLER, FONT_RESET -> ready;
                    case SELECT_TAB_1, SELECT_TAB_2, SELECT_TAB_3, SELECT_TAB_4, SELECT_TAB_5,
                         SELECT_TAB_6, SELECT_TAB_7, SELECT_TAB_8, SELECT_TAB_9 ->
                        id.ordinal() - ActionId.SELECT_TAB_1.ordinal() < owner.groupTabCount();
                    case MAXIMIZE_GROUP -> owner.workspace().model().groups().size() > 1;
                    case CLOSE_GROUP -> owner.selectedTab() != null;
                    case FOCUS_GROUP_LEFT -> owner.workspace().neighbour(Side.LEFT).isPresent();
                    case FOCUS_GROUP_RIGHT -> owner.workspace().neighbour(Side.RIGHT).isPresent();
                    case FOCUS_GROUP_UP -> owner.workspace().neighbour(Side.UP).isPresent();
                    case FOCUS_GROUP_DOWN -> owner.workspace().neighbour(Side.DOWN).isPresent();
                    default -> present;
                });
                action(id).setEnabled(enabled);
            }
            if (owner.windowCommands() != null && owner.chrome() != null) owner.windowCommands().refresh();
        } finally { updatingActions = false; }
        if (owner.commandPalette() != null) owner.commandPalette().refreshIfChanged();
    }

}
