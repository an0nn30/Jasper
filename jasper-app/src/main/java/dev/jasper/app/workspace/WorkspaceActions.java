package dev.jasper.app.workspace;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.contributions.OtherTab;
import dev.jasper.app.layout.Side;
import dev.jasper.app.palette.PaletteScope;
import dev.jasper.terminal.view.TerminalAction;
import dev.jasper.terminal.view.TerminalView;
import java.awt.event.ActionEvent;
import java.util.EnumMap;
import java.util.List;
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
            if (id == ActionId.CLOSE_PANE) { content.requestClose(); return; }
            String key = contentCommand(id);
            if (key != null) { content.commands().stream().filter(c -> c.id().equals(key) && c.enabled()).findFirst().ifPresent(c -> c.run().run()); return; }
        }
        TerminalContent terminal = owner.currentTerminal();
        TerminalPane pane = owner.currentPane();
        TerminalView view = pane == null ? null : pane.view();
        switch (id) {
            case COMMAND_PALETTE -> owner.commandPalette().open(PaletteScope.ALL_ID);
            case NEW_TAB -> owner.newTab(owner.directory());
            case CLOSE_TAB -> owner.closeParentTab(owner.activeParentTab());
            case NEXT_TAB -> owner.selectParentTabRelative(1);
            case PREVIOUS_TAB -> owner.selectParentTabRelative(-1);
            case SELECT_TAB_1, SELECT_TAB_2, SELECT_TAB_3, SELECT_TAB_4, SELECT_TAB_5,
                 SELECT_TAB_6, SELECT_TAB_7, SELECT_TAB_8, SELECT_TAB_9 ->
                owner.selectParentTabAt(id.ordinal() - ActionId.SELECT_TAB_1.ordinal());
            case NEW_WINDOW -> owner.requestNewWindow();
            case CLOSE_WINDOW -> owner.requestCloseWindow();
            case MOVE_TAB_TO_NEW_WINDOW -> owner.moveTabToNewWindow(owner.selectedTab());
            case MOVE_TAB_TO_WINDOW -> owner.moveToOtherWindow();
            case APPLY_DEFAULT_LAYOUT -> owner.layouts().applyDefault();
            case SAVE_LAYOUT_AS_NEW -> owner.layouts().saveAsNew();
            case SAVE_LAYOUT_AS_DEFAULT -> owner.layouts().saveAsDefault();
            case RESET_DEFAULT_LAYOUT -> owner.layouts().resetDefault();
            case MOVE_PANE_TO_NEW_TAB -> owner.moveToNewTab(owner.selectedTab());
            case MOVE_PANE_TO_TAB_1, MOVE_PANE_TO_TAB_2, MOVE_PANE_TO_TAB_3, MOVE_PANE_TO_TAB_4, MOVE_PANE_TO_TAB_5,
                 MOVE_PANE_TO_TAB_6, MOVE_PANE_TO_TAB_7, MOVE_PANE_TO_TAB_8, MOVE_PANE_TO_TAB_9 ->
                owner.moveToTab(owner.selectedTab(), id.ordinal() - ActionId.MOVE_PANE_TO_TAB_1.ordinal());
            case MOVE_TAB_INTO_TAB_1, MOVE_TAB_INTO_TAB_2, MOVE_TAB_INTO_TAB_3, MOVE_TAB_INTO_TAB_4, MOVE_TAB_INTO_TAB_5,
                 MOVE_TAB_INTO_TAB_6, MOVE_TAB_INTO_TAB_7, MOVE_TAB_INTO_TAB_8, MOVE_TAB_INTO_TAB_9 ->
                owner.moveTabIntoTab(owner.activeParentTab(), id.ordinal() - ActionId.MOVE_TAB_INTO_TAB_1.ordinal());
            case OPEN_FILE -> owner.openFile();
            case QUIT -> owner.requestQuit();
            case CLOSE_PANE -> owner.closeTab(owner.selectedTab());
            case SPLIT_RIGHT -> owner.split(Side.RIGHT);
            case SPLIT_DOWN -> owner.split(Side.DOWN);
            case MAXIMIZE_PANE -> owner.toggleMaximize();
            case TOGGLE_ZEN_MODE -> owner.toggleZenMode();
            case TOGGLE_RAIL -> owner.setRailVisible(!owner.railVisible());
            case TOGGLE_STATUS_BAR -> owner.chooseStatusVisible(!owner.statusVisible());
            case FOCUS_PANE_LEFT -> owner.focusGroup(Side.LEFT);
            case FOCUS_PANE_RIGHT -> owner.focusGroup(Side.RIGHT);
            case FOCUS_PANE_UP -> owner.focusGroup(Side.UP);
            case FOCUS_PANE_DOWN -> owner.focusGroup(Side.DOWN);
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
            case MANAGE_CYGWIN_PACKAGES -> owner.onManageCygwinPackages.run();
            case OPEN_SETTINGS -> owner.openSettings();
            case RELOAD_CONFIG -> owner.reloadConfiguration();
        }
        owner.update();
    }

    /** Whether {@code targets} offer the tab at {@code index} of the strip, 0 for the first. */
    private static boolean offers(List<OtherTab> targets, int index) {
        return targets.stream().anyMatch(other -> other.number() == index + 1);
    }

    private static String contentCommand(ActionId id) {
        return switch (id) {
            case FIND -> "find"; case FIND_NEXT -> "find-next"; case FIND_PREVIOUS -> "find-previous";
            case COPY -> "copy"; case PASTE -> "paste";
            case FONT_BIGGER -> "font-bigger"; case FONT_SMALLER -> "font-smaller"; case FONT_RESET -> "font-reset";
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
            // The tabs Move Pane to Tab and Move Tab Into offer, once a pass (pane flexibility spec 4 and 5).
            var paneTargets = owner.paneTargets(owner.selectedTab());
            var tabTargets = owner.tabTargets(owner.activeParentTab());
            for (ActionId id : ActionId.values()) {
                String contentKey = contentCommand(id);
                // A plugin's item closes through its own close request (workspace plan 8a Rulings 15).
                boolean contentEnabled = content != null && (id == ActionId.CLOSE_PANE
                    || contentKey != null && content.commands().stream().anyMatch(c -> c.id().equals(contentKey) && c.enabled()));
                boolean enabled = !owner.closed() && (contentEnabled || switch (id) {
                    case MANAGE_CYGWIN_PACKAGES -> id.availableOn(System.getProperty("os.name", "")) && owner.onManageCygwinPackages != null;
                    case OPEN_SETTINGS -> owner.configurationConnected();
                    case RELOAD_CONFIG -> owner.configurationConnected();
                    case COMMAND_PALETTE, NEW_TAB, NEW_WINDOW, CLOSE_WINDOW, OPEN_FILE, QUIT, SPLIT_RIGHT,
                         SPLIT_DOWN -> true;
                    // A window's only parent tab stays (workspace plan 8a Rulings 6).
                    case MOVE_TAB_TO_NEW_WINDOW -> owner.mayMoveTabToNewWindow(owner.activeParentTab());
                    // A parent tab or a window moves whole or not at all (workspace plan 7b Rulings 7).
                    case MOVE_TAB_TO_WINDOW -> owner.mayMoveToOtherWindow();
                    // Window \u203a Layouts (full windows plan 4): a window manager's window; saving needs the saved layouts.
                    case APPLY_DEFAULT_LAYOUT -> owner.layouts().applicable();
                    case SAVE_LAYOUT_AS_NEW, SAVE_LAYOUT_AS_DEFAULT -> owner.layouts().savable();
                    case RESET_DEFAULT_LAYOUT -> owner.layouts().resettable();
                    // A tab's only pane, a pinned item and one being closed stay (workspace plan 8b Rulings 9).
                    case MOVE_PANE_TO_NEW_TAB -> owner.mayMoveToNewTab(owner.selectedTab());
                    // Its own tab's number, a number no tab has and a pane that may not move do nothing (pane
                    // flexibility spec 4).
                    case MOVE_PANE_TO_TAB_1, MOVE_PANE_TO_TAB_2, MOVE_PANE_TO_TAB_3, MOVE_PANE_TO_TAB_4, MOVE_PANE_TO_TAB_5,
                         MOVE_PANE_TO_TAB_6, MOVE_PANE_TO_TAB_7, MOVE_PANE_TO_TAB_8, MOVE_PANE_TO_TAB_9 ->
                        offers(paneTargets, id.ordinal() - ActionId.MOVE_PANE_TO_TAB_1.ordinal());
                    // So for the shown tab, and a tab holding an item that may not move (pane flexibility spec 5;
                    // workspace plan 9a Rulings 22).
                    case MOVE_TAB_INTO_TAB_1, MOVE_TAB_INTO_TAB_2, MOVE_TAB_INTO_TAB_3, MOVE_TAB_INTO_TAB_4, MOVE_TAB_INTO_TAB_5,
                         MOVE_TAB_INTO_TAB_6, MOVE_TAB_INTO_TAB_7, MOVE_TAB_INTO_TAB_8, MOVE_TAB_INTO_TAB_9 ->
                        offers(tabTargets, id.ordinal() - ActionId.MOVE_TAB_INTO_TAB_1.ordinal());
                    case TOGGLE_ZEN_MODE -> true;
                    case TOGGLE_RAIL, TOGGLE_STATUS_BAR -> !owner.zenMode();
                    case PASTE -> running;
                    case COPY -> ready && pane.view().hasSelection();
                    case CLEAR_TERMINAL -> ready && !pane.session().usingAlternateBuffer();
                    case FIND, FIND_NEXT, FIND_PREVIOUS, PREVIOUS_PROMPT, NEXT_PROMPT,
                         CLEAR_SCROLLBACK, FONT_BIGGER, FONT_SMALLER, FONT_RESET -> ready;
                    case CLOSE_TAB -> owner.selectedTab() != null;
                    case NEXT_TAB, PREVIOUS_TAB -> owner.parentTabCount() > 1;
                    case SELECT_TAB_1, SELECT_TAB_2, SELECT_TAB_3, SELECT_TAB_4, SELECT_TAB_5,
                         SELECT_TAB_6, SELECT_TAB_7, SELECT_TAB_8, SELECT_TAB_9 ->
                        id.ordinal() - ActionId.SELECT_TAB_1.ordinal() < owner.parentTabCount();
                    case MAXIMIZE_PANE -> owner.workspace().model().groups().size() > 1;
                    case CLOSE_PANE -> owner.selectedTab() != null;
                    case FOCUS_PANE_LEFT -> owner.workspace().neighbour(Side.LEFT).isPresent();
                    case FOCUS_PANE_RIGHT -> owner.workspace().neighbour(Side.RIGHT).isPresent();
                    case FOCUS_PANE_UP -> owner.workspace().neighbour(Side.UP).isPresent();
                    case FOCUS_PANE_DOWN -> owner.workspace().neighbour(Side.DOWN).isPresent();
                    default -> present;
                });
                action(id).setEnabled(enabled);
            }
            if (owner.windowCommands() != null && owner.chrome() != null) owner.windowCommands().refresh();
        } finally { updatingActions = false; }
        if (owner.commandPalette() != null) owner.commandPalette().refreshIfChanged();
    }

}
