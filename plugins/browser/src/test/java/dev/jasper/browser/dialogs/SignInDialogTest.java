package dev.jasper.browser.dialogs;

import dev.jasper.browser.dialogs.SignInDialog.Login;
import dev.jasper.browser.dialogs.SignInDialog.Outcome;
import dev.jasper.browser.item.BrowserItem;
import dev.jasper.browser.item.BrowserItems;
import dev.jasper.browser.item.DataStores;
import dev.jasper.browser.item.ItemTestAccess;
import dev.jasper.browser.page.DialogPlace;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.permissions.Permission;
import dev.jasper.browser.permissions.PermissionRequest;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.store.CapturedLog;
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TabPlacement;
import dev.jasper.sdk.ui.UiAnchor;
import dev.jasper.sdk.ui.UiEvent;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Jasper's dialog for an HTTP sign-in (plan 8, spec section 13, F4): a modal over the page's item, its first line
 * naming the website or proxy and the realm, then User name, Password and, when the sign-in can be remembered,
 * "Remember this password"; Password Manager when offered, Cancel and OK. OK answers what was typed, a login chosen in
 * the picker closes it as Picked, and anything else is Cancelled, once. It shares the page's flood guard and waits
 * behind its permission prompt. The pages are stand-in items' own, open on the testkit's fake host, so no Chromium is
 * needed; the picker is a test's function.
 */
class SignInDialogTest {
    private static final String ID = "dev.jasper.browser";
    private static final String TITLE = SignInDialog.TITLE;
    private static final String CORP = "The website “intranet.example” requires a user name and password for “Corp”.";
    private final MemoryStores memory = new MemoryStores();
    private final Answers answers = new Answers();
    // The clock of a user who takes their time: each reading is 10 s after the last, so no dialog comes quickly. One
    // for the test, as the page's flood guard compares the times its dialogs read.
    private final AtomicLong now = new AtomicLong();
    private final LongSupplier unhurried = () -> now.addAndGet(10_000);
    private Shown shown;

    @AfterEach void closeHost() throws Exception {
        if (shown != null) onUiThread(shown::close);
        memory.close();
    }

    @Test void theHeadingNamesTheWebsiteAndItsRealmOrTheWebsiteAlone() {
        assertThat(SignInDialog.heading("intranet.example", 443, "Corp", false)).isEqualTo(CORP);
        assertThat(SignInDialog.heading("intranet.example", 443, "", false))
            .isEqualTo("The website “intranet.example” requires a user name and password.");
        assertThat(SignInDialog.heading("intranet.example", 80, "  ", false)).as("a blank realm is none")
            .isEqualTo("The website “intranet.example” requires a user name and password.");
    }

    /** F22: a proxy's dialog names the proxy and its port, not a website. */
    @Test void aProxyHeadingNamesTheProxy() {
        assertThat(SignInDialog.heading("proxy.example", 8118, "Squid", true))
            .isEqualTo("The proxy “proxy.example:8118” requires a user name and password for “Squid”.");
        assertThat(SignInDialog.heading("proxy.example", 8118, "", true))
            .isEqualTo("The proxy “proxy.example:8118” requires a user name and password.");
    }

    @Test void theDialogIsAnOverlayWithItsLineFieldsToggleAndButtonsInOrder() throws Exception {
        shown = show();
        onUiThread(() -> sign(true, true, none()));
        onUiThread(() -> {
            assertThat(shown.host.dialogPresentation(ID, TITLE)).contains(DialogSpec.Presentation.OVERLAY);
            Nodes.Form form = form();
            assertThat(form.kind()).isEqualTo(Nodes.FormKind.SHEET);
            assertThat(form.title()).isEqualTo("Sign In");
            assertThat(form.sections()).hasSize(4);
            assertThat(form.sections().get(0)).isInstanceOfSatisfying(Nodes.Text.class, text -> {
                assertThat(text.text()).isEqualTo(CORP);
                assertThat(text.role()).isEqualTo(Nodes.TextRole.BODY);
            });
            Nodes.Field user = user(form);
            assertThat(user.kind()).isEqualTo(Nodes.FieldKind.TEXT);
            assertThat(user.value()).isEmpty();
            assertThat(user.label()).isEqualTo("User name");
            assertThat(((Nodes.FormRow) form.sections().get(1)).label()).isEqualTo("User name");
            Nodes.Secret password = password(form);
            assertThat(password.kind()).isEqualTo(Nodes.SecretKind.INPUT);
            assertThat(password.label()).isEqualTo("Password");
            assertThat(((Nodes.FormRow) form.sections().get(2)).label()).isEqualTo("Password");
            assertThat(toggle(form)).hasValueSatisfying(toggle -> {
                assertThat(toggle.label()).isEqualTo("Remember this password");
                assertThat(toggle.checked()).as("off").isFalse();
            });
            assertThat(form.actions()).extracting(Nodes.Button::label).containsExactly("Password Manager", "Cancel", "OK");
            assertThat(form.actions()).extracting(Nodes.Button::kind)
                .containsExactly(Nodes.ButtonKind.SECONDARY, Nodes.ButtonKind.SECONDARY, Nodes.ButtonKind.PRIMARY);
            String ok = form.actions().getLast().action();
            assertThat(user.trailingAction()).as("Enter in the user name is OK").isEqualTo(ok);
            assertThat(password.submitAction()).as("Enter in the password is OK").isEqualTo(ok);
            assertThat(form.cancelAction()).as("Escape is Cancel").isEqualTo(form.actions().get(1).action());
            assertThat(answers.calls).as("unanswered while it shows").isEmpty();
        });
    }

