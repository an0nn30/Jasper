package dev.jasper.browser.preview;

import dev.jasper.browser.api.MarkdownPreview;
import dev.jasper.browser.api.PreviewEvent;
import dev.jasper.browser.api.PreviewFind;
import dev.jasper.browser.api.PreviewTheme;
import dev.jasper.browser.engine.PreviewScheme;
import dev.jasper.browser.json.StrictJson;
import dev.jasper.browser.page.PreviewPage;
import dev.jasper.sdk.Variant;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.regex.PatternSyntaxException;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * One Markdown preview in a page of Chromium's, the API's {@link MarkdownPreview}: the documents, images and theme its
 * caller gives go to its {@link PreviewScheme.Origin} and, as calls, to its page's {@code preview.js}; what the page
 * tells comes back through {@link #received}, read where it arrived, as {@link PreviewEvent}s. It names no Chromium
 * type: its page is a set of {@link Hooks}, which the service makes from a {@link PreviewPage} and a test records. UI
 * thread, as the API says, and every future it returns completes there.
 *
 * <p><b>Before the page is ready</b> (its script has said {@code ready}), calls wait: on {@code ready} the page is sent the
 * theme, then only the latest document, its images, then the last scroll. A Find or a selection read asked before then
 * answers at once with nothing found, and a reveal does nothing, as the page shows no document yet. Thirty seconds after
 * its browser exists ({@link #created}), a page that is not ready, or that has shown no document although one was asked
 * for, is {@link PreviewEvent.Unavailable} (plan 9's N-F15 and N16); the documents after the first it shows have no
 * deadline.
 *
 * <p><b>Documents</b> are encoded on the background executor, one after another, and each skips once a later one is
 * given; the page is asked to show only the latest. The page announces a document it showed; one older than the latest
 * asked for is not told, nor is the layout of any but the document last told shown.
 *
 * <p><b>Images</b> are each encoded once, as PNG on the background executor, and kept while their caller holds them (a
 * {@link WeakHashMap}, whose keys {@code BufferedImage}'s identity equality makes identity keys). Each is served as
 * {@code /image/<key>.png} and set in the page only if its id still wants that image.
 *
 * <p><b>Find</b> runs {@code find.js} in the page (N-F13): each {@code markAll}, {@code select} and {@code selectedText}
 * carries a request id that the page's answer names. A new {@code markAll} or {@code select} cancels the one waiting
 * before it, and a new {@code selectedText} the selection read waiting.
 *
 * <p>After {@link #close()}, or once unavailable, every call does nothing, every future that waits is cancelled, as is any
 * a later call returns, and nothing more is told.
 */
final class PagePreview implements MarkdownPreview {
    /**
     * How long after its browser exists the page has to say it is ready and, once a document is asked for, to show one:
     * long enough for a cold first start of Chromium's renderer, macOS's first check of it, and a loaded machine.
     */
    static final Duration LOAD_WAIT = Duration.ofSeconds(30);
    /** Why a page that was not ready, or showed no document, by then is unavailable. */
    static final String NOT_LOADED = "The Markdown preview's page did not load within 30 seconds.";
    /** The most UTF-8 a document may hold: 16 MiB. */
    static final int MAX_DOCUMENT = 16 << 20;

    /**
     * The page's side of a preview, in plain types: production makes it from a {@link PreviewPage}.
     *
     * @param view the canvas the page is drawn on
     * @param run runs a script in the page's main frame once its browser exists
     * @param background hears the theme's background on each {@link #theme}, for the canvas to paint before the page's
     *     first frame
     * @param copy copies the page's selection
     * @param selectAll selects the whole page
     * @param focus gives the canvas the keyboard focus
     * @param close closes the page
     */
    record Hooks(JComponent view, Consumer<String> run, Consumer<Color> background, Runnable copy, Runnable selectAll,
            Runnable focus, Runnable close) {
        Hooks {
            Objects.requireNonNull(view, "view");
            Objects.requireNonNull(run, "run");
            Objects.requireNonNull(background, "background");
            Objects.requireNonNull(copy, "copy");
            Objects.requireNonNull(selectAll, "selectAll");
            Objects.requireNonNull(focus, "focus");
            Objects.requireNonNull(close, "close");
        }
    }

    /** What a Find request waits for: the count of {@code markAll}, or whether {@code select} selected a match. */
    private enum Kind { MARK, STEP }

    private final PreviewScheme.Origin origin;
    private final Hooks hooks;
    private final Executor background;
    private final Consumer<PreviewEvent> events;
    private final BiFunction<Duration, Runnable, Runnable> later;
    private final List<Runnable> closes = new ArrayList<>();
    // Whether the page is ready, whether the preview closed or is unavailable, and whether the page has shown a document
    // asked for; what stops the page's deadline.
    private boolean ready, closed, unavailable, showedDocument;
    private Runnable watch = () -> { };
    // The theme's call, the last scroll asked for (NaN for none).
    private String themeCall;
    private double scroll = Double.NaN;
    // The last revision given (read by the background executor), the latest the origin holds, and the latest told shown.
    private volatile long requested = -1;
    private long stored = -1, shown = -1;
    // The documents' encoding, one after another.
    private CompletableFuture<Void> documents = CompletableFuture.completedFuture(null);
    // Each image id's image or reason, and the PNG of each image encoded, or being encoded.
    private final Map<Integer, BufferedImage> wanted = new LinkedHashMap<>();
    private final Map<Integer, String> reasons = new LinkedHashMap<>();
    private final Map<BufferedImage, Encoded> encoded = new WeakHashMap<>();
    private final Map<BufferedImage, Boolean> encoding = new WeakHashMap<>();
    private int nextKey;
    // The last request id given; the Find request waiting, its kind and its future; the selection read waiting.
    private long issued, finding, selecting;
    private Kind kind;
    private CompletableFuture<Integer> marking;
    private CompletableFuture<Boolean> stepping;
    private CompletableFuture<String> selection;
    private String findQuery;

    /**
     * A preview on {@code origin}, its page {@code hooks}.
     *
     * @param origin the preview's origin, which it closes
     * @param hooks the page
     * @param background encodes documents and images
     * @param events told what happens in the preview, on the UI thread, until it closes
     */
    PagePreview(PreviewScheme.Origin origin, Hooks hooks, Executor background, Consumer<PreviewEvent> events) {
        this(origin, hooks, background, events, PagePreview::onUiAfter);
    }

    /** As the other constructor, with the UI-thread timer that runs a task after a delay and answers what stops it. */
    PagePreview(PreviewScheme.Origin origin, Hooks hooks, Executor background, Consumer<PreviewEvent> events,
            BiFunction<Duration, Runnable, Runnable> later) {
        this.origin = Objects.requireNonNull(origin, "origin");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        this.background = Objects.requireNonNull(background, "background");
        this.events = Objects.requireNonNull(events, "events");
        this.later = Objects.requireNonNull(later, "later");
    }

    @Override public JComponent component() {
        return hooks.view();
    }

    @Override public void theme(PreviewTheme theme) {
        Objects.requireNonNull(theme, "theme");
        if (done()) return;
        hooks.background().accept(theme.palette().background());
        themeCall = call("theme", StrictJson.quote(PreviewCss.json(PreviewCss.variables(theme))), StrictJson.quote(PreviewCss.mermaid(theme)),
            String.valueOf(theme.variant() == Variant.DARK));
        if (ready) hooks.run().accept(themeCall);
    }

    @Override public void show(long revision, String html) {
        Objects.requireNonNull(html, "html");
        if (done()) return;
        if (revision <= requested) throw new IllegalArgumentException("A preview's revision must be greater than " + requested + ": " + revision);
        if (utf8Length(html) > MAX_DOCUMENT) throw new IllegalArgumentException("A preview's document must be at most 16 MiB of UTF-8");
        requested = revision;
        // One after another, so the origin ends with the latest, and each skips once a later one is given.
        documents = documents.exceptionally(failure -> null).thenRunAsync(() -> {
            if (revision != requested) return;
            origin.document(revision, html);
            EventQueue.invokeLater(() -> stored(revision));
        }, background);
    }

    @Override public void image(int id, BufferedImage image) {
        Objects.requireNonNull(image, "image");
        if (done()) return;
        reasons.remove(id);
        wanted.put(id, image);
        Encoded png = encoded.get(image);
        if (png != null) {
            set(id, png);
            return;
        }
        if (encoding.put(image, Boolean.TRUE) != null) return;
        background.execute(() -> {
            byte[] written = png(image);
            EventQueue.invokeLater(() -> encodedImage(image, written));
        });
    }

    @Override public void imageReason(int id, String reason) {
        Objects.requireNonNull(reason, "reason");
        if (done()) return;
        wanted.remove(id);
        reasons.put(id, reason);
        if (ready) hooks.run().accept(call("imageReason", StrictJson.quote(Integer.toString(id)), StrictJson.quote(reason)));
    }

    @Override public void clearImages() {
        if (done()) return;
        wanted.clear();
        reasons.clear();
        origin.clearImages();
        if (ready) hooks.run().accept(call("clearImages"));
    }

    @Override public void scrollTo(double y) {
        if (done() || !Double.isFinite(y)) return;
        scroll = y;
        if (ready) hooks.run().accept(scrollCall());
    }

    @Override public void reveal(String elementId) {
        Objects.requireNonNull(elementId, "elementId");
        if (!done() && ready) hooks.run().accept(call("reveal", StrictJson.quote(elementId)));
    }

    @Override public CompletableFuture<Integer> markAll(PreviewFind find) {
        Objects.requireNonNull(find, "find");
        if (done()) return cancelled();
        dropFind();
        if (!ready) return CompletableFuture.completedFuture(0);
        var answer = new CompletableFuture<Integer>();
        marking = answer;
        ask(Kind.MARK, find, command("search", ++issued, find, null));
        return answer;
    }

    @Override public CompletableFuture<Boolean> select(PreviewFind find, boolean forward) {
        Objects.requireNonNull(find, "find");
        if (done()) return cancelled();
        dropFind();
        if (!ready) return CompletableFuture.completedFuture(false);
        var answer = new CompletableFuture<Boolean>();
        stepping = answer;
        ask(Kind.STEP, find, command("step", ++issued, find, forward));
        return answer;
    }

    @Override public void clearFind() {
        if (!done() && ready) hooks.run().accept(PreviewPage.find("{\"action\":\"clear\"}"));
    }

    @Override public CompletableFuture<String> selectedText() {
        if (done()) return cancelled();
        dropSelection();
        if (!ready) return CompletableFuture.completedFuture("");
        var answer = new CompletableFuture<String>();
        selection = answer;
        selecting = ++issued;
        hooks.run().accept(PreviewPage.find("{\"action\":\"selection\",\"search\":" + selecting + "}"));
        return answer;
    }

    @Override public void copy() {
        if (!done()) hooks.copy().run();
    }

    @Override public void selectAll() {
        if (!done()) hooks.selectAll().run();
    }

    @Override public void focus() {
        if (!done()) hooks.focus().run();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        stopWaiting();
        origin.close();
        hooks.close().run();
        List<Runnable> told = List.copyOf(closes);
        closes.clear();
        for (Runnable listener : told) listener.run();
    }

    /** Has {@code listener} run once the preview closes, as the service forgets it then. UI thread. */
    void onClose(Runnable listener) {
        closes.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * The page's browser exists: unless, within {@link #LOAD_WAIT}, the page says it is ready and, if a document has
     * been asked for by then, shows one, the preview is unavailable. UI thread.
     */
    void created() {
        if (done() || showedDocument) return;
        stopWatch();
        watch = later.apply(LOAD_WAIT, () -> {
            watch = () -> { };
            if (!ready || requested >= 0 && !showedDocument) unavailable(NOT_LOADED);
        });
    }

    /**
     * A message of the page, which {@link PreviewMessages} read from a router request the page's router checked: taken
     * only once the page is ready, but for {@code ready} itself. UI thread.
     *
     * @param message what the page told
     */
    void received(PreviewMessages.Message message) {
        Objects.requireNonNull(message, "message");
        if (!done()) take(message);
    }

    /**
     * The preview cannot show any more, for {@code reason}: it tells its caller once, and cancels what waits. UI thread.
     *
     * @param reason why, as the user reads it
     */
    void unavailable(String reason) {
        Objects.requireNonNull(reason, "reason");
        if (done()) return;
        unavailable = true;
        stopWaiting();
        events.accept(new PreviewEvent.Unavailable(reason));
    }

    private void take(PreviewMessages.Message message) {
        if (message instanceof PreviewMessages.Ready) {
            if (!ready) becomeReady();
            return;
        }
        if (!ready) return;
        switch (message) {
            case PreviewMessages.Shown(long revision) -> {
                // Any document asked for, the latest or not, shows the page works: the deadline ends.
                if (revision <= requested && !showedDocument) {
                    showedDocument = true;
                    stopWatch();
                }
                if (revision != requested || revision <= shown) return;
                shown = revision;
                events.accept(new PreviewEvent.Shown(revision));
            }
            case PreviewMessages.Laid(PreviewEvent.Geometry geometry) -> {
                if (geometry.revision() == shown) events.accept(geometry);
            }
            case PreviewMessages.Scrolled(double y, boolean user) -> events.accept(new PreviewEvent.Scrolled(y, user));
            case PreviewMessages.Link(int id) -> events.accept(new PreviewEvent.LinkActivated(id));
            case PreviewMessages.Found(long request, int count, boolean invalid) -> found(request, count, invalid);
            case PreviewMessages.Selected(long request, String text) -> {
                if (selection == null || request != selecting) return;
                CompletableFuture<String> answer = selection;
                selection = null;
                answer.complete(text);
            }
            case PreviewMessages.Ready ignored -> { }
        }
    }

    /** The page has loaded: it gets the theme, then the latest document, its images, then the last scroll. */
    private void becomeReady() {
        ready = true;
        if (themeCall != null) hooks.run().accept(themeCall);
        if (stored >= 0 && stored == requested) hooks.run().accept(showCall(stored));
        for (var image : wanted.entrySet()) {
            Encoded png = encoded.get(image.getValue());
            if (png != null) hooks.run().accept(imageCall(image.getKey(), png));
        }
        for (var reason : reasons.entrySet())
            hooks.run().accept(call("imageReason", StrictJson.quote(Integer.toString(reason.getKey())), StrictJson.quote(reason.getValue())));
        if (!Double.isNaN(scroll)) hooks.run().accept(scrollCall());
    }

    /** The origin holds the document of {@code revision}: the page is asked to show it, if it still is the latest. */
    private void stored(long revision) {
        if (done() || revision <= stored) return;
        stored = revision;
        if (ready && revision == requested) hooks.run().accept(showCall(revision));
    }

    /** {@code image} is encoded, or could not be: each id that still wants it gets it. */
    private void encodedImage(BufferedImage image, byte[] written) {
        encoding.remove(image);
        if (done() || written == null) return;
        var png = new Encoded(nextKey++, written);
        encoded.put(image, png);
        for (var each : wanted.entrySet()) if (each.getValue() == image) set(each.getKey(), png);
    }

    /** Image {@code id} shows {@code png}: the origin serves it and the page, once ready, sets it. */
    private void set(int id, Encoded png) {
        origin.image(png.key(), png.bytes());
        if (ready) hooks.run().accept(imageCall(id, png));
    }

    /** A Find request's answer: it completes the request it names, if that one still waits. */
    private void found(long request, int count, boolean invalid) {
        if (kind == null || request != finding) return;
        Kind answered = kind;
        String query = findQuery;
        CompletableFuture<Integer> marked = marking;
        CompletableFuture<Boolean> stepped = stepping;
        clearFindRequest();
        var refused = new PatternSyntaxException("JavaScript refuses this regular expression", query, -1);
        if (answered == Kind.MARK) {
            if (invalid) marked.completeExceptionally(refused);
            else marked.complete(count);
        } else if (invalid) {
            stepped.completeExceptionally(refused);
        } else {
            stepped.complete(count > 0);
        }
    }

    private void ask(Kind asked, PreviewFind find, String command) {
        kind = asked;
        finding = issued;
        findQuery = find.query();
        hooks.run().accept(PreviewPage.find(command));
    }

    /** A {@code find.js} command for {@code find}, with its request id, and for a step its direction. */
    private static String command(String action, long id, PreviewFind find, Boolean forward) {
        return "{\"action\":\"" + action + "\",\"search\":" + id + ",\"query\":" + StrictJson.quote(find.query()) + ",\"matchCase\":"
            + find.matchCase() + ",\"wholeWord\":" + find.wholeWord() + ",\"regex\":" + find.regex()
            + (forward == null ? "" : ",\"forward\":" + forward) + "}";
    }

    /** The Find request waiting, if any, is cancelled. */
    private void dropFind() {
        CompletableFuture<Integer> marked = marking;
        CompletableFuture<Boolean> stepped = stepping;
        clearFindRequest();
        if (marked != null) marked.cancel(false);
        if (stepped != null) stepped.cancel(false);
    }

    private void clearFindRequest() {
        kind = null;
        finding = 0;
        findQuery = null;
        marking = null;
        stepping = null;
    }

    private void dropSelection() {
        CompletableFuture<String> waiting = selection;
        selection = null;
        selecting = 0;
        if (waiting != null) waiting.cancel(false);
    }

    /** Nothing waits any more: the watch stops, and the futures that wait are cancelled. */
    private void stopWaiting() {
        stopWatch();
        dropFind();
        dropSelection();
    }

    /** The page's deadline stops. */
    private void stopWatch() {
        watch.run();
        watch = () -> { };
    }

    /** Whether the preview does nothing more: closed, or unavailable. */
    private boolean done() {
        return closed || unavailable;
    }

    private static <T> CompletableFuture<T> cancelled() {
        var future = new CompletableFuture<T>();
        future.cancel(false);
        return future;
    }

    private String scrollCall() {
        return call("scrollTo", StrictJson.quote(Double.toString(scroll)));
    }

    private static String showCall(long revision) {
        return call("show", StrictJson.quote(Long.toString(revision)));
    }

    private static String imageCall(int id, Encoded png) {
        return call("image", StrictJson.quote(Integer.toString(id)), StrictJson.quote("/image/" + png.key() + ".png"));
    }

    /** {@code jasperPreview.<function>(<arguments>);}, each argument already JavaScript. */
    private static String call(String function, String... arguments) {
        return "jasperPreview." + function + "(" + String.join(", ", arguments) + ");";
    }

    /** {@code image} as PNG; null when it cannot be written. Background. */
    private static byte[] png(BufferedImage image) {
        try {
            var out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", out)) return null;
            return out.toByteArray();
        } catch (IOException | RuntimeException unwritable) {
            System.getLogger(PagePreview.class.getName()).log(System.Logger.Level.WARNING, "A Markdown preview's image could not be encoded",
                unwritable);
            return null;
        }
    }

    /** How many bytes {@code text} takes in UTF-8, an unpaired surrogate as the replacement character's three. */
    static long utf8Length(String text) {
        long bytes = 0;
        for (int at = 0; at < text.length(); at++) {
            char c = text.charAt(at);
            if (c < 0x80) bytes += 1;
            else if (c < 0x800) bytes += 2;
            else if (Character.isHighSurrogate(c) && at + 1 < text.length() && Character.isLowSurrogate(text.charAt(at + 1))) {
                bytes += 4;
                at++;
            } else bytes += 3;
        }
        return bytes;
    }

    /** A UI-thread timer that runs {@code task} once, after {@code delay}; the answer stops it. */
    static Runnable onUiAfter(Duration delay, Runnable task) {
        var timer = new Timer((int) delay.toMillis(), event -> task.run());
        timer.setRepeats(false);
        timer.start();
        return timer::stop;
    }

    /**
     * An image as the origin serves it.
     *
     * @param key its key in {@code /image/<key>.png}
     * @param bytes its PNG
     */
    private record Encoded(int key, byte[] bytes) { }
}
