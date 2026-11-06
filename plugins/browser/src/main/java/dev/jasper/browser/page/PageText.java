package dev.jasper.browser.page;

import dev.jasper.browser.json.StrictJson;
import java.awt.EventQueue;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.cef.browser.CefDevToolsClient;

/**
 * Text put where the user is typing in a page, and Return pressed there, for the text another plugin inserts into its
 * item (spec section 15, Snippets in page fields; H30), and the page's selection read for copy on select (spec section
 * 15), through the page's DevTools client. Chromium's Java API inserts no text, presses no key and reads no selection
 * without the page's scripts, so DevTools does. None of it touches the clipboard.
 *
 * <p>{@link #insertIntoField} and {@link #selectedText} each run a script in a world of Jasper's own. The first run in a
 * document asks, in order:
 * <ol>
 *   <li>{@code Page.getFrameTree}, for the main frame's id;</li>
 *   <li>{@code Page.createIsolatedWorld}, for Jasper's world in it, {@value #WORLD}, the same for both scripts;</li>
 *   <li>{@code Runtime.enable}, which reports every context, that world's unique id among them; then
 *       {@code Runtime.disable}, and {@code Runtime.releaseObjectGroup} for the {@code console} group, the objects of the
 *       page's console messages that the enable reported;</li>
 *   <li>{@code Runtime.evaluate}, which runs the script in that unique context only: {@code insert-text.js} with the
 *       text, a JSON string literal, or {@code selection.js} with the longest selection read, {@value #MAX_SELECTION}
 *       characters.</li>
 * </ol>
 * The world's unique id is kept for the document (H33), so a later run in it asks only {@code Runtime.evaluate}. The
 * page's next main-frame load start drops it ({@link #newDocument}), and so does any evaluation that fails, late,
 * answered with an error, as for a context gone with its document, or unreadable: that run does nothing, and the next
 * asks for the world again. An id that arrives after a new document began is used for its run and not kept. A stale id
 * can only fail: a context id may repeat in another renderer process, a unique id never does, so no script runs in
 * another document's world. The script runs with no user activation, which would let the page open popups, and its
 * answer comes back by value. Runs go one at a time, an insertion or a read, so Runtime is enabled for one at a time,
 * and only for the first run in a document. While it is enabled, which takes a moment, the page could notice that
 * DevTools is attached.
 *
 * <p>Only the script's own answer is an answer: for an insertion, that the field took the text, that the focus is where
 * the world cannot reach, or that it takes no text (H38); for a read, the selected text, a frame of another origin, a
 * selection unchanged since the last read (H36), or nothing. Every call times out after five seconds, and a late call,
 * an error answer, a refused call and an answer that cannot be read each fail the run, as the text may still go in or
 * nothing can tell.
 * DevTools' answers are read as untrusted input, within bounds, as the page's own scripts can shape the frame tree and
 * the selection.
 *
 * <p>{@link #insertText} types text with {@code Input.insertText} into whatever has the focus, cross-origin frames
 * included. {@link #pressReturn} sends {@code Input.dispatchKeyEvent}'s key down, with Return's text, which a form submits
 * on, then its key up. Both are trusted input, so they give the page user activation, as the user's own typing does.
 *
 * <p>The evaluation's answer, up to {@value #MAX_SELECTION_ANSWER} characters for a read, is read on the parsing
 * executor, the plugin's background executor, off the UI thread; that bound, checked before the parse, bounds the parse
 * too, and a parse that executor refuses, once the plugin has stopped, fails its run. The other answers are read where
 * they arrive. Each later call is made on the UI thread, as the
 * page's other DevTools calls are. The methods run on the UI thread; their futures complete on any thread.
 */
