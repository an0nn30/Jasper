package dev.jasper.editor.ui;

import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.vim.VimTextArea;
import java.util.ArrayList;
import java.util.List;

/**
 * The views of one document (pane flexibility spec 6): their text areas show one Swing document with one undo history,
 * and the session reads its text from them and replaces it once for all of them, each view keeping its own caret line.
 * UI thread only.
 */
final class TextViews {
    private final List<EditorTab> views = new ArrayList<>();
    /** The text the last view showed when it closed, which the session may still read. */
    private String detached = "";

    /** @param session the document's session, which from now on reads and replaces the text through these views */
    TextViews(DocumentSession session) { session.attach(this::text, this::replace); }

    /** @param view a view whose text area shows the document */
    void add(EditorTab view) { views.add(view); }

    /** @return how many views show the document */
    int size() { return views.size(); }

    /**
     * {@code view} has closed: the other views keep the document and its history, and its text area lets go of them.
     *
     * @param view one of the views
     */
    void remove(EditorTab view) {
        if (!views.remove(view)) return;
        if (views.isEmpty()) detached = view.area().getText();
        else view.area().unshare();
    }

    private String text() { return views.isEmpty() ? detached : views.getFirst().area().getText(); }

    /** A load or reload: the text is set once, which every view shows, and the one history starts again. */
    private void replace(String text, boolean keepPosition) {
        if (views.isEmpty()) {
            detached = text;
            return;
        }
        List<EditorTab> open = List.copyOf(views);
        int[] lines = new int[open.size()];
        for (int i = 0; i < lines.length; i++) lines[i] = open.get(i).beginReplace(keepPosition);
        VimTextArea area = open.getFirst().area();
        try { area.setText(text); }
        finally { for (EditorTab view : open) view.endReplace(); }
        area.discardAllEdits();
        for (int i = 0; i < lines.length; i++) open.get(i).replaced(lines[i]);
    }
}
