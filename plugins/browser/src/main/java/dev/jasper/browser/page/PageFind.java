package dev.jasper.browser.page;

import dev.jasper.browser.json.StrictJson;
import dev.jasper.sdk.Subscription;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import javax.swing.Timer;
import org.cef.browser.CefFrame;

/**
 * Find in page (spec section 10), behind a browser item's find bar. Chromium's own find takes no regular expressions,
 * so the search runs in page scripts: {@code find.js} runs in each of the page's frames, cross-origin ones included, at
 * most {@value #MAX_FRAMES} of them, the main frame first and the others in the order Chromium lists them
 * ({@code CefBrowser.getFrameIdentifiers}), and each frame answers its count through the {@code links} package's
 * message router (op {@code find}). The counts add up, and the current match steps through the matches frame by frame
 * in that order, round from the last to the first. The query is invalid when the main frame says so.
 *
 * <p>A query is plain text or, with {@code regex}, a JavaScript regular expression ({@code RegExp}, with {@code i} when
 * case does not matter), cut to {@value #MAX_QUERY} characters; it reaches a frame's script only as a JSON string. A
 * frame matches its visible text a block at a time, each run of white space as one space, so a match may span inline
 * elements and line breaks in the page's source; a plain query's runs of white space match any. It keeps at most
 * {@value #MAX_MATCHES} matches, or those it found in 250 ms, and highlights them, every match {@code #FFFF00} and the
 * current one {@code #FF9632}, with the CSS Custom Highlight API, which leaves the page's nodes and scripts alone. It
 * opens the {@code details}, {@code hidden="until-found"} elements and regions of {@code aria-expanded="false"}
 * controls that hide the current match, and searches again, and answers a new count, when its document's nodes, text
 * or some of their attributes change, less often the longer its search takes, and no more once a search ran out of
 * time. A search settles once every frame has answered or {@link #ANSWER_WAIT}
 * has passed, and its first match is then the current one; a later answer still counts. The main frame's next
 * document ends the search. Find runs in the page's own world, so the page's scripts can see the query.
 *
 * <p>Any script in a frame could send that frame's answers, so an answer is only shown: a count of 0 to
 * {@value #MAX_MATCHES}, taken for the search under way and from a frame that search reached. UI thread.
 */
public final class PageFind {
    /** The longest query, in characters: a longer one is cut. */
    public static final int MAX_QUERY = 1_000;
    /** The most matches a frame counts and highlights. */
    public static final int MAX_MATCHES = 10_000;
    /** The most frames a search reaches. */
    static final int MAX_FRAMES = 100;
    /** How long a search waits for its frames before it settles without the rest, and a selection is asked for. */
    static final Duration ANSWER_WAIT = Duration.ofSeconds(1);
    /** The page script: a function of one command. */
    static final String SOURCE = source();
    private static final String SCRIPT_URL = "jasper-about:find.js";
    private static final String CLEAR = script("{\"action\":\"clear\"}");
    private static final Result NONE = new Result(0, 0, false), INVALID = new Result(0, 0, true);
    private static final Runnable NOTHING = () -> { };
    private static final System.Logger LOG = System.getLogger(PageFind.class.getName());

    /**
     * Where a search stands.
     *
     * @param count how many matches every frame counts together
     * @param current which of them is current, from 1 in frame order; 0 for none
     * @param invalid whether the query is no regular expression, with no matches
     */
    public record Result(int count, int current, boolean invalid) {
        public Result {
            if (count < 0 || current < 0 || current > count || invalid && count > 0)
                throw new IllegalArgumentException("No such result: " + count + ", " + current + ", " + invalid);
        }
    }

    private final Supplier<List<String>> frames;
    private final Supplier<Optional<String>> focusedFrame;
    private final BiConsumer<String, String> scripts;
    private final BiFunction<Duration, Runnable, Runnable> later;
    private final List<Runnable> changes = new CopyOnWriteArrayList<>();
    // The last id given to a search or a selection request; the search under way, 0 for none; the frames it reached,
    // and each one's count, -1 until it answers; whether one said the pattern is invalid; the current match.
    private long issued, search;
    private List<String> searched = List.of();
    private int[] counts = new int[0];
    private boolean invalid, settled, closed;
    private int currentFrame = -1, currentMatch = -1;
    // The search's result until it settles, and what ends its wait.
    private CompletableFuture<Result> settling;
    private Runnable waiting = NOTHING;
    // The selection asked for: its id, its frame, its answer and what ends its wait.
    private long selectionId;
    private String selectionFrame;
    private CompletableFuture<String> selecting;
    private Runnable selectionWait = NOTHING;

