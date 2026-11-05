package dev.jasper.browser.preview;

import dev.jasper.browser.api.PreviewEvent;
import dev.jasper.browser.api.PreviewFind;
import dev.jasper.browser.engine.PreviewScheme;
import dev.jasper.browser.json.StrictJson;
import dev.jasper.sdk.Variant;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.regex.PatternSyntaxException;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A preview's Java side with recorded hooks: the scripts it runs in its page, what it asks of its canvas, and what it
 * tells, over a real origin whose answers the test reads. Every call runs on the UI thread, as the API requires; the
 * background work runs at once, and its result comes back on the UI thread, which each step waits for.
 */
class PagePreviewTest {
    private final PreviewScheme scheme = new PreviewScheme();
    private final PreviewScheme.Origin origin = scheme.open();
    private final List<String> ran = new ArrayList<>(), asked = new ArrayList<>();
    private final List<Color> backgrounds = new ArrayList<>();
    private final List<PreviewEvent> told = new ArrayList<>();
    private final List<Duration> delays = new ArrayList<>();
    private final List<Runnable> waits = new ArrayList<>();
    private int backgroundTasks;
    private final JComponent canvas = new JComponent() { };
    private final Executor background = task -> {
        backgroundTasks++;
        task.run();
    };
    private final PagePreview preview = new PagePreview(origin, new PagePreview.Hooks(canvas, ran::add, backgrounds::add,
        () -> asked.add("copy"), () -> asked.add("selectAll"), () -> asked.add("focus"), () -> asked.add("close")),
        background, told::add, (delay, task) -> {
            delays.add(delay);
            waits.add(task);
            return () -> waits.remove(task);
        });

    @Test void theCanvasAndCommandsAreTheHooks() throws Exception {
        ui(() -> {
            assertThat(preview.component()).isSameAs(canvas);
            preview.copy();
            preview.selectAll();
            preview.focus();
        });
        assertThat(asked).containsExactly("copy", "selectAll", "focus");
    }

    /** Review Focus 5: the theme reaches the page before the document, and the canvas hears its background. */
    @Test void themeGoesBeforeTheFirstDocument() throws Exception {
        ui(() -> {
            preview.show(1, "<p data-b=\"0\">One</p>");
            preview.theme(PreviewCssTest.theme(Variant.DARK));
        });
        assertThat(backgrounds).containsExactly(PreviewCssTest.PALETTE.background());
        assertThat(ran).as("nothing before the page is ready").isEmpty();
        ui(() -> hear(preview, "{\"op\":\"ready\"}"));
        assertThat(ran).hasSize(2);
        assertThat(ran.getFirst()).startsWith("jasperPreview.theme(\"").endsWith(", true);");
        assertThat(ran.get(1)).isEqualTo("jasperPreview.show(\"1\");");
        String call = ran.getFirst();
        String variables = call.substring("jasperPreview.theme(".length(), call.indexOf("\", \"") + 1);
        Map<String, Object> parsed = StrictJson.object(unquote(variables), 1).orElseThrow();
        assertThat(parsed).containsEntry("--bg", "#101820").containsEntry("--font-size", "13.0px");
        assertThat(text(answer("document/1"))).isEqualTo("<p data-b=\"0\">One</p>");
        ui(() -> preview.theme(PreviewCssTest.theme(Variant.LIGHT)));
        assertThat(ran.getLast()).as("a theme once ready goes at once").startsWith("jasperPreview.theme(").endsWith(", false);");
    }

    /** Review Focus 2: typing fast sends only the latest revision, and a stale shown is never announced. */
    @Test void onlyTheLatestRevisionIsShownOrAnnounced() throws Exception {
        ui(() -> {
            preview.show(1, "one");
            preview.show(2, "two");
            preview.show(3, "three");
        });
        assertThat(answer("document/1").status()).as("only the latest document is served").isEqualTo(404);
        assertThat(text(answer("document/3"))).isEqualTo("three");
        ui(() -> hear(preview, "{\"op\":\"ready\"}"));
        assertThat(ran).containsExactly("jasperPreview.show(\"3\");");
        ui(() -> {
            hear(preview, "{\"op\":\"shown\",\"rev\":\"2\"}");
            hear(preview, "{\"op\":\"shown\",\"rev\":\"3\"}");
            hear(preview, "{\"op\":\"shown\",\"rev\":\"3\"}");
        });
        assertThat(told).containsExactly(new PreviewEvent.Shown(3));
        assertThatThrownBy(() -> preview.show(3, "again")).isInstanceOf(IllegalArgumentException.class);
        ui(() -> preview.show(4, "four"));
        assertThat(ran.getLast()).isEqualTo("jasperPreview.show(\"4\");");
        ui(() -> hear(preview, "{\"op\":\"shown\",\"rev\":\"3\"}"));
        assertThat(told).as("an older shown after a newer show").containsExactly(new PreviewEvent.Shown(3));
    }

