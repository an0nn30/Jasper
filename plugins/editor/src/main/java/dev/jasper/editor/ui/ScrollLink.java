package dev.jasper.editor.ui;

import dev.jasper.editor.markdown.MarkdownResult;
import dev.jasper.sdk.Subscription;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.function.Consumer;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Position;

/** EDT-owned correspondence between source passages and the currently attached preview. */
final class ScrollLink implements AutoCloseable {
    /** Where a pane shows its document, in pixels from the document's top, and how that changes. */
    interface Scroller {
        /** @return the top of what the pane shows */
        int y();
        /** @return the largest {@link #y()}: the document's height less the pane's, never negative */
        int max();
        /** @return the size of what the pane shows */
        Dimension extent();
        /** @return the size of the whole document */
        Dimension size();
        /** Shows the document from {@code y}. */
        void move(int y);
        /**
         * @param byUser hears each change of the position or the sizes, with whether the user made it
         * @return the registration, which the link closes when it closes
         */
        Subscription onMoved(Consumer<Boolean> byUser);
    }

    /**
     * A scroll pane's viewport: the source's, and the Basic preview's. It tells every change as the user's; the link
     * recognises its own moves by the position it expects them to reach.
     */
    static final class ViewportScroller implements Scroller {
        private final JViewport viewport;
        ViewportScroller(JViewport viewport) { this.viewport = Objects.requireNonNull(viewport); }
        @Override public int y() { return viewport.getViewPosition().y; }
        @Override public int max() { return Math.max(0, viewport.getViewSize().height - viewport.getExtentSize().height); }
        @Override public Dimension extent() { return viewport.getExtentSize(); }
        @Override public Dimension size() { return viewport.getViewSize(); }
        @Override public void move(int y) { viewport.setViewPosition(new Point(viewport.getViewPosition().x, y)); }
        @Override public Subscription onMoved(Consumer<Boolean> byUser) {
            ChangeListener listener = event -> byUser.accept(true);
            viewport.addChangeListener(listener);
            return () -> viewport.removeChangeListener(listener);
        }
        /** Sizes the viewport's view to the viewport, so positions read from it are current. */
        void layout() { viewport.doLayout(); }
    }

    private final DocumentArea area;
    private final PreviewSurface surface;
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
    private boolean linked, split, closed, adjusting, queued, replacing;
    private Pane leader;
    private Integer replacementY;
    private long generation, epoch, adjustmentToken, geometryRevision;
    /** A source passage and the preview block it starts; a null block is the end of both documents. */
    private record Knot(Position source, String blockId) { }
    private record Geometry(int offset, double sourceY, double previewY) { }
    private record Reading(Position start, Position end, double fraction, boolean bottom) { }

    /**
     * @param surface the preview the source is linked with
     * @param linked whether the panes follow each other while Split shows both
     */
    ScrollLink(DocumentArea area, PreviewSurface surface, boolean linked) {
        this.area = area; this.surface = surface; this.linked = linked;
        source = new Pane(new ViewportScroller(area.scroll.getViewport())); rendered = new Pane(surface.scroller()); leader = source;
        area.area.getDocument().addDocumentListener(edits);
        area.area.addComponentListener(resize);
        area.area.addPropertyChangeListener("font", font);
        area.area.getFoldManager().addPropertyChangeListener(folds);
        surface.beforeReplace(() -> { replacementY = rendered.y(); replacing = true; });
    }

