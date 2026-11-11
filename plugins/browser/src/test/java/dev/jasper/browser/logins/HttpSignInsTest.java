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
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TabPlacement;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.vault.api.Credential;
import dev.jasper.vault.api.CredentialPickResult;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
 * challenges (F8); one answer serves the page's waiting challenges of the same protection space (F30); and the item's
 * close cancels every challenge exactly once (review focus 5). The pages are stand-in items' own on the testkit's fake
 * host, the Vault is {@link FakeVault}, and Chromium's side is a recording {@link SignIn}.
 */
class HttpSignInsTest {
    private static final String ID = "dev.jasper.browser";
    private static final String TITLE = SignInDialog.TITLE;
    private static final String TOP = "https://intranet.example/", NEXT = "https://intranet.example/next";
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
            assertThat(fake.calls).as("the login chosen, its fetch under way")
                .containsExactly("credential https://intranet.example (Corp)");
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
        fake.login("https://www.intranet.example (Corp)", "u", "pw");
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
        fake.login("https://intranet.example (Corp)", "u", "pw");
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
