package dev.jasper.editor.ui;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

/** The content pane captured when Find opens, retained while the query owns focus. */
public sealed interface FindTarget {
    /** A source editor using RSyntaxTextArea's existing search engine. */
    record Source(RSyntaxTextArea area) implements FindTarget {
        public Source { Objects.requireNonNull(area); }
    }
    /** A rendered Markdown document. */
    record Preview(PreviewSurface surface) implements FindTarget {
        public Preview { Objects.requireNonNull(surface); }
    }
    default boolean available() {
        return switch (this) {
            case Source source -> !Boolean.TRUE.equals(source.area().getClientProperty("jasper.editor.closed"));
            case Preview preview -> !preview.surface().closed();
        };
    }
    /** Gives the pane the keyboard focus. */
    default void focus() {
        switch (this) {
            case Source source -> source.area().requestFocusInWindow();
            case Preview preview -> preview.surface().focus();
        }
    }
    /** @return the pane's selected text, or the empty text when nothing is selected */
    default CompletableFuture<String> selectedText() {
        return switch (this) {
            case Source source -> CompletableFuture.completedFuture(Objects.requireNonNullElse(source.area().getSelectedText(), ""));
            case Preview preview -> preview.surface().selectedText();
        };
    }
    /** @return what the target searches: the source's text area or the preview's surface */
    default Object owner() {
        return switch (this) { case Source source -> source.area(); case Preview preview -> preview.surface(); };
    }
}
