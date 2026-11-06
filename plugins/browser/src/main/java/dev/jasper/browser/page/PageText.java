package dev.jasper.browser.page;

import dev.jasper.browser.json.StrictJson;
import java.awt.EventQueue;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.cef.browser.CefDevToolsClient;

/**
 * Text put where the user is typing in a page, and Return pressed there, for the text another plugin inserts into its
 * item (spec section 15, Snippets in page fields), through the page's DevTools client. Chromium's Java API inserts no
 * text and presses no key, so DevTools does: {@code Page.getFrameTree} names the main frame, {@code
 * Page.createIsolatedWorld} gives it a world of Jasper's own, and {@code Runtime.evaluate} runs {@code insert-text.js}
 * there, the text as a JSON string literal, with no user activation, as activation would let the page open popups; its
 * answer comes back by value. The world is asked for at each insertion, not kept, as each new document of the main
 * frame drops its worlds' contexts. Return is {@code Input.dispatchKeyEvent}'s key down, with Return's text, which a
 * form submits on, then its key up once the down is answered. DevTools' answers are read as untrusted input, within
 * bounds, as the page's own scripts can shape the frame tree; anything else is a field that did not take the text. Each
 * call after the first is made on the UI thread, as the page's other DevTools calls are. Only {@link #insert} and
 * {@link #pressReturn} touch Chromium. Any thread.
 */
final class PageText {
    /** The name of Jasper's isolated world in the main frame. */
    static final String WORLD = "Jasper text insertion";
    /** The longest main frame id kept, in characters: Chromium's are 32. */
    static final int MAX_ID = 256;
    /** The longest frame tree read, in characters, and how deep it nests at most: a page that has more is pasted into. */
    static final int MAX_TREE = 1 << 20, MAX_TREE_DEPTH = 64;
    /** The longest other answer read, in characters, and how deep it nests at most. */
    static final int MAX_ANSWER = 1 << 16, MAX_ANSWER_DEPTH = 16;
    /** The page script: a function of the text. */
    static final String SOURCE = source();
    /** Return's key down, with the text a key press carries, and its key up. */
    static final String RETURN_DOWN = "{\"type\":\"keyDown\",\"key\":\"Enter\",\"code\":\"Enter\",\"windowsVirtualKeyCode\":13,"
        + "\"text\":\"\\r\",\"unmodifiedText\":\"\\r\"}",
        RETURN_UP = "{\"type\":\"keyUp\",\"key\":\"Enter\",\"code\":\"Enter\",\"windowsVirtualKeyCode\":13}";
    // How long each DevTools call may take before its answer is given up.
    private static final long TIMEOUT_SECONDS = 5;
    private static final CompletableFuture<Optional<String>> NO_ANSWER = CompletableFuture.completedFuture(Optional.empty());
    private static final System.Logger LOG = System.getLogger(PageText.class.getName());

    private PageText() { }

