package dev.jasper.browser.dialogs;

import dev.jasper.browser.item.BrowserItem;
import dev.jasper.browser.item.BrowserItems;
import dev.jasper.browser.item.DataStores;
import dev.jasper.browser.item.ItemTestAccess;
import dev.jasper.browser.page.DialogPlace;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.permissions.Decision;
import dev.jasper.browser.permissions.Permission;
import dev.jasper.browser.permissions.PermissionRequest;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TabPlacement;
import dev.jasper.sdk.ui.UiEvent;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A site's permission prompt (spec section 12, plan 6 Task 5): Jasper's modal over the page's item, headed "Allow
 * Camera" and the like, which says which site wants what, with Block and Allow; Escape and its own close dismiss it.
 * It waits while its item is not its window's active item; a page shows one at a time, an equal request joins the
 * prompt asked already and the others wait in order; the page's close, Chromium's reset of its dialogs and a main-frame
 * load start dismiss the prompt shown and those waiting; a page whose modals come one straight after another is refused
 * the third until it loads again (rulings F25 and R43), its alerts and prompts counted together. Each asker is answered
 * exactly once. The pages are stand-in items' own, open on the testkit's fake host, so no Chromium is needed.
 */
class PermissionPromptTest {
    private static final String ID = "dev.jasper.browser";
    private static final String SITE = "https://camera.example";
    private static final PermissionRequest CAMERA = new PermissionRequest(SITE, Permission.CAMERA),
        MICROPHONE = new PermissionRequest(SITE, Permission.MICROPHONE);
    // The double-click interval the prompts are given: no Allow or Block counts until it has passed.
    private static final long INTERVAL = 500;
    private final MemoryStores memory = new MemoryStores();
    private Shown shown;

    @AfterEach void closeHost() throws Exception {
        if (shown != null) onUiThread(shown::close);
        memory.close();
    }

    /** Each prompt is an overlay over its item with the permission's heading, the site's request, then Block and Allow. */
    @Test void eachPromptIsHeadedByItsPermissionAndSaysWhichSiteWantsItWithBlockThenAllow() throws Exception {
        shown = show();
        Map<Permission, String> texts = Map.of(
            Permission.CAMERA, "The website https://camera.example wants to use your camera.",
            Permission.MICROPHONE, "The website https://camera.example wants to use your microphone.",
            Permission.CAMERA_AND_MICROPHONE, "The website https://camera.example wants to use your camera and microphone.",
            Permission.NOTIFICATIONS, "The website https://camera.example wants to send you notifications.");
        LongSupplier unhurried = unhurried();
        for (Permission permission : List.of(Permission.CAMERA, Permission.MICROPHONE, Permission.CAMERA_AND_MICROPHONE, Permission.NOTIFICATIONS)) {
            var answers = new Answers();
            String heading = "Allow " + permission.label();
            onUiThread(() -> ask(new PermissionRequest(SITE, permission), answers, unhurried));
            onUiThread(() -> {
                assertThat(shown.host.dialogPresentation(ID, heading)).as(heading).contains(DialogSpec.Presentation.OVERLAY);
                Nodes.Form form = form(heading);
                assertThat(form.kind()).isEqualTo(Nodes.FormKind.SHEET);
                assertThat(form.title()).isEqualTo(heading);
                assertThat(form.sections()).singleElement().isInstanceOfSatisfying(Nodes.Text.class, text -> {
                    assertThat(text.text()).isEqualTo(texts.get(permission));
                    assertThat(text.role()).isEqualTo(Nodes.TextRole.BODY);
                });
                assertThat(form.actions()).extracting(Nodes.Button::label).containsExactly("Block", "Allow");
                assertThat(form.actions()).extracting(Nodes.Button::kind).containsExactly(Nodes.ButtonKind.SECONDARY, Nodes.ButtonKind.PRIMARY);
                assertThat(form.cancelAction()).as("Escape dismisses, neither blocking nor allowing").isNotBlank()
                    .isNotIn(form.actions().get(0).action(), form.actions().get(1).action());
                assertThat(form.submission()).as("Enter allows nothing a page could time a prompt for").isEqualTo(Nodes.FormSubmission.NONE);
                assertThat(answers.calls).as("unanswered while it shows").isEmpty();
                press(heading, form.cancelAction());
                assertThat(answers.calls).containsExactly("dismissed");
                assertThat(shown.host.windows()).isEmpty();
            });
        }
        onUiThread(() -> assertThat(shown.host.failures()).isEmpty());
    }

