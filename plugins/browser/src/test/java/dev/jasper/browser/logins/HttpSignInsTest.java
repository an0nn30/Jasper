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
 * answered with "Remember this password" is saved at the page's next main-frame 2xx from its origin, and a remembered
 * login fills the top-level challenge in without the dialog, once until the page's main frame gets a response (review
 * focus 1); and the item's close and the plugin's stop cancel every challenge exactly once and drop every save, its
 * password zeroed (review focus 5). The pages are stand-in items' own on the testkit's fake host, the Vault is
 * {@link FakeVault}, and Chromium's side is a recording {@link SignIn}.
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
                .containsExactly("credentialIfGranted https://intranet.example (Corp)", "credential https://intranet.example (Corp)");
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

    /** L28: a main-frame load failure, such as a download or a stopped load, ends the navigation's bypass. */
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
                assertThat(host.notifications().lines())
                    .containsExactly(ID + "|ERROR|Could not save the password for intranet.example|Could not save the login");
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
     * the page's flood guard never counts such a fill.
     */
    @Test void aGrantedLoginFillsWithoutTheDialog() throws Exception {
        open(true);
        fake.login(CORP, "u", "pw");
        onUiThread(() -> challenge("a", TOP, "intranet.example", 443, "Corp", false));
        onUiThread(() -> {
            assertThat(told).containsExactly("a proceed u pw");
            assertThat(host.windows()).as("no dialog").isEmpty();
            assertThat(fake.calls).containsExactly("credentialIfGranted " + CORP);
            assertThat(fake.issued).singleElement().satisfies(credential -> assertThat(FakeVault.closed(credential)).isTrue());
        });
        tripsQuickly();
        onUiThread(() -> {
            for (String name : List.of("b", "c", "d")) {
                page.mainFrameResponded(TOP, 200);
                challenge(name, TOP, "intranet.example", 443, "Corp", false);
            }
            assertThat(told).endsWith("b proceed u pw", "c proceed u pw", "d proceed u pw");
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
     * between, shows the dialog instead of looping; once the main frame gets a response, the next challenge fills
     * silently again.
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
            challenge("later", TOP, "intranet.example", 443, "Corp", false);
            assertThat(told).as("filled silently again after the response").endsWith("later proceed u wrong");
            assertThat(host.windows()).isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * L24: should Chromium report the 401 (or a proxy's 407) before it challenges again, that response does not let a
     * wrong remembered password be filled again: the second challenge shows the dialog.
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
            challenge("proxied", TOP, "intranet.example", 443, "Corp", false);
        });
        onUiThread(() -> {
            assertThat(host.windowView(ID, TITLE)).as("after a 407").isPresent();
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
            assertThat(told).as("a locked vault marked nothing tried").containsExactly("a cancel", "unlocked proceed u pw");
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
            new Row(false, signIn("framed", "https://files.example/", TOP, "files.example", 443, "Corp", false, "basic")),
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

    /** F5: a same-origin sub-resource's challenge offers Password Manager, but is never remembered nor filled silently. */
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

    /** F10, review focus 5: the item's close withdraws a save under way, which the Vault sees cancelled, and zeroes it. */
    @Test void closingTheItemWithdrawsTheSave() throws Exception {
        open(true);
        var saving = new CompletableFuture<Optional<CredentialDescriptor>>();
        fake.saveAnswer = saving;
        char[] given = "pw".toCharArray();
        onUiThread(() -> challenge("a", TOP, "intranet.example", 443, "Corp", false));
        onUiThread(() -> {
            okShown("u", given, true);
            page.mainFrameResponded(TOP, 200);
            assertThat(fake.calls).as("the save under way, its unlock prompt showing").containsExactly("saveLogin " + CORP + " u");
            item.close();
            assertThat(saving).as("withdrawn").isCancelled();
            assertThat(given).as("zeroed").containsOnly((char) 0);
            assertThat(host.notifications().lines()).isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    /** Review focus 5: the plugin's stop cancels the challenges, withdraws the save under way and drops the pending one. */
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
     * A Basic challenge of the page's top-level navigation to {@link #TOP}, as Chromium's thread hands it over, to the
     * item's sign-ins, outside /dev/null. UI thread.
     */
    private void challenge(String name, String top, String challenger, int port, String realm, boolean proxy) {
        if (items == null) open(false);
        items.signIns().challenged(page, place, false, signIn(name, top, top, challenger, port, realm, proxy, "basic"));
    }

    /** A sign-in Chromium asks for a request of {@code origin}'s on the top address {@code top}, told to {@link #told}. */
    private SignIn signIn(String name, String origin, String top, String challenger, int port, String realm, boolean proxy,
                          String scheme) {
        if (items == null) open(false);
        return new SignIn(origin, proxy, challenger, port, realm, scheme, top,
            (user, password) -> told.add(name + " proceed " + user + " " + password), () -> told.add(name + " cancel"));
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
        String toggle = form().sections().stream().filter(Nodes.SettingRow.class::isInstance).map(Nodes.SettingRow.class::cast)
            .findFirst().orElseThrow(() -> new AssertionError("no Remember this password")).control().id();
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
        for (Throwable thrown = record.getThrown(); thrown != null; thrown = thrown.getCause()) text.append(' ').append(thrown);
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
