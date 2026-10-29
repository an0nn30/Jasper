package dev.jasper.browser.dialogs;

import dev.jasper.browser.item.BrowserItem;
import dev.jasper.browser.item.BrowserItems;
import dev.jasper.browser.page.Page;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.files.FileChoice;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TabPlacement;
import dev.jasper.sdk.ui.UiEvent;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.cef.handler.CefJSDialogHandler.JSDialogType;
import org.cef.misc.BoolRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A page's dialogs (spec section 5): {@code alert} and {@code confirm} are Jasper's in-window modals over the page's
 * item, built from the SDK's nodes, and each is answered exactly once, whether the user answers it, cancels it, or the
 * item closes under it; {@code prompt} shows nothing; {@code beforeunload} lets the page go without asking; and a file
 * input opens Jasper's chooser over the item. The pages are the items' own, open on the testkit's fake host, so no
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
        var ok = new Replies();
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.CONFIRM, "Leave?", ok);
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
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.CONFIRM, "Leave?", cancel);
        onUiThread(() -> press(CONFIRM, form(CONFIRM).actions().getFirst().action()));
        var escape = new Replies();
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.CONFIRM, "Leave?", escape);
        onUiThread(() -> press(CONFIRM, form(CONFIRM).cancelAction()));
        var closed = new Replies();
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.CONFIRM, "Leave?", closed);
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
        String face = new String(Character.toChars(0x1F600));
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.ALERT, "a".repeat(9_998) + face + "tail", new Replies());
        onUiThread(() -> {
            String text = ((Nodes.Text) form(ALERT).sections().getFirst()).text();
            assertThat(text).isEqualTo("a".repeat(9_998) + "…");
            press(ALERT, form(ALERT).cancelAction());
        });
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.ALERT, "b".repeat(10_000), new Replies());
        onUiThread(() -> {
            assertThat(((Nodes.Text) form(ALERT).sections().getFirst()).text()).as("10,000 fit").isEqualTo("b".repeat(10_000));
            press(ALERT, form(ALERT).cancelAction());
        });
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.ALERT, null, new Replies());
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
        onUiThread(() -> {
            shown.host.queuePathSelection(List.of(folder));
            shown.host.queuePathSelection(List.of(target));
        });
        var chosenFolder = new Files();
        FileInput.choose(shown.page, FileInput.Mode.OPEN_FOLDER, "", "", chosenFolder, chosenFolder::cancel);
        var saved = new Files();
        FileInput.choose(shown.page, FileInput.Mode.SAVE, "", "out.txt", saved, saved::cancel);
        onUiThread(() -> {
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
            var context = new AtomicReference<PluginContext>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), context::set);
            var items = new BrowserItems(context.get(), page -> { });
            host.activateTerminalWindow(host.addTerminalWindow());
            BrowserItem item = items.open(URI.create("https://example.com"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            result.set(new Shown(host, items, item, item.page()));
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

    /** A script's replies, as {@code ok|text}, in order. */
    private static final class Replies implements java.util.function.BiConsumer<Boolean, String> {
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