    @Test void eachControlIsThereOnlyByItsFlag() throws Exception {
        shown = show();
        for (boolean offers : List.of(false, true))
            for (boolean remembers : List.of(false, true)) {
                var told = new Answers();
                onUiThread(() -> SignInDialog.show(shown.page, shown.place, "intranet.example", 443, "Corp", false, offers, remembers,
                    none(), told, unhurried));
                onUiThread(() -> {
                    Nodes.Form form = form();
                    String flags = "offers " + offers + ", remembers " + remembers;
                    assertThat(form.actions()).as(flags).extracting(Nodes.Button::label)
                        .containsExactlyElementsOf(offers ? List.of("Password Manager", "Cancel", "OK") : List.of("Cancel", "OK"));
                    assertThat(toggle(form).isPresent()).as(flags).isEqualTo(remembers);
                    press(form.cancelAction());
                    assertThat(told.calls).containsExactly("cancelled");
                });
            }
    }

    @Test void okGivesTheTypedUserNamePasswordAndToggleAndEnterInEitherFieldIsOk() throws Exception {
        shown = show();
        onUiThread(() -> sign(true, true, none()));
        onUiThread(() -> {
            view().type(SignInDialog.USER, "u");
            view().setSecret(SignInDialog.PASSWORD, "pw".toCharArray());
            view().emit(new UiEvent.Toggled(SignInDialog.REMEMBER, true));
            assertThat(toggle(form())).hasValueSatisfying(toggle -> assertThat(toggle.checked()).as("ticked").isTrue());
            assertThat(user(form()).value()).as("the typed name kept").isEqualTo("u");
            press(form().actions().getLast().action());
            assertThat(answers.calls).containsExactly("ok|u|pw|true");
            assertThat(shown.host.windows()).as("the dialog closed").isEmpty();
        });
        var entered = new Answers();
        onUiThread(() -> SignInDialog.show(shown.page, shown.place, "intranet.example", 443, "", false, true, true, none(), entered,
            unhurried));
        onUiThread(() -> {
            view().type(SignInDialog.USER, "v");
            view().setSecret(SignInDialog.PASSWORD, "pw2".toCharArray());
            press(password(form()).submitAction());
            assertThat(entered.calls).as("Enter in the password, not ticked").containsExactly("ok|v|pw2|false");
        });
        var unticked = new Answers();
        onUiThread(() -> SignInDialog.show(shown.page, shown.place, "intranet.example", 443, "", false, false, false, none(), unticked,
            unhurried));
        onUiThread(() -> {
            press(user(form()).trailingAction());
            assertThat(unticked.calls).as("Enter in the empty user name").containsExactly("ok|||false");
            assertThat(shown.host.failures()).isEmpty();
        });
    }