    /**
     * A find for a page with these parts: the page's has its browser's, and a test's records.
     *
     * @param frames the identifiers of the page's frames, its main frame first and the others in Chromium's order; none
     *     before it has a browser
     * @param focusedFrame the frame that has the page's focus, else its main frame; empty before it has a browser
     * @param scripts runs a script in the frame with an identifier; nothing for a frame that has gone
     * @param later runs a task on the UI thread after a delay, and answers what cancels it
     */
    PageFind(Supplier<List<String>> frames, Supplier<Optional<String>> focusedFrame, BiConsumer<String, String> scripts,
             BiFunction<Duration, Runnable, Runnable> later) {
        this.frames = Objects.requireNonNull(frames, "frames");
        this.focusedFrame = Objects.requireNonNull(focusedFrame, "focusedFrame");
        this.scripts = Objects.requireNonNull(scripts, "scripts");
        this.later = Objects.requireNonNull(later, "later");
    }

    /** The find of {@code page}, which reaches the frames of its browser once ready. */
    static PageFind of(Page page) {
        return new PageFind(() -> frames(page.readyBrowser()), () -> focused(page.readyBrowser()),
            (frame, script) -> run(page.readyBrowser(), frame, script), PageFind::onUiAfter);
    }

    /**
     * Searches every frame for {@code query}, ending the search before. An empty query ends it, as {@link #clear} does.
     *
     * @param query what to find; cut to {@value #MAX_QUERY} characters
     * @param matchCase whether case matters
     * @param regex whether the query is a JavaScript regular expression
     * @return the search's result once it settles, with its first match current; cancelled when a new search, a
     *     {@link #clear} or the page's close comes first. Completed at once for an empty query or a page without frames
     */
    public CompletableFuture<Result> find(String query, boolean matchCase, boolean regex) {
        Objects.requireNonNull(query, "query");
        String cut = cut(query);
        if (closed) return CompletableFuture.completedFuture(NONE);
        if (cut.isEmpty()) {
            clear();
            return CompletableFuture.completedFuture(NONE);
        }
        List<String> listed = frames.get().stream().filter(Objects::nonNull).distinct().limit(MAX_FRAMES).toList();
        for (String gone : searched) if (!listed.contains(gone)) run(gone, CLEAR);
        end();
        search = ++issued;
        searched = listed;
        counts = new int[listed.size()];
        Arrays.fill(counts, -1);
        CompletableFuture<Result> result = new CompletableFuture<>();
        settling = result;
        long asked = search;
        if (listed.isEmpty()) {
            settle(asked);
            return result;
        }
        String command = script("{\"action\":\"search\",\"search\":" + asked + ",\"query\":" + StrictJson.quote(cut)
            + ",\"matchCase\":" + matchCase + ",\"regex\":" + regex + "}");
        for (String frame : listed) run(frame, command);
        if (!settled && asked == search) waiting = later.apply(ANSWER_WAIT, () -> settle(asked));
        return result;
    }

    /**
     * Makes the next match current, in the frame order, round from the last to the first, and scrolls the page to it.
     *
     * @return where the search stands now; nothing changes without a search or without matches
     */
    public Result next() {
        return step(true);
    }

    /**
     * Makes the match before current, in the frame order, round from the first to the last, and scrolls the page to it.
     *
     * @return where the search stands now; nothing changes without a search or without matches
     */
    public Result previous() {
        return step(false);
    }

    /** Ends the search: every frame it reached removes its highlights. */
    public void clear() {
        if (closed) return;
        for (String frame : searched) run(frame, CLEAR);
        end();
    }

    /**
     * Where the search under way stands, with what its frames have answered so far.
     *
     * @return the result; empty without a search, after {@link #clear} and once the main frame's next document ended it
     */
    public Optional<Result> result() {
        return search == 0 ? Optional.empty() : Optional.of(current());
    }

