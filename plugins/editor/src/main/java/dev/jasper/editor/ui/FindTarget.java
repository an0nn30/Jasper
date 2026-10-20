package dev.jasper.editor.ui;

import java.util.Objects;
import javax.swing.text.JTextComponent;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

/** The content pane captured when Find opens, retained while the query owns focus. */
public sealed interface FindTarget {
    /** A source editor using RSyntaxTextArea's existing search engine. */
    record Source(RSyntaxTextArea area) implements FindTarget {
        public Source { Objects.requireNonNull(area); }
    }
    /** A rendered Markdown document. */
    record Preview(PreviewPane pane) implements FindTarget {
        public Preview { Objects.requireNonNull(pane); }
    }
    default JTextComponent content() {
        return switch (this) { case Source source -> source.area(); case Preview preview -> preview.pane().editor(); };
    }
    default boolean available() {
        return switch (this) {
            case Source source -> !Boolean.TRUE.equals(source.area().getClientProperty("jasper.editor.closed"));
            case Preview preview -> !preview.pane().closed();
        };
    }
}