    @Test void cancelEscapeAndTheDialogsOwnCloseGiveCancelled() throws Exception {
        shown = show();
        for (String how : List.of("cancel", "escape", "close")) {
            var told = new Answers();
            onUiThread(() -> SignInDialog.show(shown.page, shown.place, "intranet.example", 443, "Corp", false, true, true, none(), told,
                unhurried));
            onUiThread(() -> {
                view().type(SignInDialog.USER, "u");
                view().setSecret(SignInDialog.PASSWORD, "pw".toCharArray());
                switch (how) {
                    case "cancel" -> press(form().actions().get(1).action());
                    case "escape" -> press(form().cancelAction());
                    default -> assertThat(shown.host.requestClose("dialog")).isTrue();
                }
                assertThat(told.calls).as(how).containsExactly("cancelled");
                assertThat(shown.host.windows()).as(how).isEmpty();
            });
        }
        onUiThread(() -> assertThat(shown.host.failures()).isEmpty());
    }

    /** The password field is empty by the time the dialog is answered, and an answer's array is the caller's copy. */
    @Test void theSecretIsClearedAndArraysZeroedOnClose() throws Exception {
        shown = show();
        var left = new CopyOnWriteArrayList<Integer>();
        var given = new AtomicReference<char[]>();
        var view = new AtomicReference<FakeKitView>();
        Consumer<Outcome> reading = outcome -> {
            view.get().withSecret(SignInDialog.PASSWORD, chars -> left.add(chars.length));
            if (outcome instanceof Outcome.Ok ok) given.set(ok.password());
        };
        for (boolean ok : List.of(true, false)) {
            onUiThread(() -> SignInDialog.show(shown.page, shown.place, "intranet.example", 443, "Corp", false, false, false, none(),
                reading, unhurried));
            onUiThread(() -> {
                view.set(view());
                view().setSecret(SignInDialog.PASSWORD, "pw".toCharArray());
                press(ok ? form().actions().getLast().action() : form().cancelAction());
            });
        }
        assertThat(left).as("the field held nothing as OK and Cancel were answered").containsExactly(0, 0);
        assertThat(given.get()).as("OK's copy, the caller's to zero").containsExactly('p', 'w');

        // A login the picker gives once the dialog has closed is zeroed unused.
        var late = new CompletableFuture<Optional<Login>>() {
            @Override public boolean cancel(boolean interrupt) {
                return false; // a picker that ignores the withdrawal
            }
        };
        var told = new Answers();
        onUiThread(() -> SignInDialog.show(shown.page, shown.place, "intranet.example", 443, "Corp", false, true, false,
            anchor -> late, told, unhurried));
        onUiThread(() -> {
            press(SignInDialog.MANAGER);
            press(form().cancelAction());
            assertThat(told.calls).containsExactly("cancelled");
        });
        char[] password = "pw".toCharArray();
        onUiThread(() -> late.complete(Optional.of(new Login("u", password))));
        assertThat(password).as("zeroed unused").containsOnly((char) 0);
        onUiThread(() -> assertThat(told.calls).containsExactly("cancelled"));
    }

    @Test void passwordManagerOpensThePickerOnItsButtonAndAChoiceGivesPicked() throws Exception {
        shown = show();
        var picker = new Picker();
        onUiThread(() -> sign(true, true, picker));
        onUiThread(() -> {
            view().setSecret(SignInDialog.PASSWORD, "typed".toCharArray());
            press(form().actions().getFirst().action());
            assertThat(picker.anchors).singleElement().satisfies(anchor -> assertThat(anchor.isOpen()).as("on the shown button").isTrue());
            assertThat(answers.calls).as("unanswered while the picker shows").isEmpty();
            picker.picks.getFirst().complete(Optional.of(new Login("u", "pw".toCharArray())));
            assertThat(answers.calls).containsExactly("picked|u|pw");
            assertThat(shown.host.windows()).as("the dialog closed").isEmpty();
            assertThat(picker.anchors.getFirst().isOpen()).as("the button's anchor").isFalse();
            assertThat(shown.host.failures()).isEmpty();
        });
    }

    @Test void aCancelledPickerKeepsTheDialog() throws Exception {
        shown = show();
        var picker = new Picker();
        onUiThread(() -> sign(true, true, picker));
        onUiThread(() -> {
            press(SignInDialog.MANAGER);
            picker.picks.getFirst().complete(Optional.empty());
            assertThat(shown.host.windowView(ID, TITLE)).as("kept").isPresent();
            assertThat(answers.calls).isEmpty();
            press(SignInDialog.MANAGER);
            assertThat(picker.picks).as("the picker opens again").hasSize(2);
            try (var log = new CapturedLog(SignInDialog.class.getName())) {
                picker.picks.getLast().completeExceptionally(new IllegalStateException("picker failed"));
                assertThat(log.records()).singleElement().satisfies(record -> {
                    assertThat(record.getMessage()).isEqualTo("The password manager failed for a sign-in");
                    assertThat(record.getThrown()).hasMessage("picker failed");
                });
            }
            assertThat(shown.host.windowView(ID, TITLE)).as("kept after a failed pick").isPresent();
            view().type(SignInDialog.USER, "u");
            press(form().actions().getLast().action());
            assertThat(answers.calls).containsExactly("ok|u||false");
        });
    }

