package dev.jasper.app.workspace;

import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.TabNames;
import dev.jasper.app.config.TerminalConfig;
import dev.jasper.app.config.TitleTemplate;
/** Applies saved values on the EDT: window chrome always takes them; a pane's own font size survives reloads that keep the saved size. */
final class WorkspaceConfiguration {
    private final WindowContent owner;
    private ConfigSnapshot configured;
    private float configuredFontSize = TerminalPane.DEFAULT_FONT_SIZE;
    private TitleTemplate tabName = TabNames.DEFAULT_PATTERN;
    WorkspaceConfiguration(WindowContent owner) { this.owner = owner; }
    ConfigSnapshot snapshot() { return configured; }
    float configuredFontSize() { return configuredFontSize; }
    /** The saved tab name pattern, parsed (tab names spec 3). */
    TitleTemplate tabName() { return tabName; }

    void apply(ConfigSnapshot next, boolean macOs) {
        if (owner.closed()) return;
        ConfigSnapshot previous = configured;
        if (previous == null) owner.initializeViewMode(next.viewMode());
        configured = next;
        owner.setAnimations(next.animations());
        owner.setTabHeight(next.tabHeight());
        owner.setRailWidth(next.railWidth());
        owner.setToolbarHeight(next.toolbarHeight());
        owner.setStatusBarHeight(next.statusBarHeight());
        if (owner.toolbarMode() != next.toolbar()) owner.setToolbarMode(next.toolbar());
        if (owner.statusVisible() != next.statusBar()) owner.setStatusVisible(next.statusBar());
        boolean sizeChanged = previous == null || previous.fontSize() != next.fontSize();
        boolean optionsChanged = previous == null || !previous.font().equals(next.font())
            || liveBehaviorChanged(previous.terminal(), next.terminal());
        boolean dimChanged = previous == null
            || previous.terminal().dimInactivePanes() != next.terminal().dimInactivePanes();
        boolean exitChanged = previous == null || previous.terminal().onExit() != next.terminal().onExit();
        configuredFontSize = next.fontSize();
        if (optionsChanged || dimChanged || exitChanged) {
            for (TerminalContent terminal : owner.terminalTabs()) {
                TerminalPane pane = terminal.pane();
                if (optionsChanged && pane.view() != null) {
                    float size = sizeChanged ? next.fontSize() : pane.view().fontSize();
                    pane.view().applyOptions(pane.view().options().toBuilder()
                        .fontFamily(next.font().family()).fontSize(size).fallbackFonts(next.font().fallback())
                        .ligatures(next.font().ligatures()).lineHeight(next.font().lineHeight())
                        .cursorStyle(next.terminal().cursorShape()).cursorBlink(next.terminal().cursorBlink())
                        .optionAsMeta(next.terminal().optionAsMeta()).scrollback(next.terminal().scrollback())
                        .copyOnSelect(next.terminal().copyOnSelect()).bell(next.terminal().bell()).build());
                }
                if (dimChanged) pane.setConfiguredDim(next.terminal().dimInactivePanes());
                if (exitChanged) pane.setShellExitBehavior(next.terminal().onExit());
            }
        }
        if (previous == null || !previous.terminal().tabName().equals(next.terminal().tabName())) {
            tabName = TitleTemplate.parse(next.terminal().tabName());
            for (TerminalContent terminal : owner.terminalTabs()) terminal.onChanged.run();
        }
        // A plugin's view in a pane dims under an overlay of the configured amount too (workspace plan 8a Rulings 12).
        if (dimChanged) owner.dimPanes();
        if (previous == null || !previous.keybindings().equals(next.keybindings())) owner.setBindings(next.bindings(macOs));
        if (previous == null || previous.visibleRows() != next.visibleRows()) owner.commandPalette().setVisibleRows(next.visibleRows());
        owner.requestGlass();
    }

    private static boolean liveBehaviorChanged(TerminalConfig previous, TerminalConfig next) {
        return previous.optionAsMeta() != next.optionAsMeta()
            || previous.cursorShape() != next.cursorShape()
            || previous.cursorBlink() != next.cursorBlink()
            || previous.copyOnSelect() != next.copyOnSelect()
            || previous.bell() != next.bell();
    }

}
