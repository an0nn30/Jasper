package dev.jasper.editor.testsupport;

import dev.jasper.browser.api.MarkdownPreview;
import dev.jasper.browser.api.MarkdownPreviews;
import dev.jasper.browser.api.PreviewEvent;
import dev.jasper.browser.api.PreviewFind;
import dev.jasper.browser.api.PreviewTheme;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.swing.JComponent;

/**
 * The Browser's Markdown previews without Chromium: each preview it makes records what it is asked, and a test fires the
 * events Chromium's page would. UI thread, as the real service is.
 */
public final class FakeMarkdownPreviews implements MarkdownPreviews {
    private final List<Recording> made = new ArrayList<>();
    private boolean available = true;

    /** @param value what {@link #available()} answers from now on */
    public void available(boolean value) { available = value; }

    @Override public boolean available() { return available; }

    @Override public MarkdownPreview create(Consumer<PreviewEvent> events) {
        var preview = new Recording(events);
        made.add(preview);
        return preview;
    }

    /** @return every preview made, the first first */
    public List<Recording> made() { return List.copyOf(made); }

    /** @return the preview made last */
    public Recording last() { return made.getLast(); }

    /**
     * A preview that records each call as a line, such as {@code show 2} or {@code imageReason 0 Loading image}, keeps
     * the themes and documents it was given, and tells its events once a test fires them, until it closes.
     */
    public static final class Recording implements MarkdownPreview {
        private final Consumer<PreviewEvent> events;
        private final JComponent component = new JComponent() { };
        private final List<String> calls = new ArrayList<>();
        private final List<PreviewTheme> themes = new ArrayList<>();
        private final Map<Long, String> documents = new HashMap<>();
        private final Map<Integer, BufferedImage> images = new HashMap<>();
        private boolean closed;

        Recording(Consumer<PreviewEvent> events) { this.events = Objects.requireNonNull(events); }

        /** Tells the preview's caller {@code event}, as Chromium's page would; nothing once the preview has closed. */
        public void fire(PreviewEvent event) { if (!closed) events.accept(event); }

        /** @return each call so far, in order */
        public List<String> calls() { return List.copyOf(calls); }

        /** @return each theme given, in order */
        public List<PreviewTheme> themes() { return List.copyOf(themes); }

        /** @return the HTML {@link #show} gave {@code revision}, or null */
        public String document(long revision) { return documents.get(revision); }

        /** @return the image given for {@code id} and not cleared since, or null */
        public BufferedImage image(int id) { return images.get(id); }

        /** @return whether the preview has closed */
        public boolean closed() { return closed; }

        private void call(String line) { if (!closed) calls.add(line); }

        @Override public JComponent component() { return component; }
        @Override public void theme(PreviewTheme theme) { call("theme"); if (!closed) themes.add(theme); }
        @Override public void show(long revision, String html) { call("show " + revision); if (!closed) documents.put(revision, html); }
        @Override public void image(int id, BufferedImage image) { call("image " + id); if (!closed) images.put(id, image); }
        @Override public void imageReason(int id, String reason) { call("imageReason " + id + " " + reason); }
        @Override public void clearImages() { call("clearImages"); images.clear(); }
        @Override public void scrollTo(double y) { call("scrollTo " + y); }
        @Override public void reveal(String elementId) { call("reveal " + elementId); }
        @Override public CompletableFuture<Integer> markAll(PreviewFind find) { call("markAll " + find.query()); return CompletableFuture.completedFuture(0); }
        @Override public CompletableFuture<Boolean> select(PreviewFind find, boolean forward) {
            call("select " + find.query() + " " + forward);
            return CompletableFuture.completedFuture(false);
        }
        @Override public void clearFind() { call("clearFind"); }
        @Override public CompletableFuture<String> selectedText() { call("selectedText"); return CompletableFuture.completedFuture(""); }
        @Override public void copy() { call("copy"); }
        @Override public void selectAll() { call("selectAll"); }
        @Override public void focus() { call("focus"); }
        @Override public void close() { call("close"); closed = true; }
    }
}