    @Test void aPickWhileOnePickRunsDoesNothing() throws Exception {
        shown = show();
        var picker = new Picker();
        onUiThread(() -> sign(true, true, picker));
        onUiThread(() -> {
            press(SignInDialog.MANAGER);
            press(SignInDialog.MANAGER);
            assertThat(picker.picks).as("one pick").hasSize(1);
            press(form().cancelAction());
            assertThat(picker.picks.getFirst().isCancelled()).as("the dialog's close withdraws the pick").isTrue();
            assertThat(answers.calls).containsExactly("cancelled");
        });
    }

    @Test void closingTheItemAnswersCancelledOnce() throws Exception {
        shown = show();
        var picker = new Picker();
        onUiThread(() -> sign(true, true, picker));
        onUiThread(() -> {
            press(SignInDialog.MANAGER);
            shown.item.close();
            assertThat(shown.host.windows()).as("the dialog went with its item").isEmpty();
            assertThat(answers.calls).containsExactly("cancelled");
            assertThat(picker.picks.getFirst().isCancelled()).as("the pick withdrawn").isTrue();
        });
        var later = new Answers();
        onUiThread(() -> SignInDialog.show(shown.page, shown.place, "intranet.example", 443, "Corp", false, true, true, none(), later,
            unhurried));
        onUiThread(() -> {
            assertThat(answers.calls).as("once").containsExactly("cancelled");
            assertThat(later.calls).as("a closed page shows nothing").containsExactly("cancelled");
            assertThat(shown.host.windows()).isEmpty();
            assertThat(shown.host.failures()).isEmpty();
        });
    }

    /** A page the user is not looking at cannot cover the window; its item's close answers a waiting dialog once. */
    @Test void aBackgroundItemsDialogWaitsUntilItsItemIsActive() throws Exception {
        shown = show();
        var other = new AtomicReference<BrowserItem>();
        onUiThread(() -> other.set(shown.items.open(URI.create("https://example.org"), TabPlacement.NEW_TAB, Optional.empty(),
            Optional.empty())));
        onUiThread(() -> sign(true, true, none()));
        onUiThread(() -> {
            assertThat(shown.host.windows()).as("waiting").isEmpty();
            shown.item.tab().select();
        });
        onUiThread(() -> {
            assertThat(shown.host.windowView(ID, TITLE)).as("its item is active").isPresent();
            press(form().cancelAction());
            other.get().tab().select();
        });
        var waiting = new Answers();
        onUiThread(() -> SignInDialog.show(shown.page, shown.place, "intranet.example", 443, "", false, false, false, none(), waiting,
            unhurried));
        onUiThread(() -> {
            assertThat(shown.host.windows()).isEmpty();
            shown.item.close();
            assertThat(waiting.calls).containsExactly("cancelled");
        });
        onUiThread(() -> {
            assertThat(waiting.calls).as("once").containsExactly("cancelled");
            assertThat(answers.calls).containsExactly("cancelled");
            assertThat(shown.host.failures()).isEmpty();
        });
    }

    /** Chromium's reset of the page's dialogs, or closing what show returned, ends it, shown, waiting or not started. */
    @Test void aResetOrItsSubscriptionEndsItAsCancelled() throws Exception {
        shown = show();
        onUiThread(() -> sign(true, true, none()));
        onUiThread(() -> {
            shown.page.dialogsReset();
            assertThat(answers.calls).containsExactly("cancelled");
            assertThat(shown.host.windows()).isEmpty();
        });
        var ended = new Answers();
        var subscription = new AtomicReference<Subscription>();
        onUiThread(() -> subscription.set(SignInDialog.show(shown.page, shown.place, "intranet.example", 443, "", false, false, false,
            none(), ended, unhurried)));
        onUiThread(() -> {
            assertThat(shown.host.windowView(ID, TITLE)).isPresent();
            subscription.get().close();
            subscription.get().close();
            assertThat(ended.calls).containsExactly("cancelled");
            assertThat(shown.host.windows()).isEmpty();
        });
        var early = new Answers();
        onUiThread(() -> {
            SignInDialog.show(shown.page, shown.place, "intranet.example", 443, "", false, false, false, none(), early, unhurried).close();
            assertThat(early.calls).as("before it starts").containsExactly("cancelled");
        });
        onUiThread(() -> {
            assertThat(shown.host.windows()).as("it never shows").isEmpty();
            assertThat(early.calls).containsExactly("cancelled");
            assertThat(shown.host.failures()).isEmpty();
        });
    }

