package dev.jasper.browser.dialogs;

import dev.jasper.browser.item.BrowserItem;
import dev.jasper.browser.item.BrowserItems;
import dev.jasper.browser.item.ItemTestAccess;
import dev.jasper.browser.page.DialogPlace;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.files.FileChoice;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ContentSpec;
import dev.jasper.sdk.ui.ContentTab;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.OverlaySpec;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.PluginPopover;
import dev.jasper.sdk.ui.SplitChoice;
import dev.jasper.sdk.ui.SplitItemSpec;
import dev.jasper.sdk.ui.TabPlacement;
import dev.jasper.sdk.ui.TabRestorer;
import dev.jasper.sdk.ui.UiAnchor;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.Windows;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import javax.swing.SwingUtilities;
import org.cef.handler.CefJSDialogHandler.JSDialogType;
import org.cef.misc.BoolRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A page's dialogs (spec section 5): {@code alert} and {@code confirm} are Jasper's in-window modals over the page's
 * item, built from the SDK's nodes, and each is answered exactly once, whether the user answers it, cancels it, or the
 * item closes under it; they wait while their item is not its window's active item, go when Chromium resets the page's
 * dialogs, and a page that floods them is refused them until it loads again; {@code prompt} shows nothing;
 * {@code beforeunload} lets the page go without asking; and a file input opens Jasper's chooser over the item. The pages are the items' own, open on the testkit's fake host, so no
 * Chromium is needed: Chromium's side is played by the test thread, which asks as Chromium's own thread would, and
 * by recording replies.
 */
class PageDialogsTest {
    private static final String ID = "dev.jasper.browser";
    private static final String ALERT = "Web Page Alert", CONFIRM = "Web Page Confirmation";
    private Shown shown;

    @AfterEach void closeHost() throws Exception {
        if (shown != null) onUiThread(shown::close);
    }