    @Test void allowAndBlockAnswerTheirDecisionAndEscapeAndItsOwnCloseNone() throws Exception {
        shown = show();
        LongSupplier unhurried = unhurried();
        var allowed = new Answers();
        onUiThread(() -> ask(CAMERA, allowed, unhurried));
        onUiThread(() -> press("Allow Camera", form("Allow Camera").actions().getLast().action()));
        var blocked = new Answers();
        onUiThread(() -> ask(CAMERA, blocked, unhurried));
        onUiThread(() -> press("Allow Camera", form("Allow Camera").actions().getFirst().action()));
        var escaped = new Answers();
        onUiThread(() -> ask(CAMERA, escaped, unhurried));
        onUiThread(() -> press("Allow Camera", form("Allow Camera").cancelAction()));
        var closed = new Answers();
        onUiThread(() -> ask(CAMERA, closed, unhurried));
        onUiThread(() -> {
            assertThat(shown.host.requestClose("dialog")).as("the prompt's own close").isTrue();
            assertThat(allowed.calls).containsExactly("ALLOWED");
            assertThat(blocked.calls).containsExactly("BLOCKED");
            assertThat(escaped.calls).containsExactly("dismissed");
            assertThat(closed.calls).containsExactly("dismissed");
            assertThat(shown.host.windows()).isEmpty();
            assertThat(shown.host.failures()).isEmpty();
        });
    }

