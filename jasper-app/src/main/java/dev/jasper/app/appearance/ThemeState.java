package dev.jasper.app.appearance;

import dev.jasper.app.config.WorkspaceThemeChoice;
import java.util.Objects;

/** Requested workspace choice and resolved sources; a missing workspace follows chrome. */
record ThemeState(Theme choice, WorkspaceThemeChoice workspaceChoice, Theme workspace) {
    ThemeState {
        Objects.requireNonNull(choice);
        Objects.requireNonNull(workspaceChoice);
        if (workspaceChoice.matchesChrome()) workspace = null;
    }
    static ThemeState defaults() { return new ThemeState(Theme.DARK, WorkspaceThemeChoice.MATCH, null); }
    ThemeState configure(Theme next) { return new ThemeState(next, workspaceChoice, workspace); }
    ResolvedTheme resolve() {
        var chrome = ThemeManager.generated(choice);
        return new ResolvedTheme(chrome, workspace == null ? chrome : ThemeManager.generated(workspace));
    }
}