    /** Only a controller-validated, attached result may supply the next correspondence. */
    void install(long generation, MarkdownResult result) {
        if (closed || generation < this.generation || surface.installedResult().orElse(null) != result || surface.installing()) return;
        this.generation = generation; this.result = result; epoch++; queued = false;
        int length = area.area.getDocument().getLength();
        var built = new ArrayList<Knot>();
        // In source order; blocks starting together stay in the result's order, and the first the preview has stands for them.
        result.blocks().stream().filter(block -> block.sourceStart() < length)
            .sorted(Comparator.comparingInt(MarkdownResult.BlockAnchor::sourceStart))
            .forEach(block -> built.add(new Knot(position(block.sourceStart()), block.id())));
        built.add(new Knot(position(length), null));
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

    /** Coalesce geometry changes and bound each correspondence-building EDT turn. */
    void geometryChanged() {
        if (closed || result == null) return;
        geometryRevision++;
        if (queued) return;
        queued = true;
        rebuild(epoch, generation, source.sequence, rendered.sequence);
    }
    private void rebuild(long token, long installedGeneration, long sourceSequence, long previewSequence) {
        SwingUtilities.invokeLater(() -> {
            if (closed || token != epoch || installedGeneration != generation) return;
            adjusting = true;
            try { source.layout(); rendered.layout(); }
            finally { adjusting = false; }
            geometry = List.of();
            new Rebuild(token, installedGeneration, geometryRevision, sourceSequence, previewSequence).step();
        });
    }

    private final class Rebuild {
        private final long token, installedGeneration, revision, sourceSequence, previewSequence;
        private final TreeMap<Integer,Integer> collapsed = new TreeMap<>();
        private final ArrayList<Geometry> next = new ArrayList<>();
        private int index, resolved = -1;
        private double previousSource, previousPreview;
        Rebuild(long token, long installedGeneration, long revision, long sourceSequence, long previewSequence) {
            this.token=token; this.installedGeneration=installedGeneration; this.revision=revision;
            this.sourceSequence=sourceSequence; this.previewSequence=previewSequence;
            if (area.area.isCodeFoldingEnabled()) {
                var manager=area.area.getFoldManager();
                for(int i=0;i<manager.getFoldCount();i++)collapsed(manager.getFold(i),collapsed);
            }
        }
        void step() {
            if(closed || token!=epoch || installedGeneration!=generation) return;
            if(revision!=geometryRevision) {
                // A no-map user scroll cannot update its reading; retain the original restoration guards.
                rebuild(token,installedGeneration,sourceSequence,previewSequence);return;
            }
            long deadline=System.nanoTime()+4_000_000L;
            for(int count=0;index<knots.size() && count<128;count++,index++) {
                var knot=knots.get(index);
                int offset=knot.source.getOffset(),visible=offset;
                if(offset==resolved)continue;
                var fold=collapsed.floorEntry(offset);
                if(fold!=null && offset<=fold.getValue())visible=fold.getKey();
                try {
                    var sourceBounds=area.area.modelToView2D(visible);
                    var previewY=previewY(knot);
                    if(sourceBounds!=null && previewY.isPresent()) {
                        previousSource=Math.max(previousSource,sourceBounds.getY());
                        previousPreview=Math.max(previousPreview,previewY.getAsDouble());
                        next.add(new Geometry(offset,previousSource,previousPreview));
                        resolved=offset;
                    }
                } catch(BadLocationException impossible) {throw new IllegalStateException(impossible);}
                if(System.nanoTime()>=deadline){index++;break;}
            }
            if(index<knots.size()){SwingUtilities.invokeLater(this::step);return;}
            queued=false;geometry=List.copyOf(next);adjusting=true;
            try {
                restoreIfCurrent(source,sourceSequence);restoreIfCurrent(rendered,previewSequence);
                replacementY=null;
                if(linked && split)align();
                remember(source);remember(rendered);
                source.savedSequence=source.sequence;rendered.savedSequence=rendered.sequence;
            } finally {adjusting=false;}
        }
    }
    private OptionalDouble previewY(Knot knot) {
        if (knot.blockId != null) return surface.blockY(knot.blockId);
        double end = surface.endY();
        return Double.isNaN(end) ? OptionalDouble.empty() : OptionalDouble.of(end);
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
        Dimension extent = pane.scroller.extent(), size = pane.scroller.size();
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
        pane.scroller.move(target);
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
        source.moves.close(); rendered.moves.close();
        area.area.getDocument().removeDocumentListener(edits); area.area.removeComponentListener(resize);
        area.area.removePropertyChangeListener("font",font); area.area.getFoldManager().removePropertyChangeListener(folds);
        surface.beforeReplace(()->{});
    }
    private final class Pane {
        final Scroller scroller;
        final Subscription moves;
        int lastY;
        Integer expected;
        long adjustment, sequence, savedSequence;
        Dimension extent, size;
        Reading reading;
        Pane(Scroller scroller) {
            this.scroller=scroller; lastY=y(); extent=scroller.extent(); size=scroller.size();
            moves=scroller.onMoved(byUser -> moved(this));
        }
        int y() { return scroller.y(); }
        int max() { return scroller.max(); }
        /** A viewport's view takes the viewport's size first; a surface that lays itself out needs nothing. */
        void layout() { if (scroller instanceof ViewportScroller viewport) viewport.layout(); }
    }
}