    /** A page the user is not looking at cannot cover the window: its prompt waits until its item is active. */
    @Test void aBackgroundItemsPromptWaitsUntilItsItemIsActive() throws Exception {
        shown = show();
        var other = new AtomicReference<BrowserItem>();
        onUiThread(() -> other.set(shown.items.open(URI.create("https://example.org"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty())));
        var answers = new Answers();
        onUiThread(() -> ask(MICROPHONE, answers, unhurried()));
        onUiThread(() -> {
            assertThat(shown.host.windows()).as("waiting").isEmpty();
            assertThat(answers.calls).isEmpty();
            shown.item.tab().select();
        });
        onUiThread(() -> {
            assertThat(shown.host.windowView(ID, "Allow Microphone")).as("its item is active").isPresent();
            press("Allow Microphone", form("Allow Microphone").actions().getLast().action());
            assertThat(answers.calls).containsExactly("ALLOWED");
            other.get().tab().select();
            shown.item.tab().select();
        });
        onUiThread(() -> {
            assertThat(shown.host.windows()).as("an answered prompt does not come back").isEmpty();
            assertThat(answers.calls).containsExactly("ALLOWED");
        });
    }

    /** Plan 6's third review focus: an item that closes while its prompts wait answers each once, unshown. */
    @Test void anItemThatClosesWhileItsPromptsWaitAnswersEachOnceUnshown() throws Exception {
        shown = show();
        var other = new AtomicReference<BrowserItem>();
        onUiThread(() -> other.set(shown.items.open(URI.create("https://example.org"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty())));
        var camera = new Answers();
        var microphone = new Answers();
        onUiThread(() -> {
            LongSupplier unhurried = unhurried();
            ask(CAMERA, camera, unhurried);
            ask(MICROPHONE, microphone, unhurried);
        });
        onUiThread(() -> {
            assertThat(camera.calls).isEmpty();
            shown.item.close();
            assertThat(camera.calls).containsExactly("dismissed");
            assertThat(microphone.calls).containsExactly("dismissed");
            other.get().tab().select();
        });
        var late = new Answers();
        onUiThread(() -> ask(CAMERA, late, unhurried()));
        onUiThread(() -> {
            assertThat(shown.host.windows()).isEmpty();
            assertThat(camera.calls).as("once").containsExactly("dismissed");
            assertThat(microphone.calls).containsExactly("dismissed");
            assertThat(late.calls).as("a closed page is answered at once").containsExactly("dismissed");
            assertThat(shown.host.failures()).isEmpty();
        });
    }

    /**
     * A main-frame load start takes the page's prompts away, the one shown and those waiting, each answered once; the
     * new document's guard counts none of them, so it may show two quick prompts of its own.
     */
    @Test void aMainFrameLoadStartDismissesTheShownPromptAndTheWaitingOnesEachOnce() throws Exception {
        shown = show();
        var clock = new AtomicLong();
        var camera = new Answers();
        var microphone = new Answers();
        var notifications = new Answers();
        onUiThread(() -> {
            ask(CAMERA, camera, clock::get);
            ask(MICROPHONE, microphone, clock::get);
            ask(new PermissionRequest("https://other.example", Permission.NOTIFICATIONS), notifications, clock::get);
        });
        onUiThread(() -> {
            assertThat(shown.host.windowView(ID, "Allow Camera")).isPresent();
            clock.set(100);
            PageTestAccess.loadStarted(shown.page, "https://camera.example/next");
            assertThat(shown.host.windows()).isEmpty();
            assertThat(camera.calls).containsExactly("dismissed");
            assertThat(microphone.calls).containsExactly("dismissed");
            assertThat(notifications.calls).containsExactly("dismissed");
        });
        onUiThread(() -> {
            assertThat(shown.host.windows()).as("no waiting prompt comes after").isEmpty();
            assertThat(camera.calls).containsExactly("dismissed");
            assertThat(microphone.calls).containsExactly("dismissed");
            assertThat(notifications.calls).containsExactly("dismissed");
        });
        var next = new Answers();
        onUiThread(() -> {
            clock.set(200);
            ask(CAMERA, next, clock::get);
        });
        onUiThread(() -> {
            assertThat(shown.host.windowView(ID, "Allow Camera")).as("the new document may ask").isPresent();
            clock.set(200 + INTERVAL);
            press("Allow Camera", form("Allow Camera").actions().getLast().action());
            assertThat(next.calls).containsExactly("ALLOWED");
        });
        var second = new Answers();
        onUiThread(() -> {
            clock.set(300 + INTERVAL);
            ask(MICROPHONE, second, clock::get);
        });
        onUiThread(() -> {
            assertThat(shown.host.windowView(ID, "Allow Microphone")).as("its second quick prompt").isPresent();
            press("Allow Microphone", form("Allow Microphone").cancelAction());
            assertThat(second.calls).containsExactly("dismissed");
            assertThat(shown.host.failures()).isEmpty();
        });
    }

    /** Chromium's reset of the page's dialogs takes its prompts away too, shown and waiting. */
    @Test void aResetOfThePagesDialogsDismissesTheShownPromptAndTheWaitingOnes() throws Exception {
        shown = show();
        var camera = new Answers();
        var microphone = new Answers();
        onUiThread(() -> {
            LongSupplier unhurried = unhurried();
            ask(CAMERA, camera, unhurried);
            ask(MICROPHONE, microphone, unhurried);
        });
        onUiThread(() -> {
            shown.page.dialogsReset();
            assertThat(camera.calls).containsExactly("dismissed");
            assertThat(microphone.calls).containsExactly("dismissed");
        });
        onUiThread(() -> {
            assertThat(shown.host.windows()).isEmpty();
            assertThat(shown.host.failures()).isEmpty();
        });
    }

    /**
     * A page shows one prompt at a time: a request equal to one asked already shares its prompt and its answer, and a
     * different one waits, then shows once the first is answered.
     */
    @Test void equalRequestsShareOnePromptAndADifferentOneComesAfter() throws Exception {
        shown = show();
        var first = new Answers();
        var joined = new Answers();
        var different = new Answers();
        var joinedWaiting = new Answers();
        onUiThread(() -> {
            LongSupplier unhurried = unhurried();
            ask(CAMERA, first, unhurried);
            ask(MICROPHONE, different, unhurried);
            ask(new PermissionRequest(SITE, Permission.CAMERA), joined, unhurried);
            ask(new PermissionRequest(SITE, Permission.MICROPHONE), joinedWaiting, unhurried);
        });
        onUiThread(() -> {
            assertThat(shown.host.windows()).as("one prompt").hasSize(1);
            assertThat(shown.host.windowView(ID, "Allow Camera")).isPresent();
            press("Allow Camera", form("Allow Camera").actions().getFirst().action());
            assertThat(first.calls).containsExactly("BLOCKED");
            assertThat(joined.calls).as("the same answer").containsExactly("BLOCKED");
            assertThat(different.calls).isEmpty();
        });
        onUiThread(() -> {
            assertThat(shown.host.windows()).as("then the next").hasSize(1);
            assertThat(shown.host.windowView(ID, "Allow Microphone")).isPresent();
            press("Allow Microphone", form("Allow Microphone").actions().getLast().action());
            assertThat(different.calls).containsExactly("ALLOWED");
            assertThat(joinedWaiting.calls).as("joined while it waited").containsExactly("ALLOWED");
        });
        onUiThread(() -> {
            assertThat(shown.host.windows()).isEmpty();
            assertThat(first.calls).containsExactly("BLOCKED");
            assertThat(shown.host.failures()).isEmpty();
        });
    }

    /**
     * Ruling F25 with R43's guard: the third modal a page opens within a second of the one before closing is refused
     * unshown, prompts and alerts counted together, as is every later one, until the page loads again.
     */
    @Test void aPagesThirdQuickModalIsRefusedUntilItLoadsAgainItsAlertsAndPromptsCountedTogether() throws Exception {
        shown = show();
        var clock = new AtomicLong();
        var alert = new CopyOnWriteArrayList<String>();
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.ALERT, "https://camera.example/", "Hello", (ok, text) -> alert.add(ok + ""), clock::get);
        onUiThread(() -> {
            clock.set(100);
            press("Web Page Alert", form("Web Page Alert").actions().getFirst().action());
        });
        var second = new Answers();
        onUiThread(() -> {
            clock.set(500);
            ask(CAMERA, second, clock::get);
        });
        onUiThread(() -> {
            assertThat(shown.host.windowView(ID, "Allow Camera")).as("the second modal shows").isPresent();
            clock.set(700);
            press("Allow Camera", form("Allow Camera").cancelAction());
        });
        var third = new Answers();
        var waiting = new Answers();
        onUiThread(() -> {
            clock.set(1_200);
            ask(MICROPHONE, third, clock::get);
            ask(new PermissionRequest(SITE, Permission.NOTIFICATIONS), waiting, clock::get);
        });
        onUiThread(() -> {
            assertThat(shown.host.windows()).as("refused unshown").isEmpty();
            assertThat(alert).containsExactly("true");
            assertThat(second.calls).containsExactly("dismissed");
            assertThat(third.calls).containsExactly("dismissed");
            assertThat(waiting.calls).containsExactly("dismissed");
            clock.set(90_000);
        });
        var later = new Answers();
        onUiThread(() -> ask(CAMERA, later, clock::get));
        onUiThread(() -> {
            assertThat(later.calls).as("refused until the page loads again, however late").containsExactly("dismissed");
            PageTestAccess.loadStarted(shown.page, "https://camera.example/next");
        });
        var reloaded = new Answers();
        onUiThread(() -> ask(CAMERA, reloaded, clock::get));
        onUiThread(() -> {
            assertThat(shown.host.windowView(ID, "Allow Camera")).as("a new document may ask again").isPresent();
            clock.set(90_000 + INTERVAL);
            press("Allow Camera", form("Allow Camera").actions().getLast().action());
            assertThat(reloaded.calls).containsExactly("ALLOWED");
            assertThat(shown.host.failures()).isEmpty();
        });
    }

    /**
     * Ruling V19: a page cannot time a click onto Allow. Until the double-click interval has passed since the prompt
     * showed, Allow and Block are ignored, from the mouse and the keyboard alike, so a double-click lure's second click
     * does nothing; Escape still dismisses, as dismissing grants nothing.
     */
    @Test void allowAndBlockAreIgnoredUntilTheDoubleClickIntervalHasPassedAndEscapeIsNot() throws Exception {
        shown = show();
        var clock = new AtomicLong(10_000);
        var camera = new Answers();
        onUiThread(() -> ask(CAMERA, camera, clock::get));
        onUiThread(() -> {
            Nodes.Form form = form("Allow Camera");
            clock.set(10_001);
            press("Allow Camera", form.actions().getLast().action());
            press("Allow Camera", form.actions().getFirst().action());
            clock.set(10_000 + INTERVAL - 1);
            press("Allow Camera", form.actions().getLast().action());
            assertThat(camera.calls).as("ignored inside the interval").isEmpty();
            assertThat(shown.host.windowView(ID, "Allow Camera")).as("still asking").isPresent();
            clock.set(10_000 + INTERVAL);
            press("Allow Camera", form.actions().getLast().action());
            assertThat(camera.calls).containsExactly("ALLOWED");
        });
        var blocked = new Answers();
        onUiThread(() -> {
            clock.set(20_000);
            ask(MICROPHONE, blocked, clock::get);
        });
        onUiThread(() -> {
            clock.set(20_000 + INTERVAL);
            press("Allow Microphone", form("Allow Microphone").actions().getFirst().action());
            assertThat(blocked.calls).containsExactly("BLOCKED");
        });
        var escaped = new Answers();
        onUiThread(() -> {
            clock.set(30_000);
            ask(CAMERA, escaped, clock::get);
        });
        onUiThread(() -> {
            clock.set(30_001);
            press("Allow Camera", form("Allow Camera").cancelAction());
            assertThat(escaped.calls).as("Escape at once").containsExactly("dismissed");
            assertThat(shown.host.windows()).isEmpty();
            assertThat(shown.host.failures()).isEmpty();
        });
    }

    /**
     * Ruling V19: nothing of the page's can stack over its prompt, so a page's alert, confirm or prompt asked while its
     * permission prompt is asked waits, and shows once the prompt has been answered; one Chromium resets meanwhile goes
     * unshown, answered once.
     */
    @Test void aPagesScriptDialogWaitsWhileItsPromptIsAskedAndShowsOnceItIsAnswered() throws Exception {
        shown = show();
        var clock = new AtomicLong(10_000);
        var camera = new Answers();
        onUiThread(() -> ask(CAMERA, camera, clock::get));
        var alert = new CopyOnWriteArrayList<String>();
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.ALERT, "https://camera.example/", "Over it", (ok, text) -> alert.add(ok + ""), clock::get);
        onUiThread(() -> {
            assertThat(shown.host.windowView(ID, "Web Page Alert")).as("held while the prompt shows").isEmpty();
            assertThat(shown.host.windows()).hasSize(1);
            clock.set(12_000);
            press("Allow Camera", form("Allow Camera").actions().getLast().action());
            assertThat(camera.calls).containsExactly("ALLOWED");
        });
        onUiThread(() -> {
            assertThat(shown.host.windowView(ID, "Web Page Alert")).as("once the prompt is answered").isPresent();
            assertThat(alert).isEmpty();
            press("Web Page Alert", form("Web Page Alert").actions().getFirst().action());
            assertThat(alert).containsExactly("true");
        });
        var microphone = new Answers();
        onUiThread(() -> {
            clock.set(30_000);
            ask(MICROPHONE, microphone, clock::get);
        });
        var confirm = new CopyOnWriteArrayList<String>();
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.CONFIRM, "https://camera.example/", "Reset?", (ok, text) -> confirm.add(ok + ""), clock::get);
        onUiThread(() -> {
            assertThat(shown.host.windowView(ID, "Web Page Confirmation")).isEmpty();
            shown.page.dialogsReset();
            assertThat(microphone.calls).containsExactly("dismissed");
            assertThat(confirm).as("answered once, unshown").containsExactly("false");
        });
        onUiThread(() -> {
            assertThat(shown.host.windows()).as("the reset confirm never shows").isEmpty();
            assertThat(confirm).containsExactly("false");
            assertThat(shown.host.failures()).isEmpty();
        });
    }

    /**
     * Ruling V19: the interval starts again when another of the page's modals closes while the prompt shows, as an
     * alert under it does, so the second click of a double-click on that alert's OK cannot land on Allow.
     */
    @Test void theIntervalStartsAgainWhenAnotherModalOfThePageClosesWhileThePromptShows() throws Exception {
        shown = show();
        var clock = new AtomicLong(10_000);
        var alert = new CopyOnWriteArrayList<String>();
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.ALERT, "https://camera.example/", "Under it", (ok, text) -> alert.add(ok + ""), clock::get);
        var camera = new Answers();
        onUiThread(() -> {
            assertThat(shown.host.windowView(ID, "Web Page Alert")).isPresent();
            clock.set(10_100);
            ask(CAMERA, camera, clock::get);
        });
        onUiThread(() -> {
            assertThat(shown.host.windowView(ID, "Allow Camera")).as("over the alert").isPresent();
            clock.set(11_000);
            press("Web Page Alert", form("Web Page Alert").actions().getFirst().action());
            assertThat(alert).containsExactly("true");
            clock.set(11_000 + INTERVAL - 1);
            press("Allow Camera", form("Allow Camera").actions().getLast().action());
            assertThat(camera.calls).as("inside the interval the alert's close started again").isEmpty();
            clock.set(11_000 + INTERVAL);
            press("Allow Camera", form("Allow Camera").actions().getLast().action());
            assertThat(camera.calls).containsExactly("ALLOWED");
            assertThat(shown.host.failures()).isEmpty();
        });
    }

    /** The interval is the platform's double-click interval, or 500 ms when the platform names none that will do. */
    @Test void theIntervalIsThePlatformsDoubleClickIntervalOr500Milliseconds() {
        assertThat(PermissionPrompt.clickInterval(null)).isEqualTo(500);
        assertThat(PermissionPrompt.clickInterval(300)).isEqualTo(300);
        assertThat(PermissionPrompt.clickInterval(2_000)).isEqualTo(2_000);
        assertThat(PermissionPrompt.clickInterval("fast")).isEqualTo(500);
        assertThat(PermissionPrompt.clickInterval(0)).isEqualTo(500);
        assertThat(PermissionPrompt.clickInterval(-5)).isEqualTo(500);
    }

    /** The plugin's stop dismisses a page's prompts, shown and waiting, while Chromium still runs (ruling F43). */
    @Test void dismissingAPagesPromptsAnswersTheShownAndWaitingOnesOnce() throws Exception {
        shown = show();
        var camera = new Answers();
        var microphone = new Answers();
        onUiThread(() -> {
            LongSupplier unhurried = unhurried();
            ask(CAMERA, camera, unhurried);
            ask(MICROPHONE, microphone, unhurried);
        });
        onUiThread(() -> {
            PermissionPrompt.dismiss(shown.page);
            assertThat(camera.calls).containsExactly("dismissed");
            assertThat(microphone.calls).containsExactly("dismissed");
            PermissionPrompt.dismiss(shown.page);
        });
        onUiThread(() -> {
            assertThat(shown.host.windows()).isEmpty();
            assertThat(camera.calls).containsExactly("dismissed");
            assertThat(microphone.calls).containsExactly("dismissed");
            assertThat(shown.host.failures()).isEmpty();
        });
    }

    /** Asks {@code request} over the shown item's page, as its item does. UI thread. */
    private void ask(PermissionRequest request, Consumer<Optional<Decision>> answer, LongSupplier clock) {
        DialogPlace place = shown.page.dialogPlace().orElseGet(() -> shown.place);
        PermissionPrompt.ask(place, shown.page, request, answer, clock, INTERVAL);
    }

    /** A clock a user who takes their time answers by: each reading is 10 s after the last, so no prompt comes quickly. */
    private static LongSupplier unhurried() {
        var now = new AtomicLong();
        return () -> now.addAndGet(10_000);
    }

    /** The form of the open dialog titled {@code title}. UI thread. */
    private Nodes.Form form(String title) {
        return (Nodes.Form) view(title).spec().root();
    }

    private FakeKitView view(String title) {
        return shown.host.windowView(ID, title).orElseThrow(() -> new AssertionError("no dialog " + title));
    }

    /** Presses the button, or the key, that reports {@code action}. UI thread. */
    private void press(String title, String action) {
        view(title).emit(new UiEvent.Action(action));
    }

    /** A started stand-in Browser on a fake host, with one browser item open in a window. */
    private Shown show() throws Exception {
        var result = new AtomicReference<Shown>();
        onUiThread(() -> {
            var host = new FakePluginHost();
            var started = new AtomicReference<BrowserItems>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(),
                plugin -> started.set(new BrowserItems(plugin, BrowserSettings.declare(plugin.settings())::read, new DataStores(memory.make(), () -> false), page -> { })));
            var items = started.get();
            host.activateTerminalWindow(host.addTerminalWindow());
            BrowserItem item = items.open(URI.create("https://camera.example/call"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            Page page = ItemTestAccess.page(item);
            result.set(new Shown(host, items, item, page, page.dialogPlace().orElseThrow()));
        });
        return result.get();
    }

    private static void onUiThread(ThrowingRunnable body) throws Exception {
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                body.run();
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        // What the body posted, such as the next prompt's show, runs before the next step.
        SwingUtilities.invokeAndWait(() -> { });
        if (failure.get() instanceof Exception exception) throw exception;
        if (failure.get() instanceof Error error) throw error;
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private record Shown(FakePluginHost host, BrowserItems items, BrowserItem item, Page page, DialogPlace place) {
        void close() {
            host.close();
        }
    }

    /** A prompt's answers, as the decision's name or {@code dismissed}, in order. */
    private static final class Answers implements Consumer<Optional<Decision>> {
        final List<String> calls = new CopyOnWriteArrayList<>();

        @Override public void accept(Optional<Decision> decision) {
            calls.add(decision.map(Decision::name).orElse("dismissed"));
        }
    }
}
