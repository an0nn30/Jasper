package dev.jasper.editor.testsupport;

import dev.jasper.browser.api.MarkdownPreview;
import dev.jasper.browser.api.MarkdownPreviews;
import dev.jasper.browser.api.PreviewEvent;
import dev.jasper.browser.api.PreviewFind;
import dev.jasper.browser.api.PreviewTheme;
import java.awt.event.HierarchyEvent;
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
    private String unavailableWhenShown;

    /** @param value what {@link #available()} answers from now on */
    public void available(boolean value) { available = value; }

    /**
     * Previews made from now on report {@code Unavailable(reason)} at once from inside the layout that first shows their
     * canvas, as a page whose preview client has already failed does: it asks for the client when its canvas shows.
     */
    public void unavailableWhenShown(String reason) { unavailableWhenShown = Objects.requireNonNull(reason); }

    @Override public boolean available() { return available; }

    @Override public MarkdownPreview create(Consumer<PreviewEvent> events) {
        var preview = new Recording(events, unavailableWhenShown);
        made.add(preview);
        return preview;
    }

    /** @return every preview made, the first first */
    public List<Recording> made() { return List.copyOf(made); }

    /** @return the preview made last */
    public Recording last() { return made.getLast(); }

    /**
     * A preview that records each call as a line, such as {@code show 2} or {@code imageReason 0 Loading image}, keeps
     * the themes and documents it was given, and tells its events once a test fires them, until it closes. Its canvas
     * holds the focus from {@link #focus()} until the preview closes, as a canvas that took it would. Each Find answer
     * and selection read waits until the test completes its future, in any order, and its close cancels those still
     * waiting; while {@link #ready(boolean)} is false, they answer at once with nothing found, as a page not yet ready
     * does.
     */
    public static final class Recording implements MarkdownPreview {
        private final Consumer<PreviewEvent> events;
        private final JComponent component = new JComponent() {
            @Override public boolean isFocusOwner() { return focused; }
        };
        private final List<String> calls = new ArrayList<>();
        private final List<PreviewTheme> themes = new ArrayList<>();
        private final Map<Long, String> documents = new HashMap<>();
        private final Map<Integer, BufferedImage> images = new HashMap<>();
        private final List<PreviewFind> finds = new ArrayList<>();
        private final List<CompletableFuture<Integer>> marks = new ArrayList<>();
        private final List<CompletableFuture<Boolean>> steps = new ArrayList<>();
        private final List<CompletableFuture<String>> selections = new ArrayList<>();
        private boolean closed, focused, shown, ready = true;

        Recording(Consumer<PreviewEvent> events, String unavailableWhenShown) {
            this.events = Objects.requireNonNull(events);
            if (unavailableWhenShown != null) component.addHierarchyListener(event -> {
                if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0 || !component.isShowing() || shown) return;
                shown = true;
                fire(new PreviewEvent.Unavailable(unavailableWhenShown));
            });
        }

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

        /**
         * @param value false: from now on, Find and the selection read answer at once with nothing found, as the page
         *     does before it is ready; true: they wait for the test again
         */
        public void ready(boolean value) { ready = value; }

        /** @return what each {@code markAll} and {@code select} looked for, in order */
        public List<PreviewFind> finds() { return List.copyOf(finds); }

        /** @return each future {@code markAll} returned, in order, for the test to complete */
        public List<CompletableFuture<Integer>> marks() { return List.copyOf(marks); }

        /** @return each future {@code select} returned, in order, for the test to complete */
        public List<CompletableFuture<Boolean>> steps() { return List.copyOf(steps); }

        /** @return each future {@code selectedText} returned, in order, for the test to complete */
        public List<CompletableFuture<String>> selections() { return List.copyOf(selections); }

        /** A future a test completes; once closed, one cancelled; before ready, one that holds {@code nothing}. */
        private <T> CompletableFuture<T> answer(List<CompletableFuture<T>> waiting, T nothing) {
            var future = new CompletableFuture<T>();
            if (closed) future.cancel(false);
            else if (!ready) future.complete(nothing);
            else waiting.add(future);
            return future;
        }

        private void call(String line) { if (!closed) calls.add(line); }

        @Override public JComponent component() { return component; }
        @Override public void theme(PreviewTheme theme) { call("theme"); if (!closed) themes.add(theme); }
        @Override public void show(long revision, String html) { call("show " + revision); if (!closed) documents.put(revision, html); }
        @Override public void image(int id, BufferedImage image) { call("image " + id); if (!closed) images.put(id, image); }
        @Override public void imageReason(int id, String reason) { call("imageReason " + id + " " + reason); }
        @Override public void clearImages() { call("clearImages"); images.clear(); }
        @Override public void scrollTo(double y) { call("scrollTo " + y); }
        @Override public void reveal(String elementId) { call("reveal " + elementId); }
        @Override public CompletableFuture<Integer> markAll(PreviewFind find) {
            call("markAll " + find.query());
            if (!closed) finds.add(find);
            return answer(marks, 0);
        }
        @Override public CompletableFuture<Boolean> select(PreviewFind find, boolean forward) {
            call("select " + find.query() + " " + forward);
            if (!closed) finds.add(find);
            return answer(steps, false);
        }
        @Override public void clearFind() { call("clearFind"); }
        @Override public CompletableFuture<String> selectedText() { call("selectedText"); return answer(selections, ""); }
        @Override public void copy() { call("copy"); }
        @Override public void selectAll() { call("selectAll"); }
        @Override public void focus() { call("focus"); if (!closed) focused = true; }
        @Override public void close() {
            call("close");
            closed = true;
            focused = false;
            for (var waiting : List.of(marks, steps, selections)) waiting.forEach(future -> future.cancel(false));
        }
    }
}
