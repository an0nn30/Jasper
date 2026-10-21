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
    // ConfigurableCaret calls this legacy no-bias overload even when DefaultCaret holds a weak bidi hit.
    @Override @SuppressWarnings("deprecation")
    public java.awt.Rectangle modelToView(javax.swing.text.JTextComponent component,int offset)
            throws javax.swing.text.BadLocationException {
        var bias=javax.swing.text.Position.Bias.Forward;
        if(component.getCaret() instanceof javax.swing.text.DefaultCaret caret && caret.getDot()==offset)
            bias=caret.getDotBias();
        var bounds=super.modelToView(component,offset,bias);
        if(bounds!=null && component.getCaret() instanceof org.fife.ui.rtextarea.ConfigurableCaret caret) {
            switch(caret.getStyle()) {
                case BLOCK_STYLE, BLOCK_BORDER_STYLE, UNDERLINE_STYLE -> {
                    var view=(EditorTextView)getRootView(component).getView(0);
                    return view.cellBounds(offset,getVisibleEditorRect(),bias).getBounds();
                }
                default -> { }
            }
        }
        return bounds;
    }
    @Override protected javax.swing.text.Highlighter createHighlighter() { return new EditorTextView.ShapedHighlighter(); }
    @Override public View create(Element element) { return new EditorTextView(element,typography); }
}
