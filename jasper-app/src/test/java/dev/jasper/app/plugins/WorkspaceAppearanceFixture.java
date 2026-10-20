package dev.jasper.app.plugins;

import dev.jasper.app.appearance.*;
import dev.jasper.ui.theme.*;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.ui.WorkspacePalette;
import dev.jasper.sdk.ui.EditorPalette;
import java.util.*;
import java.util.function.Consumer;

/** Native source for the same appearance contract exercised by the fake. */
final class WorkspaceAppearanceFixture {
    private ResolvedTheme current;
    WorkspaceAppearanceFixture() { this(ThemeTestSupport.resolved(true)); }
    WorkspaceAppearanceFixture(ResolvedTheme initial) { current = initial; }
    private final List<Consumer<ResolvedTheme>> listeners = new ArrayList<>();
    ResolvedTheme current() { return current; }
    Subscription subscribe(Consumer<ResolvedTheme> listener) {
        listeners.add(listener); listener.accept(current);
        return new Subscription(() -> listeners.remove(listener));
    }
    void setPair(ResolvedTheme value) { current=value; for(var listener:List.copyOf(listeners))listener.accept(current); }
    void setVariant(Variant variant) {
        current = ThemeTestSupport.resolved(variant == Variant.DARK);
        for (var listener : List.copyOf(listeners)) listener.accept(current);
    }
    /** A workspace of {@code variant} carrying exactly these plugin-facing palettes, as HostedPalettes reads them back. */
    void set(Variant variant, WorkspacePalette palette, EditorPalette editor, dev.jasper.sdk.ui.SyntaxPalette syntax) {
        boolean dark = variant == Variant.DARK;
        var roles = new EnumMap<SemanticColor, java.awt.Color>(SemanticColor.class);
        roles.put(SemanticColor.SURFACE_PANEL, palette.background());
        roles.put(SemanticColor.SURFACE_PANEL_RAISED, palette.surfaceRaised());
        roles.put(SemanticColor.SURFACE_CARD, palette.surfaceSunken());
        roles.put(SemanticColor.TEXT_PRIMARY, palette.text());
        roles.put(SemanticColor.TEXT_SECONDARY, palette.textMuted());
        roles.put(SemanticColor.BORDER_WINDOW, palette.border());
        roles.put(SemanticColor.SELECTION_BG, palette.selection());
        roles.put(SemanticColor.TEXT_ONSELECTION, palette.selectionText());
        roles.put(SemanticColor.SURFACE_CONTROL_HOVER, palette.hover());
        roles.put(SemanticColor.ACCENT, palette.accent());
        roles.put(SemanticColor.STATUS_SUCCESS, palette.success());
        roles.put(SemanticColor.STATUS_WARNING, palette.warning());
        roles.put(SemanticColor.STATUS_DANGER, palette.error());
        var styles = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
        syntax.styles().forEach((role, style) -> styles.put(SyntaxRole.valueOf(role.name()),
            new SyntaxStyle(style.color(), style.bold(), style.italic())));
        var base = ThemeTestSupport.withSyntax(ThemeTestSupport.recolored(ThemeTestSupport.generated(dark), roles), styles);
        var workspace = new GeneratedTheme("Contract", dark, "", base.semantic(), base.terminal(), new EditorColors(editor.background(),
            editor.foreground(), editor.caret(), editor.selection(), editor.currentLine(), editor.gutter(), editor.lineNumbers()),
            base.syntax(), base.glass(), base.derivedOverrides());
        current = ThemeTestSupport.mixed(current.chrome(), workspace);
        for (var listener : List.copyOf(listeners)) listener.accept(current);
    }
}
