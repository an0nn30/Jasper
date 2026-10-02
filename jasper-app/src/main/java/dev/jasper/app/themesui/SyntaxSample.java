package dev.jasper.app.themesui;

import dev.jasper.app.appearance.GeneratedTheme;
import dev.jasper.app.appearance.SyntaxRole;
import dev.jasper.app.appearance.SyntaxStyle;
import dev.jasper.app.appearance.Token;
import java.awt.Font;
import java.util.List;
import javax.swing.JTextPane;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

/** Fixed, pre-tokenised source. No parser or plugin dependency; EDT only. */
final class SyntaxSample extends JTextPane {
    static final String TEXT = "// Theme preview\nclass Sample {\n    String greet(int count) {\n        return \"Hello\" + 42;\n    }\n}\n";
    record Span(int start, int end, SyntaxRole role) {}
    static final List<Span> SPANS = List.of(
        new Span(0,16,SyntaxRole.COMMENT), new Span(17,22,SyntaxRole.KEYWORD), new Span(23,29,SyntaxRole.TYPE),
        new Span(36,42,SyntaxRole.TYPE), new Span(43,48,SyntaxRole.FUNCTION), new Span(49,52,SyntaxRole.KEYWORD),
        new Span(70,76,SyntaxRole.KEYWORD), new Span(77,84,SyntaxRole.STRING), new Span(85,86,SyntaxRole.OPERATOR),
        new Span(87,89,SyntaxRole.NUMBER));
    SyntaxSample() {
        setName("theme-preview-editor"); setText(TEXT); setEditable(false); setFocusable(false);
        setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    }
    void theme(GeneratedTheme workspace) {
        setBackground(workspace.tokens().get(Token.EDITOR_BACKGROUND));
        setForeground(workspace.tokens().get(Token.EDITOR_FOREGROUND));
        setSelectionColor(workspace.tokens().get(Token.EDITOR_SELECTION));
        getStyledDocument().setCharacterAttributes(0, TEXT.length(), attributes(workspace.syntax().get(SyntaxRole.VARIABLE)), true);
        for (var span : SPANS) getStyledDocument().setCharacterAttributes(span.start(), span.end()-span.start(), attributes(workspace.syntax().get(span.role())), true);
    }
    private static SimpleAttributeSet attributes(SyntaxStyle style) {
        var result = new SimpleAttributeSet(); StyleConstants.setForeground(result, style.color());
        StyleConstants.setBold(result, style.bold()); StyleConstants.setItalic(result, style.italic()); return result;
    }
}
