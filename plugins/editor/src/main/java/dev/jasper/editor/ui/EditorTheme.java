package dev.jasper.editor.ui;

import dev.jasper.sdk.ui.EditorPalette;
import dev.jasper.sdk.ui.SyntaxPalette;
import dev.jasper.sdk.ui.SyntaxRole;
import java.awt.Color;
import java.awt.Font;
import java.util.Map;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Style;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.RTextScrollPane;

/** Applies workspace editor surfaces and semantic syntax without replacing an editor's document. */
public final class EditorTheme {
    private static final Color CLEAR = new Color(0, 0, 0, 0);

    /** Applies the current workspace palettes to an editor and its gutter, fully opaque. */
    public void apply(RSyntaxTextArea area, RTextScrollPane scroll, EditorPalette editor, SyntaxPalette syntax) {
        apply(area, scroll, editor, syntax, 1.0);
    }

    /**
     * Applies the current workspace palettes to an editor and its gutter. Below 1, {@code opacity} makes the editor and
     * gutter backgrounds clear and their components non-opaque, so the enclosing {@link GlassSurface} paints the only
     * glass; the current-line highlight takes that alpha.
     *
     * @param opacity the workspace opacity behind the editor, from 0.1 to 1
     */
    public void apply(RSyntaxTextArea area, RTextScrollPane scroll, EditorPalette editor, SyntaxPalette syntax, double opacity) {
        SyntaxScheme scheme = (SyntaxScheme) area.getSyntaxScheme().clone();
        Font font = area.getFont();
        for (int token = 0; token < TokenTypes.DEFAULT_NUM_TOKEN_TYPES; token++) {
            Style current = scheme.getStyle(token);
            Style style = current == null ? new Style() : (Style) current.clone();
            SyntaxRole role = role(token);
            if (role == null) {
                style.foreground = fixed(editor.foreground());
                style.font = font.deriveFont(Font.PLAIN);
            } else {
                var value = syntax.style(role);
                style.foreground = fixed(value.color());
                style.font = font.deriveFont((value.bold() ? Font.BOLD : 0) | (value.italic() ? Font.ITALIC : 0));
            }
            scheme.setStyle(token, style);
        }
        area.setSyntaxScheme(scheme);
        boolean glass = opacity < 1;
        Color background = glass ? CLEAR : fixed(editor.background());
        Color gutterBackground = glass ? CLEAR : fixed(editor.gutter());
        area.setBackground(background);
        area.setOpaque(!glass);
        area.setForeground(fixed(editor.foreground()));
        area.setCaretColor(fixed(editor.caret()));
        area.setSelectionColor(fixed(editor.selection()));
        area.setCurrentLineHighlightColor(glass ? translucent(editor.currentLine(), opacity) : fixed(editor.currentLine()));
        scroll.setOpaque(!glass);
        scroll.setBackground(background);
        scroll.putClientProperty("FlatLaf.style", Map.of("background", background));
        scroll.getViewport().setOpaque(!glass);
        scroll.getViewport().setBackground(background);
        if (scroll.getRowHeader() != null) {
            scroll.getRowHeader().setOpaque(!glass);
            scroll.getRowHeader().setBackground(gutterBackground);
        }
        Gutter gutter = scroll.getGutter();
        gutter.setOpaque(!glass);
        gutter.setBackground(gutterBackground);
        // Line numbers and fold indicators fill with the gutter background themselves; clear, they leave the glass.
        for (var child : gutter.getComponents()) if (child instanceof javax.swing.JComponent component) component.setOpaque(!glass);
        gutter.setBorderColor(glass ? CLEAR : fixed(editor.gutter()));
        gutter.setFoldBackground(gutterBackground);
        gutter.setLineNumberColor(fixed(editor.lineNumbers()));
    }

    private static Color translucent(Color color, double opacity) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) Math.round(255 * opacity));
    }

    // UIResource colors are delegate defaults: a chrome LAF refresh may replace them without a workspace event.
    private static Color fixed(Color color) { return new Color(color.getRGB(), true); }

    private static SyntaxRole role(int token) {
        return switch (token) {
            case TokenTypes.RESERVED_WORD, TokenTypes.RESERVED_WORD_2, TokenTypes.PREPROCESSOR -> SyntaxRole.KEYWORD;
            case TokenTypes.DATA_TYPE -> SyntaxRole.TYPE;
            case TokenTypes.FUNCTION -> SyntaxRole.FUNCTION;
            case TokenTypes.VARIABLE -> SyntaxRole.VARIABLE;
            case TokenTypes.LITERAL_BOOLEAN -> SyntaxRole.CONSTANT;
            case TokenTypes.LITERAL_NUMBER_DECIMAL_INT, TokenTypes.LITERAL_NUMBER_FLOAT,
                TokenTypes.LITERAL_NUMBER_HEXADECIMAL -> SyntaxRole.NUMBER;
            case TokenTypes.LITERAL_STRING_DOUBLE_QUOTE, TokenTypes.LITERAL_CHAR, TokenTypes.LITERAL_BACKQUOTE -> SyntaxRole.STRING;
            case TokenTypes.COMMENT_EOL, TokenTypes.COMMENT_MULTILINE -> SyntaxRole.COMMENT;
            case TokenTypes.COMMENT_DOCUMENTATION, TokenTypes.COMMENT_KEYWORD, TokenTypes.COMMENT_MARKUP -> SyntaxRole.DOC_COMMENT;
            case TokenTypes.ANNOTATION -> SyntaxRole.ANNOTATION;
            case TokenTypes.OPERATOR -> SyntaxRole.OPERATOR;
            case TokenTypes.SEPARATOR -> SyntaxRole.PUNCTUATION;
            case TokenTypes.MARKUP_TAG_DELIMITER, TokenTypes.MARKUP_TAG_NAME -> SyntaxRole.TAG;
            case TokenTypes.MARKUP_TAG_ATTRIBUTE, TokenTypes.MARKUP_TAG_ATTRIBUTE_VALUE -> SyntaxRole.ATTRIBUTE;
            case TokenTypes.REGEX -> SyntaxRole.REGEX;
            case TokenTypes.ERROR_IDENTIFIER, TokenTypes.ERROR_NUMBER_FORMAT,
                TokenTypes.ERROR_STRING_DOUBLE, TokenTypes.ERROR_CHAR -> SyntaxRole.ERROR;
            default -> null;
        };
    }

    /** Returns a linear blend of two colors. */
    public static Color blend(Color base, Color toward, double amount) {
        return new Color((int) Math.round(base.getRed() + (toward.getRed() - base.getRed()) * amount),
            (int) Math.round(base.getGreen() + (toward.getGreen() - base.getGreen()) * amount),
            (int) Math.round(base.getBlue() + (toward.getBlue() - base.getBlue()) * amount));
    }
}
