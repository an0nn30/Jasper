package dev.jasper.app.appearance;

import javax.swing.UIManager;

/** Native semantic defaults consumed by workspace-aware Swing clients. EDT only. */
final class WorkspaceDefaults {
    private WorkspaceDefaults() {}
    static void publish(GeneratedTheme theme) {
        UIManager.put("Jasper.workspace.background", theme.tokens().get(Token.SURFACE));
        UIManager.put("Jasper.workspace.surfaceRaised", theme.tokens().get(Token.SURFACE_RAISED));
        UIManager.put("Jasper.workspace.surfaceSunken", theme.tokens().get(Token.SURFACE_SUNKEN));
        UIManager.put("Jasper.workspace.text", theme.tokens().get(Token.TEXT));
        UIManager.put("Jasper.workspace.textMuted", theme.tokens().get(Token.TEXT_MUTED));
        UIManager.put("Jasper.workspace.border", theme.tokens().get(Token.BORDER));
        UIManager.put("Jasper.workspace.selection", theme.tokens().get(Token.SELECTION));
        UIManager.put("Jasper.workspace.selectionText", theme.tokens().get(Token.SELECTION_TEXT));
        UIManager.put("Jasper.workspace.hover", theme.tokens().get(Token.HOVER));
        UIManager.put("Jasper.workspace.accent", theme.tokens().get(Token.ACCENT));
        UIManager.put("Jasper.workspace.success", theme.tokens().get(Token.SUCCESS));
        UIManager.put("Jasper.workspace.warning", theme.tokens().get(Token.WARNING));
        UIManager.put("Jasper.workspace.error", theme.tokens().get(Token.ERROR));
        UIManager.put("Jasper.workspace.editorBackground", theme.tokens().get(Token.EDITOR_BACKGROUND));
        UIManager.put("Jasper.workspace.editorForeground", theme.tokens().get(Token.EDITOR_FOREGROUND));
        UIManager.put("Jasper.workspace.editorCaret", theme.tokens().get(Token.EDITOR_CARET));
        UIManager.put("Jasper.workspace.editorSelection", theme.tokens().get(Token.EDITOR_SELECTION));
        UIManager.put("Jasper.workspace.editorCurrentLine", theme.tokens().get(Token.EDITOR_CURRENT_LINE));
        UIManager.put("Jasper.workspace.editorGutter", theme.tokens().get(Token.EDITOR_GUTTER));
        UIManager.put("Jasper.workspace.editorLineNumbers", theme.tokens().get(Token.EDITOR_LINE_NUMBERS));
        UIManager.put("Jasper.workspace.dark", theme.dark());
        theme.syntax().forEach((role, style) -> {
            String key = "Jasper.workspace.syntax." + role.key();
            UIManager.put(key + ".color", style.color());
            UIManager.put(key + ".bold", style.bold());
            UIManager.put(key + ".italic", style.italic());
        });
    }
}
