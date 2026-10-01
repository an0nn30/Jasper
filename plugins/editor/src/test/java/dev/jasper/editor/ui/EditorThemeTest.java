package dev.jasper.editor.ui;

import dev.jasper.sdk.ui.EditorPalette;
import dev.jasper.sdk.ui.SyntaxPalette;
import dev.jasper.sdk.ui.SyntaxRole;
import dev.jasper.sdk.ui.SyntaxStyle;
import java.awt.Color;
import java.awt.Font;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.text.AbstractDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EditorThemeTest {
    static final EditorPalette EDITOR = new EditorPalette(Color.DARK_GRAY, Color.WHITE, Color.YELLOW,
        Color.BLUE, Color.GRAY, Color.BLACK, Color.LIGHT_GRAY);

    static SyntaxPalette syntax(int offset) {
        var styles = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
        for (var role : SyntaxRole.values()) styles.put(role, new SyntaxStyle(new Color(offset + role.ordinal()),
            role.ordinal() % 2 == 0, role.ordinal() % 3 == 0));
        return new SyntaxPalette(styles);
    }

    @Test void everyTokenUsesItsSemanticRoleAndCurrentFont() {
        var area = new RSyntaxTextArea();
        area.setFont(new Font("Monospaced", Font.PLAIN, 19));
        var scroll = new RTextScrollPane(area);
        var palette = syntax(0x223300);
        new EditorTheme().apply(area, scroll, EDITOR, palette);
        var mapping = Map.ofEntries(
            Map.entry(SyntaxRole.KEYWORD, new int[] {TokenTypes.RESERVED_WORD, TokenTypes.RESERVED_WORD_2, TokenTypes.PREPROCESSOR}),
            Map.entry(SyntaxRole.TYPE, new int[] {TokenTypes.DATA_TYPE}),
            Map.entry(SyntaxRole.FUNCTION, new int[] {TokenTypes.FUNCTION}),
            Map.entry(SyntaxRole.VARIABLE, new int[] {TokenTypes.VARIABLE}),
            Map.entry(SyntaxRole.CONSTANT, new int[] {TokenTypes.LITERAL_BOOLEAN}),
            Map.entry(SyntaxRole.NUMBER, new int[] {TokenTypes.LITERAL_NUMBER_DECIMAL_INT, TokenTypes.LITERAL_NUMBER_FLOAT, TokenTypes.LITERAL_NUMBER_HEXADECIMAL}),
            Map.entry(SyntaxRole.STRING, new int[] {TokenTypes.LITERAL_STRING_DOUBLE_QUOTE, TokenTypes.LITERAL_CHAR, TokenTypes.LITERAL_BACKQUOTE}),
            Map.entry(SyntaxRole.COMMENT, new int[] {TokenTypes.COMMENT_EOL, TokenTypes.COMMENT_MULTILINE}),
            Map.entry(SyntaxRole.DOC_COMMENT, new int[] {TokenTypes.COMMENT_DOCUMENTATION, TokenTypes.COMMENT_KEYWORD, TokenTypes.COMMENT_MARKUP}),
            Map.entry(SyntaxRole.ANNOTATION, new int[] {TokenTypes.ANNOTATION}),
            Map.entry(SyntaxRole.OPERATOR, new int[] {TokenTypes.OPERATOR}),
            Map.entry(SyntaxRole.PUNCTUATION, new int[] {TokenTypes.SEPARATOR}),
            Map.entry(SyntaxRole.TAG, new int[] {TokenTypes.MARKUP_TAG_DELIMITER, TokenTypes.MARKUP_TAG_NAME}),
            Map.entry(SyntaxRole.ATTRIBUTE, new int[] {TokenTypes.MARKUP_TAG_ATTRIBUTE, TokenTypes.MARKUP_TAG_ATTRIBUTE_VALUE}),
            Map.entry(SyntaxRole.REGEX, new int[] {TokenTypes.REGEX}),
            Map.entry(SyntaxRole.ERROR, new int[] {TokenTypes.ERROR_IDENTIFIER, TokenTypes.ERROR_NUMBER_FORMAT, TokenTypes.ERROR_STRING_DOUBLE, TokenTypes.ERROR_CHAR}));
        for (var entry : mapping.entrySet()) for (int token : entry.getValue()) {
            var style = area.getSyntaxScheme().getStyle(token);
            var semantic = palette.style(entry.getKey());
            assertThat(style.foreground).as("token %s", token).isEqualTo(semantic.color());
            assertThat(style.font.getFamily()).isEqualTo("Monospaced");
            assertThat(style.font.getSize()).isEqualTo(19);
            assertThat(style.font.getStyle()).isEqualTo((semantic.bold() ? Font.BOLD : 0) | (semantic.italic() ? Font.ITALIC : 0));
        }
        for (int token : new int[] {TokenTypes.IDENTIFIER, TokenTypes.WHITESPACE, TokenTypes.MARKUP_COMMENT,
            TokenTypes.MARKUP_DTD, TokenTypes.MARKUP_PROCESSING_INSTRUCTION, TokenTypes.MARKUP_CDATA_DELIMITER,
            TokenTypes.MARKUP_CDATA, TokenTypes.MARKUP_ENTITY_REFERENCE}) {
            assertThat(area.getSyntaxScheme().getStyle(token).foreground).isEqualTo(EDITOR.foreground());
        }
        assertThat(area.getBackground()).isEqualTo(EDITOR.background());
        assertThat(area.getCaretColor()).isEqualTo(EDITOR.caret());
        assertThat(scroll.getGutter().getBackground()).isEqualTo(EDITOR.gutter());
        assertThat(scroll.getClientProperty("FlatLaf.style")).isEqualTo(
            Map.of("background", EDITOR.background()));
    }

    @Test void rethemingKeepsDocumentSelectionUndoAndDoesNotAccumulateListeners() {
        var area = new RSyntaxTextArea();
        var scroll = new RTextScrollPane(area);
        var theme = new EditorTheme();
        var uiResourceEditor = new EditorPalette(new ColorUIResource(EDITOR.background()),
            new ColorUIResource(EDITOR.foreground()), new ColorUIResource(EDITOR.caret()),
            new ColorUIResource(EDITOR.selection()), EDITOR.currentLine(), EDITOR.gutter(), EDITOR.lineNumbers());
        theme.apply(area, scroll, uiResourceEditor, syntax(0x334400));
        area.setText("alpha beta");
        area.discardAllEdits();
        area.append(" gamma");
        area.select(2, 7);
        var document = (AbstractDocument) area.getDocument();
        int listeners = document.getDocumentListeners().length;
        var next = syntax(0x445500);
        theme.apply(area, scroll, uiResourceEditor, next);
        theme.apply(area, scroll, uiResourceEditor, next);
        Object[] previous = {UIManager.get("TextArea.background"), UIManager.get("TextArea.foreground"),
            UIManager.get("TextArea.caretForeground"), UIManager.get("TextArea.selectionBackground")};
        try {
            UIManager.put("TextArea.background", Color.MAGENTA);
            UIManager.put("TextArea.foreground", Color.RED);
            UIManager.put("TextArea.caretForeground", Color.GREEN);
            UIManager.put("TextArea.selectionBackground", Color.ORANGE);
            SwingUtilities.updateComponentTreeUI(scroll);
        } finally {
            UIManager.put("TextArea.background", previous[0]);
            UIManager.put("TextArea.foreground", previous[1]);
            UIManager.put("TextArea.caretForeground", previous[2]);
            UIManager.put("TextArea.selectionBackground", previous[3]);
        }
        assertThat(area.getDocument()).isSameAs(document);
        assertThat(area.getText()).isEqualTo("alpha beta gamma");
        assertThat(area.getSelectionStart()).isEqualTo(2);
        assertThat(area.getSelectionEnd()).isEqualTo(7);
        assertThat(area.canUndo()).isTrue();
        assertThat(document.getDocumentListeners()).hasSize(listeners);
        assertThat(area.getSyntaxScheme().getStyle(TokenTypes.RESERVED_WORD).foreground)
            .isEqualTo(next.style(SyntaxRole.KEYWORD).color());
        assertThat(area.getBackground()).isEqualTo(EDITOR.background());
        assertThat(area.getForeground()).isEqualTo(EDITOR.foreground());
        assertThat(area.getCaretColor()).isEqualTo(EDITOR.caret());
        assertThat(area.getSelectionColor()).isEqualTo(EDITOR.selection());
    }

    @Test void blendMovesPartWayTowardTheSecondColour() {
        assertThat(EditorTheme.blend(Color.BLACK, Color.WHITE, 0.5)).isEqualTo(new Color(128, 128, 128));
    }
}