    @Test void htmlOverSixteenMibOfUtf8IsRefused() throws Exception {
        String twoBytes = String.valueOf((char) 0xE9).repeat(8 * 1024 * 1024);
        ui(() -> preview.show(1, twoBytes));
        assertThatThrownBy(() -> preview.show(2, twoBytes + "x")).isInstanceOf(IllegalArgumentException.class);
        assertThat(answer("document/1").body()).hasSize(16 * 1024 * 1024);
    }

    @Test void anOlderRevisionsGeometryIsDropped() throws Exception {
        ready();
        ui(() -> {
            preview.show(1, "one");
            hear(preview, "{\"op\":\"shown\",\"rev\":\"1\"}");
            hear(preview, geometry(1, "0,20"));
            preview.show(2, "two");
            hear(preview, geometry(1, "0,30"));
            hear(preview, "{\"op\":\"shown\",\"rev\":\"2\"}");
            hear(preview, geometry(1, "0,40"));
            hear(preview, geometry(2, "0,50"));
        });
        assertThat(told).containsExactly(new PreviewEvent.Shown(1), new PreviewEvent.Geometry(1, new double[] {0, 20}, 900, 400),
            new PreviewEvent.Geometry(1, new double[] {0, 30}, 900, 400), new PreviewEvent.Shown(2),
            new PreviewEvent.Geometry(2, new double[] {0, 50}, 900, 400));
    }

    @Test void scrollsLinksAndRevealsGoBetweenThePageAndItsCaller() throws Exception {
        ui(() -> {
            preview.scrollTo(10);
            preview.scrollTo(250.5);
            preview.reveal("ignored-before-ready");
        });
        assertThat(ran).isEmpty();
        ui(() -> hear(preview, "{\"op\":\"ready\"}"));
        assertThat(ran).as("the last scroll").containsExactly("jasperPreview.scrollTo(\"250.5\");");
        ui(() -> {
            preview.scrollTo(Double.NaN);
            preview.reveal("intro\"</script>");
            hear(preview, "{\"op\":\"scrolled\",\"y\":\"80\",\"user\":true}");
            hear(preview, "{\"op\":\"link\",\"id\":\"3\"}");
            hear(preview, "{\"op\":\"link\",\"id\":\"x\"}");
        });
        assertThat(ran).containsExactly("jasperPreview.scrollTo(\"250.5\");", "jasperPreview.reveal(\"intro\\\"</script>\");");
        assertThat(told).containsExactly(new PreviewEvent.Scrolled(80, true), new PreviewEvent.LinkActivated(3));
    }

    @Test void theSameImageTwiceIsEncodedOnce() throws Exception {
        var image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        ui(() -> {
            preview.image(1, image);
            preview.image(2, image);
        });
        assertThat(backgroundTasks).isEqualTo(1);
        ready();
        assertThat(ran).containsExactlyInAnyOrder("jasperPreview.image(\"1\", \"/image/0.png\");", "jasperPreview.image(\"2\", \"/image/0.png\");");
        assertThat(answer("image/0.png").mimeType()).isEqualTo("image/png");
        assertThat(answer("image/0.png").body()).startsWith(0x89, 'P', 'N', 'G');
        ui(() -> preview.image(3, image));
        assertThat(backgroundTasks).as("cached by identity").isEqualTo(1);
        assertThat(ran.getLast()).isEqualTo("jasperPreview.image(\"3\", \"/image/0.png\");");
        var other = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        ui(() -> preview.image(3, other));
        assertThat(backgroundTasks).as("an equal but other image is another").isEqualTo(2);
        assertThat(ran.getLast()).isEqualTo("jasperPreview.image(\"3\", \"/image/1.png\");");
    }

