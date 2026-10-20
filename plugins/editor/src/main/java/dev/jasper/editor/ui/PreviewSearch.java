package dev.jasper.editor.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import org.fife.ui.rsyntaxtextarea.RSyntaxUtilities;
import org.fife.ui.rtextarea.SearchContext;

/** Find over the installed Swing document's visible text, with source Find's option semantics. */
final class PreviewSearch {
    private record Match(int start, int end) { }
    private final PreviewPane pane;
    private final List<Object> tags = new ArrayList<>();
    PreviewSearch(PreviewPane pane) { this.pane = pane; }

    int markAll(SearchContext context) {
        clear();
        var matches = matches(context);
        var editor = pane.editor();
        var painter = new DefaultHighlighter.DefaultHighlightPainter(editor.getSelectionColor());
        try { for (var match : matches) tags.add(editor.getHighlighter().addHighlight(match.start, match.end, painter)); }
        catch (BadLocationException impossible) { throw new IllegalStateException(impossible); }
        return matches.size();
    }
    boolean find(SearchContext context) {
        var matches = matches(context);
        if (matches.isEmpty()) return false;
        var editor = pane.editor();
        boolean forward = context.getSearchForward();
        int from = forward ? editor.getSelectionEnd() : editor.getSelectionStart();
        Match selected = null;
        if (forward) {
            for (var match : matches) if (match.start >= from) { selected = match; break; }
            if (selected == null && context.getSearchWrap()) selected = matches.getFirst();
        } else {
            for (var match : matches) if (match.end <= from) selected = match;
            if (selected == null && context.getSearchWrap()) selected = matches.getLast();
        }
        if (selected == null) return false;
        editor.select(selected.start, selected.end);
        try {
            var bounds = editor.modelToView2D(selected.start);
            if (bounds != null) editor.scrollRectToVisible(bounds.getBounds());
        } catch (BadLocationException impossible) { throw new IllegalStateException(impossible); }
        return true;
    }
    void clear() {
        tags.forEach(pane.editor().getHighlighter()::removeHighlight);
        tags.clear();
    }
    private List<Match> matches(SearchContext context) {
        var result = new ArrayList<Match>();
        String query = context.getSearchFor();
        if (pane.closed() || query == null || query.isEmpty()) return result;
        var doc = pane.editor().getDocument();
        final String text;
        try { text = doc.getText(0, doc.getLength()); }
        catch (BadLocationException impossible) { throw new IllegalStateException(impossible); }
        if (context.isRegularExpression()) {
            String expression = context.getWholeWord() ? "\\b" + query + "\\b" : query;
            var matcher = Pattern.compile(expression, RSyntaxUtilities.getPatternFlags(context.getMatchCase(), Pattern.MULTILINE)).matcher(text);
            // Matcher.find advances after an empty match. Source mark-all likewise omits empty ranges.
            while (matcher.find()) if (matcher.start() != matcher.end()) result.add(new Match(matcher.start(), matcher.end()));
        } else {
            // The literal Source path uses String case folding and Character.isLetterOrDigit boundaries.
            // Fold once and scan without allocating a suffix for every occurrence.
            String haystack = context.getMatchCase() ? text : text.toLowerCase();
            String needle = context.getMatchCase() ? query : query.toLowerCase();
            int start = 0;
            while (start <= haystack.length() - needle.length()) {
                int offset = haystack.indexOf(needle, start);
                if (offset < 0) break;
                int end = offset + needle.length();
                boolean word = !context.getWholeWord()
                    || (offset == 0 || !Character.isLetterOrDigit(haystack.charAt(offset - 1)))
                    && (end == haystack.length() || !Character.isLetterOrDigit(haystack.charAt(end)));
                // Preserve valid document offsets even when locale-sensitive folding changes length.
                if (word && end <= text.length()) result.add(new Match(offset, end));
                start = word ? end : offset + 1;
            }
        }
        return result;
    }
}