    /**
     * The text selected in the frame that has the page's focus, for the find bar to start from.
     *
     * @return the selection, when it is one line of at most {@value #MAX_QUERY} characters; else "", as for a frame that
     *     does not answer within {@link #ANSWER_WAIT}. Cancelled when asked again first
     */
    public CompletableFuture<String> selection() {
        dropSelection();
        Optional<String> frame = closed ? Optional.empty() : focusedFrame.get();
        if (frame.isEmpty()) return CompletableFuture.completedFuture("");
        long asked = ++issued;
        String asking = frame.get();
        CompletableFuture<String> answer = new CompletableFuture<>();
        selectionId = asked;
        selectionFrame = asking;
        selecting = answer;
        run(asking, script("{\"action\":\"selection\",\"search\":" + asked + "}"));
        if (selecting == answer) selectionWait = later.apply(ANSWER_WAIT, () -> selected(asking, asked, ""));
        return answer;
    }

    /**
     * Tells {@code listener} each time the result changes on its own after its search settled: a frame's late answer, a
     * frame's new count after its document changed, and the end of the search with the main frame's next document.
     *
     * @param listener told on the UI thread
     * @return the registration; the page's close ends it
     */
    public Subscription onChange(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        if (closed) return () -> { };
        changes.add(listener);
        return () -> changes.remove(listener);
    }

    /**
     * A frame answers a search, as the message router hands it on: taken only for the search under way, from a frame it
     * reached, with a count within the bounds.
     *
     * @param frame the identifier of the frame that answered, as Chromium names it
     * @param id the search the answer is for
     * @param count the frame's matches
     * @param isInvalid whether the frame found the query no regular expression
     */
    void answered(String frame, long id, int count, boolean isInvalid) {
        if (closed || id == 0 || id != search || count < 0 || count > MAX_MATCHES) return;
        int at = searched.indexOf(frame);
        if (at < 0) return;
        Result before = current();
        counts[at] = isInvalid ? 0 : count;
        // Every frame compiles the same pattern, so the main frame's answer, first, says whether it is invalid.
        if (at == 0) invalid = isInvalid;
        // As the frame's script does: its current match stays, or its last one when it has fewer.
        if (at == currentFrame && currentMatch >= counts[at]) {
            currentMatch = counts[at] - 1;
            if (currentMatch < 0) currentFrame = -1;
        }
        if (!settled) {
            if (Arrays.stream(counts).allMatch(each -> each >= 0)) settle(id);
        } else if (!current().equals(before)) {
            tell();
        }
    }

    /**
     * A frame answers the selection asked for, as the message router hands it on: taken only for the request under way,
     * from the frame asked.
     *
     * @param frame the identifier of the frame that answered
     * @param id the request the answer is for
     * @param text the frame's selected text
     */
    void selected(String frame, long id, String text) {
        if (closed || selecting == null || id != selectionId || !selectionFrame.equals(frame)) return;
        CompletableFuture<String> answer = selecting;
        selecting = null;
        dropSelection();
        answer.complete(text.length() <= MAX_QUERY && text.indexOf('\n') < 0 && text.indexOf('\r') < 0 ? text : "");
    }

    /** The main frame starts loading another document: the search ends, as its highlights went with the one before. */
    void dropped() {
        if (closed || search == 0) return;
        end();
        tell();
    }

    /** The page closes: the search and the selection asked for end, and nothing more runs or is told. */
    void close() {
        if (closed) return;
        end();
        dropSelection();
        closed = true;
        changes.clear();
    }

    /** The search settles: its first match is current, unless one already is, and its result is told. */
    private void settle(long id) {
        if (closed || id != search || settled) return;
        settled = true;
        waiting.run();
        waiting = NOTHING;
        if (currentFrame < 0 && !invalid) {
            for (int at = 0; at < counts.length; at++) {
                if (counts[at] > 0) {
                    currentFrame = at;
                    currentMatch = 0;
                    select(at, 0);
                    break;
                }
            }
        }
        CompletableFuture<Result> result = settling;
        settling = null;
        result.complete(current());
    }

    private Result step(boolean forward) {
        if (closed || search == 0) return NONE;
        if (invalid) return INVALID;
        int was = currentFrame;
        if (currentFrame >= 0 && forward && currentMatch + 1 < counts[currentFrame]) {
            currentMatch++;
        } else if (currentFrame >= 0 && !forward && currentMatch > 0) {
            currentMatch--;
        } else {
            int frameCount = counts.length, start = currentFrame < 0 ? (forward ? 0 : frameCount - 1) : currentFrame + (forward ? 1 : -1);
            int found = -1;
            for (int step = 0; step < frameCount && found < 0; step++) {
                int at = Math.floorMod(start + (forward ? step : -step), frameCount);
                if (counts[at] > 0) found = at;
            }
            if (found < 0) return current();
            currentFrame = found;
            currentMatch = forward ? 0 : counts[found] - 1;
        }
        if (was >= 0 && was != currentFrame) select(was, -1);
        select(currentFrame, currentMatch);
        return current();
    }

