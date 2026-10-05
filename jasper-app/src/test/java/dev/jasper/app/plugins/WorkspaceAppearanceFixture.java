package dev.jasper.app.plugins;

import dev.jasper.app.appearance.*;
import dev.jasper.ui.theme.*;
import dev.jasper.ui.themeio.*;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.ui.WorkspacePalette;
import dev.jasper.sdk.ui.EditorPalette;
import java.util.*;
import java.util.function.Consumer;

/** Native source for the same appearance contract exercised by the fake. */
final class WorkspaceAppearanceFixture {
    private ResolvedTheme current = new ThemeController().current();
    private final List<Consumer<ResolvedTheme>> listeners = new ArrayList<>();
    ResolvedTheme current() { return current; }
    Subscription subscribe(Consumer<ResolvedTheme> listener) {
        listeners.add(listener); listener.accept(current);
        return new Subscription(() -> listeners.remove(listener));
    }
    void setVariant(Variant variant) {
        var old = current.chrome();
        var chrome = new GeneratedTheme(old.name(), variant == Variant.DARK, old.author(), old.tokens(), old.syntax(),
            old.terminal(), old.uiKeys(), old.icons(), old.glass(), old.semantic());
        current = new ResolvedTheme(chrome, current.matching() ? chrome : current.workspace());
        for (var listener : List.copyOf(listeners)) listener.accept(current);
    }
    void set(Variant variant, WorkspacePalette palette, EditorPalette editor, dev.jasper.sdk.ui.SyntaxPalette syntax) {
        var previous = current.workspace();
        var tokens = new EnumMap<Token, java.awt.Color>(Token.class); tokens.putAll(previous.tokens());
        tokens.put(Token.SURFACE, palette.background());
        tokens.put(Token.SURFACE_RAISED, palette.surfaceRaised());
        tokens.put(Token.SURFACE_SUNKEN, palette.surfaceSunken());
        tokens.put(Token.TEXT, palette.text());
        tokens.put(Token.TEXT_MUTED, palette.textMuted());
        tokens.put(Token.BORDER, palette.border());
        tokens.put(Token.SELECTION, palette.selection());
        tokens.put(Token.SELECTION_TEXT, palette.selectionText());
        tokens.put(Token.HOVER, palette.hover());
        tokens.put(Token.ACCENT, palette.accent());
        tokens.put(Token.SUCCESS, palette.success());
        tokens.put(Token.WARNING, palette.warning());
        tokens.put(Token.ERROR, palette.error());
        tokens.put(Token.EDITOR_BACKGROUND, editor.background());
        tokens.put(Token.EDITOR_FOREGROUND, editor.foreground());
        tokens.put(Token.EDITOR_CARET, editor.caret());
        tokens.put(Token.EDITOR_SELECTION, editor.selection());
        tokens.put(Token.EDITOR_CURRENT_LINE, editor.currentLine());
        tokens.put(Token.EDITOR_GUTTER, editor.gutter());
        tokens.put(Token.EDITOR_LINE_NUMBERS, editor.lineNumbers());
        var styles = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
        syntax.styles().forEach((role, style) -> styles.put(SyntaxRole.valueOf(role.name()),
            new SyntaxStyle(style.color(), style.bold(), style.italic())));
        var workspace = new GeneratedTheme("Contract", variant == Variant.DARK, "", tokens, styles,
            previous.terminal(), previous.uiKeys(), previous.icons(), previous.glass(), previous.semantic());
        current = new ResolvedTheme(current.chrome(), workspace);
        for (var listener : List.copyOf(listeners)) listener.accept(current);
    }
}
