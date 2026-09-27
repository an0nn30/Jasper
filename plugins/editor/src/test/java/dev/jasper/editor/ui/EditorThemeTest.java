package dev.jasper.editor.ui;

import dev.jasper.sdk.Variant;
import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EditorThemeTest {
    private static final List<String> KEYS = List.of("TextArea.background", "TextArea.foreground", "TextArea.caretForeground",
        "TextArea.selectionBackground", "Panel.background", "Label.disabledForeground");
    private final Map<String, Object> saved = new HashMap<>();

    @BeforeEach void install() {
        for (String key : KEYS) saved.put(key, UIManager.get(key));
        UIManager.put("TextArea.background", new ColorUIResource(0x2B2B2B));
        UIManager.put("TextArea.foreground", new ColorUIResource(0xA9B7C6));
        UIManager.put("TextArea.caretForeground", new ColorUIResource(0xBBBBBB));
        UIManager.put("TextArea.selectionBackground", new ColorUIResource(0x214283));
        UIManager.put("Panel.background", new ColorUIResource(0x3C3F41));
        UIManager.put("Label.disabledForeground", new ColorUIResource(0x777777));
    }

    @AfterEach void restore() { saved.forEach(UIManager::put); }

    @Test void tokenColoursFollowTheVariantAndSurfacesFollowTheJasperTheme() {
        var area = new RSyntaxTextArea();
        var scroll = new RTextScrollPane(area);
        var theme = new EditorTheme();
        theme.apply(area, scroll, Variant.DARK);
        assertThat(area.getBackground()).isEqualTo(new Color(0x2B2B2B));
        assertThat(area.getCaretColor()).isEqualTo(new Color(0xBBBBBB));
        assertThat(area.getSelectionColor()).isEqualTo(new Color(0x214283));
        assertThat(area.getCurrentLineHighlightColor()).isEqualTo(EditorTheme.blend(new Color(0x2B2B2B), new Color(0xA9B7C6), 0.06));
        assertThat(scroll.getGutter().getBackground()).isEqualTo(new Color(0x3C3F41));
        assertThat(scroll.getGutter().getLineNumberColor()).isEqualTo(new Color(0x777777));
        Color darkKeyword = area.getSyntaxScheme().getStyle(Token.RESERVED_WORD).foreground;
        theme.apply(area, scroll, Variant.LIGHT);
        assertThat(area.getSyntaxScheme().getStyle(Token.RESERVED_WORD).foreground).isNotEqualTo(darkKeyword);
        assertThat(area.getBackground()).as("the surface still follows Jasper").isEqualTo(new Color(0x2B2B2B));
    }

    @Test void blendMovesPartWayTowardTheSecondColour() {
        assertThat(EditorTheme.blend(Color.BLACK, Color.WHITE, 0.5)).isEqualTo(new Color(128, 128, 128));
    }
}