    /** An image whose id no longer wants it by the time it is encoded is not set. */
    @Test void anImageReplacedWhileItIsEncodedIsNotSet() throws Exception {
        var queued = new ArrayList<Runnable>();
        var deferred = new PagePreview(origin, new PagePreview.Hooks(canvas, ran::add, backgrounds::add, () -> { }, () -> { }, () -> { },
            () -> { }), queued::add, told::add, (delay, task) -> () -> { });
        ui(() -> {
            hear(deferred, "{\"op\":\"ready\"}");
            deferred.image(1, new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            deferred.imageReason(1, "Too large");
            queued.forEach(Runnable::run);
        });
        assertThat(ran).containsExactly("jasperPreview.imageReason(\"1\", \"Too large\");");
        assertThat(answer("image/0.png").status()).as("not set in the origin either").isEqualTo(404);
    }

    @Test void clearImagesClearsTheOriginAndThePage() throws Exception {
        ready();
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ui(() -> {
            preview.image(1, image);
            preview.imageReason(2, "Load external images to see it");
        });
        assertThat(answer("image/0.png").status()).isEqualTo(200);
        ui(preview::clearImages);
        assertThat(answer("image/0.png").status()).isEqualTo(404);
        assertThat(ran).as("the image once encoded, after the reason").containsExactly(
            "jasperPreview.imageReason(\"2\", \"Load external images to see it\");", "jasperPreview.image(\"1\", \"/image/0.png\");",
            "jasperPreview.clearImages();");
        ui(() -> preview.image(1, image));
        assertThat(answer("image/0.png").status()).as("set again from the cache").isEqualTo(200);
        assertThat(backgroundTasks).isEqualTo(1);
    }

    /** N-F13: Find runs find.js in the page, and each answer completes only the request it names. */
    @Test void aReplyCompletesItsOwnRequestOnly() throws Exception {
        ready();
        var marked = new ArrayList<CompletableFuture<Integer>>();
        var selected = new ArrayList<CompletableFuture<Boolean>>();
        var text = new ArrayList<CompletableFuture<String>>();
        ui(() -> marked.add(preview.markAll(new PreviewFind("needle", true, true, false))));
        Map<String, Object> search = findCommand(ran.getLast());
        assertThat(search).containsExactly(Map.entry("action", "search"), Map.entry("search", 1L), Map.entry("query", "needle"),
            Map.entry("matchCase", true), Map.entry("wholeWord", true), Map.entry("regex", false));
        ui(() -> {
            hear(preview, "{\"op\":\"find\",\"search\":2,\"count\":5,\"invalid\":false}");
            text.add(preview.selectedText());
        });
        assertThat(marked.getFirst()).as("an answer to another request").isNotDone();
        assertThat(findCommand(ran.getLast())).containsExactly(Map.entry("action", "selection"), Map.entry("search", 2L));
        ui(() -> {
            hear(preview, "{\"op\":\"find\",\"search\":1,\"count\":5,\"invalid\":false}");
            hear(preview, "{\"op\":\"find\",\"search\":1,\"selection\":\"word\"}");
            hear(preview, "{\"op\":\"find\",\"search\":2,\"selection\":\"needle\"}");
        });
        assertThat(marked.getFirst()).isCompletedWithValue(5);
        assertThat(text.getFirst()).isCompletedWithValue("needle");
        ui(() -> selected.add(preview.select(new PreviewFind("needle", true, true, false), false)));
        assertThat(findCommand(ran.getLast())).containsEntry("action", "step").containsEntry("search", 3L).containsEntry("forward", false)
            .containsEntry("query", "needle");
        ui(() -> hear(preview, "{\"op\":\"find\",\"search\":3,\"count\":5,\"invalid\":false}"));
        assertThat(selected.getFirst()).isCompletedWithValue(true);
        ui(() -> {
            selected.add(preview.select(new PreviewFind("none", false, false, false), true));
            hear(preview, "{\"op\":\"find\",\"search\":4,\"count\":0,\"invalid\":false}");
        });
        assertThat(selected.getLast()).isCompletedWithValue(false);
        ui(preview::clearFind);
        assertThat(findCommand(ran.getLast())).containsExactly(Map.entry("action", "clear"));
    }

    @Test void anInvalidAnswerFailsWithPatternSyntaxException() throws Exception {
        ready();
        var marked = new ArrayList<CompletableFuture<Integer>>();
        var selected = new ArrayList<CompletableFuture<Boolean>>();
        ui(() -> {
            marked.add(preview.markAll(new PreviewFind("(", false, false, true)));
            hear(preview, "{\"op\":\"find\",\"search\":1,\"count\":0,\"invalid\":true}");
            selected.add(preview.select(new PreviewFind("[", false, false, true), true));
            hear(preview, "{\"op\":\"find\",\"search\":2,\"count\":0,\"invalid\":true}");
        });
        assertThatThrownBy(marked.getFirst()::get).isInstanceOf(ExecutionException.class).cause()
            .isInstanceOf(PatternSyntaxException.class).hasMessageContaining("(");
        assertThatThrownBy(selected.getFirst()::get).cause().isInstanceOf(PatternSyntaxException.class);
    }

    @Test void aNewMarkAllOrSelectCancelsTheOneWaiting() throws Exception {
        ready();
        var waiting = new ArrayList<CompletableFuture<?>>();
        ui(() -> {
            waiting.add(preview.markAll(new PreviewFind("a", false, false, false)));
            waiting.add(preview.select(new PreviewFind("a", false, false, false), true));
            waiting.add(preview.markAll(new PreviewFind("ab", false, false, false)));
        });
        assertThat(waiting.get(0)).isCancelled();
        assertThat(waiting.get(1)).isCancelled();
        assertThat(waiting.get(2)).isNotDone();
        ui(() -> hear(preview, "{\"op\":\"find\",\"search\":1,\"count\":3,\"invalid\":false}"));
        assertThat(waiting.get(2)).as("the cancelled request's late answer").isNotDone();
    }

    @Test void findBeforeThePageIsReadyAnswersAtOnceWithNothing() throws Exception {
        var marked = new ArrayList<CompletableFuture<Integer>>();
        var selected = new ArrayList<CompletableFuture<Boolean>>();
        var text = new ArrayList<CompletableFuture<String>>();
        ui(() -> {
            marked.add(preview.markAll(new PreviewFind("a", false, false, false)));
            selected.add(preview.select(new PreviewFind("a", false, false, false), true));
            text.add(preview.selectedText());
            preview.clearFind();
        });
        assertThat(marked.getFirst()).isCompletedWithValue(0);
        assertThat(selected.getFirst()).isCompletedWithValue(false);
        assertThat(text.getFirst()).isCompletedWithValue("");
        assertThat(ran).isEmpty();
    }

    /** Review Focus 2: a tab closed while a Find waits. */
    @Test void closeCancelsWaitingAnswersAndTellsNothingMore() throws Exception {
        ready();
        var waiting = new ArrayList<CompletableFuture<?>>();
        ui(() -> {
            preview.show(1, "one");
            waiting.add(preview.markAll(new PreviewFind("a", false, false, false)));
            waiting.add(preview.selectedText());
            preview.close();
            preview.close();
        });
        assertThat(waiting).allSatisfy(future -> assertThat(future).isCancelled());
        assertThat(asked).as("once").containsExactly("close");
        assertThat(answer("document/1").status()).as("the origin closed").isEqualTo(404);
        int scripts = ran.size();
        var after = new ArrayList<CompletableFuture<?>>();
        ui(() -> {
            hear(preview, "{\"op\":\"shown\",\"rev\":\"1\"}");
            hear(preview, "{\"op\":\"find\",\"search\":1,\"count\":3,\"invalid\":false}");
            preview.unavailable("Chromium stopped");
            preview.show(2, "two");
            preview.theme(PreviewCssTest.theme(Variant.DARK));
            preview.scrollTo(3);
            preview.copy();
            after.add(preview.markAll(new PreviewFind("a", false, false, false)));
            after.add(preview.select(new PreviewFind("a", false, false, false), true));
            after.add(preview.selectedText());
        });
        assertThat(told).isEmpty();
        assertThat(ran).hasSize(scripts);
        assertThat(asked).containsExactly("close");
        assertThat(backgrounds).isEmpty();
        assertThat(after).allSatisfy(future -> assertThat(future).isCancelled());
    }

    @Test void aFailedPreviewClientGivesOneUnavailable() throws Exception {
        var waiting = new ArrayList<CompletableFuture<?>>();
        ready();
        ui(() -> {
            waiting.add(preview.markAll(new PreviewFind("a", false, false, false)));
            preview.unavailable("The browser could not start");
            preview.unavailable("again");
            hear(preview, "{\"op\":\"scrolled\",\"y\":\"1\",\"user\":true}");
        });
        assertThat(told).containsExactly(new PreviewEvent.Unavailable("The browser could not start"));
        assertThat(waiting.getFirst()).isCancelled();
    }

    /** N-F15 and N16: a page that never becomes ready is unavailable thirty seconds after its browser exists. */
    @Test void aPageNotReadyThirtySecondsAfterItsBrowserExistsIsUnavailable() throws Exception {
        ui(preview::created);
        assertThat(delays).containsExactly(Duration.ofSeconds(30));
        ui(() -> List.copyOf(waits).forEach(Runnable::run));
        assertThat(told).containsExactly(new PreviewEvent.Unavailable(PagePreview.NOT_LOADED));
        ui(() -> hear(preview, "{\"op\":\"ready\"}"));
        assertThat(ran).as("nothing after Unavailable").isEmpty();
    }

    /** N16: a page that is ready but never shows the document asked for is unavailable at the same deadline. */
    @Test void aReadyPageThatNeverShowsTheDocumentAskedForIsUnavailableAtTheDeadline() throws Exception {
        ui(() -> {
            preview.created();
            preview.show(1, "one");
            hear(preview, "{\"op\":\"ready\"}");
        });
        assertThat(ran).containsExactly("jasperPreview.show(\"1\");");
        assertThat(waits).as("ready does not end the watch while the document waits").hasSize(1);
        ui(() -> List.copyOf(waits).forEach(Runnable::run));
        assertThat(told).containsExactly(new PreviewEvent.Unavailable(PagePreview.NOT_LOADED));
        ui(() -> {
            hear(preview, "{\"op\":\"shown\",\"rev\":\"1\"}");
            preview.show(2, "two");
        });
        assertThat(told).as("nothing after Unavailable").hasSize(1);
        assertThat(ran).hasSize(1);
    }

    /** N16: the first document shown ends the watch, also one older than the latest asked for; later ones have none. */
    @Test void aPageThatShowsADocumentInTimeStopsItsWatch() throws Exception {
        var waited = new ArrayList<Runnable>();
        ui(() -> {
            preview.created();
            waited.addAll(waits);
            preview.show(1, "one");
            hear(preview, "{\"op\":\"ready\"}");
            preview.show(2, "two");
            hear(preview, "{\"op\":\"shown\",\"rev\":\"1\"}");
        });
        assertThat(waits).as("the watch was stopped").isEmpty();
        ui(() -> {
            waited.forEach(Runnable::run);
            preview.show(3, "three");
        });
        assertThat(told).as("an older revision's shown is not told, and the deadline passing tells nothing").isEmpty();
        assertThat(delays).as("no watch for a later document").hasSize(1);
    }

    /** N16: a page ready by the deadline with no document asked for is not unavailable. */
    @Test void aReadyPageAskedForNothingIsNotUnavailableAtTheDeadline() throws Exception {
        ui(() -> {
            preview.created();
            hear(preview, "{\"op\":\"ready\"}");
        });
        ui(() -> List.copyOf(waits).forEach(Runnable::run));
        assertThat(told).isEmpty();
        ui(() -> preview.show(1, "one"));
        assertThat(ran).as("it still shows what it is asked").containsExactly("jasperPreview.show(\"1\");");
        assertThat(delays).as("and has no watch for it").hasSize(1);
    }

    @Test void aPreviewClosedBeforeTheDeadlineTellsNothing() throws Exception {
        var waited = new ArrayList<Runnable>();
        ui(() -> {
            preview.created();
            waited.addAll(waits);
            preview.show(1, "one");
            preview.close();
        });
        assertThat(waits).as("the watch was stopped").isEmpty();
        ui(() -> waited.forEach(Runnable::run));
        assertThat(told).isEmpty();
    }

    /** The page is ready: it has posted {@code ready}. */
    private void ready() throws Exception {
        ui(() -> hear(preview, "{\"op\":\"ready\"}"));
    }

    /** {@code to} hears what the page sent as {@code request}, read as the page's wiring reads it: nothing for no message. */
    static void hear(PagePreview to, String request) {
        PreviewMessages.read(request).ifPresent(to::received);
    }

    private static String geometry(long revision, String tops) {
        return "{\"op\":\"geometry\",\"rev\":\"" + revision + "\",\"tops\":\"" + tops + "\",\"height\":\"900\",\"viewport\":\"400\"}";
    }

    private PreviewScheme.Answer answer(String path) {
        return scheme.answer("GET", origin.shell() + path);
    }

    private static String text(PreviewScheme.Answer answer) {
        assertThat(answer.status()).isEqualTo(200);
        return new String(answer.body(), StandardCharsets.UTF_8);
    }

    /** The JSON string literal {@code literal}, read back. */
    private static String unquote(String literal) {
        return (String) StrictJson.object("{\"v\":" + literal + "}", 1).orElseThrow().get("v");
    }

    /** The command a script calls {@code find.js} with, as its members: the script is find.js, then that call. */
    static Map<String, Object> findCommand(String script) {
        assertThat(script).startsWith("/*").contains("CSS.highlights");
        String call = script.substring(script.lastIndexOf("\n({") + 2);
        String suffix = ", \"jasperPreviewQuery\");";
        assertThat(call).as("answered through the preview's router").endsWith(suffix);
        return StrictJson.object(call.substring(0, call.length() - suffix.length()), 1).orElseThrow();
    }

    /** Runs {@code step} on the UI thread, then whatever it handed the UI thread. */
    private static void ui(Runnable step) throws Exception {
        EventQueue.invokeAndWait(step);
        EventQueue.invokeAndWait(() -> { });
    }
}
