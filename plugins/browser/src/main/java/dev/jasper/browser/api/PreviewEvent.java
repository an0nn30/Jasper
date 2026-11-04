package dev.jasper.browser.api;

import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalDouble;

/** What a {@link MarkdownPreview} reports, on the UI thread. Lengths are CSS pixels from the top of the document. */
public sealed interface PreviewEvent {
    /**
     * A document is in the page and laid out.
     *
     * @param revision the revision {@link MarkdownPreview#show} gave it
     */
    record Shown(long revision) implements PreviewEvent { }

    /**
     * Where a document's blocks are laid out: after {@link Shown}, and again whenever the layout changes, as when a
     * diagram, an image or a font arrives.
     */
    final class Geometry implements PreviewEvent {
        private final long revision;
        private final double[] tops;
        private final double height, viewport;

        /**
         * The layout of a document's blocks.
         *
         * @param revision the document's revision
         * @param tops each block's top, in the order of the document's {@code data-b} numbers; NaN for a block with no
         *     box; copied
         * @param height the document's height
         * @param viewport the height of the view
         */
        public Geometry(long revision, double[] tops, double height, double viewport) {
            this.revision = revision;
            this.tops = Objects.requireNonNull(tops, "tops").clone();
            this.height = height;
            this.viewport = viewport;
        }

        /**
         * The document's revision.
         *
         * @return the revision {@link MarkdownPreview#show} gave it
         */
        public long revision() { return revision; }

        /**
         * How many blocks the document has.
         *
         * @return the number of blocks
         */
        public int blocks() { return tops.length; }

        /**
         * Where a block's top is.
         *
         * @param block the block's {@code data-b} number
         * @return its top; empty for a block with no box, and for a number outside {@code 0} to {@code blocks() - 1}
         */
        public OptionalDouble top(int block) {
            if (block < 0 || block >= tops.length || Double.isNaN(tops[block])) return OptionalDouble.empty();
            return OptionalDouble.of(tops[block]);
        }

        /**
         * The document's height.
         *
         * @return the height
         */
        public double height() { return height; }

        /**
         * The height of the view.
         *
         * @return the height
         */
        public double viewport() { return viewport; }

        @Override public boolean equals(Object other) {
            return other instanceof Geometry that && revision == that.revision && Arrays.equals(tops, that.tops)
                && Double.compare(height, that.height) == 0 && Double.compare(viewport, that.viewport) == 0;
        }

        @Override public int hashCode() {
            return Objects.hash(revision, Arrays.hashCode(tops), height, viewport);
        }

        @Override public String toString() {
            return "Geometry[revision=" + revision + ", blocks=" + tops.length + ", height=" + height + ", viewport=" + viewport + "]";
        }
    }

    /**
     * The page scrolled.
     *
     * @param y the top of the view
     * @param user whether the user scrolled it; false for the scrolling {@link MarkdownPreview#scrollTo} asked for
     */
    record Scrolled(double y, boolean user) implements PreviewEvent { }

    /**
     * The user clicked a link of the document, or pressed Enter on it. The page goes nowhere.
     *
     * @param id the link's {@code jasper-link:} id
     */
    record LinkActivated(int id) implements PreviewEvent { }

    /**
     * Chromium cannot draw the preview, or has stopped: it shows nothing more for the rest of Jasper's run.
     *
     * @param reason why, as the user reads it
     */
    record Unavailable(String reason) implements PreviewEvent {
        /** Rejects a null reason. */
        public Unavailable { Objects.requireNonNull(reason, "reason"); }
    }
}
