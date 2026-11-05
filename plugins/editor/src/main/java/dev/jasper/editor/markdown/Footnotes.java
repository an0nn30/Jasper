package dev.jasper.editor.markdown;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.commonmark.ext.footnotes.FootnoteDefinition;
import org.commonmark.ext.footnotes.FootnoteReference;
import org.commonmark.node.Block;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
import org.commonmark.node.Node;

/**
 * Numbers footnotes by first reference and lists the definitions a preview shows, for both dialects. The preview shows
 * the document without its definitions, then each referenced definition in number order; a definition that is never
 * referenced is left out.
 */
final class Footnotes {
    private static final Pattern WHITESPACE = Pattern.compile("[ \t\r\n]+");

    /** A shown footnote, numbered from 1, with the fragment names {@link #name} gives it. */
    static final class Note {
        private final int number;
        private final FootnoteDefinition definition;
        private final FootnoteReference first;
        private String id;
        private String referenceId;

        private Note(int number, FootnoteDefinition definition, FootnoteReference first) {
            this.number = number; this.definition = definition; this.first = first;
        }
        int number() { return number; }
        FootnoteDefinition definition() { return definition; }
        /** The reference that numbered this note, the only one that carries {@link #referenceId()}. */
        FootnoteReference first() { return first; }
        /** The definition's fragment name, normally {@code fn-<n>}. */
        String id() { return id; }
        /** The first reference's fragment name, normally {@code fnref-<n>}. */
        String referenceId() { return referenceId; }
    }

    private final Map<String, FootnoteDefinition> definitions = new HashMap<>();
    private final Map<FootnoteDefinition, Note> numbered = new IdentityHashMap<>();
    private final List<Note> notes = new ArrayList<>();
    private final List<Heading> headings = new ArrayList<>();

    /** Numbers the document's footnotes, and lists the headings the preview shows, in the order it shows them. */
    Footnotes(Node document) {
        // As commonmark resolves a reference: the first definition of a label wins.
        walk(document, node -> {
            if (node instanceof FootnoteDefinition definition) definitions.putIfAbsent(label(definition.getLabel()), definition);
            return true;
        });
        Predicate<Node> shown = node -> {
            switch (node) {
                case FootnoteDefinition ignored -> { return false; }
                // An image's children are its alternative text, not shown nodes.
                case Image ignored -> { return false; }
                case FootnoteReference reference -> reference(reference);
                case Heading heading -> headings.add(heading);
                default -> { }
            }
            return true;
        };
        walk(document, shown);
        // A definition can reference a footnote not yet numbered; it joins the end of the list.
        for (int i = 0; i < notes.size(); i++) walk(notes.get(i).definition, shown);
    }

    /** The shown footnotes, in number order. */
    List<Note> notes() { return Collections.unmodifiableList(notes); }

    /** The headings the preview shows, in its order: the document's, then those of the shown footnotes in number order. */
    List<Heading> headings() { return Collections.unmodifiableList(headings); }

    /** The footnote a reference points at; null for a reference to none of the shown footnotes. */
    Note note(FootnoteReference reference) {
        return numbered.get(definitions.get(label(reference.getLabel())));
    }

    /**
     * Names every footnote from the ids that named the headings, after the last heading, so no footnote renames a
     * heading and none clashes with one.
     */
    void name(HeadingIds ids) {
        for (var note : notes) {
            note.id = ids.next("fn-" + note.number);
            note.referenceId = ids.next("fnref-" + note.number);
        }
    }

    private void reference(FootnoteReference reference) {
        var definition = definitions.get(label(reference.getLabel()));
        if (definition == null || numbered.containsKey(definition)) return;
        var note = new Note(notes.size() + 1, definition, reference);
        numbered.put(definition, note);
        notes.add(note);
    }

    /** Matches labels as commonmark does: trimmed, case-folded, and each run of whitespace one space. */
    private static String label(String label) {
        return WHITESPACE.matcher(label.trim().toLowerCase(Locale.ROOT).toUpperCase(Locale.ROOT)).replaceAll(" ");
    }

    /** Visits root's descendants in document order without growing the Java stack; a false visit skips the node's children. */
    private static void walk(Node root, Predicate<Node> visit) {
        Node node = root.getFirstChild();
        while (node != null) {
            if (node instanceof Block) MarkdownRenderer.checkInterrupted();
            Node next = visit.test(node) ? node.getFirstChild() : null;
            if (next == null) {
                next = node;
                while (next != root && next.getNext() == null) next = next.getParent();
                next = next == root ? null : next.getNext();
            }
            node = next;
        }
    }
}