    @Test void anAlertIsAnOverlayOverItsItemHeadedWebPageAlertWithTheMessageAsTextAndOK() throws Exception {
        shown = show();
        var replies = new Replies();
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.ALERT, "Saved.\n<b>Twice</b>", replies);
        onUiThread(() -> {
            assertThat(shown.host.dialogPresentation(ID, ALERT)).contains(DialogSpec.Presentation.OVERLAY);
            Nodes.Form form = form(ALERT);
            assertThat(form.kind()).isEqualTo(Nodes.FormKind.SHEET);
            assertThat(form.title()).isEqualTo(ALERT);
            assertThat(form.sections()).singleElement().isInstanceOfSatisfying(Nodes.Text.class, text -> {
                assertThat(text.text()).as("plain text, as the page wrote it").isEqualTo("Saved.\n<b>Twice</b>");
                assertThat(text.role()).isEqualTo(Nodes.TextRole.BODY);
            });
            assertThat(form.actions()).singleElement().satisfies(ok -> {
                assertThat(ok.label()).isEqualTo("OK");
                assertThat(ok.kind()).isEqualTo(Nodes.ButtonKind.PRIMARY);
            });
            assertThat(form.cancelAction()).as("Escape").isNotBlank();
            assertThat(replies.calls).as("unanswered while it shows").isEmpty();

            press(ALERT, form.actions().getFirst().action());
            assertThat(replies.calls).containsExactly("true|");
            assertThat(shown.host.windows()).as("the dialog closed").isEmpty();
            assertThat(shown.host.failures()).isEmpty();
        });
    }

    @Test void aConfirmationHasCancelThenOKAndIsTrueOnlyForOK() throws Exception {
        shown = show();
        LongSupplier unhurried = unhurried();
        var ok = new Replies();
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.CONFIRM, "Leave?", ok, unhurried);
        onUiThread(() -> {
            assertThat(shown.host.dialogPresentation(ID, CONFIRM)).contains(DialogSpec.Presentation.OVERLAY);
            Nodes.Form form = form(CONFIRM);
            assertThat(form.kind()).isEqualTo(Nodes.FormKind.SHEET);
            assertThat(form.title()).isEqualTo(CONFIRM);
            assertThat(form.sections()).singleElement().isInstanceOfSatisfying(Nodes.Text.class,
                text -> assertThat(text.text()).isEqualTo("Leave?"));
            assertThat(form.actions()).extracting(Nodes.Button::label).containsExactly("Cancel", "OK");
            assertThat(form.actions()).extracting(Nodes.Button::kind).containsExactly(Nodes.ButtonKind.SECONDARY, Nodes.ButtonKind.PRIMARY);
            assertThat(form.cancelAction()).as("Escape is Cancel").isEqualTo(form.actions().getFirst().action());
            press(CONFIRM, form.actions().getLast().action());
            assertThat(ok.calls).containsExactly("true|");
            assertThat(shown.host.windows()).isEmpty();
        });

        var cancel = new Replies();
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.CONFIRM, "Leave?", cancel, unhurried);
        onUiThread(() -> press(CONFIRM, form(CONFIRM).actions().getFirst().action()));
        var escape = new Replies();
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.CONFIRM, "Leave?", escape, unhurried);
        onUiThread(() -> press(CONFIRM, form(CONFIRM).cancelAction()));
        var closed = new Replies();
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.CONFIRM, "Leave?", closed, unhurried);
        onUiThread(() -> {
            assertThat(shown.host.requestClose("dialog")).as("the dialog's own close").isTrue();
            assertThat(cancel.calls).containsExactly("false|");
            assertThat(escape.calls).containsExactly("false|");
            assertThat(closed.calls).containsExactly("false|");
            assertThat(shown.host.windows()).isEmpty();
            assertThat(shown.host.failures()).isEmpty();
        });
    }

    /** The message is the page's: at most 10,000 characters are shown, cut with an ellipsis, never inside a character. */
    @Test void aLongMessageIsCutWithAnEllipsisAndNeverInsideACharacter() throws Exception {
        shown = show();
        LongSupplier unhurried = unhurried();
        String face = new String(Character.toChars(0x1F600));
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.ALERT, "a".repeat(9_998) + face + "tail", new Replies(), unhurried);
        onUiThread(() -> {
            String text = ((Nodes.Text) form(ALERT).sections().getFirst()).text();
            assertThat(text).isEqualTo("a".repeat(9_998) + "…");
            press(ALERT, form(ALERT).cancelAction());
        });
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.ALERT, "b".repeat(10_000), new Replies(), unhurried);
        onUiThread(() -> {
            assertThat(((Nodes.Text) form(ALERT).sections().getFirst()).text()).as("10,000 fit").isEqualTo("b".repeat(10_000));
            press(ALERT, form(ALERT).cancelAction());
        });
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.ALERT, null, new Replies(), unhurried);
        onUiThread(() -> assertThat(((Nodes.Text) form(ALERT).sections().getFirst()).text()).as("alert()").isEmpty());
    }

    /**
     * Plan 4a's first review focus: an item that closes while its page's dialog shows closes only itself, takes the
     * dialog with it, and the page's script is answered once, as cancelled. Both of a page's own closes are its item's:
     * Close Pane and a script's {@code window.close()}.
     */
    @Test void anItemThatClosesWhileAnAlertShowsDismissesItAndContinuesItOnce() throws Exception {
        shown = show();
        var replies = new Replies();
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.ALERT, "Wait", replies);
        var other = new AtomicReference<BrowserItem>();
        onUiThread(() -> {
            other.set(shown.items.open(URI.create("https://example.org"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty()));
            assertThat(shown.host.windowView(ID, ALERT)).isPresent();
            shown.item.close();
            assertThat(shown.host.windows()).as("the dialog went with its item").isEmpty();
            assertThat(replies.calls).containsExactly("false|");
            assertThat(shown.items.items()).as("only that item closed").containsExactly(other.get());
            assertThat(shown.host.contentTabs()).containsExactly(other.get().tab());
            shown.item.close();
        });
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.CONFIRM, "Again?", replies);
        onUiThread(() -> {
            assertThat(shown.host.windows()).as("a closed page shows nothing").isEmpty();
            assertThat(replies.calls).as("answered at once, as cancelled").containsExactly("false|", "false|");
            assertThat(shown.host.failures()).isEmpty();
        });

        var popup = show();
        try {
            var confirmed = new Replies();
            ScriptDialog.ask(popup.page, ScriptDialog.Kind.CONFIRM, "Close?", confirmed);
            onUiThread(() -> {
                assertThat(popup.host.windowView(ID, CONFIRM)).isPresent();
                popup.page.closeItem(); // window.close()
                assertThat(popup.host.windows()).isEmpty();
                assertThat(confirmed.calls).containsExactly("false|");
                assertThat(popup.host.contentTabs()).isEmpty();
                assertThat(popup.host.failures()).isEmpty();
            });
        } finally {
            onUiThread(popup::close);
        }
    }

    /**
     * A page the user is not looking at cannot cover the window: its item's dialog waits until the item is its window's
     * active item, and shows then.
     */
    @Test void aBackgroundItemsAlertWaitsUntilItsItemIsActive() throws Exception {
        shown = show();
        var other = new AtomicReference<BrowserItem>();
        onUiThread(() -> {
            other.set(shown.items.open(URI.create("https://example.org"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty()));
            assertThat(shown.item.tab().isActive()).as("behind the other item").isFalse();
        });
        var replies = new Replies();
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.ALERT, "Behind", replies);
        onUiThread(() -> {
            assertThat(shown.host.windows()).as("waiting").isEmpty();
            assertThat(replies.calls).isEmpty();
            other.get().tab().select();
        });
        onUiThread(() -> {
            assertThat(shown.host.windows()).as("another item activated").isEmpty();
            shown.item.tab().select();
        });
        onUiThread(() -> {
            assertThat(shown.host.dialogPresentation(ID, ALERT)).as("its item is active").contains(DialogSpec.Presentation.OVERLAY);
            assertThat(replies.calls).isEmpty();
            press(ALERT, form(ALERT).actions().getFirst().action());
            assertThat(replies.calls).containsExactly("true|");
            other.get().tab().select();
            shown.item.tab().select();
        });
        onUiThread(() -> {
            assertThat(shown.host.windows()).as("an answered dialog does not come back").isEmpty();
            assertThat(replies.calls).containsExactly("true|");
            assertThat(shown.host.failures()).isEmpty();
        });
    }

    @Test void anItemThatClosesWhileItsDialogWaitsAnswersItOnceUnshown() throws Exception {
        shown = show();
        var other = new AtomicReference<BrowserItem>();
        onUiThread(() -> other.set(shown.items.open(URI.create("https://example.org"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty())));
        var replies = new Replies();
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.CONFIRM, "Behind", replies);
        onUiThread(() -> {
            assertThat(replies.calls).isEmpty();
            shown.item.close();
            assertThat(replies.calls).containsExactly("false|");
            other.get().tab().select();
        });
        onUiThread(() -> {
            assertThat(shown.host.windows()).isEmpty();
            assertThat(replies.calls).as("once").containsExactly("false|");
            assertThat(shown.items.items()).containsExactly(other.get());
            assertThat(shown.host.failures()).isEmpty();
        });
    }

    /** Chromium's reset of a page's dialogs (a new document) ends the dialog shown and the one waiting, as cancelled. */
    @Test void aResetDismissesTheDialogShownOrWaiting() throws Exception {
        shown = show();
        var shownReply = new Replies();
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.CONFIRM, "Shown", shownReply);
        onUiThread(() -> {
            assertThat(shown.host.windowView(ID, CONFIRM)).isPresent();
            shown.page.dialogsReset();
            assertThat(shown.host.windows()).isEmpty();
            assertThat(shownReply.calls).containsExactly("false|");
        });
        var other = new AtomicReference<BrowserItem>();
        onUiThread(() -> other.set(shown.items.open(URI.create("https://example.org"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty())));
        var waitingReply = new Replies();
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.ALERT, "Waiting", waitingReply);
        onUiThread(() -> {
            assertThat(waitingReply.calls).isEmpty();
            shown.page.dialogsReset();
            assertThat(waitingReply.calls).containsExactly("false|");
            shown.item.tab().select();
        });
        onUiThread(() -> {
            assertThat(shown.host.windows()).as("a reset dialog never shows").isEmpty();
            assertThat(shownReply.calls).containsExactly("false|");
            assertThat(waitingReply.calls).containsExactly("false|");
            new PageDialogs.Scripts().onResetDialogState(null); // a browser that is no page's: nothing to reset
            assertThat(shown.host.failures()).isEmpty();
        });
    }

    /**
     * A script that opens dialog after dialog, as {@code for (;;) alert(1)} does, would hold the window: the third
     * dialog opened within a second of the one before closing is answered false unshown, as is every later one, until
     * the page loads a new document.
     */
    @Test void aPageThatFloodsDialogsIsRefusedThemUntilItLoadsAgain() throws Exception {
        shown = show();
        var clock = new AtomicLong();
        var first = new Replies();
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.ALERT, "1", first, clock::get);
        onUiThread(() -> {
            clock.set(100);
            press(ALERT, form(ALERT).actions().getFirst().action());
        });
        clock.set(600);
        var second = new Replies();
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.ALERT, "2", second, clock::get);
        onUiThread(() -> {
            assertThat(shown.host.windowView(ID, ALERT)).as("the second shows").isPresent();
            clock.set(700);
            press(ALERT, form(ALERT).actions().getFirst().action());
        });
        clock.set(1_650);
        var third = new Replies();
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.CONFIRM, "3", third, clock::get);
        onUiThread(() -> assertThat(third.calls).as("950 ms after the second closed").containsExactly("false|"));
        clock.set(60_000);
        var later = new Replies();
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.ALERT, "4", later, clock::get);
        onUiThread(() -> {
            assertThat(shown.host.windows()).as("refused unshown").isEmpty();
            assertThat(first.calls).containsExactly("true|");
            assertThat(second.calls).containsExactly("true|");
            assertThat(third.calls).containsExactly("false|");
            assertThat(later.calls).as("refused until the page loads again, however late").containsExactly("false|");
            PageTestAccess.loadStarted(shown.page, "https://example.com/next");
        });
        var reloaded = new Replies();
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.ALERT, "5", reloaded, clock::get);
        onUiThread(() -> {
            assertThat(shown.host.windowView(ID, ALERT)).as("a new document may ask again").isPresent();
            press(ALERT, form(ALERT).actions().getFirst().action());
            assertThat(reloaded.calls).containsExactly("true|");
            assertThat(shown.host.failures()).isEmpty();
        });
    }

    @Test void theFloodGuardRefusesTheThirdQuickDialogOfARunUntilRearmed() {
        var slow = new DialogFlood();
        for (long at = 0; at < 20_000; at += 2_000) {
            assertThat(slow.admit(at)).as("a dialog 1.1 s after the last closed, at " + at).isTrue();
            slow.closed(at + 900);
        }

        var quick = new DialogFlood();
        assertThat(quick.admit(0)).isTrue();
        quick.closed(10);
        assertThat(quick.admit(20)).as("the second of a run").isTrue();
        quick.closed(30);
        assertThat(quick.admit(1_031)).as("1,001 ms later starts a new run").isTrue();
        quick.closed(1_040);
        assertThat(quick.admit(1_050)).isTrue();
        quick.closed(1_060);
        assertThat(quick.admit(1_060 + DialogFlood.QUICK_MILLIS)).as("the third of a run, 1,000 ms after").isFalse();
        assertThat(quick.admit(1_000_000)).as("refused until rearmed").isFalse();
        quick.rearm();
        assertThat(quick.admit(1_000_001)).isTrue();
        quick.closed(1_000_002);
        assertThat(quick.admit(1_000_003)).as("a run starts afresh").isTrue();
        quick.closed(1_000_004);
        assertThat(quick.admit(1_000_005)).isFalse();
    }

    @Test void aDialogForNoPageIsAnsweredAtOnceAsCancelled() throws Exception {
        var replies = new Replies();
        ScriptDialog.ask(null, ScriptDialog.Kind.ALERT, "Lost", replies);
        onUiThread(() -> assertThat(replies.calls).containsExactly("false|"));
    }

    /** iTerm2 has no handler for {@code prompt()}, so Jasper shows nothing and the page gets null at once. */
    @Test void promptShowsNothingAndIsContinuedWithFalseAndNoText() throws Exception {
        var replies = new Replies();
        boolean handled = new PageDialogs.Scripts().onJSDialog(null, "https://example.com", JSDialogType.JSDIALOGTYPE_PROMPT,
            "Your name?", "Ann", replies::accept, new BoolRef());
        assertThat(handled).isTrue();
        assertThat(replies.calls).containsExactly("false|");
    }

    @Test void beforeUnloadIsContinuedSoThePageClosesWithoutAsking() throws Exception {
        for (boolean reload : List.of(false, true)) {
            var replies = new Replies();
            assertThat(new PageDialogs.Scripts().onBeforeUnloadDialog(null, "Changes you made may not be saved.", reload, replies::accept))
                .isTrue();
            assertThat(replies.calls).containsExactly("true|");
        }
    }

    @Test void anAlertOrConfirmationFromABrowserThatIsNoPagesIsCancelled() throws Exception {
        for (JSDialogType type : List.of(JSDialogType.JSDIALOGTYPE_ALERT, JSDialogType.JSDIALOGTYPE_CONFIRM)) {
            var replies = new Replies();
            var suppress = new BoolRef();
            assertThat(new PageDialogs.Scripts().onJSDialog(null, "https://example.com", type, "Hi", "", replies::accept, suppress))
                .isTrue();
            assertThat(suppress.get()).as("a handled dialog is never suppressed").isFalse();
            onUiThread(() -> assertThat(replies.calls).as(type.name()).containsExactly("false|"));
        }
    }

    /** {@code <input type=file>} opens Jasper's chooser over the item, for local files only. */
    @Test void aFileInputOpensJaspersChooserForOneOrSeveralLocalFiles() throws Exception {
        shown = show();
        Path report = Path.of("/tmp/report.pdf").toAbsolutePath(), notes = Path.of("/tmp/notes.txt").toAbsolutePath();
        onUiThread(() -> {
            shown.host.queueFileChoice(List.of(new FileLocation.Local(report)));
            shown.host.queueFileChoice(List.of(new FileLocation.Local(report), new FileLocation.Local(notes)));
        });
        var one = new Files();
        FileInput.choose(shown.page, FileInput.Mode.OPEN, "", "", one, one::cancel);
        var several = new Files();
        FileInput.choose(shown.page, FileInput.Mode.OPEN_MULTIPLE, "", "", several, several::cancel);
        onUiThread(() -> {
            List<FileChoice> choices = shown.host.fileChoices();
            assertThat(choices).hasSize(2).allSatisfy(choice -> {
                assertThat(choice.owner()).as("over the item").isSameAs(shown.item.tab());
                assertThat(choice.mode()).isEqualTo(FileChoice.Mode.OPEN);
                assertThat(choice.title()).isEqualTo("Open");
                assertThat(choice.sources()).as("local places only").isEmpty();
                assertThat(choice.initial()).isEmpty();
            });
            assertThat(choices).extracting(FileChoice::multiple).containsExactly(false, true);
            assertThat(one.calls).containsExactly(List.of(report.toString()));
            assertThat(several.calls).containsExactly(List.of(report.toString(), notes.toString()));
            assertThat(shown.host.failures()).isEmpty();
        });
    }

    @Test void aFolderInputChoosesAFolderAndASaveAsksForAFile() throws Exception {
        shown = show();
        Path folder = Path.of("/tmp/photos").toAbsolutePath(), target = Path.of("/tmp/out.txt").toAbsolutePath();
        var choosers = new RecordingWindows[1];
        onUiThread(() -> {
            // The fake's folder and save choosers share one queue, so the page's windows record which one ran.
            DialogPlace place = shown.page.dialogPlace().orElseThrow();
            choosers[0] = new RecordingWindows(place.windows());
            shown.page.setDialogPlace(new DialogPlace(place.kit(), choosers[0], place.owner()));
            shown.host.queuePathSelection(List.of(folder));
            shown.host.queuePathSelection(List.of(target));
        });
        var chosenFolder = new Files();
        FileInput.choose(shown.page, FileInput.Mode.OPEN_FOLDER, "", "", chosenFolder, chosenFolder::cancel);
        var saved = new Files();
        FileInput.choose(shown.page, FileInput.Mode.SAVE, "", "out.txt", saved, saved::cancel);
        onUiThread(() -> {
            assertThat(choosers[0].calls).containsExactly(
                "chooseDirectory|" + shown.item.tab().hashCode() + "|Choose Folder|Optional.empty",
                "chooseSaveFile|" + shown.item.tab().hashCode() + "|Save|Optional[" + Path.of(System.getProperty("user.home")).resolve("out.txt") + "]");
            assertThat(chosenFolder.calls).containsExactly(List.of(folder.toString()));
            assertThat(saved.calls).containsExactly(List.of(target.toString()));
            assertThat(shown.host.fileChoices()).as("folders and saves have choosers of their own").isEmpty();
        });
    }

    @Test void aCancelledChooserIsCancelledOnceAndOneWhoseItemClosesToo() throws Exception {
        shown = show();
        var cancelled = new Files();
        FileInput.choose(shown.page, FileInput.Mode.OPEN, "", "", cancelled, cancelled::cancel);
        var noFolder = new Files();
        FileInput.choose(shown.page, FileInput.Mode.OPEN_FOLDER, "", "", noFolder, noFolder::cancel);
        onUiThread(() -> {
            assertThat(cancelled.calls).containsExactly("cancel");
            assertThat(noFolder.calls).containsExactly("cancel");
            shown.host.queueFileChoice(List.of(new FileLocation.Local(Path.of("/tmp/late.txt").toAbsolutePath())));
            shown.host.duringPathSelection(shown.item::close);
        });
        var closing = new Files();
        FileInput.choose(shown.page, FileInput.Mode.OPEN, "", "", closing, closing::cancel);
        var afterwards = new Files();
        FileInput.choose(shown.page, FileInput.Mode.SAVE, "", "", afterwards, afterwards::cancel);
        onUiThread(() -> {
            assertThat(closing.calls).as("the item closed while the chooser showed").containsExactly("cancel");
            assertThat(afterwards.calls).as("a closed page chooses nothing").containsExactly("cancel");
            assertThat(shown.host.contentTabs()).isEmpty();
            assertThat(shown.host.failures()).isEmpty();
        });
    }

    @Test void aChooserStartsWhereChromiumSuggestsAndASavedNameStaysInItsFolder() {
        Path home = Path.of(System.getProperty("user.home"));
        Path absolute = Path.of("/tmp/start/file.txt").toAbsolutePath();
        assertThat(FileInput.initial(FileInput.Mode.OPEN, "")).isEmpty();
        assertThat(FileInput.initial(FileInput.Mode.OPEN, absolute.toString())).contains(absolute);
        assertThat(FileInput.initial(FileInput.Mode.OPEN, "relative.txt")).isEmpty();
        assertThat(FileInput.initial(FileInput.Mode.SAVE, "report.pdf")).contains(home.resolve("report.pdf"));
        assertThat(FileInput.initial(FileInput.Mode.SAVE, "../../escape.txt")).as("only the name").contains(home.resolve("escape.txt"));
        assertThat(FileInput.initial(FileInput.Mode.SAVE, absolute.toString())).contains(absolute);
        assertThat(FileInput.title(FileInput.Mode.OPEN, "")).isEqualTo("Open");
        assertThat(FileInput.title(FileInput.Mode.OPEN_MULTIPLE, " ")).isEqualTo("Open");
        assertThat(FileInput.title(FileInput.Mode.OPEN_FOLDER, "")).isEqualTo("Choose Folder");
        assertThat(FileInput.title(FileInput.Mode.SAVE, "")).isEqualTo("Save");
        assertThat(FileInput.title(FileInput.Mode.SAVE, "Save File")).isEqualTo("Save File");
    }

    /** A clock a user who takes their time answers by: each reading is 10 s after the last, so no dialog comes quickly. */
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

    /** A started stand-in Browser on a fake host, with one browser item open in a window. UI thread. */
    private static Shown show() throws Exception {
        var result = new AtomicReference<Shown>();
        onUiThread(() -> {
            var host = new FakePluginHost();
            var started = new AtomicReference<BrowserItems>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(),
                plugin -> started.set(new BrowserItems(plugin, BrowserSettings.declare(plugin.settings())::read, page -> { })));
            var items = started.get();
            host.activateTerminalWindow(host.addTerminalWindow());
            BrowserItem item = items.open(URI.create("https://example.com"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            result.set(new Shown(host, items, item, ItemTestAccess.page(item)));
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
        // What the body posted, such as a chooser's or dialog's show, runs before the next step.
        SwingUtilities.invokeAndWait(() -> { });
        if (failure.get() instanceof Exception exception) throw exception;
        if (failure.get() instanceof Error error) throw error;
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private record Shown(FakePluginHost host, BrowserItems items, BrowserItem item, Page page) {
        void close() {
            host.close();
        }
    }

    /**
     * The fake host's windows, recording each folder, save and file chooser asked for as
     * {@code chooser|owner's hash|title|initial}.
     */
    private static final class RecordingWindows implements Windows {
        final List<String> calls = new CopyOnWriteArrayList<>();
        private final Windows windows;

        RecordingWindows(Windows windows) {
            this.windows = windows;
        }

        @Override public Optional<Path> chooseDirectory(WindowOwner owner, String title, Optional<Path> initial) {
            calls.add("chooseDirectory|" + owner.hashCode() + "|" + title + "|" + initial);
            return windows.chooseDirectory(owner, title, initial);
        }

        @Override public Optional<Path> chooseSaveFile(WindowOwner owner, String title, Optional<Path> initial) {
            calls.add("chooseSaveFile|" + owner.hashCode() + "|" + title + "|" + initial);
            return windows.chooseSaveFile(owner, title, initial);
        }

        @Override public List<Path> chooseFiles(WindowOwner owner, String title, Optional<Path> initial) {
            calls.add("chooseFiles|" + owner.hashCode() + "|" + title + "|" + initial);
            return windows.chooseFiles(owner, title, initial);
        }

        @Override public List<FileLocation> choose(FileChoice choice) {
            calls.add("choose|" + choice.owner().hashCode() + "|" + choice.title() + "|" + choice.initial());
            return windows.choose(choice);
        }

        @Override public PluginPopover popover(UiAnchor anchor) { return windows.popover(anchor); }

        @Override public ContentTab openTab(ContentSpec spec, WindowOwner owner, UiView content, TabPlacement placement) {
            return windows.openTab(spec, owner, content, placement);
        }

        @Override public Subscription restorer(String kind, TabRestorer restorer) { return windows.restorer(kind, restorer); }

        @Override public Subscription splitItem(SplitItemSpec spec, BiConsumer<WindowHandle, TabPlacement> opener) {
            return windows.splitItem(spec, opener);
        }

        @Override public List<SplitChoice> splitChoices() { return windows.splitChoices(); }

        @Override public PluginDialog dialog(DialogSpec spec) { return windows.dialog(spec); }

        @Override public PluginDialog overlay(OverlaySpec spec) { return windows.overlay(spec); }
    }

    /** A script's replies, as {@code ok|text}, in order. */
    private static final class Replies implements BiConsumer<Boolean, String> {
        final List<String> calls = new CopyOnWriteArrayList<>();

        @Override public void accept(Boolean ok, String text) {
            calls.add(ok + "|" + text);
        }
    }

    /** A file input's replies: the chosen paths, or {@code cancel}, in order. */
    private static final class Files implements java.util.function.Consumer<List<String>> {
        final List<Object> calls = new CopyOnWriteArrayList<>();

        @Override public void accept(List<String> paths) {
            calls.add(List.copyOf(paths));
        }

        void cancel() {
            calls.add("cancel");
        }
    }
}