    /**
     * F4: the page's dialogs share one flood guard, so a page that retries a sign-in after each Cancel gets two quick
     * dialogs, and the third, and every later one, is cancelled unshown until the page loads again; its alerts count.
     */
    @Test void aThirdQuickDialogIsRefused() throws Exception {
        shown = show();
        var clock = new AtomicLong(10_000);
        var alert = new CopyOnWriteArrayList<String>();
        ScriptDialog.ask(shown.page, ScriptDialog.Kind.ALERT, "https://intranet.example/", "First", (ok, text) -> alert.add(ok + ""),
            clock::get);
        onUiThread(() -> {
            clock.set(10_100);
            press(view("Web Page Alert"), form(view("Web Page Alert")).actions().getFirst().action());
        });
        clock.set(10_600);
        var second = new Answers();
        onUiThread(() -> SignInDialog.show(shown.page, shown.place, "intranet.example", 443, "Corp", false, false, false, none(), second,
            clock::get));
        onUiThread(() -> {
            assertThat(shown.host.windowView(ID, TITLE)).as("the second of the run shows").isPresent();
            clock.set(10_700);
            press(form().cancelAction());
        });
        clock.set(10_700 + DialogFlood.QUICK_MILLIS);
        var third = new Answers();
        onUiThread(() -> SignInDialog.show(shown.page, shown.place, "intranet.example", 443, "Corp", false, false, false, none(), third,
            clock::get));
        onUiThread(() -> assertThat(third.calls).as("a second after the second closed").containsExactly("cancelled"));
        clock.set(120_000);
        var later = new Answers();
        onUiThread(() -> SignInDialog.show(shown.page, shown.place, "intranet.example", 443, "Corp", false, false, false, none(), later,
            clock::get));
        onUiThread(() -> {
            assertThat(shown.host.windows()).as("refused unshown").isEmpty();
            assertThat(alert).containsExactly("true");
            assertThat(second.calls).containsExactly("cancelled");
            assertThat(later.calls).as("however late, until the page loads again").containsExactly("cancelled");
            PageTestAccess.loadStarted(shown.page, "https://intranet.example/next");
        });
        var reloaded = new Answers();
        onUiThread(() -> SignInDialog.show(shown.page, shown.place, "intranet.example", 443, "Corp", false, false, false, none(),
            reloaded, clock::get));
        onUiThread(() -> {
            assertThat(shown.host.windowView(ID, TITLE)).as("a new document may ask again").isPresent();
            press(form().cancelAction());
            assertThat(reloaded.calls).containsExactly("cancelled");
            assertThat(shown.host.failures()).isEmpty();
        });
    }

    /** V19: a sign-in waits while the page has a permission prompt asked, so it cannot stack over the prompt. */
    @Test void itWaitsBehindAPermissionPrompt() throws Exception {
        shown = show();
        var decisions = new CopyOnWriteArrayList<String>();
        onUiThread(() -> PermissionPrompt.ask(shown.place, shown.page, new PermissionRequest("https://intranet.example", Permission.CAMERA),
            decision -> decisions.add(decision.map(Enum::name).orElse("dismissed")), unhurried, 500));
        onUiThread(() -> sign(true, true, none()));
        onUiThread(() -> {
            assertThat(shown.host.windowView(ID, "Allow Camera")).isPresent();
            assertThat(shown.host.windowView(ID, TITLE)).as("behind the prompt").isEmpty();
            FakeKitView prompt = view("Allow Camera");
            press(prompt, form(prompt).cancelAction());
            assertThat(decisions).containsExactly("dismissed");
        });
        onUiThread(() -> {
            assertThat(shown.host.windowView(ID, TITLE)).as("once the prompt is answered").isPresent();
            press(form().cancelAction());
            assertThat(answers.calls).containsExactly("cancelled");
            assertThat(shown.host.failures()).isEmpty();
        });
    }

