package dev.jasper.editor.textview;

import dev.jasper.sdk.ui.TerminalTypography;
import java.util.function.Supplier;
import javax.swing.text.Element;
import javax.swing.text.View;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaUI;

/** RSTA editor kit, highlighter and bracket augmentations with one shared shaped document view. */
public final class EditorTextAreaUI extends RSyntaxTextAreaUI {
    private final Supplier<TerminalTypography> typography;
    public EditorTextAreaUI(RSyntaxTextArea area, Supplier<TerminalTypography> typography) {
        super(area); this.typography=typography;
    }
    @Override protected javax.swing.text.Highlighter createHighlighter() { return new EditorTextView.ShapedHighlighter(); }
    @Override public View create(Element element) { return new EditorTextView(element,typography); }
}