final class PageText {
    /** The name of Jasper's isolated world in the main frame. */
    static final String WORLD = "Jasper";
    /** The longest main frame id and unique context id kept, in characters: Chromium's are shorter. */
    static final int MAX_ID = 256;
    /** The longest frame tree read, in characters, and how deep it nests at most. */
    static final int MAX_TREE = 1 << 20, MAX_TREE_DEPTH = 64;
    /** The longest other answer or event read, in characters, and how deep it nests at most. */
    static final int MAX_ANSWER = 1 << 16, MAX_ANSWER_DEPTH = 16;
    /** The insertion's script: a function of the text. */
    static final String INSERT_TEXT = source("insert-text.js");
    /** The selection's script: a function of the longest text it gives. */
    static final String SELECTION = source("selection.js");
    /** The longest selection read, in characters: a longer one reads as none. */
    static final int MAX_SELECTION = 1 << 20;
    /** The longest answer to a read, in characters: the longest selection, every character escaped, and room to spare. */
    static final int MAX_SELECTION_ANSWER = 6 * MAX_SELECTION + MAX_ANSWER;
    /** Return's key down, with the text a key press carries, and its key up. */
    static final String RETURN_DOWN = "{\"type\":\"keyDown\",\"key\":\"Enter\",\"code\":\"Enter\",\"windowsVirtualKeyCode\":13,"
        + "\"text\":\"\\r\",\"unmodifiedText\":\"\\r\"}",
        RETURN_UP = "{\"type\":\"keyUp\",\"key\":\"Enter\",\"code\":\"Enter\",\"windowsVirtualKeyCode\":13}";
    /** How long each DevTools call may take before it fails. */
    static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String CONTEXT_CREATED = "Runtime.executionContextCreated";
    // What releases the objects of the console messages Runtime.enable reported.
    private static final String CONSOLE_GROUP = "{\"objectGroup\":\"console\"}";
    private static final System.Logger LOG = System.getLogger(PageText.class.getName());

    private final BiFunction<String, String, CompletableFuture<String>> devTools;
    private final Executor ui, parsing;
    private final Duration timeout;
    // UI thread: the world's unique id, kept for the document it was asked for in, or null; and which document runs are in,
    // counted by newDocument.
    private String world;
    private long generation;
    // The run under way, or the last, an insertion or a read: the next waits for it. UI thread.
    private CompletableFuture<?> last = CompletableFuture.completedFuture(null);
    // The world whose report the run under way waits for, while Runtime is enabled for it; else null.
    private volatile Awaited awaited;

    private record Awaited(String frame, int context, CompletableFuture<String> uniqueId) { }

