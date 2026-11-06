package dev.jasper.browser.page;

import dev.jasper.browser.permissions.Permission;
import dev.jasper.browser.permissions.PermissionRequest;
import dev.jasper.browser.store.CapturedLog;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.cef.callback.CefMediaAccessCallback;
import org.cef.callback.CefMediaAccessCallback.MediaPermissionFlags;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pages' camera and microphone (spec section 12, plan 6 Task 5): which permission Chromium's flags ask for, and what the
 * client's permission handler does with a request. The page's top-level site asks (ruling V16), through the page's
 * privacy hooks, which its item answers; an allowed answer continues the request with exactly what it asked for, and
 * anything else cancels it, once. A browser that is no page's, a page with no site, screen capture and no device are
 * cancelled at once, asking nothing. Chromium's side is played by the test: its flags, the address its main frame shows
 * and a recording callback. No Chromium.
 */
class MediaAccessTest {
    private static final int AUDIO = MediaPermissionFlags.DEVICE_AUDIO_CAPTURE, VIDEO = MediaPermissionFlags.DEVICE_VIDEO_CAPTURE,
        DESKTOP_AUDIO = MediaPermissionFlags.DESKTOP_AUDIO_CAPTURE, DESKTOP_VIDEO = MediaPermissionFlags.DESKTOP_VIDEO_CAPTURE;

    @Test void theFlagsGiveTheCameraTheMicrophoneOrBothAndScreenCaptureOrNoDeviceGivesNothing() {
        assertThat(MediaPermissionFlags.NONE).isZero();
        assertThat(MediaAccess.permission(VIDEO)).contains(Permission.CAMERA);
        assertThat(MediaAccess.permission(AUDIO)).contains(Permission.MICROPHONE);
        assertThat(MediaAccess.permission(VIDEO | AUDIO)).contains(Permission.CAMERA_AND_MICROPHONE);
        assertThat(MediaAccess.permission(MediaPermissionFlags.NONE)).as("no device").isEmpty();
        for (int screen : List.of(DESKTOP_AUDIO, DESKTOP_VIDEO, DESKTOP_AUDIO | DESKTOP_VIDEO, DESKTOP_VIDEO | AUDIO,
                DESKTOP_AUDIO | VIDEO, DESKTOP_VIDEO | VIDEO | AUDIO))
            assertThat(MediaAccess.permission(screen)).as("screen capture, " + screen).isEmpty();
        assertThat(MediaAccess.permission(16)).as("a flag Jasper does not know").isEmpty();
        assertThat(MediaAccess.permission(16 | VIDEO)).isEmpty();
        assertThat(MediaAccess.permission(-1)).isEmpty();
    }

    /** Ruling F26: nothing asks outside Jasper's prompt, so a browser that is no page's, as DevTools', is cancelled. */
    @Test void aBrowserThatIsNoPagesIsCancelledAndTheRequestHandled() {
        var callback = new Callback();
        assertThat(new MediaAccess(() -> false).onRequestMediaAccessPermission(null, null, "https://example.com", VIDEO, callback))
            .as("handled, so Chromium shows nothing of its own").isTrue();
        assertThat(callback.calls).containsExactly("cancel");
    }

    /**
     * Ruling V16: the page's top-level site asks, as its main frame's address names it, whatever frame asked; an allowed
     * answer continues with what the request asked for, and a refusal cancels it, each once.
     */
    @Test void theTopLevelSiteAsksForWhatTheFlagsNameAndTheAnswerContinuesOrCancelsOnce() throws Exception {
        var asked = new CopyOnWriteArrayList<PermissionRequest>();
        var answers = new CopyOnWriteArrayList<Consumer<Boolean>>();
        Page page = onUi(() -> page((request, answer) -> {
            asked.add(request);
            answers.add(answer);
        }));
        var access = new MediaAccess(() -> false);
        var camera = new Callback();
        access.ask(page, "https://ada:secret@Camera.Example:443/call?x=1#y", VIDEO, camera::allow, camera::cancel);
        var both = new Callback();
        access.ask(page, "https://camera.example/call", VIDEO | AUDIO, both::allow, both::cancel);
        var microphone = new Callback();
        access.ask(page, "http://localhost:8080/", AUDIO, microphone::allow, microphone::cancel);
        onUi(() -> null);
        assertThat(asked).containsExactly(new PermissionRequest("https://camera.example", Permission.CAMERA),
            new PermissionRequest("https://camera.example", Permission.CAMERA_AND_MICROPHONE),
            new PermissionRequest("http://localhost:8080", Permission.MICROPHONE));
        assertThat(camera.calls).as("unanswered until the item answers").isEmpty();
        onUi(() -> {
            answers.get(0).accept(true);
            answers.get(1).accept(true);
            answers.get(2).accept(false);
            answers.get(0).accept(false);
            answers.get(2).accept(true);
            return null;
        });
        assertThat(camera.calls).as("what it asked for, once").containsExactly("continue " + VIDEO);
        assertThat(both.calls).containsExactly("continue " + (VIDEO | AUDIO));
        assertThat(microphone.calls).containsExactly("cancel");
    }

