package dev.jasper.app.workspace;

import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.TerminalConfig;
/** Applies changed saved values on the EDT without resetting unrelated temporary choices. */
final class WorkspaceConfiguration {
    private final WindowContent owner;
    private ConfigSnapshot configured;
    private float configuredFontSize = TerminalPane.DEFAULT_FONT_SIZE;
    WorkspaceConfiguration(WindowContent owner) { this.owner = owner; }
    ConfigSnapshot snapshot() { return configured; }
    float configuredFontSize() { return configuredFontSize; }

    void apply(ConfigSnapshot next, boolean macOs) {
        if (owner.closed()) return;
        ConfigSnapshot previous = configured;
        configured = next;
        if (previous == null || previous.tabHeight() != next.tabHeight()) owner.setTabHeight(next.tabHeight());
        if (previous == null || previous.toolbar() != next.toolbar()) owner.setToolbarMode(next.toolbar());
        if (previous == null || previous.statusBar() != next.statusBar()) owner.setStatusVisible(next.statusBar());
        boolean sizeChanged = previous == null || previous.fontSize() != next.fontSize();
        boolean optionsChanged = previous == null || !previous.font().equals(next.font())
            || liveBehaviorChanged(previous.terminal(), next.terminal());
        boolean dimChanged = previous == null
            || previous.terminal().dimInactivePanes() != next.terminal().dimInactivePanes();
        boolean exitChanged = previous == null || previous.terminal().onExit() != next.terminal().onExit();
        configuredFontSize = next.fontSize();
        if (optionsChanged || dimChanged || exitChanged) {
            for (int i = 0; i < owner.tabStrip().getTabCount(); i++) {
                for (TerminalPane pane : ((TerminalTab) owner.tabStrip().getComponentAt(i)).panes()) {
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
        }
        if (previous == null || !previous.keybindings().equals(next.keybindings())) owner.setBindings(next.bindings(macOs));
        if (previous == null || previous.historyEnabled() != next.historyEnabled()) owner.setHistoryEnabled(next.historyEnabled());
        if (previous == null || previous.maxResults() != next.maxResults()) owner.commandPalette().setMaxResults(next.maxResults());
        if (previous == null || !previous.trivialCommands().equals(next.trivialCommands()))
            owner.commandPalette().setTrivialCommands(next.trivialCommands());
    }

    private static boolean liveBehaviorChanged(TerminalConfig previous, TerminalConfig next) {
        return previous.optionAsMeta() != next.optionAsMeta()
            || previous.cursorShape() != next.cursorShape()
            || previous.cursorBlink() != next.cursorBlink()
            || previous.copyOnSelect() != next.copyOnSelect()
            || previous.bell() != next.bell();
    }

}
