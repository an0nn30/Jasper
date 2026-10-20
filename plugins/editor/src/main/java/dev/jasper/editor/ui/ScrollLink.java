package dev.jasper.editor.ui;

import dev.jasper.editor.markdown.MarkdownResult;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Position;

/** EDT-owned correspondence between source passages and the currently attached preview. */
final class ScrollLink implements AutoCloseable {
    private final DocumentArea area;
    private final PreviewPane preview;
    private final Pane source, rendered;
    private final DocumentListener edits = new DocumentListener() {
        @Override public void insertUpdate(DocumentEvent e) { invalidate(); }
        @Override public void removeUpdate(DocumentEvent e) { invalidate(); }
        @Override public void changedUpdate(DocumentEvent e) { invalidate(); }
    };
    private final ComponentAdapter resize = new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent e) { geometryChanged(); }
    };
    private final PropertyChangeListener folds = e -> geometryChanged();
    private final PropertyChangeListener font = e -> geometryChanged();
    private List<Knot> knots = List.of();
    private List<Geometry> geometry = List.of();
    private MarkdownResult result;
    private boolean linked = true, split, closed, adjusting, queued, replacing;
    private Pane leader;
    private Integer replacementY;
    private long generation, epoch, adjustmentToken;
    private record Knot(Position position, int previewOffset) { }
    private record Geometry(int offset, double sourceY, double previewY) { }
    private record Reading(Position start, Position end, double fraction, boolean bottom) { }

    ScrollLink(DocumentArea area, PreviewPane preview) {
        this.area = area; this.preview = preview;
        source = new Pane(area.scroll.getViewport()); rendered = new Pane(area.previewScroll.getViewport()); leader = source;
        area.area.getDocument().addDocumentListener(edits);
        area.area.addComponentListener(resize);
        area.area.addPropertyChangeListener("font", font);
        area.area.getFoldManager().addPropertyChangeListener(folds);
        preview.beforeReplace = () -> { replacementY = rendered.y(); replacing = true; };
    }

    /** Only a controller-validated, attached result may supply the next correspondence. */
    void install(long generation, MarkdownResult result) {
        if (closed || generation < this.generation || preview.installedResult().orElse(null) != result || preview.installing()) return;
        this.generation = generation; this.result = result; epoch++; queued = false;
        var next = new TreeMap<Integer, Integer>();
        for (var block : result.blocks()) {
            var offset = preview.anchor(block.id());
            if (offset.isPresent()) next.putIfAbsent(block.sourceStart(), offset.getAsInt());
        }
        next.put(area.area.getDocument().getLength(), preview.editor().getDocument().getLength());
        var built = new ArrayList<Knot>();
        next.forEach((offset, target) -> built.add(new Knot(position(offset), target)));
        knots = List.copyOf(built);
        // setDocument/caret reset belongs to replacement, not to a new user scroll.
        rendered.lastY = rendered.y(); rendered.expected = rendered.y(); replacing = false;
        geometryChanged();
    }

    /** Positions were captured while the old geometry was current, before document listeners run. */
    void invalidate() {
        if (closed) return;
        if (result != null) { source.savedSequence = source.sequence; rendered.savedSequence = rendered.sequence; }
        result = null; knots = List.of(); geometry = List.of(); epoch++; queued = false; replacing = false;
    }

    void linked(boolean value) {
        if (closed || linked == value) return;
        linked = value;
        if (value && split && !geometry.isEmpty()) align();
    }

    void split(boolean value) {
        split = value;
        if (value && linked) { leader = source; source.reading = null; geometryChanged(); }
    }

    /** Coalesce image, font, fold and viewport changes; no Markdown parsing occurs here. */
    void geometryChanged() {
        if (closed || result == null || queued) return;
        queued = true;
        long token = epoch, installedGeneration = generation;
        long sourceSequence = source.sequence, previewSequence = rendered.sequence;
        SwingUtilities.invokeLater(() -> {
            if (closed || token != epoch || installedGeneration != generation) return;
            queued = false;
            adjusting = true;
            try {
                area.scroll.getViewport().doLayout(); area.previewScroll.getViewport().doLayout();
                rebuild();
                restoreIfCurrent(source, sourceSequence);
                restoreIfCurrent(rendered, previewSequence);
                replacementY = null;
                if (linked && split) align();
                remember(source); remember(rendered);
                source.savedSequence = source.sequence; rendered.savedSequence = rendered.sequence;
            } finally { adjusting = false; }
        });
    }

    private void rebuild() {
        var collapsed = new TreeMap<Integer, Integer>();
        if (area.area.isCodeFoldingEnabled()) {
            var manager = area.area.getFoldManager();
            for (int i=0;i<manager.getFoldCount();i++) collapsed(manager.getFold(i), collapsed);
        }
        var next = new ArrayList<Geometry>();
        double previousSource = 0, previousPreview = 0;
        for (var knot : knots) {
            int offset = knot.position.getOffset(), visible = offset;
            var fold = collapsed.floorEntry(offset);
            if (fold != null && offset <= fold.getValue()) visible = fold.getKey();
            try {
                var s = area.area.modelToView2D(visible);
                var p = preview.editor().modelToView2D(knot.previewOffset);
                if (s == null || p == null) continue;
                // A nested container marker may share a row with its first child.
                previousSource = Math.max(previousSource, s.getY());
                previousPreview = Math.max(previousPreview, p.getY());
                next.add(new Geometry(offset, previousSource, previousPreview));
            } catch (BadLocationException impossible) { throw new IllegalStateException(impossible); }
        }
        geometry = List.copyOf(next);
    }
    private void collapsed(org.fife.ui.rsyntaxtextarea.folding.Fold fold, TreeMap<Integer,Integer> target) {
        if (fold.isCollapsed()) {
            try { target.put(area.area.getLineStartOffset(fold.getStartLine()), fold.getEndOffset()); }
            catch (BadLocationException impossible) { throw new IllegalStateException(impossible); }
        }
        else for (int i=0;i<fold.getChildCount();i++) collapsed(fold.getChild(i), target);
    }
    private void moved(Pane pane) {
        int y = pane.y();
        Dimension extent = pane.viewport.getExtentSize(), size = pane.viewport.getViewSize();
        boolean resized = !extent.equals(pane.extent) || !size.equals(pane.size);
        pane.extent = extent; pane.size = size;
        if (resized) geometryChanged();
        if (y == pane.lastY) return;
        pane.lastY = y;
        if (adjusting || replacing || pane.expected != null && pane.expected == y) return;
        pane.expected = null;
        if (resized) return; // Layout clamping is not user motion.
        leader = pane; pane.sequence++;
        if (result == null) return;
        pane.savedSequence = pane.sequence;
        remember(pane);
        if (linked && split) align();
    }
    private void align() {
        if (geometry.isEmpty()) return;
        Pane follower = leader == source ? rendered : source;
        if (leader.y() == 0) { move(follower, 0); return; }
        if (leader.max() > 0 && leader.y() >= leader.max()-1) { move(follower, follower.max()); return; }
        int index = bracket(leader.y(), leader == source, false);
        var low = geometry.get(index); var high = geometry.get(nextRow(index, leader == source));
        double from = coordinate(low, leader == source), to = coordinate(high, leader == source);
        double fraction = fraction(leader.y(), from, to);
        move(follower, coordinate(low, follower == source) + fraction * (coordinate(high, follower == source)-coordinate(low, follower == source)));
        remember(follower);
    }
    private void remember(Pane pane) {
        if (geometry.isEmpty()) return;
        int index = bracket(pane.y(), pane == source, false);
        var low = geometry.get(index); var high = geometry.get(nextRow(index, pane == source));
        pane.reading = new Reading(position(low.offset), position(high.offset),
            fraction(pane.y(), coordinate(low,pane==source), coordinate(high,pane==source)), pane.max()>0 && pane.y()>=pane.max()-1);
    }
    private void restoreIfCurrent(Pane pane, long sequence) {
        if (sequence != pane.sequence) return;
        if (pane.savedSequence == pane.sequence) restore(pane);
        else if (pane == rendered && replacementY != null) move(pane, replacementY);
    }
    private void restore(Pane pane) {
        var reading = pane.reading;
        if (reading == null || geometry.isEmpty()) return;
        if (reading.bottom) { move(pane, pane.max()); return; }
        double from = mappedOffset(reading.start.getOffset(), pane == source);
        double to = mappedOffset(reading.end.getOffset(), pane == source);
        move(pane, from + reading.fraction * (to-from));
    }
    private double mappedOffset(int offset, boolean source) {
        int index = bracket(offset, false, true);
        var low = geometry.get(index); var high = geometry.get(Math.min(index+1,geometry.size()-1));
        double fraction = fraction(offset, low.offset, high.offset);
        return coordinate(low,source) + fraction*(coordinate(high,source)-coordinate(low,source));
    }
    private int bracket(double value, boolean source, boolean offsets) {
        int low=0, high=geometry.size();
        while (low<high) {
            int middle=(low+high)>>>1; var point=geometry.get(middle);
            if ((offsets ? point.offset : coordinate(point,source)) <= value) low=middle+1; else high=middle;
        }
        int index=Math.max(0,low-1);
        // All hidden source offsets use the visible fold start's preview passage.
        if (!offsets) {
            double row=coordinate(geometry.get(index),source);
            low=0; high=index;
            while (low<high) {
                int middle=(low+high)>>>1;
                if (coordinate(geometry.get(middle),source)<row) low=middle+1; else high=middle;
            }
            index=low;
        }
        return index;
    }
    private int nextRow(int index, boolean source) {
        double row=coordinate(geometry.get(index),source);
        int low=index+1, high=geometry.size();
        while (low<high) {
            int middle=(low+high)>>>1;
            if (coordinate(geometry.get(middle),source)<=row) low=middle+1; else high=middle;
        }
        return Math.min(low,geometry.size()-1);
    }
    private static double coordinate(Geometry point, boolean source) { return source ? point.sourceY : point.previewY; }
    private static double fraction(double value, double from, double to) { return to<=from ? 0 : Math.max(0,Math.min(1,(value-from)/(to-from))); }
    private Position position(int offset) {
        try { return area.area.getDocument().createPosition(Math.max(0,Math.min(offset,area.area.getDocument().getLength()))); }
        catch (BadLocationException impossible) { throw new IllegalStateException(impossible); }
    }
    private void move(Pane pane, double y) {
        int target=(int)Math.round(Math.max(0,Math.min(pane.max(),y)));
        pane.expected=target; long token=++adjustmentToken; pane.adjustment=token;
        pane.viewport.setViewPosition(new Point(pane.viewport.getViewPosition().x,target));
        pane.lastY=pane.y();
        // Keep the expected value across event turns; repeated delayed events cannot take leadership.
        long mapEpoch=epoch;
        SwingUtilities.invokeLater(() -> {
            if (!closed && mapEpoch==epoch && pane.adjustment==token && pane.y()==target) remember(pane);
        });
    }
    @Override public void close() {
        if (closed) return;
        invalidate(); closed=true;
        source.viewport.removeChangeListener(source.listener); rendered.viewport.removeChangeListener(rendered.listener);
        area.area.getDocument().removeDocumentListener(edits); area.area.removeComponentListener(resize);
        area.area.removePropertyChangeListener("font",font); area.area.getFoldManager().removePropertyChangeListener(folds);
        preview.beforeReplace=()->{};
    }
    private final class Pane {
        final JViewport viewport;
        final ChangeListener listener = e -> moved(this);
        int lastY;
        Integer expected;
        long adjustment, sequence, savedSequence;
        Dimension extent, size;
        Reading reading;
        Pane(JViewport viewport) {
            this.viewport=viewport; lastY=y(); extent=viewport.getExtentSize(); size=viewport.getViewSize();
            viewport.addChangeListener(listener);
        }
        int y() { return viewport.getViewPosition().y; }
        int max() { return Math.max(0,viewport.getViewSize().height-viewport.getExtentSize().height); }
    }
}