    /** Spec section 12: a page with no site of its own, screen capture and a request for no device ask nothing. */
    @Test void noSiteScreenCaptureAndNoDeviceAreCancelledAtOnceAskingNothing() throws Exception {
        var asked = new CopyOnWriteArrayList<PermissionRequest>();
        Page page = onUi(() -> page((request, answer) -> asked.add(request)));
        var access = new MediaAccess(() -> false);
        var cancelled = new CopyOnWriteArrayList<String>();
        for (String address : List.of("jasper-about:welcome", "about:blank", "data:text/html,<p>", "", "https://", "ftp://example.com/")) {
            var callback = new Callback();
            access.ask(page, address, VIDEO, callback::allow, callback::cancel);
            cancelled.addAll(callback.calls);
        }
        for (int flags : List.of(DESKTOP_VIDEO, DESKTOP_AUDIO | AUDIO, MediaPermissionFlags.NONE)) {
            var callback = new Callback();
            access.ask(page, "https://example.com/", flags, callback::allow, callback::cancel);
            cancelled.addAll(callback.calls);
        }
        assertThat(cancelled).as("each cancelled at once, on the asking thread").containsOnly("cancel").hasSize(9);
        onUi(() -> null);
        assertThat(asked).isEmpty();
    }

    /** A page with nobody to answer, a closed one, and one whose item fails to answer are refused: the request is cancelled. */
    @Test void aPageWithNoOneToAnswerAClosedOneOrAFailingItemIsCancelled() throws Exception {
        Page unanswered = onUi(() -> new Page(URI.create("https://example.com"), () -> { }, PageTestAccess.noFavicons(), Runnable::run,
            PageContext.persistent()));
        var asked = new CopyOnWriteArrayList<PermissionRequest>();
        Page closed = onUi(() -> {
            Page page = page((request, answer) -> asked.add(request));
            page.close();
            return page;
        });
        Page failing = onUi(() -> page((request, answer) -> { throw new IllegalStateException("broken"); }));
        var access = new MediaAccess(() -> false);
        var first = new Callback();
        access.ask(unanswered, "https://example.com/", VIDEO, first::allow, first::cancel);
        var second = new Callback();
        access.ask(closed, "https://example.com/", AUDIO, second::allow, second::cancel);
        var third = new Callback();
        try (var log = new CapturedLog(PagePrivacy.class.getName())) {
            access.ask(failing, "https://example.com/", AUDIO, third::allow, third::cancel);
            onUi(() -> null);
            assertThat(log.records()).singleElement().satisfies(record -> {
                assertThat(record.getLevel()).isEqualTo(java.util.logging.Level.WARNING);
                assertThat(record.getThrown()).hasMessage("broken");
            });
        }
        assertThat(first.calls).containsExactly("cancel");
        assertThat(second.calls).containsExactly("cancel");
        assertThat(third.calls).containsExactly("cancel");
        assertThat(asked).as("a closed page's item is not asked").isEmpty();
    }

    /** Ruling F43: once Chromium has stopped, an answer that comes late touches no native callback. */
    @Test void anAnswerAfterChromiumStoppedCallsNothing() throws Exception {
        var answers = new CopyOnWriteArrayList<Consumer<Boolean>>();
        Page page = onUi(() -> page((request, answer) -> answers.add(answer)));
        var stopped = new AtomicBoolean();
        var access = new MediaAccess(stopped::get);
        var allowed = new Callback();
        access.ask(page, "https://example.com/", VIDEO, allowed::allow, allowed::cancel);
        var refused = new Callback();
        access.ask(page, "https://example.com/", AUDIO, refused::allow, refused::cancel);
        onUi(() -> null);
        stopped.set(true);
        onUi(() -> {
            answers.get(0).accept(true);
            answers.get(1).accept(false);
            return null;
        });
        assertThat(allowed.calls).isEmpty();
        assertThat(refused.calls).isEmpty();
    }

    /** A page whose item answers through {@code handler}. UI thread. */
    private static Page page(java.util.function.BiConsumer<PermissionRequest, Consumer<Boolean>> handler) {
        var page = new Page(URI.create("https://example.com"), () -> { }, PageTestAccess.noFavicons(), Runnable::run, PageContext.persistent());
        page.privacy().onPermissionRequest(handler);
        return page;
    }

    private static <T> T onUi(java.util.concurrent.Callable<T> task) throws Exception {
        var result = new java.util.concurrent.atomic.AtomicReference<T>();
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(task.call());
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        // What the task posted runs before the next step.
        SwingUtilities.invokeAndWait(() -> { });
        if (failure.get() instanceof Error error) throw error;
        if (failure.get() instanceof Exception exception) throw exception;
        return result.get();
    }

    /** Chromium's callback for one request: what it was told, as {@code continue <flags>} or {@code cancel}, in order. */
    private static final class Callback implements CefMediaAccessCallback {
        final List<String> calls = new CopyOnWriteArrayList<>();

        @Override public void Continue(int allowed) {
            allow(allowed);
        }

        @Override public void Cancel() {
            cancel();
        }

        void allow(int allowed) {
            calls.add("continue " + allowed);
        }

        void cancel() {
            calls.add("cancel");
        }
    }
}
