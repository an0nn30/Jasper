package dev.jasper.browser.logins;

import dev.jasper.browser.dialogs.DialogsTestAccess;
import dev.jasper.browser.dialogs.SignInDialog;
import dev.jasper.browser.dialogs.SignInDialog.Outcome;
import dev.jasper.browser.item.BrowserItem;
import dev.jasper.browser.item.BrowserItems;
import dev.jasper.browser.item.DataStores;
import dev.jasper.browser.item.ItemTestAccess;
import dev.jasper.browser.page.DialogPlace;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.page.SignIn;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.store.CapturedLog;
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TabPlacement;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.vault.api.Credential;
import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.api.CredentialPickResult;
import dev.jasper.vault.api.Kind;
import dev.jasper.vault.api.LockState;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The plugin's HTTP sign-ins (plan 8, spec section 13): a page's challenge shows Jasper's sign-in dialog over its
 * item, one at a time, the rest waiting in order, and past eight waiting the next is cancelled at once; OK and a login
 * chosen in the Vault's picker sign in, anything else cancels; the dialog offers the Vault's controls only as the
 * challenge allows, and none without the Vault or in /dev/null; a main-frame load start cancels the earlier document's
 * challenges (F8); one answer serves the page's waiting challenges of the same protection space (F30); a sign-in
 * answered with "Remember this password" is saved at the page's next main-frame 2xx from its origin in the same
 * navigation (L34), and a remembered login fills the top-level challenge of a navigation the user began in without the
 * dialog (L35), once until its origin answers and that page commits (review focus 1, L30); and the item's close and
 * the plugin's stop cancel every challenge exactly once and drop every save, its password zeroed (review focus 5). The
 * pages are stand-in items' own on the testkit's fake host, the Vault is {@link FakeVault}, and Chromium's side is a
 * recording {@link SignIn}.
 */
class HttpSignInsTest {
    private static final String ID = "dev.jasper.browser";
    private static final String TITLE = SignInDialog.TITLE;
    private static final String TOP = "https://intranet.example/", NEXT = "https://intranet.example/next";
    // The login name of TOP's sign-ins for the realm Corp.
    private static final String CORP = "https://intranet.example (Corp)";
    private final MemoryStores memory = new MemoryStores();
    private final FakeVault fake = new FakeVault();
    // What Chromium was told, in order: "NAME proceed USER PASSWORD" or "NAME cancel".
    private final List<String> told = new CopyOnWriteArrayList<>();
    private final AtomicLong now = new AtomicLong();
    // The page's clock, for its navigations' activations and Jasper's marks (L35, L42).
    private final AtomicLong millis = new AtomicLong(1_000_000);
    private AutoCloseable clock;
    private FakePluginHost host;
    private BrowserItems items;
    private BrowserItem item;
    private Page page;
    private DialogPlace place;

    // The sign-in dialogs' flood guard reads a user who takes their time: 10 s between readings.
    @BeforeEach void unhurried() {
        clock = DialogsTestAccess.signInsTimedBy(() -> now.addAndGet(10_000));
    }