    @Test void noAnswerOrLoginNamesItsUserOrPassword() {
        assertThat(new Login("alice", "pw".toCharArray())).asString().doesNotContain("alice", "pw");
        assertThat(new Outcome.Ok("alice", "pw".toCharArray(), true)).asString().doesNotContain("alice", "pw");
        assertThat(new Outcome.Picked("alice", "pw".toCharArray())).asString().doesNotContain("alice", "pw");
    }

    /** Shows a sign-in for intranet.example's Corp realm over the shown item, told to {@link #answers}. UI thread. */
    private void sign(boolean offers, boolean remembers, Function<UiAnchor, CompletableFuture<Optional<Login>>> manager) {
        SignInDialog.show(shown.page, shown.place, "intranet.example", 443, "Corp", false, offers, remembers, manager, answers,
            unhurried);
    }

    /** A picker that never opens: nothing chosen. */
    private static Function<UiAnchor, CompletableFuture<Optional<Login>>> none() {
        return anchor -> CompletableFuture.completedFuture(Optional.empty());
    }

    private static Nodes.Field user(Nodes.Form form) {
        return (Nodes.Field) ((Nodes.FormRow) form.sections().get(1)).control();
    }

    private static Nodes.Secret password(Nodes.Form form) {
        return (Nodes.Secret) ((Nodes.FormRow) form.sections().get(2)).control();
    }

    private static Optional<Nodes.Toggle> toggle(Nodes.Form form) {
        return form.sections().stream().filter(Nodes.SettingRow.class::isInstance)
            .map(row -> (Nodes.Toggle) ((Nodes.SettingRow) row).control()).findFirst();
    }

    /** The form of the open sign-in dialog. UI thread. */
    private Nodes.Form form() {
        return form(view());
    }

    private static Nodes.Form form(FakeKitView view) {
        return (Nodes.Form) view.spec().root();
    }

    private FakeKitView view() {
        return view(TITLE);
    }

    private FakeKitView view(String title) {
        return shown.host.windowView(ID, title).orElseThrow(() -> new AssertionError("no dialog " + title));
    }

    /** Presses the button, or the key, that reports {@code action} in the sign-in dialog. UI thread. */
    private void press(String action) {
        press(view(), action);
    }

    private static void press(FakeKitView view, String action) {
        view.emit(new UiEvent.Action(action));
    }

    /** A started stand-in Browser on a fake host, with one browser item open in a window. */
    private Shown show() throws Exception {
        var result = new AtomicReference<Shown>();
        onUiThread(() -> {
            var host = new FakePluginHost();
            var started = new AtomicReference<BrowserItems>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), plugin -> started.set(new BrowserItems(
                plugin, BrowserSettings.declare(plugin.settings())::read, new DataStores(memory.make(), () -> false), Optional.empty(),
                page -> { })));
            var items = started.get();
            host.activateTerminalWindow(host.addTerminalWindow());
            BrowserItem item = items.open(URI.create("https://intranet.example/"), TabPlacement.NEW_TAB, Optional.empty(),
                Optional.empty());
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
        // What the body posted, such as the dialog's show, runs before the next step.
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

    /** A picker that records each anchor it opens on and leaves each pick for the test to answer. */
    private static final class Picker implements Function<UiAnchor, CompletableFuture<Optional<Login>>> {
        final List<UiAnchor> anchors = new CopyOnWriteArrayList<>();
        final List<CompletableFuture<Optional<Login>>> picks = new CopyOnWriteArrayList<>();

        @Override public CompletableFuture<Optional<Login>> apply(UiAnchor anchor) {
            anchors.add(anchor);
            var pick = new CompletableFuture<Optional<Login>>();
            picks.add(pick);
            return pick;
        }
    }

    /** A dialog's answers, in order: {@code ok|user|password|remember}, {@code picked|user|password}, {@code cancelled}. */
    private static final class Answers implements Consumer<Outcome> {
        final List<String> calls = new CopyOnWriteArrayList<>();

        @Override public void accept(Outcome outcome) {
            calls.add(switch (outcome) {
                case Outcome.Ok ok -> "ok|" + ok.user() + "|" + new String(ok.password()) + "|" + ok.remember();
                case Outcome.Picked picked -> "picked|" + picked.user() + "|" + new String(picked.password());
                case Outcome.Cancelled cancelled -> "cancelled";
            });
        }
    }
}