    /**
     * Puts {@code text} into the field that has {@code browser}'s focus, as {@code insert-text.js} finds it. Any thread.
     *
     * @param browser a page's browser, or null while the page has none ready
     * @param text the text
     * @return true when the field took the text; false without a browser, without a field the script reaches, and when
     *     DevTools fails or gives anything else before the script runs, or refuses to run it. It fails when the script's
     *     answer takes more than five seconds, as the text may still go in then; never otherwise
     */
    static CompletableFuture<Boolean> insert(PageBrowser browser, String text) {
        if (browser == null) return CompletableFuture.completedFuture(false);
        try {
            CefDevToolsClient client = browser.getDevToolsClient();
            if (client == null) return CompletableFuture.completedFuture(false);
            // Until the script runs, nothing went in, so any failure is a field that did not take the text. Each answer is
            // read where it arrives, and each next call is made on the UI thread, as the page's other DevTools calls are.
            CompletableFuture<OptionalInt> world = ask(client, "Page.getFrameTree", null, MAX_TREE)
                .thenApply(tree -> tree.flatMap(PageText::mainFrame))
                .thenComposeAsync(frame -> frame.map(id -> ask(client, "Page.createIsolatedWorld", isolatedWorld(id), MAX_ANSWER))
                    .orElse(NO_ANSWER), EventQueue::invokeLater)
                .thenApply(answer -> answer.map(PageText::context).orElse(OptionalInt.empty()))
                .exceptionally(failure -> OptionalInt.empty());
            return world.thenComposeAsync(context -> context.isEmpty() ? CompletableFuture.completedFuture(false)
                : evaluate(client, context.getAsInt(), text), EventQueue::invokeLater);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Text could not be put into a page", failure);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Runs {@code insert-text.js} with {@code text} in {@code context}: whether the field took the text; false when
     * DevTools refuses to run it, as for a context gone with its document or a closed browser; failed when the answer is
     * late, as the text may still go in.
     */
    private static CompletableFuture<Boolean> evaluate(CefDevToolsClient client, int context, String text) {
        CompletableFuture<String> call;
        try {
            call = client.executeDevToolsMethod("Runtime.evaluate", evaluation(context, text));
        } catch (RuntimeException refused) {
            LOG.log(System.Logger.Level.WARNING, "Text could not be put into a page", refused);
            return CompletableFuture.completedFuture(false);
        }
        return call.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).handle((answer, failure) -> {
            if (failure == null) return inserted(answer);
            if (cause(failure) instanceof TimeoutException late) throw new CompletionException(late);
            return false; // an error answer: nothing ran
        });
    }

    /**
     * Presses Return where {@code browser}'s focus is: its key down, then, once DevTools has answered it however it did,
     * its key up, so no key is left down. Any thread.
     *
     * @param browser a page's browser, or null while the page has none ready, which presses nothing
     */
    static void pressReturn(PageBrowser browser) {
        if (browser == null) return;
        try {
            CefDevToolsClient client = browser.getDevToolsClient();
            if (client == null) return;
            client.executeDevToolsMethod("Input.dispatchKeyEvent", RETURN_DOWN)
                .handleAsync((down, failure) -> client.executeDevToolsMethod("Input.dispatchKeyEvent", RETURN_UP), EventQueue::invokeLater)
                .thenCompose(up -> up)
                .whenComplete((up, failure) -> {
                    if (failure != null) LOG.log(System.Logger.Level.DEBUG, "Return could not be pressed in a page", failure);
                });
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Return could not be pressed in a page", failure);
        }
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
        Map<String, Object> root = answer(answer).orElse(null);
        return root != null && root.get("executionContextId") instanceof Long id && id == id.intValue()
            ? OptionalInt.of(id.intValue()) : OptionalInt.empty();
    }

    /**
     * What {@code Runtime.evaluate} is asked: {@link #SOURCE} called with {@code text}, a JSON string literal, in
     * {@code context}, its answer by value, without user activation, awaiting nothing, its exceptions unreported.
     *
     * @param context the isolated world's context
     * @param text the text
     * @return the parameters, a JSON object
     */
    static String evaluation(int context, String text) {
        String expression = SOURCE + "\n(" + StrictJson.quote(text) + ");";
        return "{\"expression\":" + StrictJson.quote(expression) + ",\"contextId\":" + context
            + ",\"returnByValue\":true,\"userGesture\":false,\"awaitPromise\":false,\"silent\":true}";
    }

    /**
     * Whether DevTools' answer to {@code Runtime.evaluate} says the field took the text: its {@code result} is the
     * boolean {@code true} by value, and nothing threw.
     *
     * @param answer what DevTools answered
     * @return whether it does; false for anything else, and never an exception
     */
    static boolean inserted(String answer) {
        Map<String, Object> root = answer(answer).orElse(null);
        return root != null && !root.containsKey("exceptionDetails") && root.get("result") instanceof Map<?, ?> result
            && "boolean".equals(result.get("type")) && Boolean.TRUE.equals(result.get("value"));
    }

    /** Calls {@code method} with {@code parameters}, or none: its answer, empty when it fails, is longer than {@code max} or is late. */
    private static CompletableFuture<Optional<String>> ask(CefDevToolsClient client, String method, String parameters, int max) {
        CompletableFuture<String> call = parameters == null ? client.executeDevToolsMethod(method)
            : client.executeDevToolsMethod(method, parameters);
        return call.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .handle((answer, failure) -> failure == null && answer != null && answer.length() <= max ? Optional.of(answer) : Optional.empty());
    }

    /** A short answer as a JSON object. */
    private static Optional<Map<String, Object>> answer(String text) {
        return text == null || text.length() > MAX_ANSWER ? Optional.empty() : StrictJson.object(text, MAX_ANSWER_DEPTH);
    }

    /** What {@code failure} is, as a stage of a future wraps it. */
    private static Throwable cause(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }

    private static String source() {
        try (InputStream in = PageText.class.getResourceAsStream("insert-text.js")) {
            if (in == null) throw new IllegalStateException("The Browser plugin has no insert-text.js");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