    @AfterEach void close() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            if (host != null) host.close();
        });
        clock.close();
        memory.close();
    }

    @Test void aChallengeShowsTheDialogAndOkProceedsOnce() throws Exception {
        onUiThread(() -> challenge("a", TOP, "intranet.example", 443, "Corp", false));
        onUiThread(() -> {
            Nodes.Form form = form();
            assertThat(((Nodes.Text) form.sections().getFirst()).text())
                .isEqualTo("The website “intranet.example” requires a user name and password for “Corp”.");
            assertThat(told).as("waiting for the user").isEmpty();
            type("u", "pw");
            press(ok());
            assertThat(told).containsExactly("a proceed u pw");
            assertThat(host.windows()).as("the dialog closed").isEmpty();
            items.signIns().closed(page);
            assertThat(told).as("once").containsExactly("a proceed u pw");
            assertThat(host.failures()).isEmpty();
        });
    }

    @Test void aSecondChallengeWaitsForTheFirst() throws Exception {
        onUiThread(() -> {
            challenge("a", TOP, "intranet.example", 443, "Corp", false);
            challenge("b", TOP, "intranet.example", 443, "Other", false);
        });
        onUiThread(() -> {
            assertThat(host.windows()).as("one dialog at a time").hasSize(1);
            assertThat(heading()).contains("“Corp”");
            type("u", "pw");
            press(ok());
            assertThat(told).containsExactly("a proceed u pw");
        });
        onUiThread(() -> {
            assertThat(heading()).as("the second shows next").contains("“Other”");
            press(form().cancelAction());
            assertThat(told).containsExactly("a proceed u pw", "b cancel");
            assertThat(host.windows()).isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    @Test void theNinthWaitingChallengeIsCancelled() throws Exception {
        onUiThread(() -> {
            for (int at = 0; at <= HttpSignIns.WAITING_LIMIT + 1; at++)
                challenge("c" + at, TOP, "intranet.example", 443, "Realm " + at, false);
            assertThat(told).as("one shown, eight waiting, the ninth waiting cancelled at once")
                .containsExactly("c" + (HttpSignIns.WAITING_LIMIT + 1) + " cancel");
        });
        for (int at = 0; at <= HttpSignIns.WAITING_LIMIT; at++) {
            int shown = at;
            onUiThread(() -> {
                assertThat(heading()).as("in the order asked").contains("“Realm " + shown + "”");
                press(form().cancelAction());
            });
        }
        onUiThread(() -> {
            assertThat(told).hasSize(HttpSignIns.WAITING_LIMIT + 2).doesNotHaveDuplicates();
            assertThat(host.windows()).isEmpty();
        });
    }

    /** Review focus 5: the item's close cancels the shown and waiting challenges once; a late login is closed unused. */
    @Test void closingTheItemCancelsEveryChallengeOnce() throws Exception {
        open(true);
        UUID id = fake.login("https://intranet.example (Corp)", "u", "pw");
        fake.ungranted.add(id); // a login the Browser has no saved grant for, so the dialog shows
        var withdrawn = new AtomicBoolean();
        var late = new CompletableFuture<Optional<Credential>>() {
            @Override public boolean cancel(boolean interrupt) {
                withdrawn.set(true);
                return false; // a Vault that answers after the withdrawal
            }
        };
        fake.credentialAnswer = late;
        onUiThread(() -> {
            challenge("a", TOP, "intranet.example", 443, "Corp", false);
            challenge("b", TOP, "intranet.example", 443, "Other", false);
            challenge("c", TOP, "intranet.example", 8443, "Corp", false);
        });
        onUiThread(() -> {
            press(managerAction());
            assertThat(fake.calls).as("no silent fill, then the login chosen, its fetch under way")
                .containsExactly("credentialIfGranted https://intranet.example (Corp)",
                    "credential https://intranet.example (Corp)");
            item.close();
            assertThat(told).containsExactlyInAnyOrder("a cancel", "b cancel", "c cancel");
            assertThat(withdrawn).as("the close withdrew the Vault's fetch, whose login then goes to no one").isTrue();
            assertThat(host.windows()).isEmpty();
            items.signIns().closed(page);
        });
        Credential credential = fake.issue(id);
        onUiThread(() -> late.complete(Optional.of(credential)));
        onUiThread(() -> {
            assertThat(FakeVault.closed(credential)).as("a late login is closed").isTrue();
            assertThat(told).as("each once, the late login used for none").containsExactlyInAnyOrder("a cancel", "b cancel", "c cancel");
            challenge("d", TOP, "intranet.example", 443, "Corp", false);
            assertThat(told).as("a closed page's challenge").contains("d cancel");
            assertThat(host.failures()).isEmpty();
        });
    }

    /** F8: a main-frame load start cancels the earlier document's challenges, shown and waiting, closing the dialog. */
    @Test void aLoadStartCancelsTheEarlierDocumentsChallenges() throws Exception {
        onUiThread(() -> {
            challenge("a", TOP, "intranet.example", 443, "Corp", false);
            challenge("b", TOP, "intranet.example", 443, "Other", false);
        });
        onUiThread(() -> {
            assertThat(host.windows()).hasSize(1);
            PageTestAccess.loadStarted(page, "https://intranet.example/next");
            assertThat(told).containsExactlyInAnyOrder("a cancel", "b cancel");
            assertThat(host.windows()).as("the dialog closed").isEmpty();
        });
        onUiThread(() -> {
            assertThat(host.windows()).as("nothing waits").isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    /** F8: a challenge of the document that loaded stays, and a later load start cancels only what came before it. */
    @Test void aChallengeOfTheNewDocumentStays() throws Exception {
        onUiThread(() -> challenge("a", TOP, "intranet.example", 443, "Corp", false));
        onUiThread(() -> {
            PageTestAccess.loadStarted(page, "https://intranet.example/next");
            challenge("b", TOP, "intranet.example", 443, "Other", false);
        });
        onUiThread(() -> {
            assertThat(told).containsExactly("a cancel");
            assertThat(heading()).as("the new document's").contains("“Other”");
            PageTestAccess.loadStarted(page, "https://intranet.example/last");
            assertThat(told).as("a second load start cancels the challenge that came before it").containsExactly("a cancel", "b cancel");
            challenge("c", TOP, "intranet.example", 443, "Third", false);
        });
        onUiThread(() -> {
            assertThat(heading()).as("the last document's stays").contains("“Third”");
            type("u", "pw");
            press(ok());
            assertThat(told).containsExactly("a cancel", "b cancel", "c proceed u pw");
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * L27: once a page's dialogs trip its flood guard, three realms quickly one after another here, the page's own
     * challenges are refused, but the top-level challenge of a navigation Jasper starts for the user shows, and so does
     * each wrong-password challenge of it, while it is pending; a framed or other challenge during it is still refused,
     * and the next load start re-arms the guard as before.
     */
    @Test void aTrippedGuardStillShowsTheUsersNavigationsOwnSignIn() throws Exception {
        tripsQuickly();
        onUiThread(() -> {
            challenge("a", TOP, "intranet.example", 443, "One", false);
            challenge("b", TOP, "intranet.example", 443, "Two", false);
            challenge("c", TOP, "intranet.example", 443, "Three", false);
        });
        onUiThread(() -> press(form().cancelAction()));
        onUiThread(() -> press(form().cancelAction()));
        onUiThread(() -> {
            assertThat(told).as("the third refused unshown").containsExactly("a cancel", "b cancel", "c cancel");
            assertThat(host.windows()).isEmpty();
            challenge("own", TOP, "intranet.example", 443, "One", false);
        });
        onUiThread(() -> {
            assertThat(told).as("the page's own top-level fetch").endsWith("own cancel");
            page.load(URI.create(NEXT));
            items.signIns().challenged(page, place, false,
                signIn("framed", "https://files.example/", NEXT, "files.example", 443, "One", false, "basic"));
            items.signIns().challenged(page, place, false,
                signIn("fetch", TOP + "api", NEXT, "intranet.example", 443, "One", false, "basic"));
        });
        onUiThread(() -> {
            assertThat(told).as("refused during the navigation too").endsWith("framed cancel", "fetch cancel");
            assertThat(host.windows()).isEmpty();
            challenge("navigation", NEXT, "intranet.example", 443, "One", false);
        });
        onUiThread(() -> {
            assertThat(heading()).as("the user's navigation asks").contains("“One”");
            type("u", "wrong");
            press(ok());
            assertThat(told).endsWith("navigation proceed u wrong");
            challenge("again", NEXT, "intranet.example", 443, "One", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("Chromium's challenge again, for the wrong password").isPresent();
            press(form().cancelAction());
            assertThat(told).endsWith("again cancel");
            PageTestAccess.loadStarted(page, NEXT);
            challenge("rearmed", NEXT, "intranet.example", 443, "One", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("the load start re-armed the guard").isPresent();
            press(form().cancelAction());
            assertThat(told).endsWith("rearmed cancel");
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * L28: the user's Cancel of a dialog shown past the trip ends the navigation's bypass, for the challenge waiting
     * then and for one that comes later, so a navigation that never commits gives the page one dialog; Jasper's next
     * navigation has a bypass of its own.
     */
    @Test void aCancelEndsTheUsersNavigationsBypass() throws Exception {
        trip();
        onUiThread(() -> {
            page.load(URI.create(NEXT));
            challenge("first", NEXT, "intranet.example", 443, "One", false);
            challenge("queued", NEXT, "intranet.example", 443, "Two", false);
        });
        onUiThread(() -> {
            assertThat(heading()).as("past the trip").contains("\u201COne\u201D");
            press(form().cancelAction());
            assertThat(told).endsWith("first cancel");
        });
        onUiThread(() -> {
            assertThat(told).as("the one waiting, refused unshown").endsWith("queued cancel");
            assertThat(host.windowView(ID, TITLE)).isEmpty();
            challenge("later", NEXT, "intranet.example", 443, "Three", false);
        });
        onUiThread(() -> {
            assertThat(told).as("one that comes later").endsWith("later cancel");
            assertThat(host.windowView(ID, TITLE)).isEmpty();
            page.load(URI.create(NEXT));
            challenge("again", NEXT, "intranet.example", 443, "One", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("Jasper's next navigation").isPresent();
            press(form().cancelAction());
            assertThat(told).endsWith("again cancel");
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * L28, L29: a main-frame load failure of the address the navigation's sign-in was asked from, such as a stopped
     * load, ends its bypass; one before any sign-in ends nothing
     * ({@link #onlyTheChallengesOwnLoadFailureEndsTheUsersNavigationsBypass}).
     */
    @Test void aLoadFailureEndsTheUsersNavigationsBypass() throws Exception {
        trip();
        onUiThread(() -> {
            page.load(URI.create(NEXT));
            challenge("navigation", NEXT, "intranet.example", 443, "One", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("past the trip").isPresent();
            type("u", "pw");
            press(ok());
            assertThat(told).endsWith("navigation proceed u pw");
            PageTestAccess.loadFailed(page, NEXT, "ERR_ABORTED");
            challenge("after", NEXT, "intranet.example", 443, "One", false);
        });
        onUiThread(() -> {
            assertThat(told).as("refused once the load failed").endsWith("after cancel");
            assertThat(host.windowView(ID, TITLE)).isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * L27 and V-F25: a page that retries its fetch after each Cancel trips the guard at its third retry; then the
     * user's navigation still asks, while the old page's retry during it is refused.
     */
    @Test void aRetryPageTrippedAfterTwoCancelsStillLetsTheUserNavigate() throws Exception {
        tripsQuickly();
        String api = TOP + "api";
        for (String name : List.of("retry1", "retry2")) {
            onUiThread(() -> items().signIns().challenged(page, place, false,
                signIn(name, api, TOP, "intranet.example", 443, "Corp", false, "basic")));
            onUiThread(() -> press(form().cancelAction()));
        }
        onUiThread(() -> {
            assertThat(told).containsExactly("retry1 cancel", "retry2 cancel");
            items.signIns().challenged(page, place, false, signIn("retry3", api, TOP, "intranet.example", 443, "Corp", false, "basic"));
        });
        onUiThread(() -> {
            assertThat(told).as("the third retry refused").endsWith("retry3 cancel");
            page.load(URI.create(NEXT));
            items.signIns().challenged(page, place, false, signIn("retry4", api, NEXT, "intranet.example", 443, "Corp", false, "basic"));
            challenge("navigation", NEXT, "intranet.example", 443, "Corp", false);
        });
        onUiThread(() -> {
            assertThat(told).as("the old page's retry during the navigation").endsWith("retry4 cancel");
            type("u", "pw");
            press(ok());
            assertThat(told).endsWith("navigation proceed u pw");
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * Concern C: one answer's password array is zeroed after the last same-realm challenge it signs in, and an answer
     * that comes once the page's sign-ins ended signs in nothing and is zeroed too.
     */
    @Test void anAnswersArrayIsZeroedAfterItsLastUseAndOneAfterTheEndIsUsedForNothing() throws Exception {
        onUiThread(() -> {
            challenge("a", TOP, "intranet.example", 443, "Corp", false);
            challenge("same", TOP, "intranet.example", 443, "Corp", false);
        });
        char[] given = "pw".toCharArray();
        onUiThread(() -> {
            LoginsTestAccess.answerShown(items.signIns(), page).accept(new Outcome.Ok("u", given, false));
            assertThat(told).containsExactly("a proceed u pw", "same proceed u pw");
            assertThat(given).as("zeroed after the last of them").containsOnly((char) 0);
            press(form().cancelAction()); // the answered challenge's dialog, which answers nothing more
            assertThat(told).containsExactly("a proceed u pw", "same proceed u pw");
            challenge("b", TOP, "intranet.example", 443, "Other", false);
        });
        char[] late = "pw".toCharArray();
        onUiThread(() -> {
            var answer = LoginsTestAccess.answerShown(items.signIns(), page);
            items.signIns().closed(page);
            assertThat(told).endsWith("b cancel");
            answer.accept(new Outcome.Ok("u", late, false));
            assertThat(told).as("nothing signed in after the end").endsWith("b cancel").doesNotContain("b proceed u pw");
            assertThat(late).as("zeroed").containsOnly((char) 0);
        });
    }

    /** The plugin's stop cancels every page's challenges, shown and waiting, once; and each one after it at once. */
    @Test void theStopCancelsEveryChallengeOnceAndEachLaterOneAtOnce() throws Exception {
        onUiThread(() -> {
            challenge("a", TOP, "intranet.example", 443, "Corp", false);
            challenge("b", TOP, "intranet.example", 443, "Other", false);
        });
        onUiThread(() -> {
            assertThat(host.windows()).hasSize(1);
            items.signIns().close();
            assertThat(told).containsExactlyInAnyOrder("a cancel", "b cancel");
            assertThat(host.windows()).as("the dialog closed").isEmpty();
            items.signIns().navigating(page);
            challenge("c", TOP, "intranet.example", 443, "Corp", false);
            assertThat(told).as("after the stop").endsWith("c cancel");
            items.signIns().close();
            item.close();
            assertThat(told).as("each once").hasSize(3);
            assertThat(host.failures()).isEmpty();
        });
    }

    /** A page's own close, without its item's, cancels its challenges at once, the waiting ones too (deviation 8). */
    @Test void aPagesOwnCloseCancelsItsChallengesAtOnce() throws Exception {
        onUiThread(() -> {
            challenge("a", TOP, "intranet.example", 443, "Corp", false);
            challenge("b", TOP, "intranet.example", 443, "Other", false);
        });
        onUiThread(() -> {
            page.close();
            assertThat(told).as("in the same turn").containsExactlyInAnyOrder("a cancel", "b cancel");
            assertThat(host.windows()).isEmpty();
        });
        onUiThread(() -> assertThat(told).as("each once").hasSize(2));
    }

    /**
     * L13 and a Digest challenge: with the Vault, a host {@link URI} cannot read (an underscore, which Chromium
     * accepts) and a Digest challenge each get the plain dialog.
     */
    @Test void anUnreadableHostAndADigestChallengeGetThePlainDialog() throws Exception {
        open(true);
        String underscore = "https://my_host.example/";
        onUiThread(() -> items.signIns().challenged(page, place, false,
            signIn("l13", underscore, underscore, "my_host.example", 443, "Corp", false, "basic")));
        onUiThread(() -> {
            assertThat(labels()).containsExactly("Cancel", "OK");
            assertThat(remember()).isFalse();
            press(form().cancelAction());
            items.signIns().challenged(page, place, false, signIn("digest", TOP, TOP, "intranet.example", 443, "Corp", false, "digest"));
        });
        onUiThread(() -> {
            assertThat(labels()).containsExactly("Cancel", "OK");
            assertThat(remember()).isFalse();
            press(form().cancelAction());
            assertThat(told).containsExactly("l13 cancel", "digest cancel");
        });
    }

    /** F30: one answer serves the page's waiting challenges of the same host, port, realm, scheme and proxy flag. */
    @Test void oneAnswerServesThePagesSameRealmChallenges() throws Exception {
        onUiThread(() -> {
            challenge("a", TOP, "intranet.example", 443, "Corp", false);
            challenge("same", TOP, "intranet.example", 443, "Corp", false);
            challenge("realm", TOP, "intranet.example", 443, "Other", false);
            challenge("port", TOP, "intranet.example", 8443, "Corp", false);
            challenge("host", TOP, "files.example", 443, "Corp", false);
            challenge("proxy", TOP, "intranet.example", 443, "Corp", true);
            items.signIns().challenged(page, place, false, signIn("scheme", TOP, TOP, "intranet.example", 443, "Corp", false, "digest"));
            challenge("again", TOP, "intranet.example", 443, "Corp", false);
        });
        onUiThread(() -> {
            type("u", "pw");
            press(ok());
            assertThat(told).containsExactly("a proceed u pw", "same proceed u pw", "again proceed u pw");
        });
        for (String next : List.of("realm", "port", "host", "proxy", "scheme"))
            onUiThread(() -> {
                assertThat(host.windowView(ID, TITLE)).as(next + " still asks").isPresent();
                press(form().cancelAction());
                assertThat(told).last().isEqualTo(next + " cancel");
            });
        onUiThread(() -> assertThat(host.windows()).isEmpty());
    }

    /** F30 for a login chosen in the Vault's picker, which is searched for the host without its leading www. (F26). */
    @Test void aLoginChosenInThePickerSignsInTheSameRealmsChallenges() throws Exception {
        open(true);
        fake.ungranted.add(fake.login("https://www.intranet.example (Corp)", "u", "pw"));
        String www = "https://www.intranet.example/";
        onUiThread(() -> {
            items.signIns().challenged(page, place, false, signIn("a", www, www, "www.intranet.example", 443, "Corp", false, "basic"));
            items.signIns().challenged(page, place, false, signIn("same", www, www, "www.intranet.example", 443, "Corp", false, "basic"));
        });
        onUiThread(() -> {
            press(managerAction());
            assertThat(fake.picks).singleElement().satisfies(request -> assertThat(request.search()).isEqualTo("intranet.example"));
            assertThat(fake.pickOwners).containsExactly(item.tab().window());
            assertThat(told).containsExactly("a proceed u pw", "same proceed u pw");
            assertThat(host.windows()).isEmpty();
            assertThat(fake.issued).singleElement().satisfies(credential -> assertThat(FakeVault.closed(credential)).isTrue());
            assertThat(host.failures()).isEmpty();
        });
    }

    @Test void aCancelledPickerKeepsTheDialog() throws Exception {
        open(true);
        fake.ungranted.add(fake.login("https://intranet.example (Corp)", "u", "pw"));
        fake.pickAnswer = CompletableFuture.completedFuture(new CredentialPickResult.Cancelled());
        onUiThread(() -> challenge("a", TOP, "intranet.example", 443, "Corp", false));
        onUiThread(() -> {
            press(managerAction());
            assertThat(fake.picks).hasSize(1);
            assertThat(host.windowView(ID, TITLE)).as("kept").isPresent();
            assertThat(told).isEmpty();
            press(form().cancelAction());
            assertThat(told).containsExactly("a cancel");
        });
    }

    @Test void withoutTheVaultTheDialogHasNoVaultControls() throws Exception {
        onUiThread(() -> challenge("a", TOP, "intranet.example", 443, "Corp", false));
        onUiThread(() -> {
            assertThat(labels()).containsExactly("Cancel", "OK");
            assertThat(remember()).isFalse();
            press(form().cancelAction());
        });
        open(true);
        onUiThread(() -> challenge("b", TOP, "intranet.example", 443, "Corp", false));
        onUiThread(() -> {
            assertThat(labels()).as("with the Vault, a top-level Basic challenge").containsExactly("Password Manager", "Cancel", "OK");
            assertThat(remember()).isTrue();
            press(form().cancelAction());
        });
        onUiThread(() -> items.signIns().challenged(page, place, false,
            signIn("c", "https://files.example/", TOP, "intranet.example", 443, "Corp", false, "basic")));
        onUiThread(() -> {
            assertThat(labels()).as("a framed one, from another origin").containsExactly("Password Manager", "Cancel", "OK");
            assertThat(remember()).as("never remembered").isFalse();
            press(form().cancelAction());
        });
        onUiThread(() -> challenge("d", TOP, "proxy.example", 8118, "Squid", true));
        onUiThread(() -> {
            assertThat(heading()).as("F22").isEqualTo("The proxy “proxy.example:8118” requires a user name and password for “Squid”.");
            assertThat(labels()).as("a proxy's is the plain dialog").containsExactly("Cancel", "OK");
            assertThat(remember()).isFalse();
            press(form().cancelAction());
            assertThat(told).containsExactly("a cancel", "b cancel", "c cancel", "d cancel");
        });
    }

    @Test void devNullHasNoVaultControls() throws Exception {
        open(true);
        onUiThread(() -> items.signIns().challenged(page, place, true,
            signIn("a", TOP, TOP, "intranet.example", 443, "Corp", false, "basic")));
        onUiThread(() -> {
            assertThat(labels()).containsExactly("Cancel", "OK");
            assertThat(remember()).isFalse();
            type("u", "pw");
            press(ok());
            assertThat(told).containsExactly("a proceed u pw");
            assertThat(fake.calls).as("the Vault is never asked").isEmpty();
        });
    }

    /** The item hands its page's sign-ins to the plugin's, whose dialog shows over it, and its close cancels them. */
    @Test void aPagesSignInReachesTheItemsDialog() throws Exception {
        open(false);
        onUiThread(() -> {
            page.signIn(signIn("a", TOP, TOP, "intranet.example", 443, "Corp", false, "basic"));
            page.signIn(signIn("b", TOP, TOP, "intranet.example", 443, "Other", false, "basic"));
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).isPresent();
            item.close();
            assertThat(told).containsExactlyInAnyOrder("a cancel", "b cancel");
            page.signIn(signIn("c", TOP, TOP, "intranet.example", 443, "Corp", false, "basic"));
            assertThat(told).as("a closed page cancels at once").contains("c cancel");
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * Remember ticked: the page's next main-frame 2xx from the challenge's origin saves the login under its name, over
     * the item's window, once; the pending save's array is kept while the Vault saves and zeroed once it has.
     */
    @Test void rememberedThenTwoHundredSavesTheLogin() throws Exception {
        open(true);
        onUiThread(() -> challenge("a", TOP, "intranet.example", 443, "Corp", false));
        onUiThread(() -> {
            assertThat(remember()).isTrue();
            type("u", "pw");
            tickRemember();
            press(ok());
            assertThat(told).containsExactly("a proceed u pw");
            assertThat(fake.calls).as("nothing saved before the response").isEmpty();
            page.mainFrameResponded(TOP, 200);
            assertThat(fake.calls).containsExactly("saveLogin " + CORP + " u");
            assertThat(fake.savedPasswords).containsExactly("pw");
            assertThat(fake.saveOwners).containsExactly(item.tab().window());
            page.mainFrameResponded(TOP, 200);
            assertThat(fake.calls).as("once").hasSize(1);
        });
        var saving = new CompletableFuture<Optional<CredentialDescriptor>>();
        fake.saveAnswer = saving;
        char[] given = "pw".toCharArray();
        onUiThread(() -> challenge("b", TOP, "intranet.example", 443, "Corp", false));
        onUiThread(() -> {
            okShown("u", given, true);
            assertThat(given).as("kept for the save").containsExactly('p', 'w');
            page.mainFrameResponded(TOP, 200);
            assertThat(fake.calls).hasSize(2);
            assertThat(given).as("while the Vault saves").containsExactly('p', 'w');
            saving.complete(Optional.of(new CredentialDescriptor(UUID.randomUUID(), CORP, "u", Kind.ACCOUNT_PASSWORD)));
            assertThat(given).as("zeroed once saved").containsOnly((char) 0);
            assertThat(host.notifications().lines()).isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    /** A 401, or a 2xx from another origin, drops the pending save unsaved and zeroed; a later 200 saves nothing. */
    @Test void aFourOhOneOrAnotherOriginSavesNothing() throws Exception {
        open(true);
        char[] wrong = "pw".toCharArray(), elsewhere = "pw".toCharArray();
        onUiThread(() -> challenge("a", TOP, "intranet.example", 443, "Corp", false));
        onUiThread(() -> {
            okShown("u", wrong, true);
            page.mainFrameResponded(TOP, 401);
            assertThat(wrong).as("dropped").containsOnly((char) 0);
            page.mainFrameResponded(TOP, 200);
            assertThat(fake.calls).as("the 401 dropped it").isEmpty();
            challenge("b", TOP, "intranet.example", 443, "Corp", false);
        });
        onUiThread(() -> {
            okShown("u", elsewhere, true);
            page.mainFrameResponded("https://files.example/", 200);
            assertThat(elsewhere).as("dropped").containsOnly((char) 0);
            page.mainFrameResponded(TOP, 200);
            assertThat(fake.calls).as("another origin's response dropped it").isEmpty();
            assertThat(told).containsExactly("a proceed u pw", "b proceed u pw");
            assertThat(host.failures()).isEmpty();
        });
    }

    /** F10: the item's close drops the pending save, its array zeroed, and nothing is saved after it. */
    @Test void aPendingSaveIsZeroedWhenTheItemCloses() throws Exception {
        open(true);
        char[] given = "pw".toCharArray();
        onUiThread(() -> challenge("a", TOP, "intranet.example", 443, "Corp", false));
        onUiThread(() -> {
            okShown("u", given, true);
            assertThat(given).as("pending").containsExactly('p', 'w');
            item.close();
            assertThat(given).as("zeroed unsaved").containsOnly((char) 0);
            page.mainFrameResponded(TOP, 200);
            items.signIns().responded(page, TOP, 200);
            assertThat(fake.calls).isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * A new challenge on the page, such as Chromium's again for a wrong password, drops the pending save, its array
     * zeroed; only a later answer with Remember is saved.
     */
    @Test void aNewChallengeReplacesThePendingSave() throws Exception {
        open(true);
        char[] wrong = "wrong".toCharArray(), right = "pw".toCharArray();
        onUiThread(() -> challenge("a", TOP, "intranet.example", 443, "Corp", false));
        onUiThread(() -> {
            okShown("u", wrong, true);
            challenge("again", TOP, "intranet.example", 443, "Corp", false);
            assertThat(wrong).as("replaced, zeroed").containsOnly((char) 0);
        });
        onUiThread(() -> {
            press(form().cancelAction());
            page.mainFrameResponded(TOP, 200);
            assertThat(fake.calls).as("nothing pending").isEmpty();
            challenge("b", TOP, "intranet.example", 443, "Corp", false);
        });
        onUiThread(() -> {
            okShown("u", right, true);
            page.mainFrameResponded(TOP, 200);
            assertThat(fake.calls).containsExactly("saveLogin " + CORP + " u");
            assertThat(fake.savedPasswords).containsExactly("pw");
            assertThat(right).containsOnly((char) 0);
            assertThat(told).containsExactly("a proceed u wrong", "again cancel", "b proceed u pw");
        });
    }

    /** A failed save posts "Could not save the password for HOST", with the failure's message, and zeroes the array. */
    @Test void aFailedSavePostsCouldNotSave() throws Exception {
        open(true);
        fake.saveAnswer = CompletableFuture.failedFuture(new IllegalStateException("Could not save the login"));
        char[] given = "pw".toCharArray();
        try (var log = new CapturedLog(HttpSignIns.class.getName())) {
            onUiThread(() -> challenge("a", TOP, "intranet.example", 443, "Corp", false));
            onUiThread(() -> {
                okShown("u", given, true);
                page.mainFrameResponded(TOP, 200);
                assertThat(host.notifications().lines()).containsExactly(
                    ID + "|ERROR|Could not save the password for intranet.example|Could not save the login");
                assertThat(given).containsOnly((char) 0);
            });
            assertThat(log.records()).singleElement().satisfies(record -> {
                assertThat(record.getLevel()).isEqualTo(Level.WARNING);
                assertThat(record.getThrown()).isInstanceOf(IllegalStateException.class);
            });
        }
    }

    /** An empty answer, as when unlocking is cancelled or there is no vault, posts nothing, and the array is zeroed. */
    @Test void aCancelledUnlockPostsNothing() throws Exception {
        open(true);
        fake.saveAnswer = CompletableFuture.completedFuture(Optional.empty());
        char[] given = "pw".toCharArray();
        onUiThread(() -> challenge("a", TOP, "intranet.example", 443, "Corp", false));
        onUiThread(() -> {
            okShown("u", given, true);
            page.mainFrameResponded(TOP, 200);
            assertThat(fake.calls).containsExactly("saveLogin " + CORP + " u");
            assertThat(given).containsOnly((char) 0);
            assertThat(host.notifications().lines()).isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * A remembered login the Browser may read signs the top-level challenge in with no dialog, its credential closed;
     * the page's flood guard never counts such a fill, three realms filled one straight after another here.
     */
    @Test void aGrantedLoginFillsWithoutTheDialog() throws Exception {
        open(true);
        fake.login(CORP, "u", "pw");
        onUiThread(() -> challenge("a", TOP, "intranet.example", 443, "Corp", false));
        onUiThread(() -> {
            assertThat(told).containsExactly("a proceed u pw");
            assertThat(host.windows()).as("no dialog").isEmpty();
            assertThat(fake.calls).containsExactly("credentialIfGranted " + CORP);
            assertThat(fake.issued).singleElement()
                .satisfies(credential -> assertThat(FakeVault.closed(credential)).isTrue());
        });
        for (String realm : List.of("R1", "R2", "R3"))
            fake.login("https://intranet.example (" + realm + ")", "u", "pw");
        tripsQuickly();
        onUiThread(() -> {
            for (String realm : List.of("R1", "R2", "R3")) challenge(realm, TOP, "intranet.example", 443, realm, false);
            assertThat(told).endsWith("R1 proceed u pw", "R2 proceed u pw", "R3 proceed u pw");
            challenge("other", TOP, "intranet.example", 443, "Other", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("the page's first dialog, after three fills").isPresent();
            press(form().cancelAction());
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * Review focus 1: a remembered password that is wrong is filled once; Chromium's challenge again, with no response
     * between, shows the dialog instead of looping; once the origin answers and its page commits (L30), the next
     * challenge fills silently again.
     */
    @Test void aSecondChallengeAfterASilentFillShowsTheDialog() throws Exception {
        open(true);
        fake.login(CORP, "u", "wrong");
        onUiThread(() -> {
            challenge("a", TOP, "intranet.example", 443, "Corp", false);
            assertThat(told).containsExactly("a proceed u wrong");
            challenge("again", TOP, "intranet.example", 443, "Corp", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("the dialog, not a loop").isPresent();
            assertThat(fake.calls).as("one silent fill").containsExactly("credentialIfGranted " + CORP);
            type("u", "pw");
            press(ok());
            assertThat(told).containsExactly("a proceed u wrong", "again proceed u pw");
            page.mainFrameResponded(TOP, 200);
            PageTestAccess.loadStarted(page, TOP);
            challenge("later", TOP, "intranet.example", 443, "Corp", false);
            assertThat(told).as("filled silently again after the answer's commit").endsWith("later proceed u wrong");
            assertThat(host.windows()).isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * L24: should Chromium report the 401 (or a proxy's 407) before it challenges again, that response does not let a
     * wrong remembered password be filled again, nor does the commit of its page (L30): the next challenge shows the
     * dialog.
     */
    @Test void aFourOhOneBetweenTheChallengesStillShowsTheDialog() throws Exception {
        open(true);
        fake.login(CORP, "u", "wrong");
        onUiThread(() -> {
            challenge("a", TOP, "intranet.example", 443, "Corp", false);
            page.mainFrameResponded(TOP, 401);
            challenge("again", TOP, "intranet.example", 443, "Corp", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("after a 401").isPresent();
            press(form().cancelAction());
            page.mainFrameResponded(TOP, 407);
            PageTestAccess.loadStarted(page, TOP);
            challenge("proxied", TOP, "intranet.example", 443, "Corp", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("after a 407 and its page's commit").isPresent();
            press(form().cancelAction());
            assertThat(told).containsExactly("a proceed u wrong", "again cancel", "proxied cancel");
            assertThat(fake.calls).as("one silent fill").containsExactly("credentialIfGranted " + CORP);
        });
    }

    /** Review focus 4: a locked vault shows the dialog, and asks nothing of the Vault, nor marks the name tried. */
    @Test void aLockedVaultShowsTheDialog() throws Exception {
        open(true);
        fake.login(CORP, "u", "pw");
        fake.state = LockState.LOCKED;
        onUiThread(() -> challenge("a", TOP, "intranet.example", 443, "Corp", false));
        onUiThread(() -> {
            assertThat(labels()).containsExactly("Password Manager", "Cancel", "OK");
            assertThat(remember()).isTrue();
            assertThat(fake.calls).as("the Vault is not asked while locked").isEmpty();
            press(form().cancelAction());
            fake.state = LockState.UNLOCKED;
            challenge("unlocked", TOP, "intranet.example", 443, "Corp", false);
            assertThat(told).as("a locked vault marked nothing tried")
                .containsExactly("a cancel", "unlocked proceed u pw");
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * F5, F24: a /dev/null item's challenge, a framed one, Digest, NTLM and a proxy's are never filled silently nor
     * remembered, even should an answer say Remember, which their dialogs do not offer.
     */
    @Test void devNullFramedDigestAndProxyNeverFillSilentlyOrRemember() throws Exception {
        open(true);
        fake.login(CORP, "u", "pw");
        fake.login("https://files.example (Corp)", "u", "pw");
        record Row(boolean devNull, SignIn signIn) { }
        var arrays = new ArrayList<char[]>();
        List<Row> rows = List.of(
            new Row(true, signIn("devnull", TOP, TOP, "intranet.example", 443, "Corp", false, "basic")),
            new Row(false,
                signIn("framed", "https://files.example/", TOP, "files.example", 443, "Corp", false, "basic")),
            new Row(false, signIn("digest", TOP, TOP, "intranet.example", 443, "Corp", false, "digest")),
            new Row(false, signIn("ntlm", TOP, TOP, "intranet.example", 443, "Corp", false, "ntlm")),
            new Row(false, signIn("proxy", TOP, TOP, "proxy.example", 8118, "Corp", true, "basic")));
        for (Row row : rows) {
            SignIn signIn = row.signIn();
            onUiThread(() -> items.signIns().challenged(page, place, row.devNull(), signIn));
            onUiThread(() -> {
                assertThat(host.windowView(ID, TITLE)).as("a dialog for " + signIn.originUrl()).isPresent();
                assertThat(remember()).isFalse();
                char[] given = "pw".toCharArray();
                arrays.add(given);
                okShown("u", given, true);
                page.mainFrameResponded(signIn.originUrl(), 200);
                page.mainFrameResponded(TOP, 200);
            });
        }
        onUiThread(() -> {
            assertThat(told).containsExactly("devnull proceed u pw", "framed proceed u pw", "digest proceed u pw",
                "ntlm proceed u pw", "proxy proceed u pw");
            assertThat(fake.calls).as("never filled silently, never saved").isEmpty();
            assertThat(arrays).allSatisfy(array -> assertThat(array).containsOnly((char) 0));
            assertThat(host.failures()).isEmpty();
        });
    }

    /** F5: a same-origin sub-resource's challenge offers Password Manager, but is never remembered nor filled. */
    @Test void aSameOriginSubresourceChallengeNeverRemembersOrFillsSilently() throws Exception {
        open(true);
        fake.login(CORP, "u", "pw");
        char[] given = "pw".toCharArray();
        onUiThread(() -> items.signIns().challenged(page, place, false,
            signIn("fetch", TOP + "api", TOP, "intranet.example", 443, "Corp", false, "basic")));
        onUiThread(() -> {
            assertThat(labels()).containsExactly("Password Manager", "Cancel", "OK");
            assertThat(remember()).isFalse();
            assertThat(fake.calls).as("no silent fill").isEmpty();
            okShown("u", given, true);
            assertThat(given).containsOnly((char) 0);
            page.mainFrameResponded(TOP, 200);
            assertThat(told).containsExactly("fetch proceed u pw");
            assertThat(fake.calls).as("nothing saved").isEmpty();
        });
    }

    /** F5: a site that redirects after the sign-in answers on another address of the same origin, which still saves. */
    @Test void aRedirectAfterTheSignInStillSaves() throws Exception {
        open(true);
        onUiThread(() -> challenge("a", TOP + "login", "intranet.example", 443, "Corp", false));
        onUiThread(() -> {
            type("u", "pw");
            tickRemember();
            press(ok());
            page.mainFrameResponded("https://intranet.example/home", 200);
            assertThat(fake.calls).containsExactly("saveLogin " + CORP + " u");
            assertThat(fake.savedPasswords).containsExactly("pw");
        });
    }

    /** A silent fill is the Vault's login already: it makes no pending save. */
    @Test void aSilentFillMakesNoPendingSave() throws Exception {
        open(true);
        fake.login(CORP, "u", "pw");
        onUiThread(() -> {
            challenge("a", TOP, "intranet.example", 443, "Corp", false);
            page.mainFrameResponded(TOP, 200);
            assertThat(told).containsExactly("a proceed u pw");
            assertThat(fake.calls).containsExactly("credentialIfGranted " + CORP);
        });
    }

    /** F8: a challenge cancelled as stale before its turn never marks its name tried. */
    @Test void aStaleChallengeIsNotMarkedTried() throws Exception {
        open(true);
        fake.login(CORP, "u", "pw");
        onUiThread(() -> {
            challenge("other", TOP, "intranet.example", 443, "Other", false);
            challenge("a", TOP, "intranet.example", 443, "Corp", false);
        });
        onUiThread(() -> {
            assertThat(heading()).as("waiting behind another realm's dialog").contains("“Other”");
            PageTestAccess.loadStarted(page, NEXT);
            assertThat(told).containsExactlyInAnyOrder("other cancel", "a cancel");
            challenge("next", NEXT, "intranet.example", 443, "Corp", false);
            assertThat(told).as("the name was never tried").endsWith("next proceed u pw");
            assertThat(host.failures()).isEmpty();
        });
    }

    /** F10, review focus 5: the item's close withdraws a save under way, as the Vault sees, and zeroes it. */
    @Test void closingTheItemWithdrawsTheSave() throws Exception {
        open(true);
        var saving = new CompletableFuture<Optional<CredentialDescriptor>>();
        fake.saveAnswer = saving;
        char[] given = "pw".toCharArray();
        onUiThread(() -> challenge("a", TOP, "intranet.example", 443, "Corp", false));
        onUiThread(() -> {
            okShown("u", given, true);
            page.mainFrameResponded(TOP, 200);
            assertThat(fake.calls).as("the save under way, its unlock prompt showing")
                .containsExactly("saveLogin " + CORP + " u");
            item.close();
            assertThat(saving).as("withdrawn").isCancelled();
            assertThat(given).as("zeroed").containsOnly((char) 0);
            assertThat(host.notifications().lines()).isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    /** Review focus 5: the stop cancels the challenges, withdraws the save under way and drops the pending one. */
    @Test void stopCancelsChallengesAndSaves() throws Exception {
        open(true);
        var saving = new CompletableFuture<Optional<CredentialDescriptor>>();
        fake.saveAnswer = saving;
        char[] inFlight = "pw".toCharArray(), pending = "pw2".toCharArray();
        onUiThread(() -> challenge("a", TOP, "intranet.example", 443, "Corp", false));
        onUiThread(() -> {
            okShown("u", inFlight, true);
            page.mainFrameResponded(TOP, 200);
            challenge("b", TOP, "intranet.example", 443, "Corp", false);
            challenge("c", TOP, "intranet.example", 443, "Other", false);
        });
        onUiThread(() -> okShown("u", pending, true));
        onUiThread(() -> {
            assertThat(heading()).as("the next one shows").contains("“Other”");
            items.signIns().close();
            assertThat(saving).as("withdrawn").isCancelled();
            assertThat(inFlight).as("the save's array, zeroed").containsOnly((char) 0);
            assertThat(pending).as("the pending save's, zeroed").containsOnly((char) 0);
            assertThat(told).containsExactly("a proceed u pw", "b proceed u pw2", "c cancel");
            assertThat(host.windows()).isEmpty();
            page.mainFrameResponded(TOP, 200);
            assertThat(fake.calls).as("one save, withdrawn").containsExactly("saveLogin " + CORP + " u");
            assertThat(host.notifications().lines()).isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    /** F29: neither the failure notice nor any log line of the sign-in, the pick and the save holds a secret. */
    @Test void theFailureNoticeHoldsNoPassword() throws Exception {
        open(true);
        fake.ungranted.add(fake.login("https://intranet.example (Realm-7d1)", "user-7d1", "secret-7d1"));
        fake.saveAnswer = CompletableFuture.failedFuture(new IllegalStateException("Could not save the login"));
        var logs = List.of(new CapturedLog(HttpSignIns.class.getName()), new CapturedLog(VaultLogins.class.getName()),
            new CapturedLog(SignInDialog.class.getName()), new CapturedLog(SignIn.class.getName()));
        try {
            onUiThread(() -> challenge("a", TOP, "intranet.example", 443, "Realm-7d1", false));
            onUiThread(() -> press(managerAction()));
            onUiThread(() -> {
                assertThat(told).as("picked").containsExactly("a proceed user-7d1 secret-7d1");
                challenge("b", TOP, "intranet.example", 443, "Realm-7d1", false);
            });
            onUiThread(() -> {
                type("user-7d1", "pw-7d1");
                tickRemember();
                press(ok());
                page.mainFrameResponded(TOP, 200);
            });
            assertThat(host.notifications().posts()).singleElement().satisfies(post -> {
                assertThat(post.spec().title()).isEqualTo("Could not save the password for intranet.example");
                assertThat(post.spec().content()).isEqualTo("Could not save the login");
            });
            List<LogRecord> records = logs.stream().flatMap(log -> log.records().stream()).toList();
            assertThat(records).as("the save's failure").isNotEmpty();
            for (String secret : List.of("pw-7d1", "secret-7d1", "user-7d1", "Realm-7d1"))
                assertThat(host.notifications().lines()).noneMatch(line -> line.contains(secret));
            assertThat(records).allSatisfy(record -> {
                for (String secret : List.of("pw-7d1", "secret-7d1", "user-7d1", "Realm-7d1"))
                    assertThat(logged(record)).doesNotContain(secret);
            });
        } finally {
            logs.forEach(CapturedLog::close);
        }
    }

    /**
     * L30: another origin's answer, with or without its page's commit, never lets a wrong remembered password be sent
     * again, as a page there would loop it (an address of the site, then a 204 of its own, and again); nor does it
     * when a page of the site's own commits next with no answer of its own, as one the back-forward cache restores.
     */
    @Test void anotherOriginsAnswerDoesNotReArmTheFill() throws Exception {
        open(true);
        fake.login(CORP, "u", "wrong");
        String elsewhere = "https://elsewhere.example/";
        onUiThread(() -> {
            challenge("a", TOP, "intranet.example", 443, "Corp", false);
            page.mainFrameResponded(elsewhere + "204", 204);
            challenge("again", TOP, "intranet.example", 443, "Corp", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("after another origin's 204").isPresent();
            press(form().cancelAction());
            page.mainFrameResponded(elsewhere, 200);
            PageTestAccess.loadStarted(page, elsewhere);
            challenge("later", TOP, "intranet.example", 443, "Corp", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("after another origin's page").isPresent();
            press(form().cancelAction());
            page.mainFrameResponded(elsewhere, 200);
            PageTestAccess.loadStarted(page, TOP);
            challenge("restored", TOP, "intranet.example", 443, "Corp", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("after the site's page commits on another's answer").isPresent();
            press(form().cancelAction());
            assertThat(told).containsExactly("a proceed u wrong", "again cancel", "later cancel", "restored cancel");
            assertThat(fake.calls).as("one silent fill").containsExactly("credentialIfGranted " + CORP);
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * L30: the origin's own answer re-arms the fill only at the commit that follows it: a 204 (or a download), which
     * commits nothing, does not, nor does the commit of its own 401 page, and another origin's commit first ends it.
     */
    @Test void theOriginsOwnAnswerReArmsTheFillOnlyAtItsCommit() throws Exception {
        open(true);
        fake.login(CORP, "u", "pw");
        onUiThread(() -> {
            challenge("a", TOP, "intranet.example", 443, "Corp", false);
            page.mainFrameResponded(TOP + "204", 204);
            challenge("nothing", TOP, "intranet.example", 443, "Corp", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("after a 204, which commits nothing").isPresent();
            press(form().cancelAction());
            page.mainFrameResponded(TOP, 401);
            PageTestAccess.loadStarted(page, TOP);
            challenge("unauthorized", TOP, "intranet.example", 443, "Corp", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("after the 401 page's commit").isPresent();
            press(form().cancelAction());
            page.mainFrameResponded(TOP, 200);
            PageTestAccess.loadStarted(page, "https://elsewhere.example/");
            PageTestAccess.loadStarted(page, TOP);
            challenge("replaced", TOP, "intranet.example", 443, "Corp", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("the answer's navigation replaced by another's commit")
                .isPresent();
            press(form().cancelAction());
            page.mainFrameResponded(TOP + "home", 200);
            PageTestAccess.loadStarted(page, TOP + "home");
            challenge("rearmed", TOP, "intranet.example", 443, "Corp", false);
            assertThat(told).containsExactly("a proceed u pw", "nothing cancel", "unauthorized cancel",
                "replaced cancel", "rearmed proceed u pw");
            assertThat(host.windows()).isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * L31: the site's own open redirect does not re-arm a fill either: once the page is sent to it, its 200 and its
     * commit answer another navigation than the one the fill answered, so the challenge after it shows the dialog.
     */
    @Test void anOpenRedirectOnTheSiteDoesNotReArmTheFill() throws Exception {
        open(true);
        fake.login(CORP, "u", "wrong");
        String bounce = TOP + "redirect?to=https://elsewhere.example/";
        onUiThread(() -> {
            clicked(TOP);
            challenge("a", TOP, "intranet.example", 443, "Corp", false);
            clicked(bounce);
            page.mainFrameResponded(bounce, 200);
            PageTestAccess.loadStarted(page, bounce);
            clicked(TOP);
            challenge("again", TOP, "intranet.example", 443, "Corp", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("the dialog, not a second fill").isPresent();
            press(form().cancelAction());
            assertThat(told).containsExactly("a proceed u wrong", "again cancel");
            assertThat(fake.calls).as("one silent fill").containsExactly("credentialIfGranted " + CORP);
            assertThat(host.failures()).isEmpty();
        });
    }

    /** L31: a redirect is the fill's own navigation, so its 200 and commit re-arm the fill (F6). */
    @Test void aRedirectOfTheFillsOwnNavigationReArmsIt() throws Exception {
        open(true);
        fake.login(CORP, "u", "pw");
        onUiThread(() -> {
            clicked(TOP);
            challenge("a", TOP, "intranet.example", 443, "Corp", false);
            page.mainFrameRedirected(TOP + "y");
            page.mainFrameResponded(TOP + "y", 200);
            PageTestAccess.loadStarted(page, TOP + "y");
            clicked(TOP);
            challenge("later", TOP, "intranet.example", 443, "Corp", false);
            assertThat(told).containsExactly("a proceed u pw", "later proceed u pw");
            assertThat(host.windows()).isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * L31: a fill whose navigation a new request replaces before its answer leaves its name tried while the page
     * lasts: neither the new navigation's answer nor a later sign-in through the dialog that works re-arms it.
     */
    @Test void aFillWhoseNavigationIsReplacedNeverReArmsOnThatPage() throws Exception {
        open(true);
        fake.login(CORP, "u", "pw");
        onUiThread(() -> {
            clicked(TOP);
            challenge("a", TOP, "intranet.example", 443, "Corp", false);
            clicked(NEXT);
            page.mainFrameResponded(NEXT, 200);
            PageTestAccess.loadStarted(page, NEXT);
            clicked(TOP);
            challenge("b", TOP, "intranet.example", 443, "Corp", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("replaced before its answer").isPresent();
            type("u", "pw");
            press(ok());
            page.mainFrameResponded(TOP, 200);
            PageTestAccess.loadStarted(page, TOP);
            clicked(TOP);
            challenge("c", TOP, "intranet.example", 443, "Corp", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("still, after a sign-in that worked").isPresent();
            press(form().cancelAction());
            assertThat(told).containsExactly("a proceed u pw", "b proceed u pw", "c cancel");
            assertThat(fake.calls).as("one silent fill").containsExactly("credentialIfGranted " + CORP);
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * L31: a main-frame load failure, such as the user's Stop, ends the fill's navigation, its 200 notwithstanding:
     * the commit after it re-arms nothing.
     */
    @Test void aFillWhoseNavigationFailsNeverReArms() throws Exception {
        open(true);
        fake.login(CORP, "u", "pw");
        onUiThread(() -> {
            clicked(TOP);
            challenge("a", TOP, "intranet.example", 443, "Corp", false);
            page.mainFrameResponded(TOP, 200);
            PageTestAccess.loadFailed(page, TOP, "ERR_ABORTED");
            PageTestAccess.loadStarted(page, TOP);
            challenge("b", TOP, "intranet.example", 443, "Corp", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("the failed navigation's name").isPresent();
            press(form().cancelAction());
            assertThat(told).containsExactly("a proceed u pw", "b cancel");
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * L31: a name whose fill found no login it may read sent no password, so it keeps L30's rule: another navigation's
     * answer from its origin and that page's commit re-arm it.
     */
    @Test void aNameWhoseFillSentNothingKeepsTheOriginsRule() throws Exception {
        open(true);
        UUID id = fake.login(CORP, "u", "pw");
        fake.ungranted.add(id);
        onUiThread(() -> {
            clicked(TOP);
            challenge("a", TOP, "intranet.example", 443, "Corp", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("no login the Browser may read").isPresent();
            press(form().cancelAction());
            clicked(NEXT);
            page.mainFrameResponded(NEXT, 200);
            PageTestAccess.loadStarted(page, NEXT);
            fake.ungranted.remove(id);
            clicked(TOP);
            challenge("b", TOP, "intranet.example", 443, "Corp", false);
            assertThat(told).containsExactly("a cancel", "b proceed u pw");
            assertThat(host.failures()).isEmpty();
        });
    }

    /** Minor 1: a main-frame load start before the challenged navigation's response drops the pending save, zeroed. */
    @Test void aLoadStartDropsThePendingSave() throws Exception {
        open(true);
        char[] given = "pw".toCharArray();
        onUiThread(() -> challenge("a", TOP, "intranet.example", 443, "Corp", false));
        onUiThread(() -> {
            okShown("u", given, true);
            PageTestAccess.loadStarted(page, TOP);
            assertThat(given).as("dropped").containsOnly((char) 0);
            page.mainFrameResponded(TOP, 200);
            assertThat(fake.calls).as("nothing saved").isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    /** A failure of the Vault's while filling is logged with the exception alone, and the dialog shows. */
    @Test void aVaultFailureWhileFillingShowsTheDialog() throws Exception {
        open(true);
        fake.login(CORP, "u", "pw");
        fake.grantFailure = new IllegalStateException("the Vault failed");
        try (var log = new CapturedLog(HttpSignIns.class.getName())) {
            onUiThread(() -> challenge("a", TOP, "intranet.example", 443, "Corp", false));
            onUiThread(() -> {
                assertThat(host.windowView(ID, TITLE)).isPresent();
                assertThat(told).isEmpty();
                type("u", "typed");
                press(ok());
                assertThat(told).containsExactly("a proceed u typed");
            });
            assertThat(log.records()).singleElement().satisfies(record -> {
                assertThat(record.getLevel()).isEqualTo(Level.WARNING);
                assertThat(record.getThrown()).isSameAs(fake.grantFailure);
            });
        }
    }

    /** A save the Vault refuses at the call is a failed save: the notice posts, and the array is zeroed. */
    @Test void aSaveThatThrowsAtTheCallPostsCouldNotSave() throws Exception {
        open(true);
        fake.saveFailure = new IllegalStateException("Could not save the login");
        char[] given = "pw".toCharArray();
        try (var log = new CapturedLog(HttpSignIns.class.getName())) {
            onUiThread(() -> challenge("a", TOP, "intranet.example", 443, "Corp", false));
            onUiThread(() -> {
                okShown("u", given, true);
                page.mainFrameResponded(TOP, 200);
                assertThat(host.notifications().lines()).containsExactly(
                    ID + "|ERROR|Could not save the password for intranet.example|Could not save the login");
                assertThat(given).containsOnly((char) 0);
            });
            assertThat(log.records()).singleElement()
                .satisfies(record -> assertThat(record.getThrown()).isSameAs(fake.saveFailure));
        }
    }

    /** A challenge the waiting limit refuses, as each new challenge of the page, leaves no pending save. */
    @Test void theWaitingLimitsRefusalLeavesNoPendingSave() throws Exception {
        open(true);
        char[] given = "pw".toCharArray();
        onUiThread(() -> challenge("a", TOP, "intranet.example", 443, "Corp", false));
        onUiThread(() -> {
            okShown("u", given, true);
            for (int at = 0; at <= HttpSignIns.WAITING_LIMIT + 1; at++)
                challenge("w" + at, TOP, "intranet.example", 443, "Realm " + at, false);
            assertThat(told).as("one shown, eight waiting, one refused")
                .containsExactly("a proceed u pw", "w" + (HttpSignIns.WAITING_LIMIT + 1) + " cancel");
            assertThat(given).as("dropped").containsOnly((char) 0);
            page.mainFrameResponded(TOP, 200);
            assertThat(fake.calls).as("nothing saved").isEmpty();
            items.signIns().closed(page);
        });
    }

    /**
     * L27: a fill that finds no login the Browser may read falls through to the dialog, which the user's navigation
     * shows past the page's tripped flood guard.
     */
    @Test void aFillThatFindsNoLoginStillUsesTheUsersNavigationsBypass() throws Exception {
        open(true);
        fake.ungranted.add(fake.login("https://intranet.example (One)", "u", "pw"));
        trip();
        onUiThread(() -> {
            page.load(URI.create(NEXT));
            challenge("navigation", NEXT, "intranet.example", 443, "One", false);
        });
        onUiThread(() -> {
            assertThat(fake.calls).as("the fill tried")
                .containsExactly("credentialIfGranted https://intranet.example (One)");
            assertThat(host.windowView(ID, TITLE)).as("past the trip").isPresent();
            type("u", "pw");
            press(ok());
            assertThat(told).endsWith("navigation proceed u pw");
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * F30: an answered sub-resource dialog signs in a waiting top-level challenge of the same protection space, which
     * is then neither filled nor remembered, Remember aside.
     */
    @Test void aSubresourceAnswerServesAWaitingTopLevelChallengeUnremembered() throws Exception {
        open(true);
        fake.login(CORP, "u", "pw");
        char[] given = "typed".toCharArray();
        onUiThread(() -> {
            items.signIns().challenged(page, place, false,
                signIn("fetch", TOP + "api", TOP, "intranet.example", 443, "Corp", false, "basic"));
            challenge("top", TOP, "intranet.example", 443, "Corp", false);
        });
        onUiThread(() -> {
            assertThat(remember()).as("the sub-resource's dialog").isFalse();
            okShown("u", given, true);
            assertThat(told).containsExactly("fetch proceed u typed", "top proceed u typed");
            assertThat(given).containsOnly((char) 0);
            page.mainFrameResponded(TOP, 200);
            assertThat(fake.calls).as("neither filled nor saved").isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * L34: a pending save belongs to the navigation its sign-in answered. A new main-frame request before that
     * navigation's response drops it, zeroed, so the new navigation's 2xx of the same origin saves nothing.
     */
    @Test void aNewNavigationDropsThePendingSave() throws Exception {
        open(true);
        char[] given = "pw".toCharArray();
        onUiThread(() -> challenge("a", TOP, "intranet.example", 443, "Corp", false));
        onUiThread(() -> {
            okShown("u", given, true);
            clicked(NEXT);
            page.mainFrameResponded(NEXT, 200);
            assertThat(fake.calls).as("nothing saved").isEmpty();
            assertThat(given).as("dropped").containsOnly((char) 0);
            assertThat(host.failures()).isEmpty();
        });
    }

    /** L34: a main-frame load failure before the sign-in's response drops the pending save, zeroed. */
    @Test void aLoadFailureDropsThePendingSave() throws Exception {
        open(true);
        char[] given = "pw".toCharArray();
        onUiThread(() -> challenge("a", TOP, "intranet.example", 443, "Corp", false));
        onUiThread(() -> {
            okShown("u", given, true);
            PageTestAccess.loadFailed(page, TOP, "ERR_ABORTED");
            assertThat(given).as("dropped").containsOnly((char) 0);
            page.mainFrameResponded(TOP, 200);
            assertThat(fake.calls).as("nothing saved").isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    /** L34 and F6: a redirect is the sign-in's own navigation, so the 2xx it ends in saves the pending save. */
    @Test void theSignInsOwnRedirectStillSaves() throws Exception {
        open(true);
        char[] given = "pw".toCharArray();
        onUiThread(() -> challenge("a", TOP, "intranet.example", 443, "Corp", false));
        onUiThread(() -> {
            okShown("u", given, true);
            page.mainFrameRedirected(TOP + "home");
            page.mainFrameResponded(TOP + "home", 200);
            assertThat(fake.calls).containsExactly("saveLogin " + CORP + " u");
            assertThat(fake.savedPasswords).containsExactly("pw");
            assertThat(given).as("zeroed once saved").containsOnly((char) 0);
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * L29: only a load failure of the address the user's navigation's challenge was asked from ends its bypass. One
     * before any challenge, when no address is known, and one of another address, as the late ERR_ABORTED of a
     * navigation this one replaced, end nothing; the failure of the challenge's own address ends it.
     */
    @Test void onlyTheChallengesOwnLoadFailureEndsTheUsersNavigationsBypass() throws Exception {
        trip();
        String replaced = TOP + "replaced";
        onUiThread(() -> {
            page.load(URI.create(NEXT));
            PageTestAccess.loadFailed(page, replaced, "ERR_ABORTED");
            challenge("navigation", NEXT, "intranet.example", 443, "One", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("a failure before any challenge").isPresent();
            type("u", "wrong");
            press(ok());
            PageTestAccess.loadFailed(page, replaced, "ERR_ABORTED");
            challenge("again", NEXT, "intranet.example", 443, "One", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("another address's failure").isPresent();
            type("u", "pw");
            press(ok());
            PageTestAccess.loadFailed(page, NEXT, "ERR_ABORTED");
            challenge("after", NEXT, "intranet.example", 443, "One", false);
        });
        onUiThread(() -> {
            assertThat(told).endsWith("navigation proceed u wrong", "again proceed u pw", "after cancel");
            assertThat(host.windowView(ID, TITLE)).as("its own address failed").isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * L29 and B's Minor 2: a fill's navigation keeps the answer its origin gave through a load failure of another
     * address, as a replaced navigation's late ERR_ABORTED, so the commit that follows re-arms the fill; a failure of
     * the challenge's own address ends that answer, so the commit after it re-arms nothing.
     */
    @Test void onlyTheChallengesOwnLoadFailureEndsTheFillsAnswer() throws Exception {
        open(true);
        fake.login(CORP, "u", "pw");
        onUiThread(() -> {
            clicked(TOP);
            challenge("a", TOP, "intranet.example", 443, "Corp", false);
            page.mainFrameResponded(TOP, 200);
            PageTestAccess.loadFailed(page, NEXT, "ERR_ABORTED");
            PageTestAccess.loadStarted(page, TOP);
            challenge("b", TOP, "intranet.example", 443, "Corp", false);
            assertThat(told).as("re-armed despite another address's failure")
                .containsExactly("a proceed u pw", "b proceed u pw");
            page.mainFrameResponded(TOP, 200);
            PageTestAccess.loadFailed(page, TOP, "ERR_ABORTED");
            PageTestAccess.loadStarted(page, TOP);
            challenge("c", TOP, "intranet.example", 443, "Corp", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("its own address failed").isPresent();
            press(form().cancelAction());
            assertThat(told).containsExactly("a proceed u pw", "b proceed u pw", "c cancel");
            assertThat(fake.calls).as("two silent fills").hasSize(2);
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * L35: a silent fill needs the user's action. A navigation a script starts, with no user activation and not
     * Jasper's, to a site with a remembered login shows the dialog, with Password Manager and Remember, and sends
     * nothing; the name is not tried, so the user's own navigation to the site then fills.
     */
    @Test void aScriptsNavigationToARememberedSiteShowsTheDialog() throws Exception {
        open(true);
        fake.login(CORP, "u", "pw");
        onUiThread(() -> {
            scripted(TOP);
            challenge("scripted", TOP, "intranet.example", 443, "Corp", false);
        });
        onUiThread(() -> {
            assertThat(labels()).containsExactly("Password Manager", "Cancel", "OK");
            assertThat(remember()).isTrue();
            assertThat(told).as("nothing sent").isEmpty();
            assertThat(fake.calls).as("the Vault not asked").isEmpty();
            press(form().cancelAction());
            clicked(TOP);
            challenge("clicked", TOP, "intranet.example", 443, "Corp", false);
            assertThat(told).containsExactly("scripted cancel", "clicked proceed u pw");
            assertThat(host.windows()).isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * L35: a navigation that began within the page's user activation fills, as a link the user clicks or a key the
     * user presses begins one, and so do its redirects; a script's redirects do not.
     */
    @Test void theRedirectsOfANavigationTheUserBeganFillAndAScriptsDoNot() throws Exception {
        open(true);
        fake.login(CORP, "u", "pw");
        fake.login("https://intranet.example (Other)", "u", "pw");
        onUiThread(() -> {
            long press = millis.addAndGet(60_000);
            PageTestAccess.activate(page, press);
            millis.set(press + 5_000);
            page.mainFrameRequested("http://intranet.example/");
            page.mainFrameRedirected(TOP);
            challenge("redirected", TOP, "intranet.example", 443, "Corp", false);
            assertThat(told).as("within 5 s of the press").containsExactly("redirected proceed u pw");
            millis.set(press + 5_001);
            page.mainFrameRequested("http://intranet.example/");
            page.mainFrameRedirected(TOP);
            challenge("late", TOP, "intranet.example", 443, "Other", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("a request past the activation, redirected").isPresent();
            press(form().cancelAction());
            assertThat(told).containsExactly("redirected proceed u pw", "late cancel");
            assertThat(fake.calls).as("one silent fill").containsExactly("credentialIfGranted " + CORP);
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * L35 and L27: a navigation Jasper starts for the user, as from the address field, fills without the page's
     * activation; once the user cancels one of its dialogs, a challenge of it still waiting shows the dialog instead
     * (L28).
     */
    @Test void jaspersNavigationForTheUserFillsUntilTheUserCancelsOneOfItsDialogs() throws Exception {
        open(true);
        fake.login(CORP, "u", "pw");
        fake.ungranted.add(fake.login("https://intranet.example (Other)", "u", "pw"));
        fake.login("https://intranet.example (Third)", "u", "pw");
        onUiThread(() -> {
            typed(NEXT);
            challenge("typed", NEXT, "intranet.example", 443, "Corp", false);
            assertThat(told).as("Jasper's own navigation").containsExactly("typed proceed u pw");
            typed(NEXT);
            challenge("other", NEXT, "intranet.example", 443, "Other", false);
            challenge("third", NEXT, "intranet.example", 443, "Third", false);
        });
        onUiThread(() -> {
            assertThat(heading()).as("no login the Browser may read").contains("“Other”");
            press(form().cancelAction());
        });
        onUiThread(() -> {
            assertThat(heading()).as("after the user's Cancel").contains("“Third”");
            press(form().cancelAction());
            assertThat(told).containsExactly("typed proceed u pw", "other cancel", "third cancel");
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * L42: the silent fill's Jasper half answers to Jasper's own request alone. After a navigation Jasper started that
     * commits no page (Back to an entry within the document, a 204, the user's Stop before any sign-in, a download), a
     * page's script that sends the tab to a remembered site, a second or three minutes later, gets the dialog, and
     * nothing is sent.
     */
    @Test void aJasperNavigationThatCommitsNothingLendsALaterScriptsNavigationNothing() throws Exception {
        open(true);
        fake.login(CORP, "u", "pw");
        var rows = new java.util.LinkedHashMap<String, Runnable>();
        rows.put("within the document", () -> {
            millis.addAndGet(60_000);
            page.load(URI.create(NEXT + "#part"));
            PageTestAccess.mainFrameCommitted(page, NEXT + "#part");
        });
        rows.put("a 204", () -> {
            typed(NEXT);
            page.mainFrameResponded(NEXT, 204);
        });
        rows.put("a Stop", () -> {
            typed(NEXT);
            PageTestAccess.mainFrameFailed(page, NEXT, "ERR_ABORTED");
        });
        rows.put("a download", () -> {
            typed(NEXT + "report.pdf");
            PageTestAccess.downloadStart(page, 1, 7, NEXT + "report.pdf", "report.pdf", TOP, millis.get());
        });
        for (var row : rows.entrySet())
            for (long later : List.of(1_000L, 180_000L)) {
                String name = row.getKey() + ", " + later + " ms later";
                onUiThread(() -> {
                    row.getValue().run();
                    millis.addAndGet(later);
                    page.mainFrameRequested(TOP); // the old page's script, as a form's submit
                    challenge(name, TOP, "intranet.example", 443, "Corp", false);
                });
                onUiThread(() -> {
                    assertThat(host.windowView(ID, TITLE)).as(name).isPresent();
                    press(form().cancelAction());
                });
            }
        onUiThread(() -> {
            assertThat(told).hasSize(8).allSatisfy(line -> assertThat(line).endsWith(" cancel"));
            assertThat(fake.calls).as("the Vault never asked").isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * L42: Jasper's mark waits 10 s for its navigation's request: one that comes later is not Jasper's, so it shows the
     * dialog; one within 10 s fills, and so does its redirect.
     */
    @Test void jaspersMarkLapsesUntakenAfter10Seconds() throws Exception {
        open(true);
        fake.login(CORP, "u", "pw");
        onUiThread(() -> {
            millis.addAndGet(60_000);
            page.load(URI.create(TOP));
            millis.addAndGet(10_001);
            page.mainFrameRequested(TOP);
            challenge("lapsed", TOP, "intranet.example", 443, "Corp", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("10,001 ms later").isPresent();
            press(form().cancelAction());
            millis.addAndGet(60_000);
            page.load(URI.create("http://intranet.example/"));
            millis.addAndGet(10_000);
            page.mainFrameRequested("http://intranet.example/");
            page.mainFrameRedirected(TOP);
            challenge("taken", TOP, "intranet.example", 443, "Corp", false);
            assertThat(told).containsExactly("lapsed cancel", "taken proceed u pw");
            assertThat(host.windows()).isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * L42: once Jasper's request has taken the mark, a request of the page's that replaces it, as the old document's
     * script sending the tab elsewhere before Jasper's navigation commits, is not Jasper's: the dialog shows.
     */
    @Test void aPageRequestAfterJaspersOwnIsNotJaspers() throws Exception {
        open(true);
        fake.login(CORP, "u", "pw");
        onUiThread(() -> {
            typed(NEXT + "elsewhere");
            millis.addAndGet(500);
            page.mainFrameRequested(TOP);
            challenge("replacing", TOP, "intranet.example", 443, "Corp", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("the page's own request").isPresent();
            press(form().cancelAction());
            assertThat(told).containsExactly("replacing cancel");
            assertThat(fake.calls).isEmpty();
        });
    }

    /**
     * L42: an activation carries into no new document. The click on a link fills that link's navigation, but once the
     * document it brings starts loading, that click begins none of the document's own navigations, such as its meta
     * refresh within 5 s, which shows the dialog; a click in the new document does.
     */
    @Test void theClickThatBroughtADocumentBeginsNoneOfItsOwnNavigations() throws Exception {
        open(true);
        fake.login(CORP, "u", "pw");
        fake.login("https://intranet.example (Other)", "u", "pw");
        onUiThread(() -> {
            millis.addAndGet(60_000);
            clicked(NEXT);
            challenge("link", NEXT, "intranet.example", 443, "Corp", false);
            assertThat(told).containsExactly("link proceed u pw");
            millis.addAndGet(300);
            PageTestAccess.mainFrameStarting(page);
            PageTestAccess.loadStarted(page, NEXT);
            millis.addAndGet(1_000);
            page.mainFrameRequested(TOP); // the new document's meta refresh
            challenge("refresh", TOP, "intranet.example", 443, "Other", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("within 5 s of the click, but in the document it brought")
                .isPresent();
            press(form().cancelAction());
            clicked(TOP);
            challenge("clicked", TOP, "intranet.example", 443, "Other", false);
            assertThat(told).containsExactly("link proceed u pw", "refresh cancel", "clicked proceed u pw");
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * L44: the user's Stop ends a navigation Jasper started before its request, which Chromium reports nothing of, so
     * its mark is dropped: a page's script that sends the tab to a remembered site a second later shows the dialog.
     */
    @Test void theUsersStopDropsJaspersMark() throws Exception {
        open(true);
        fake.login(CORP, "u", "pw");
        onUiThread(() -> {
            millis.addAndGet(60_000);
            page.load(URI.create(NEXT));
            page.stop();
            millis.addAndGet(1_000);
            page.mainFrameRequested(TOP); // the old page's script
            challenge("stopped", TOP, "intranet.example", 443, "Corp", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("after the Stop").isPresent();
            press(form().cancelAction());
            assertThat(told).containsExactly("stopped cancel");
            assertThat(fake.calls).isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * L44: the end of the page's pending navigation that Jasper's replaces is that of the latest request, so it keeps
     * Jasper's mark for Jasper's own request, which fills; a download of another address drops the mark, so the next
     * request is not Jasper's and shows the dialog.
     */
    @Test void theEndOfTheNavigationJaspersReplacesKeepsTheMark() throws Exception {
        open(true);
        fake.login(CORP, "u", "pw");
        fake.login("https://intranet.example (Other)", "u", "pw");
        onUiThread(() -> {
            scripted(NEXT + "slow"); // the page's own navigation, pending
            page.load(URI.create(TOP));
            PageTestAccess.mainFrameFailed(page, NEXT + "slow", "ERR_ABORTED");
            page.mainFrameRequested(TOP);
            challenge("typed", TOP, "intranet.example", 443, "Corp", false);
            assertThat(told).as("Jasper's request took the mark").containsExactly("typed proceed u pw");
            millis.addAndGet(60_000);
            page.load(URI.create(NEXT));
            String file = "https://files.example/report.pdf";
            PageTestAccess.downloadStart(page, 1, 7, file, "report.pdf", TOP, millis.get());
            millis.addAndGet(1_000);
            page.mainFrameRequested(TOP);
            challenge("after a download", TOP, "intranet.example", 443, "Other", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("another address's download").isPresent();
            press(form().cancelAction());
            assertThat(told).containsExactly("typed proceed u pw", "after a download cancel");
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * A Basic challenge of the page's top-level navigation to {@link #TOP}, as Chromium's thread hands it over, to the
     * item's sign-ins, outside /dev/null. UI thread.
     */
    private void challenge(String name, String top, String challenger, int port, String realm, boolean proxy) {
        if (items == null) open(false);
        items.signIns().challenged(page, place, false, signIn(name, top, top, challenger, port, realm, proxy, "basic"));
    }

    /**
     * A sign-in Chromium asks for a request of {@code origin}'s on the top address {@code top}, told to {@link #told},
     * in the page's latest navigation, as Chromium's thread reads it.
     */
    private SignIn signIn(String name, String origin, String top, String challenger, int port, String realm, boolean proxy,
                          String scheme) {
        if (items == null) open(false);
        Page.Navigation navigation = page.latestNavigation();
        return new SignIn(origin, proxy, challenger, port, realm, scheme, top, navigation.number(),
            navigation.activated(), navigation.byJasper(), (user, password) -> told.add(name + " proceed " + user + " "
                + password), () -> told.add(name + " cancel"));
    }

    /** The page's main frame requests {@code url} as a link the user clicks does: within its activation (L35). */
    private void clicked(String url) {
        PageTestAccess.activate(page, millis.get());
        page.mainFrameRequested(url);
    }

    /**
     * The page's main frame requests {@code url} as a page's script does, a minute later: with no user activation, and
     * not Jasper's (L35, L42).
     */
    private void scripted(String url) {
        millis.addAndGet(60_000);
        page.mainFrameRequested(url);
    }

    /** Jasper sends the page to {@code url} for the user, a minute after any press or key, and its request goes out. */
    private void typed(String url) {
        millis.addAndGet(60_000);
        page.load(URI.create(url));
        page.mainFrameRequested(url);
    }

    /** The sign-in dialogs from now on read one moment, so each opens straight after the one before closed. */
    private void tripsQuickly() throws Exception {
        clock.close();
        clock = DialogsTestAccess.signInsTimedBy(() -> 50_000);
    }

    /** Trips the page's flood guard, the sign-ins reading one moment: three realms, the third refused unshown. */
    private void trip() throws Exception {
        tripsQuickly();
        onUiThread(() -> {
            challenge("t1", TOP, "intranet.example", 443, "T1", false);
            challenge("t2", TOP, "intranet.example", 443, "T2", false);
            challenge("t3", TOP, "intranet.example", 443, "T3", false);
        });
        onUiThread(() -> press(form().cancelAction()));
        onUiThread(() -> press(form().cancelAction()));
        onUiThread(() -> assertThat(told).as("tripped").containsExactly("t1 cancel", "t2 cancel", "t3 cancel"));
    }

    /** The plugin's items, opened without the Vault when there are none yet. */
    private BrowserItems items() {
        if (items == null) open(false);
        return items;
    }

    /** A started stand-in Browser on a fake host, its one item at {@link #TOP}, with the Vault or without it. */
    private void open(boolean vault) {
        Runnable opening = () -> {
            if (host != null) host.close();
            host = new FakePluginHost();
            var started = new AtomicReference<BrowserItems>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), plugin -> started.set(new BrowserItems(plugin,
                BrowserSettings.declare(plugin.settings())::read, new DataStores(memory.make(), () -> false),
                vault ? Optional.of(LoginsTestAccess.logins(fake.api())) : Optional.empty(), page -> { })));
            items = started.get();
            host.activateTerminalWindow(host.addTerminalWindow());
            item = items.open(URI.create(TOP), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            page = ItemTestAccess.page(item);
            place = page.dialogPlace().orElseThrow();
            PageTestAccess.useClock(page, millis::get);
            // The user's click begins the page's navigation, so its remembered logins may fill (L35).
            clicked(TOP);
        };
        if (SwingUtilities.isEventDispatchThread()) opening.run();
        else {
            try {
                SwingUtilities.invokeAndWait(opening);
            } catch (Exception failed) {
                throw new AssertionError(failed);
            }
        }
    }

    /** Ticks the dialog's "Remember this password". */
    private void tickRemember() {
        String toggle = form().sections().stream().filter(Nodes.SettingRow.class::isInstance)
            .map(Nodes.SettingRow.class::cast).findFirst()
            .orElseThrow(() -> new AssertionError("no Remember this password")).control().id();
        view().emit(new UiEvent.Toggled(toggle, true));
    }

    /**
     * Answers the page's shown challenge with OK, as its dialog would, with {@code password}, an array of the test's;
     * then closes that dialog, which answers nothing more.
     */
    private void okShown(String user, char[] password, boolean remember) {
        FakeKitView dialog = view();
        LoginsTestAccess.answerShown(items.signIns(), page).accept(new Outcome.Ok(user, password, remember));
        dialog.emit(new UiEvent.Action(((Nodes.Form) dialog.spec().root()).cancelAction()));
    }

    /** A log record's message, parameters and each exception it carries, as text. */
    private static String logged(LogRecord record) {
        var text = new StringBuilder(String.valueOf(record.getMessage()));
        if (record.getParameters() != null)
            for (Object parameter : record.getParameters()) text.append(' ').append(parameter);
        for (Throwable thrown = record.getThrown(); thrown != null; thrown = thrown.getCause())
            text.append(' ').append(thrown);
        return text.toString();
    }

    /** Types {@code user} in the dialog's User name field and {@code password} in its Password field. */
    private void type(String user, String password) {
        view().type(((Nodes.FormRow) form().sections().get(1)).control().id(), user);
        view().setSecret(((Nodes.FormRow) form().sections().get(2)).control().id(), password.toCharArray());
    }

    private String ok() {
        return form().actions().getLast().action();
    }

    private String managerAction() {
        return form().actions().getFirst().action();
    }

    private List<String> labels() {
        return form().actions().stream().map(Nodes.Button::label).toList();
    }

    private boolean remember() {
        return form().sections().stream().anyMatch(Nodes.SettingRow.class::isInstance);
    }

    private String heading() {
        return ((Nodes.Text) form().sections().getFirst()).text();
    }

    private Nodes.Form form() {
        return (Nodes.Form) view().spec().root();
    }

    private FakeKitView view() {
        return host.windowView(ID, TITLE).orElseThrow(() -> new AssertionError("no sign-in dialog"));
    }

    private void press(String action) {
        view().emit(new UiEvent.Action(action));
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
        // What the body posted, such as the next dialog's show, runs before the next step.
        SwingUtilities.invokeAndWait(() -> { });
        if (failure.get() instanceof Exception exception) throw exception;
        if (failure.get() instanceof Error error) throw error;
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