    /** Where the search stands: the counts so far, and the current match's place among them. */
    private Result current() {
        if (invalid) return INVALID;
        int total = 0, before = 0;
        for (int at = 0; at < counts.length; at++) {
            int count = Math.max(0, counts[at]);
            if (at < currentFrame) before += count;
            total += count;
        }
        return new Result(total, currentFrame < 0 ? 0 : before + currentMatch + 1, false);
    }

    /** Has frame {@code at} of the search make its match {@code index} current; -1 for none. */
    private void select(int at, int index) {
        run(searched.get(at), script("{\"action\":\"select\",\"search\":" + search + ",\"index\":" + index + "}"));
    }

    /** The search ends here: nothing runs in a frame. */
    private void end() {
        CompletableFuture<Result> result = settling;
        settling = null;
        waiting.run();
        waiting = NOTHING;
        search = 0;
        searched = List.of();
        counts = new int[0];
        invalid = settled = false;
        currentFrame = currentMatch = -1;
        if (result != null) result.cancel(false);
    }

    private void dropSelection() {
        CompletableFuture<String> answer = selecting;
        selecting = null;
        selectionFrame = null;
        selectionWait.run();
        selectionWait = NOTHING;
        if (answer != null) answer.cancel(false);
    }

    private void tell() {
        for (Runnable listener : changes) listener.run();
    }

    private void run(String frame, String script) {
        if (!closed) scripts.accept(frame, script);
    }

    /** {@code find.js} called with {@code command}, a JSON object. */
    private static String script(String command) {
        return SOURCE + "\n(" + command + ");";
    }

    private static String source() {
        try (InputStream in = PageFind.class.getResourceAsStream("find.js")) {
            if (in == null) throw new IllegalStateException("The Browser plugin has no find.js");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /** {@code query} to {@value #MAX_QUERY} characters, or one fewer where a character's two halves would part. */
    static String cut(String query) {
        if (query.length() <= MAX_QUERY) return query;
        return query.substring(0, Character.isHighSurrogate(query.charAt(MAX_QUERY - 1)) ? MAX_QUERY - 1 : MAX_QUERY);
    }

    /** The main frame's identifier, when known, then the others in CEF's order. */
    static List<String> mainFirst(Optional<String> main, List<String> all) {
        if (main.isEmpty()) return all;
        var listed = new ArrayList<String>(all.size() + 1);
        listed.add(main.get());
        for (String frame : all) if (!frame.equals(main.get())) listed.add(frame);
        return listed;
    }

    private static List<String> frames(PageBrowser browser) {
        if (browser == null) return List.of();
        try {
            List<String> listed = browser.getFrameIdentifiers();
            return mainFirst(identifier(browser.getMainFrame()), listed == null ? List.of() : listed.stream().filter(Objects::nonNull).toList());
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A page's frames could not be listed", failure);
            return List.of();
        }
    }

    private static Optional<String> focused(PageBrowser browser) {
        if (browser == null) return Optional.empty();
        try {
            CefFrame frame = browser.getFocusedFrame();
            return identifier(frame == null ? browser.getMainFrame() : frame);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A page's focused frame could not be read", failure);
            return Optional.empty();
        }
    }

    /** {@code frame}'s identifier, the handle then disposed; empty for no frame. */
    private static Optional<String> identifier(CefFrame frame) {
        if (frame == null) return Optional.empty();
        try {
            return Optional.ofNullable(frame.getIdentifier());
        } finally {
            frame.dispose();
        }
    }

    private static void run(PageBrowser browser, String id, String script) {
        if (browser == null) return;
        try {
            CefFrame frame = browser.getFrameByIdentifier(id);
            if (frame == null) return;
            try {
                if (frame.isValid()) frame.executeJavaScript(script, SCRIPT_URL, 0);
            } finally {
                frame.dispose();
            }
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Find could not reach a page's frame", failure);
        }
    }

    /** A UI-thread timer that runs {@code task} once, after {@code delay}; the answer stops it. */
    private static Runnable onUiAfter(Duration delay, Runnable task) {
        var timer = new Timer((int) delay.toMillis(), event -> task.run());
        timer.setRepeats(false);
        timer.start();
        return timer::stop;
    }
}
