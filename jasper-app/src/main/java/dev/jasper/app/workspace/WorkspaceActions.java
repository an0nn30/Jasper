package dev.jasper.app.workspace;

import dev.jasper.app.commands.ActionId;
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
    }
    Action action(ActionId id) { return actions.get(id); }
    boolean updating() { return updatingActions; }
    void disable() { actions.values().forEach(action -> action.setEnabled(false)); }

    void invoke(ActionId id) {
        if (owner.commandPalette() != null && owner.commandPalette().isOpen()
            && id != ActionId.COMMAND_PALETTE && id != ActionId.HISTORY_PALETTE && id != ActionId.SNIPPETS_PALETTE) return;
        update();
        if (!action(id).isEnabled()) return;
        TerminalTab tab = owner.currentTab();
        TerminalPane pane = owner.currentPane();
        TerminalView view = pane == null ? null : pane.view();
        switch (id) {
            case COMMAND_PALETTE -> owner.commandPalette().open(PaletteScope.COMMANDS_ID);
            case HISTORY_PALETTE -> owner.commandPalette().open(PaletteScope.HISTORY_ID);
            case SNIPPETS_PALETTE -> owner.commandPalette().open(PaletteScope.SNIPPETS_ID);
            case NEW_TAB -> owner.newTab(owner.directory());
            case NEW_WINDOW -> owner.requestNewWindow();
            case QUIT -> owner.requestQuit();
            case CLOSE_TAB -> owner.closeTab(tab);
            case CLOSE_PANE -> tab.closePane(pane);
            case SPLIT_RIGHT -> tab.split(SplitTree.Axis.RIGHT);
            case SPLIT_DOWN -> tab.split(SplitTree.Axis.DOWN);
            case ZOOM_PANE -> tab.toggleZoom();
            case FOCUS_PANE_LEFT -> tab.navigate(SplitTree.Direction.LEFT);
            case FOCUS_PANE_RIGHT -> tab.navigate(SplitTree.Direction.RIGHT);
            case FOCUS_PANE_UP -> tab.navigate(SplitTree.Direction.UP);
            case FOCUS_PANE_DOWN -> tab.navigate(SplitTree.Direction.DOWN);
            case NEXT_TAB -> selectRelative(1);
            case PREVIOUS_TAB -> selectRelative(-1);
            case SELECT_TAB_1, SELECT_TAB_2, SELECT_TAB_3, SELECT_TAB_4, SELECT_TAB_5,
                 SELECT_TAB_6, SELECT_TAB_7, SELECT_TAB_8, SELECT_TAB_9 -> {
                int index = id.ordinal() - ActionId.SELECT_TAB_1.ordinal();
                if (index < owner.tabStrip().getTabCount()) owner.tabStrip().setSelectedIndex(index);
            }
            case RENAME_TAB -> {
                String name = JOptionPane.showInputDialog(owner, "Tab name (leave blank for automatic):", tab.title());
                if (name != null) tab.rename(name);
            }
            case FIND -> pane.findBar().open();
            case FIND_NEXT -> pane.findBar().next();
            case FIND_PREVIOUS -> pane.findBar().previous();
            case PREVIOUS_PROMPT -> view.execute(TerminalAction.PREVIOUS_PROMPT);
            case NEXT_PROMPT -> view.execute(TerminalAction.NEXT_PROMPT);
            case COPY -> view.execute(TerminalAction.COPY_SELECTION);
            case PASTE -> view.execute(TerminalAction.PASTE_CLIPBOARD);
            case CLEAR_SCROLLBACK -> view.execute(TerminalAction.CLEAR_SCROLLBACK);
            case FONT_BIGGER -> view.setFontSize(view.fontSize() + 1);
            case FONT_SMALLER -> view.setFontSize(view.fontSize() - 1);
            case FONT_RESET -> view.setFontSize(owner.configuredFontSize());
            case OPEN_SETTINGS -> owner.openSettings();
            case RELOAD_CONFIG -> owner.reloadConfiguration();
        }
        owner.update();
    }

    private void selectRelative(int delta) {
        if (owner.tabStrip().getTabCount() > 0) owner.tabStrip().setSelectedIndex(Math.floorMod(owner.tabStrip().getSelectedIndex() + delta, owner.tabStrip().getTabCount()));
    }

    void update() {
        updatingActions = true;
        try {
            TerminalPane pane = owner.currentPane();
            boolean present = pane != null;
            boolean ready = present && pane.view() != null;
            boolean running = present && pane.running();
            for (ActionId id : ActionId.values()) {
                boolean enabled = !owner.closed() && switch (id) {
                    case OPEN_SETTINGS -> owner.configurationConnected();
                    case RELOAD_CONFIG -> owner.configurationConnected();
                    case COMMAND_PALETTE, NEW_TAB, NEW_WINDOW, QUIT -> true;
                    case HISTORY_PALETTE -> owner.scopes().find(PaletteScope.HISTORY_ID).isPresent();
                    case SNIPPETS_PALETTE -> owner.scopes().find(PaletteScope.SNIPPETS_ID).isPresent();
                    case SPLIT_RIGHT, SPLIT_DOWN, PASTE -> running;
                    case COPY -> ready && pane.view().hasSelection();
                    case FIND, FIND_NEXT, FIND_PREVIOUS, PREVIOUS_PROMPT, NEXT_PROMPT,
                         CLEAR_SCROLLBACK, FONT_BIGGER, FONT_SMALLER, FONT_RESET -> ready;
                    case SELECT_TAB_1, SELECT_TAB_2, SELECT_TAB_3, SELECT_TAB_4, SELECT_TAB_5,
                         SELECT_TAB_6, SELECT_TAB_7, SELECT_TAB_8, SELECT_TAB_9 ->
                        id.ordinal() - ActionId.SELECT_TAB_1.ordinal() < owner.tabStrip().getTabCount();
                    default -> present;
                };
                action(id).setEnabled(enabled);
            }
            if (owner.windowCommands() != null && owner.chrome() != null) owner.windowCommands().refresh();
        } finally { updatingActions = false; }
        if (owner.commandPalette() != null) owner.commandPalette().refreshIfChanged();
    }

}
