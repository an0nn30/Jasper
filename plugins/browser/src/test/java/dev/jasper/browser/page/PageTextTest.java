package dev.jasper.browser.page;

import dev.jasper.browser.json.StrictJson;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What a page's text insertion asks of DevTools, in what order, and what it makes of the answers (spec section 15,
 * Snippets in page fields; H30). The main frame's id comes from {@code Page.getFrameTree}, and Jasper's isolated world
 * from {@code Page.createIsolatedWorld}. The world's unique id comes from the {@code Runtime.executionContextCreated}
 * event that {@code Runtime.enable} reports, and {@code Runtime.disable} follows. {@code insert-text.js} then runs in
 * that unique context only, so a navigation in between cannot run it in another document's world.
 *
 * <p>Only the script's answered true or false is an answer. A late call, an error answer, a refused call and an answer
 * that cannot be read each fail the insertion, so its item does nothing more. {@code Input.insertText} types the text
 * without the clipboard, and Return is a key down then a key up. Copy on select's read runs {@code selection.js} the same
 * way, one run at a time with the insertions. A recording DevTools stands in for Chromium's, and calls time out after
 * {@value #TIMEOUT_MILLIS} ms. The scripts themselves are checked by hand (Task 14).
 */
class PageTextTest {
    private static final long TIMEOUT_MILLIS = 100;
    private static final String FRAME = "9C3F2A", UNIQUE = "-3125.7741";
    private static final String TREE = """
        {"frameTree":{"frame":{"id":"9C3F2A","loaderId":"L1","url":"https://example.com/","domainAndRegistry":"example.com",
         "securityOrigin":"https://example.com","mimeType":"text/html","adFrameStatus":{"adFrameType":"none"},
         "secureContextType":"Secure","crossOriginIsolatedContextType":"NotIsolated","gatedAPIFeatures":[]},
         "childFrames":[{"frame":{"id":"77AB","parentId":"9C3F2A","url":"https://ads.example.net/"}}]}}""";
    private static final String WORLD = "{\"executionContextId\":12}";
    // Jasper's world, as Runtime.enable reports it.
    private static final String CREATED = created(12, PageText.WORLD, UNIQUE, false, FRAME);
    private static final String TRUE = "{\"result\":{\"type\":\"boolean\",\"value\":true}}";
    private static final String FALSE = "{\"result\":{\"type\":\"boolean\",\"value\":false}}";

    // The order an insertion asks in.
    private static final List<String> ORDER = List.of("Page.getFrameTree", "Page.createIsolatedWorld", "Runtime.enable",
        "Runtime.disable", "Runtime.evaluate");
    private final DevTools devTools = new DevTools();
    private final PageText text = made(devTools, Duration.ofMillis(TIMEOUT_MILLIS));

    @Test void theInsertionAsksInOrderAndRunsTheScriptInTheWorldsUniqueContext() throws Exception {
        assertThat(text.insertIntoField("ls -la").get(5, TimeUnit.SECONDS)).isTrue();
        assertThat(devTools.methods()).isEqualTo(ORDER);
        assertThat(devTools.parameters("Page.createIsolatedWorld"))
            .containsExactly(Map.entry("frameId", FRAME), Map.entry("worldName", PageText.WORLD));
        Map<String, Object> evaluated = devTools.parameters("Runtime.evaluate");
        assertThat(evaluated).containsEntry("uniqueContextId", UNIQUE).doesNotContainKey("contextId")
            .containsEntry("returnByValue", true).containsEntry("userGesture", false).containsEntry("awaitPromise", false)
            .containsEntry("silent", true).hasSize(6);
        assertThat((String) evaluated.get("expression")).isEqualTo(PageText.INSERT_TEXT + "\n(" + StrictJson.quote("ls -la") + ");");
    }

    @Test void anAnsweredFalseIsFalse() throws Exception {
        devTools.answer("Runtime.evaluate", FALSE);
        assertThat(text.insertIntoField("ls").get(5, TimeUnit.SECONDS)).isFalse();
    }

    /** A late answer may still come, and the text with it: the insertion fails, so nothing else types it. */
    @Test void aLateScriptAnswerFails() {
        devTools.never("Runtime.evaluate");
        assertFails(text.insertIntoField("ls"), TimeoutException.class);
        assertThat(devTools.methods()).contains("Runtime.disable");
    }

    /** H30 item 3: every call's timeout fails the insertion, and nothing after it is asked. */
    @Test void aTimeoutOnAnyCallFailsAndAsksNothingMore() {
        for (String method : List.of("Page.getFrameTree", "Page.createIsolatedWorld", "Runtime.enable")) {
            var late = new DevTools();
            late.never(method);
            assertFails(made(late, Duration.ofMillis(TIMEOUT_MILLIS)).insertIntoField("ls"), TimeoutException.class);
            assertThat(late.methods()).as(method).isEqualTo(upTo(method));
        }
    }

    /** A world gone with its document is never reported: the insertion fails rather than run the script elsewhere. */
    @Test void aWorldThatIsNotReportedFailsAndRuntimeIsDisabled() {
        devTools.events.clear();
        assertFails(text.insertIntoField("ls"), TimeoutException.class);
        assertThat(devTools.methods()).containsExactly("Page.getFrameTree", "Page.createIsolatedWorld", "Runtime.enable", "Runtime.disable");
    }

    /**
     * Item 4: a context with the world's id but another document's, as another renderer process numbers its contexts, is
     * not Jasper's world: only the event with Jasper's name, not the default world, in the main frame is.
     */
    @Test void onlyJaspersWorldInTheMainFrameGivesTheUniqueContext() {
        devTools.events.clear();
        devTools.events.add(created(12, "", "main-world", true, FRAME));
        devTools.events.add(created(12, PageText.WORLD, "other-frame", false, "77AB"));
        devTools.events.add(created(12, "Another world", "other-name", false, FRAME));
        devTools.events.add(created(13, PageText.WORLD, "other-id", false, FRAME));
        assertFails(text.insertIntoField("ls"), TimeoutException.class);
        assertThat(devTools.methods()).doesNotContain("Runtime.evaluate");
    }

    @Test void aRefusedCallFailsAndAsksNothingMore() {
        for (String method : List.of("Page.getFrameTree", "Page.createIsolatedWorld", "Runtime.enable", "Runtime.evaluate")) {
            var refusing = new DevTools();
            refusing.refuse(method);
            assertFails(made(refusing, Duration.ofMillis(TIMEOUT_MILLIS)).insertIntoField("ls"), IllegalStateException.class);
            assertThat(refusing.methods()).as(method).isEqualTo(upTo(method));
        }
    }

    /** An error answer, such as Runtime.evaluate's "uniqueContextId not found" for a world gone with its document. */
    @Test void anErrorAnswerFails() {
        for (String method : List.of("Page.getFrameTree", "Page.createIsolatedWorld", "Runtime.enable", "Runtime.evaluate")) {
            var failing = new DevTools();
            failing.fail(method);
            assertFails(made(failing, Duration.ofMillis(TIMEOUT_MILLIS)).insertIntoField("ls"), IllegalArgumentException.class);
            assertThat(failing.methods()).as(method).isEqualTo(upTo(method));
        }
    }

    @Test void anAnswerThatCannotBeReadFails() {
        for (String[] answer : new String[][] {{"Page.getFrameTree", "{}"}, {"Page.createIsolatedWorld", "{}"},
            {"Runtime.evaluate", "{\"result\":{\"type\":\"string\",\"value\":\"true\"}}"},
            {"Runtime.evaluate", "{\"result\":{\"type\":\"boolean\",\"value\":true},\"exceptionDetails\":{\"text\":\"Uncaught\"}}"}}) {
            var odd = new DevTools();
            odd.answer(answer[0], answer[1]);
            assertFails(made(odd, Duration.ofMillis(TIMEOUT_MILLIS)).insertIntoField("ls"), IllegalStateException.class);
            assertThat(odd.methods()).as(answer[1]).isEqualTo(upTo(answer[0]));
        }
    }

    /** Runtime is enabled once at a time: a second insertion waits for the first to end. */
    @Test void insertionsRunOneAtATime() throws Exception {
        var patient = new DevTools();
        PageText waiting = made(patient, Duration.ofSeconds(5));
        var pending = new CompletableFuture<String>();
        patient.answers.put("Runtime.evaluate", parameters -> pending);
        CompletableFuture<Boolean> first = waiting.insertIntoField("one"), second = waiting.insertIntoField("two");
        assertThat(patient.methods()).as("the second waits").isEqualTo(ORDER);
        patient.answer("Runtime.evaluate", FALSE);
        pending.complete(TRUE);
        assertThat(first.get(5, TimeUnit.SECONDS)).isTrue();
        assertThat(second.get(5, TimeUnit.SECONDS)).isFalse();
        assertThat(patient.methods()).hasSize(10).endsWith(ORDER.toArray(String[]::new));
    }

    /** H30: the text is typed where the focus is, cross-origin frames included, without the clipboard. */
    @Test void insertTextAsksInputInsertTextWithTheTextAsAJsonString() throws Exception {
        String hostile = "echo \"hi\" \\ </script> 😀\n";
        text.insertText(hostile).get(5, TimeUnit.SECONDS);
        assertThat(devTools.methods()).containsExactly("Input.insertText");
        assertThat(devTools.raw.getFirst()).isEqualTo("{\"text\":" + StrictJson.quote(hostile) + "}");
        assertThat(devTools.parameters("Input.insertText")).containsExactly(Map.entry("text", hostile));
        var late = new DevTools();
        late.never("Input.insertText");
        assertFails(made(late, Duration.ofMillis(TIMEOUT_MILLIS)).insertText("x"), TimeoutException.class);
        var failing = new DevTools();
        failing.fail("Input.insertText");
        assertFails(made(failing, Duration.ofMillis(TIMEOUT_MILLIS)).insertText("x"), IllegalArgumentException.class);
        var refusing = new DevTools();
        refusing.refuse("Input.insertText");
        assertFails(made(refusing, Duration.ofMillis(TIMEOUT_MILLIS)).insertText("x"), IllegalStateException.class);
    }

    /** F30, H30 item 5: Return goes down with its text, which submits a form, and comes up whatever the down's answer. */
    @Test void returnIsAKeyDownWithItsTextThenAKeyUpWhateverTheDownAnswers() throws Exception {
        text.pressReturn();
        assertThat(devTools.methods()).containsExactly("Input.dispatchKeyEvent", "Input.dispatchKeyEvent");
        Map<String, Object> down = StrictJson.object(devTools.raw.get(0), 1).orElseThrow();
        Map<String, Object> up = StrictJson.object(devTools.raw.get(1), 1).orElseThrow();
        assertThat(down).containsEntry("type", "keyDown").containsEntry("key", "Enter").containsEntry("code", "Enter")
            .containsEntry("windowsVirtualKeyCode", 13L).containsEntry("text", "\r").containsEntry("unmodifiedText", "\r");
        assertThat(up).containsEntry("type", "keyUp").containsEntry("key", "Enter").containsEntry("code", "Enter")
            .containsEntry("windowsVirtualKeyCode", 13L).doesNotContainKey("text");
        for (boolean late : new boolean[] {false, true}) {
            var odd = new DevTools();
            odd.answers.put("Input.dispatchKeyEvent", parameters -> parameters.contains("keyDown")
                ? (late ? new CompletableFuture<>() : CompletableFuture.failedFuture(new IllegalArgumentException("refused")))
                : CompletableFuture.completedFuture("{}"));
            made(odd, Duration.ofMillis(TIMEOUT_MILLIS)).pressReturn();
            awaitCalls(odd, 2);
            assertThat(odd.raw.get(1)).as(late ? "after a late down" : "after a failed down").isEqualTo(PageText.RETURN_UP);
        }
    }

    @Test void theMainFrameIsTheFrameTreesRoot() {
        assertThat(PageText.mainFrame(TREE)).contains(FRAME);
        for (String answer : new String[] {null, "", "{}", "[]", "not json", "{\"frameTree\":{}}", "{\"frameTree\":{\"frame\":{}}}",
            "{\"frameTree\":{\"frame\":{\"id\":\"\"}}}", "{\"frameTree\":{\"frame\":{\"id\":7}}}",
            "{\"frameTree\":{\"frame\":{\"id\":\"" + "A".repeat(PageText.MAX_ID + 1) + "\"}}}", TREE + " x"})
            assertThat(PageText.mainFrame(answer)).as(String.valueOf(answer)).isEmpty();
        assertThat(PageText.mainFrame(TREE.replace("\"gatedAPIFeatures\":[]", "\"x\":\"" + "a".repeat(PageText.MAX_TREE) + "\"")))
            .as("a tree over the bound").isEmpty();
        String deep = "{\"frameTree\":{\"frame\":{\"id\":\"A\"},\"childFrames\":" + "[{\"childFrames\":".repeat(40) + "[]"
            + "}]".repeat(40) + "}}";
        assertThat(PageText.mainFrame(deep)).as("frames nested past the bound").isEmpty();
    }

    @Test void theWorldsContextIsAnInteger() {
        assertThat(PageText.context(WORLD)).hasValue(12);
        for (String answer : new String[] {null, "", "{}", "{\"executionContextId\":\"12\"}", "{\"executionContextId\":1.5}",
            "{\"executionContextId\":4294967296}", WORLD + " x"})
            assertThat(PageText.context(answer)).as(String.valueOf(answer)).isEqualTo(OptionalInt.empty());
    }

    @Test void theWorldsEventGivesItsUniqueId() {
        assertThat(PageText.uniqueId(CREATED, FRAME, 12)).contains(UNIQUE);
        for (String event : new String[] {null, "", "{}", "{\"context\":{}}", created(12, PageText.WORLD, "", false, FRAME),
            created(12, PageText.WORLD, "u".repeat(PageText.MAX_ID + 1), false, FRAME), CREATED.replace("\"uniqueId\":\"" + UNIQUE + "\"", "\"uniqueId\":7"),
            CREATED + " x"})
            assertThat(PageText.uniqueId(event, FRAME, 12)).as(String.valueOf(event)).isEmpty();
    }

    @Test void onlyABooleanByValueIsTheScriptsAnswer() {
        assertThat(PageText.answered(TRUE)).contains(true);
        assertThat(PageText.answered(FALSE)).contains(false);
        for (String answer : new String[] {null, "", "{}", "{\"result\":{\"type\":\"string\",\"value\":\"true\"}}",
            "{\"result\":{\"type\":\"boolean\"}}", "{\"result\":{\"type\":\"boolean\",\"value\":true},\"exceptionDetails\":{}}",
            TRUE + " x", "{\"result\":{\"type\":\"boolean\",\"value\":true},\"x\":\"" + "a".repeat(PageText.MAX_ANSWER) + "\"}"})
            assertThat(PageText.answered(answer)).as(String.valueOf(answer)).isEmpty();
    }

    @Test void theScriptIsAFunctionOfTheText() {
        assertThat(PageText.INSERT_TEXT).startsWith("/*").contains("(function (text) {", "execCommand('insertText'", "setRangeText")
            .endsWith("})");
    }

    /**
     * Copy on select (spec section 15; F12): the selection is read as the insertion is, in Jasper's isolated world's unique
     * context, by {@code selection.js} called with the longest text Jasper copies, and its answer comes back by value.
     */
    @Test void theSelectionIsReadInTheWorldsUniqueContextAsTheInsertionIs() throws Exception {
        devTools.answer("Runtime.evaluate", selected("{\"kind\":\"text\",\"text\":\"abc\"}"));
        assertThat(text.selectedText().get(5, TimeUnit.SECONDS)).isEqualTo(new Page.Selection.Text("abc"));
        assertThat(devTools.methods()).isEqualTo(ORDER);
        Map<String, Object> evaluated = devTools.parameters("Runtime.evaluate");
        assertThat(evaluated).containsEntry("uniqueContextId", UNIQUE).doesNotContainKey("contextId")
            .containsEntry("returnByValue", true).containsEntry("userGesture", false).containsEntry("awaitPromise", false)
            .containsEntry("silent", true).hasSize(6);
        assertThat((String) evaluated.get("expression")).isEqualTo(PageText.SELECTION + "\n(" + PageText.MAX_SELECTION + ");");
    }

    @Test void theSelectionsAnswerIsTextAFrameOfAnotherOriginOrNone() {
        assertThat(PageText.selection(selected("{\"kind\":\"text\",\"text\":\"\"}"))).contains(new Page.Selection.Text(""));
        assertThat(PageText.selection(selected("{\"kind\":\"text\",\"text\":\"a\\u2028b\"}"))).contains(new Page.Selection.Text("a b"));
        assertThat(PageText.selection(selected("{\"kind\":\"frame\"}"))).contains(new Page.Selection.InFrame());
        assertThat(PageText.selection(selected("{\"kind\":\"none\"}"))).contains(new Page.Selection.None());
        String longest = "x".repeat(PageText.MAX_SELECTION);
        assertThat(PageText.selection(selected("{\"kind\":\"text\",\"text\":" + StrictJson.quote(longest) + "}")))
            .contains(new Page.Selection.Text(longest));
        String escaped = "\u0001".repeat(PageText.MAX_SELECTION);
        assertThat(PageText.selection(selected("{\"kind\":\"text\",\"text\":" + StrictJson.quote(escaped) + "}")))
            .as("the longest text, every character escaped").contains(new Page.Selection.Text(escaped));
        for (String answer : new String[] {null, "", "{}", TRUE, selected("{}"), selected("{\"kind\":\"text\"}"),
            selected("{\"kind\":\"text\",\"text\":7}"), selected("{\"kind\":\"other\"}"), selected("\"abc\""),
            selected("{\"kind\":\"text\",\"text\":\"" + "x".repeat(PageText.MAX_SELECTION + 1) + "\"}"),
            "{\"result\":{\"type\":\"object\",\"value\":{\"kind\":\"none\"}},\"exceptionDetails\":{}}",
            selected("{\"kind\":\"none\"}") + " x"})
            assertThat(PageText.selection(answer)).as(answer == null ? "null" : answer.substring(0, Math.min(answer.length(), 80)))
                .isEmpty();
    }

    @Test void aSelectionReadThatCannotBeReadOrIsLateFails() {
        devTools.answer("Runtime.evaluate", TRUE);
        assertFails(text.selectedText(), IllegalStateException.class);
        var late = new DevTools();
        late.never("Runtime.evaluate");
        assertFails(made(late, Duration.ofMillis(TIMEOUT_MILLIS)).selectedText(), TimeoutException.class);
        assertThat(late.methods()).contains("Runtime.disable");
    }

    /** Runtime is enabled once at a time: a selection read waits for an insertion under way, and the reverse. */
    @Test void selectionReadsAndInsertionsRunOneAtATime() throws Exception {
        var patient = new DevTools();
        PageText waiting = made(patient, Duration.ofSeconds(5));
        var pending = new CompletableFuture<String>();
        patient.answers.put("Runtime.evaluate", parameters -> pending);
        CompletableFuture<Boolean> inserted = waiting.insertIntoField("one");
        CompletableFuture<Page.Selection> read = waiting.selectedText();
        assertThat(patient.methods()).as("the read waits").isEqualTo(ORDER);
        patient.answer("Runtime.evaluate", selected("{\"kind\":\"none\"}"));
        pending.complete(TRUE);
        assertThat(inserted.get(5, TimeUnit.SECONDS)).isTrue();
        assertThat(read.get(5, TimeUnit.SECONDS)).isEqualTo(new Page.Selection.None());
        assertThat(patient.methods()).hasSize(10);
    }

    /**
     * The script never reads a password field (spec section 15): its check of the focused field's type comes before any
     * read of a field's value or the document's selection. The script itself runs by hand (Task 14).
     */
    @Test void theSelectionScriptRefusesAPasswordFieldBeforeItReadsAnything() {
        String script = PageText.SELECTION;
        assertThat(script).startsWith("/*").contains("(function (maxLength) {", "element.type === 'password'", "contentDocument",
            "kind: 'frame'", "getSelection()").endsWith("})");
        int refusal = script.indexOf("element.type === 'password'");
        int code = script.indexOf("(function (maxLength) {");
        assertThat(refusal).as("the refusal is code, not the header").isGreaterThan(code);
        assertThat(script.indexOf(".value", code)).as("a field's value is read after the refusal").isGreaterThan(refusal);
        assertThat(script.indexOf("getSelection()", code)).as("the document's selection is read after it").isGreaterThan(refusal);
    }

    /** {@code value}, a JSON object, as {@code Runtime.evaluate} answers a script's object by value. */
    private static String selected(String value) {
        return "{\"result\":{\"type\":\"object\",\"value\":" + value + "}}";
    }

    /** {@code event} as {@code Runtime.executionContextCreated} reports a context. */
    private static String created(int id, String name, String uniqueId, boolean isDefault, String frame) {
        return "{\"context\":{\"id\":" + id + ",\"origin\":\"https://example.com\",\"name\":" + StrictJson.quote(name)
            + ",\"uniqueId\":" + StrictJson.quote(uniqueId) + ",\"auxData\":{\"isDefault\":" + isDefault
            + ",\"type\":\"" + (isDefault ? "default" : "isolated") + "\",\"frameId\":" + StrictJson.quote(frame) + "}}}";
    }

    /** A text insertion over {@code devTools}, which tells it its events, its calls timing out after {@code timeout}. */
    private static PageText made(DevTools devTools, Duration timeout) {
        var made = new PageText(devTools, Runnable::run, timeout);
        devTools.owner = made;
        return made;
    }

    /** The calls an insertion makes up to {@code method}, which ends it: Runtime.disable follows Runtime.enable whatever. */
    private static List<String> upTo(String method) {
        int end = ORDER.indexOf(method) + (method.equals("Runtime.enable") ? 2 : 1);
        return ORDER.subList(0, end);
    }

    private static void assertFails(CompletableFuture<?> future, Class<? extends Throwable> cause) {
        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasRootCauseInstanceOf(cause);
    }

    private static void awaitCalls(DevTools devTools, int count) throws InterruptedException {
        for (int wait = 0; wait < 500 && devTools.raw.size() < count; wait++) Thread.sleep(10);
        assertThat(devTools.raw).hasSize(count);
    }

    /**
     * DevTools as a test plays it: each call recorded, then answered as the page's would be, Runtime.enable after the
     * contexts it reports. A method can be made to answer nothing, refuse the call, fail or answer something else.
     */
    private static final class DevTools implements BiFunction<String, String, CompletableFuture<String>> {
        final List<String> raw = new CopyOnWriteArrayList<>();
        final List<String> events = new ArrayList<>(List.of(created(1, "", "main", true, FRAME), CREATED));
        final Map<String, Function<String, CompletableFuture<String>>> answers = new HashMap<>();
        private final List<String> methods = new CopyOnWriteArrayList<>();
        // The insertion told this DevTools' events.
        PageText owner;

        DevTools() {
            answer("Page.getFrameTree", TREE);
            answer("Page.createIsolatedWorld", WORLD);
            answer("Runtime.evaluate", TRUE);
            answers.put("Runtime.enable", parameters -> {
                for (String event : events) owner.event("Runtime.executionContextCreated", event);
                return CompletableFuture.completedFuture("{}");
            });
        }

        @Override public CompletableFuture<String> apply(String method, String parameters) {
            methods.add(method);
            raw.add(String.valueOf(parameters));
            var answer = answers.get(method);
            return answer == null ? CompletableFuture.completedFuture("{}") : answer.apply(parameters);
        }

        void answer(String method, String answer) {
            answers.put(method, parameters -> CompletableFuture.completedFuture(answer));
        }

        void never(String method) {
            answers.put(method, parameters -> new CompletableFuture<>());
        }

        void refuse(String method) {
            answers.put(method, parameters -> { throw new IllegalStateException("refused " + method); });
        }

        void fail(String method) {
            answers.put(method, parameters -> CompletableFuture.failedFuture(new IllegalArgumentException("DevTools method failed")));
        }

        List<String> methods() {
            return List.copyOf(methods);
        }

        Map<String, Object> parameters(String method) {
            int at = methods.indexOf(method);
            return at < 0 ? Map.of() : StrictJson.object(raw.get(at), 2).orElseThrow();
        }
    }

}