    /**
     * Text insertion and selection reads through {@code devTools}: Chromium's, or a test's. It must hear every event
     * DevTools reports ({@link #event}).
     *
     * @param devTools calls a DevTools method with its parameters, a JSON object or null for none, and answers what DevTools
     *     answered; an error answer fails the future
     * @param ui runs a task on the UI thread
     * @param parsing reads the evaluations' answers, off the UI thread
     * @param timeout how long each call may take before it fails
     */
    PageText(BiFunction<String, String, CompletableFuture<String>> devTools, Executor ui, Executor parsing, Duration timeout) {
        this.devTools = Objects.requireNonNull(devTools, "devTools");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.parsing = Objects.requireNonNull(parsing, "parsing");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    /**
     * Text insertion and selection reads through {@code browser}'s DevTools client, which tells them its events from now
     * on.
     *
     * @param browser a page's ready browser
     * @param parsing the plugin's background executor, where the evaluations' answers are read
     * @return them; empty when the browser has no DevTools client
     */
    static Optional<PageText> of(PageBrowser browser, Executor parsing) {
        try {
            CefDevToolsClient client = browser.getDevToolsClient();
            if (client == null) return Optional.empty();
            var text = new PageText((method, parameters) -> parameters == null ? client.executeDevToolsMethod(method)
                : client.executeDevToolsMethod(method, parameters), EventQueue::invokeLater, parsing, TIMEOUT);
            // Added once, before Runtime is first enabled: JCEF walks its listeners unsynchronized as each event arrives,
            // and no domain reports events on this client until then.
            client.addEventListener(text::event);
            return Optional.of(text);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A page's DevTools could not be reached", failure);
            return Optional.empty();
        }
    }

    /**
     * Puts {@code text} into the field that has the page's focus, as {@code insert-text.js} finds it, once the run before
     * has ended.
     *
     * @param text the text
     * @return what became of the text, as the script answered. It fails when any call is late, refused or answered with
     *     an error, or its answer cannot be read
     */
    CompletableFuture<Page.Insertion> insertIntoField(String text) {
        Objects.requireNonNull(text, "text");
        return inWorld(INSERT_TEXT + "\n(" + StrictJson.quote(text) + ");", PageText::answered);
    }

    /**
     * What the user has selected in the page, as {@code selection.js} reads it, once the run before has ended.
     *
     * @return the selection; it fails when any call is late, refused or answered with an error, or its answer cannot be
     *     read
     */
    CompletableFuture<Page.Selection> selectedText() {
        return inWorld(SELECTION + "\n(" + MAX_SELECTION + ");", PageText::selection);
    }

    /**
     * Types {@code text} into whatever has the page's focus, a cross-origin frame's field included, as an input method
     * commits it: {@code Input.insertText}.
     *
     * @param text the text
     * @return done once DevTools has answered; failed when the call is late, refused or answered with an error
     */
    CompletableFuture<Void> insertText(String text) {
        Objects.requireNonNull(text, "text");
        return call("Input.insertText", typing(text)).thenApply(answer -> null);
    }

    /**
     * The page's main frame starts loading a new document, whose worlds are new: the kept world's id is dropped, and one
     * that arrives for a run under way is not kept. UI thread.
     */
    void newDocument() {
        generation++;
        world = null;
    }

    /** Presses Return where the page's focus is: its key down, then its key up, whatever the down's answer. */
    void pressReturn() {
        call("Input.dispatchKeyEvent", RETURN_DOWN)
            .handleAsync((down, failure) -> {
                if (failure != null) LOG.log(System.Logger.Level.DEBUG, "Return's key down failed in a page", failure);
                return call("Input.dispatchKeyEvent", RETURN_UP);
            }, ui)
            .thenCompose(up -> up)
            .whenComplete((up, failure) -> {
                if (failure != null) LOG.log(System.Logger.Level.DEBUG, "Return's key up failed in a page", failure);
            });
    }

    /**
     * An event DevTools reports: the world the run under way waits for is reported with its unique id. Any thread.
     *
     * @param method the event's method
     * @param parameters its parameters, a JSON object
     */
    void event(String method, String parameters) {
        Awaited waiting = awaited;
        if (waiting == null || !CONTEXT_CREATED.equals(method)) return;
        uniqueId(parameters, waiting.frame(), waiting.context()).ifPresent(waiting.uniqueId()::complete);
    }

    /**
     * Runs {@code expression} in Jasper's world once the run before has ended: what {@code reader} reads in DevTools' answer
     * to its evaluation, on the parsing executor.
     */
    private <T> CompletableFuture<T> inWorld(String expression, Function<String, Optional<T>> reader) {
        CompletableFuture<T> run = last.handle((done, failure) -> null).thenComposeAsync(ignored -> evaluate(expression, reader), ui);
        last = run;
        return run;
    }

    /**
     * Evaluates {@code expression} in the world kept for the document, or one asked for now: any failure from the
     * evaluation on drops the kept world, before the run ends, so the next run asks for it again. UI thread.
     */
    private <T> CompletableFuture<T> evaluate(String expression, Function<String, Optional<T>> reader) {
        CompletableFuture<String> unique = world != null ? CompletableFuture.completedFuture(world) : askWorld(generation);
        return unique.thenComposeAsync(id -> call("Runtime.evaluate", evaluation(id, expression)), ui)
            .thenCompose(answer -> parsed(answer, reader))
            .whenCompleteAsync((value, failure) -> {
                if (failure != null) world = null;
            }, ui);
    }

    /**
     * What {@code reader} reads in {@code answer}, on the parsing executor. When that executor refuses the work, as the
     * plugin's background executor does once the plugin has stopped, the read fails at once, so the run still ends and the
     * runs after it go on (H41).
     */
    private <T> CompletableFuture<T> parsed(String answer, Function<String, Optional<T>> reader) {
        try {
            return CompletableFuture.supplyAsync(() -> reader.apply(answer).orElseThrow(() -> unreadable("Runtime.evaluate")), parsing);
        } catch (RejectedExecutionException refused) {
            return CompletableFuture.failedFuture(refused);
        }
    }

    /**
     * The unique id of Jasper's world in the main frame, kept if the document is still the {@code asked}th when it
     * arrives. UI thread.
     */
    private CompletableFuture<String> askWorld(long asked) {
        CompletableFuture<String> frame = call("Page.getFrameTree", null)
            .thenApply(tree -> mainFrame(tree).orElseThrow(() -> unreadable("Page.getFrameTree")));
        return frame.thenComposeAsync(id -> call("Page.createIsolatedWorld", isolatedWorld(id)), ui)
            .thenApply(created -> context(created).orElseThrow(() -> unreadable("Page.createIsolatedWorld")))
            .thenComposeAsync(context -> uniqueContext(frame.join(), context), ui)
            .thenApplyAsync(unique -> {
                if (generation == asked) world = unique;
                return unique;
            }, ui);
    }

    /**
     * The unique id of the world {@code context} in {@code frame}, as {@code Runtime.enable} reports it: Runtime is
     * disabled again however that ends, and the console messages' objects it reported released, before the answer goes
     * on.
     */
    private CompletableFuture<String> uniqueContext(String frame, int context) {
        var reported = new CompletableFuture<String>();
        awaited = new Awaited(frame, context, reported);
        return call("Runtime.enable", null)
            .thenCompose(enabled -> reported.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS))
            .whenCompleteAsync((unique, failure) -> {
                awaited = null;
                call("Runtime.disable", null).whenComplete((disabled, notDisabled) -> {
                    if (notDisabled != null) LOG.log(System.Logger.Level.DEBUG, "Runtime could not be disabled in a page", notDisabled);
                });
                call("Runtime.releaseObjectGroup", CONSOLE_GROUP).whenComplete((released, notReleased) -> {
                    if (notReleased != null) LOG.log(System.Logger.Level.DEBUG, "A page's console objects could not be released", notReleased);
                });
            }, ui);
    }

    /** Calls {@code method}: its answer, failed when the call is refused, answered with an error or late. */
    private CompletableFuture<String> call(String method, String parameters) {
        CompletableFuture<String> answer;
        try {
            answer = devTools.apply(method, parameters);
        } catch (RuntimeException refused) {
            return CompletableFuture.failedFuture(refused);
        }
        if (answer == null) return CompletableFuture.failedFuture(new IllegalStateException("DevTools gave no answer to " + method));
        return answer.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static IllegalStateException unreadable(String method) {
        return new IllegalStateException("DevTools answered " + method + " with what Jasper cannot read");
    }

    /**
     * DevTools' answer to {@code Page.getFrameTree}: an object whose {@code frameTree}'s {@code frame} has the main frame's
     * {@code id}, a string.
     *
     * @param answer what DevTools answered
     * @return the id, of 1 to {@link #MAX_ID} characters; empty for anything else, for a text over {@link #MAX_TREE}
     *     characters or nesting deeper than {@link #MAX_TREE_DEPTH}, and never an exception
     */
    static Optional<String> mainFrame(String answer) {
        if (answer == null || answer.length() > MAX_TREE) return Optional.empty();
        Map<String, Object> root = StrictJson.object(answer, MAX_TREE_DEPTH).orElse(null);
        if (root == null || !(root.get("frameTree") instanceof Map<?, ?> tree) || !(tree.get("frame") instanceof Map<?, ?> frame)
            || !(frame.get("id") instanceof String id) || id.isEmpty() || id.length() > MAX_ID) return Optional.empty();
        return Optional.of(id);
    }

    /**
     * What {@code Page.createIsolatedWorld} is asked: Jasper's world, {@link #WORLD}, in the frame {@code frame}.
     *
     * @param frame the main frame's id
     * @return the parameters, a JSON object
     */
    static String isolatedWorld(String frame) {
        return "{\"frameId\":" + StrictJson.quote(frame) + ",\"worldName\":" + StrictJson.quote(WORLD) + "}";
    }

    /**
     * DevTools' answer to {@code Page.createIsolatedWorld}: an object whose {@code executionContextId} is an integer.
     *
     * @param answer what DevTools answered
     * @return the world's context; empty for anything else, and never an exception
     */
    static OptionalInt context(String answer) {
        Map<String, Object> root = object(answer).orElse(null);
        return root != null && root.get("executionContextId") instanceof Long id && id == id.intValue()
            ? OptionalInt.of(id.intValue()) : OptionalInt.empty();
    }

    /**
     * The unique id in a {@code Runtime.executionContextCreated} event, when it reports Jasper's world {@code context}:
     * the context of that id, named {@link #WORLD}, in {@code frame}, and not the frame's default world, so a context of
     * another renderer process that took the same id is not taken for it.
     *
     * @param event the event's parameters
     * @param frame the main frame's id
     * @param context the world's context, as {@code Page.createIsolatedWorld} answered it
     * @return the unique id, of 1 to {@link #MAX_ID} characters; empty for any other event, and never an exception
     */
    static Optional<String> uniqueId(String event, String frame, int context) {
        Map<String, Object> root = object(event).orElse(null);
        if (root == null || !(root.get("context") instanceof Map<?, ?> created) || !(created.get("auxData") instanceof Map<?, ?> aux)
            || !(created.get("id") instanceof Long id) || id != context || !WORLD.equals(created.get("name"))
            || !frame.equals(aux.get("frameId")) || !Boolean.FALSE.equals(aux.get("isDefault"))
            || !(created.get("uniqueId") instanceof String unique) || unique.isEmpty() || unique.length() > MAX_ID) return Optional.empty();
        return Optional.of(unique);
    }

    /**
     * What {@code Runtime.evaluate} is asked: {@code expression}, one of Jasper's scripts called with its argument, in the
     * context {@code unique} names, its answer by value, without user activation, awaiting nothing, its exceptions
     * unreported.
     *
     * @param unique the isolated world's unique context id
     * @param expression the script and its call
     * @return the parameters, a JSON object
     */
    static String evaluation(String unique, String expression) {
        return "{\"expression\":" + StrictJson.quote(expression) + ",\"uniqueContextId\":" + StrictJson.quote(unique)
            + ",\"returnByValue\":true,\"userGesture\":false,\"awaitPromise\":false,\"silent\":true}";
    }

    /**
     * What {@code insert-text.js} answered in DevTools' answer to {@code Runtime.evaluate}: its {@code result}, a string
     * by value, when nothing threw: {@code inserted}, {@code unreachable} or {@code not-editable} (H38).
     *
     * @param answer what DevTools answered
     * @return what became of the text; empty for anything else, and never an exception
     */
    static Optional<Page.Insertion> answered(String answer) {
        Map<String, Object> root = object(answer).orElse(null);
        if (root == null || root.containsKey("exceptionDetails") || !(root.get("result") instanceof Map<?, ?> result)
            || !"string".equals(result.get("type")) || !(result.get("value") instanceof String value)) return Optional.empty();
        return switch (value) {
            case "inserted" -> Optional.of(Page.Insertion.INSERTED);
            case "unreachable" -> Optional.of(Page.Insertion.UNREACHABLE);
            case "not-editable" -> Optional.of(Page.Insertion.NOT_EDITABLE);
            default -> Optional.empty();
        };
    }

    /**
     * What {@code selection.js} answered in DevTools' answer to {@code Runtime.evaluate}: its {@code result}, an object by
     * value, when nothing threw. Its {@code kind} is {@code text}, with the selected {@code text} of at most
     * {@link #MAX_SELECTION} characters; {@code frame}, for the focus in a frame of another origin; {@code unchanged},
     * for a selection unchanged since the last read; or {@code none}.
     *
     * @param answer what DevTools answered
     * @return the selection; empty for anything else, for an answer over {@link #MAX_SELECTION_ANSWER} characters, and
     *     never an exception
     */
    static Optional<Page.Selection> selection(String answer) {
        if (answer == null || answer.length() > MAX_SELECTION_ANSWER) return Optional.empty();
        Map<String, Object> root = StrictJson.object(answer, MAX_ANSWER_DEPTH).orElse(null);
        if (root == null || root.containsKey("exceptionDetails") || !(root.get("result") instanceof Map<?, ?> result)
            || !"object".equals(result.get("type")) || !(result.get("value") instanceof Map<?, ?> value)
            || !(value.get("kind") instanceof String kind)) return Optional.empty();
        return switch (kind) {
            case "text" -> value.get("text") instanceof String text && text.length() <= MAX_SELECTION
                ? Optional.of(new Page.Selection.Text(text)) : Optional.empty();
            case "frame" -> Optional.of(new Page.Selection.InFrame());
            case "unchanged" -> Optional.of(new Page.Selection.Unchanged());
            case "none" -> Optional.of(new Page.Selection.None());
            default -> Optional.empty();
        };
    }

    /**
     * What {@code Input.insertText} is asked: {@code text}, a JSON string.
     *
     * @param text the text
     * @return the parameters, a JSON object
     */
    static String typing(String text) {
        return "{\"text\":" + StrictJson.quote(text) + "}";
    }

    /** A short answer or event as a JSON object. */
    private static Optional<Map<String, Object>> object(String text) {
        return text == null || text.length() > MAX_ANSWER ? Optional.empty() : StrictJson.object(text, MAX_ANSWER_DEPTH);
    }

    private static String source(String name) {
        try (InputStream in = PageText.class.getResourceAsStream(name)) {
            if (in == null) throw new IllegalStateException("The Browser plugin has no " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
