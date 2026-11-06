package dev.jasper.browser.item;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.page.NavigationHistory.Entry;
import dev.jasper.browser.page.NavigationHistory.History;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.page.ServerCertificate;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TabPlacement;
import dev.jasper.sdk.ui.UiEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec section 14 on the testkit's fake host: a browser item whose page fails to load shows Jasper's error page in place
 * of it, keeps the failed address in its field, tab and restore state, and goes back, tries again and continues past a
 * certificate error as Chrome does. Chromium's side is played by the test, through the page's seams. No Chromium.
 */
class BrowserItemErrorTest {
    private static final String ID = "dev.jasper.browser";
    private static final URI EXAMPLE = URI.create("https://example.com/");
    private static final String FAILED = "https://unknown.example/";
    private static final String BAD_SSL = "https://self-signed.badssl.com/";
    private static final ServerCertificate CERTIFICATE = new ServerCertificate("self-signed.badssl.com",
        "d53a1b43dbe103e328aa1f2e50c9ee35d35d116b19c8912f86aeff71eda21e61", List.of("CN=*.badssl.com"), true);
    private static final ServerCertificate OTHER = new ServerCertificate("self-signed.badssl.com",
        "7eb3a6625f1d49cbc2b992e72c0b85a31dd5bd3d98b2b1c6e7f40750484ceb49", List.of("CN=*.badssl.com"), true);
    private FakePluginHost host;
    private BrowserItems items;
    private BrowserItem item;
    private Page page;
    private FakeKitView view;
    private final List<Integer> jumps = new ArrayList<>();
    private final AtomicInteger navigations = new AtomicInteger();

    // Stores in memory, one for each BrowserItems a test makes.
    private final MemoryStores memory = new MemoryStores();

    @AfterEach void closeStores() throws Exception {
        memory.close();
    }

    @AfterEach void closeHost() throws Exception {
        SwingUtilities.invokeAndWait(() -> { if (host != null) host.close(); });
    }

    @Test void aFailedLoadShowsTheErrorPageAndTheItemKeepsTheFailedAddress() throws Exception {
        onUiThread(() -> {
            open();
            PageTestAccess.loadFailed(page, "https://ada:secret@unknown.example/", "ERR_NAME_NOT_RESOLVED");
            assertThat(page.address()).startsWith("jasper-about:error?token=");
            assertThat(AboutScheme.parameter(page.address(), "code")).contains("ERR_NAME_NOT_RESOLVED");
            assertThat(AboutScheme.parameter(page.address(), "url")).contains(FAILED);
            assertThat(navigations).as("showing the error page is no navigation of Jasper's").hasValue(0);
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).value()).isEqualTo(FAILED);
            commitErrorPage();
            PageTestAccess.titleChanged(page, "Server Not Found");
            PageTestAccess.loadingChanged(page, false, true, false);
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).value()).as("while the error page is current").isEqualTo(FAILED);
            assertThat(host.contentPresentation(item.tab()).title()).isEqualTo("Server Not Found");
            assertThat(host.contentPresentation(item.tab()).location()).isEqualTo(FAILED);
            assertThat(host.contentPresentation(item.tab()).image()).as("the globe, not Jasper's icon").isEmpty();
            assertThat(host.contentStatus(item.tab())).singleElement().satisfies(status -> assertThat(status.text()).isEqualTo(FAILED));
            assertThat(host.restoreState(item.tab()).orElseThrow().values()).containsEntry("url", FAILED);
            assertThat(item.url()).isEqualTo(URI.create("https://ada:secret@unknown.example/"));
            PageTestAccess.addressChanged(page, "https://example.org/");
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).value()).isEqualTo("https://example.org/");
            assertThat(host.restoreState(item.tab()).orElseThrow().values()).containsEntry("url", "https://example.org/");
            PageTestAccess.addressChanged(page, "jasper-about://error/?token=" + "f".repeat(32) + "&code=ERR_FAILED&url=file%3A%2F%2F%2Fetc");
            assertThat(host.restoreState(item.tab()).orElseThrow().values()).as("an error page that is none of the item's")
                .containsEntry("url", "https://example.org/");
            PageTestAccess.addressChanged(page, "jasper-about://error/?code=ERR_FAILED");
            assertThat(host.restoreState(item.tab()).orElseThrow().values()).containsEntry("url", "https://example.org/");
            assertThat(host.failures()).isEmpty();
        });
    }

    @Test void anAbortedLoadAndAnErrorPageThatFailsShowNothing() throws Exception {
        onUiThread(() -> {
            open();
            PageTestAccess.loadFailed(page, FAILED, "ERR_ABORTED");
            assertThat(page.address()).as("a download, or a load the user stopped").isEqualTo(EXAMPLE.toString());
            PageTestAccess.loadFailed(page, FAILED, "ERR_CONNECTION_REFUSED");
            String errorPage = page.address();
            PageTestAccess.loadFailed(page, errorPage, "ERR_FAILED");
            assertThat(page.address()).as("no error page for an error page").isEqualTo(errorPage);
            for (String action : List.of("jasper-about://error-retry/?token=" + "a".repeat(32), "jasper-about://error-continue/?token=x",
                    "jasper-about://settings/", "JASPER-ABOUT:welcome")) {
                PageTestAccess.loadFailed(page, action, "ERR_FAILED");
                assertThat(page.address()).as("a Jasper action's or page's load, which ends without a page: " + action).isEqualTo(errorPage);
            }
            assertThat(host.failures()).isEmpty();
        });
    }

    /** As Chrome's Back from an error page: to the page before the failed load, past Chromium's own entry for it. */
    @Test void backFromAnErrorPageGoesToThePageBeforeTheFailedLoad() throws Exception {
        onUiThread(() -> {
            open();
            PageTestAccess.loadFailed(page, FAILED, "ERR_NAME_NOT_RESOLVED");
            String error = commitErrorPage();
            PageTestAccess.loadingChanged(page, false, true, false);
            PageTestAccess.historyRead(page, new History(2, List.of(new Entry(1, EXAMPLE.toString(), "Example"),
                new Entry(2, FAILED, ""), new Entry(3, error, "Server Not Found"))));
            assertThat(Views.button(view, BrowserToolbar.BACK).state().enabled()).isTrue();
            view.emit(new UiEvent.Action(BrowserToolbar.BACK));
            assertThat(jumps).containsExactly(1);

            jumps.clear();
            PageTestAccess.historyRead(page, new History(4, List.of(new Entry(1, EXAMPLE.toString(), "Example"), new Entry(2, FAILED, ""),
                new Entry(3, error, "Server Not Found"), new Entry(4, FAILED, ""), new Entry(5, error, "Server Not Found"))));
            item.goBack();
            assertThat(jumps).as("past each try at it").containsExactly(1);

            jumps.clear();
            PageTestAccess.historyRead(page, new History(1, List.of(new Entry(2, FAILED, ""), new Entry(3, error, "Server Not Found"))));
            assertThat(Views.button(view, BrowserToolbar.BACK).state().enabled()).as("nothing before the failed load").isFalse();
            int before = navigations.get();
            item.goBack();
            assertThat(jumps).isEmpty();
            assertThat(navigations).as("Back went nowhere").hasValue(before);

            PageTestAccess.historyRead(page, new History(1, List.of(new Entry(1, EXAMPLE.toString(), "Example"),
                new Entry(3, error, "Server Not Found"))));
            item.goBack();
            assertThat(jumps).as("a failed load Chromium kept no entry for").containsExactly(1);

            jumps.clear();
            PageTestAccess.historyRead(page, History.EMPTY);
            assertThat(Views.button(view, BrowserToolbar.BACK).state().enabled()).as("Chromium's own say, before a read").isTrue();
            item.goBack();
            assertThat(jumps).as("Chromium's own Back, which this page without a browser cannot take").isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    @Test void tryAgainAndReloadLoadTheFailedAddressAgainInPlace() throws Exception {
        onUiThread(() -> {
            open();
            PageTestAccess.loadFailed(page, FAILED, "ERR_NAME_NOT_RESOLVED");
            String error = commitErrorPage();
            String token = AboutScheme.parameter(error, "token").orElseThrow();
            PageTestAccess.historyRead(page, new History(2, List.of(new Entry(1, EXAMPLE.toString(), "Example"),
                new Entry(2, FAILED, ""), new Entry(3, error, "Server Not Found"))));
            items.tryAgain(URI.create("jasper-about://error-retry/?token=" + token));
            assertThat(jumps).as("Chromium's entry for the failed load, again").containsExactly(2);
            item.reload();
            view.emit(new UiEvent.Action(BrowserToolbar.RELOAD));
            assertThat(jumps).as("Reload on an error page tries again").containsExactly(2, 2, 2);

            jumps.clear();
            PageTestAccess.historyRead(page, new History(1, List.of(new Entry(1, EXAMPLE.toString(), "Example"),
                new Entry(3, error, "Server Not Found"))));
            items.tryAgain(URI.create("jasper-about://error-retry/?token=" + token));
            assertThat(jumps).isEmpty();
            assertThat(page.address()).as("no entry of Chromium's: the item loads it").isEqualTo(FAILED);
            assertThat(host.failures()).isEmpty();
        });
    }

    /** Try Again loads only what failed in this item, never an address a page wrote into an error page's address. */
    @Test void tryAgainActsOnlyForItsOwnItemsErrorPage() throws Exception {
        onUiThread(() -> {
            open();
            PageTestAccess.loadFailed(page, FAILED, "ERR_NAME_NOT_RESOLVED");
            String error = commitErrorPage();
            String token = AboutScheme.parameter(error, "token").orElseThrow();
            var other = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            Page otherPage = ItemTestAccess.page(other);
            String forged = "jasper-about://error/?token=" + "f".repeat(32) + "&code=ERR_FILE_NOT_FOUND&url=file%3A%2F%2F%2Fetc%2Fpasswd";
            PageTestAccess.addressChanged(otherPage, forged);
            items.tryAgain(URI.create("jasper-about://error-retry/?token=" + "f".repeat(32)));
            other.reload();
            assertThat(otherPage.address()).as("a forged error page").isEqualTo(forged);
            assertThat(page.address()).isEqualTo(error);
            items.tryAgain(URI.create("jasper-about://error-retry/?token=" + token.toUpperCase(java.util.Locale.ROOT)));
            items.tryAgain(URI.create("jasper-about://error-retry/"));
            assertThat(page.address()).isEqualTo(error);
            items.tryAgain(URI.create("jasper-about://error-retry/?token=" + token));
            assertThat(page.address()).as("its own item's, at once").isEqualTo(FAILED);
            assertThat(otherPage.address()).isEqualTo(forged);

            PageTestAccess.loadFailed(page, "file:///Users/ada/missing.txt", "ERR_FILE_NOT_FOUND");
            String local = commitErrorPage();
            PageTestAccess.historyRead(page, History.EMPTY);
            items.tryAgain(URI.create("jasper-about://error-retry/?token=" + AboutScheme.parameter(local, "token").orElseThrow()));
            assertThat(page.address()).as("a local address is tried again only through Chromium's own entry").isEqualTo(local);
            assertThat(host.failures()).isEmpty();
        });
    }

    @Test void enterInTheFieldOnAnErrorPageLoadsTheFailedAddress() throws Exception {
        onUiThread(() -> {
            open();
            PageTestAccess.loadFailed(page, "https://ada:secret@unknown.example/", "ERR_NAME_NOT_RESOLVED");
            commitErrorPage();
            view.emit(new UiEvent.Action(BrowserToolbar.ENTER));
            assertThat(page.address()).isEqualTo("https://ada:secret@unknown.example/");
            assertThat(host.failures()).isEmpty();
        });
    }

    /** Review focus 3: Continue allows that host with that certificate, once, for every item, and loads it again. */
    @Test void continueAllowsThatCertificateOnceAndLoadsThePageAgain() throws Exception {
        onUiThread(() -> {
            open();
            var other = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            assertThat(page.allowsCertificate(CERTIFICATE)).isFalse();
            page.certificateRefused(BAD_SSL, CERTIFICATE);
            PageTestAccess.loadFailed(page, BAD_SSL, "ERR_CERT_AUTHORITY_INVALID");
            String error = commitErrorPage();
            String token = AboutScheme.parameter(error, "token").orElseThrow(), proceed = AboutScheme.parameter(error, "proceed").orElseThrow();
            assertThat(AboutScheme.parameters(error, "subject")).containsExactly("CN=*.badssl.com");
            PageTestAccess.historyRead(page, History.EMPTY);

            items.proceed(URI.create("jasper-about://error-continue/?token=" + token));
            items.proceed(URI.create("jasper-about://error-continue/?token=" + "f".repeat(32)));
            assertThat(page.allowsCertificate(CERTIFICATE)).as("the page's own token, or a forged one, continues nothing").isFalse();
            assertThat(page.address()).isEqualTo(error);

            items.proceed(URI.create("jasper-about://error-continue/?token=" + proceed));
            assertThat(page.allowsCertificate(CERTIFICATE)).isTrue();
            assertThat(ItemTestAccess.page(other).allowsCertificate(CERTIFICATE)).as("every item's").isTrue();
            assertThat(page.allowsCertificate(OTHER)).as("another certificate for the same host asks again").isFalse();
            assertThat(page.address()).as("loaded again").isEqualTo(BAD_SSL);

            PageTestAccess.addressChanged(page, error.replace("jasper-about:error?", "jasper-about://error/?"));
            PageTestAccess.loadingChanged(page, false, false, false);
            items.proceed(URI.create("jasper-about://error-continue/?token=" + proceed));
            assertThat(page.address()).as("Continue works once").isNotEqualTo(BAD_SSL);
            assertThat(host.failures()).isEmpty();
        });
    }

    /** As Chrome: a revoked certificate shows the error page with no Continue to use. */
    @Test void aFatalCertificateErrorCannotBeContinued() throws Exception {
        onUiThread(() -> {
            open();
            page.certificateRefused(BAD_SSL, CERTIFICATE);
            PageTestAccess.loadFailed(page, BAD_SSL, "ERR_CERT_REVOKED");
            String error = commitErrorPage();
            assertThat(AboutScheme.parameter(error, "proceed")).isEmpty();
            assertThat(AboutScheme.parameters(error, "subject")).as("the chain still shows").containsExactly("CN=*.badssl.com");
            items.proceed(URI.create("jasper-about://error-continue/?token=" + AboutScheme.parameter(error, "token").orElseThrow()));
            assertThat(page.allowsCertificate(CERTIFICATE)).isFalse();
            assertThat(page.address()).isEqualTo(error);
            assertThat(host.failures()).isEmpty();
        });
    }

    /** An item remembers its last 50 error pages, as many as Chromium keeps entries; an older one is shown as it is. */
    @Test void anItemRemembersItsLastFiftyErrorPages() throws Exception {
        onUiThread(() -> {
            open();
            var errors = new ArrayList<String>();
            for (int at = 0; at <= 50; at++) {
                PageTestAccess.loadFailed(page, "https://unknown" + at + ".example/", "ERR_NAME_NOT_RESOLVED");
                errors.add(commitErrorPage());
            }
            PageTestAccess.addressChanged(page, errors.get(1));
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).value()).isEqualTo("https://unknown1.example/");
            PageTestAccess.addressChanged(page, errors.get(0));
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).value()).as("the oldest, forgotten").isEqualTo(errors.get(0));
            items.tryAgain(URI.create("jasper-about://error-retry/?token=" + AboutScheme.parameter(errors.get(0), "token").orElseThrow()));
            assertThat(page.address()).as("its Try Again does nothing").isEqualTo(errors.get(0));
            assertThat(host.failures()).isEmpty();
        });
    }

    @Test void aRefusedCertificateGoesWithTheFailureOfItsOwnAddressOnly() throws Exception {
        onUiThread(() -> {
            open();
            page.certificateRefused(BAD_SSL, CERTIFICATE);
            PageTestAccess.loadFailed(page, "https://expired.badssl.com/", "ERR_CERT_DATE_INVALID");
            assertThat(AboutScheme.parameter(page.address(), "proceed")).as("another address's certificate").isEmpty();
            PageTestAccess.loadFailed(page, BAD_SSL, "ERR_CERT_AUTHORITY_INVALID");
            assertThat(AboutScheme.parameter(page.address(), "proceed")).isPresent();
            PageTestAccess.loadFailed(page, BAD_SSL, "ERR_CERT_AUTHORITY_INVALID");
            assertThat(AboutScheme.parameter(page.address(), "proceed")).as("taken by the failure it went with").isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    /** Review focus 3: "Not secure" shows while a page of an allowed host is current, and goes on a secure page. */
    @Test void notSecureShowsInPlaceOfTheSiteIconWhileAnAllowedHostIsCurrent() throws Exception {
        onUiThread(() -> {
            open();
            page.certificateRefused(BAD_SSL, CERTIFICATE);
            PageTestAccess.loadFailed(page, BAD_SSL, "ERR_CERT_AUTHORITY_INVALID");
            String error = commitErrorPage();
            assertThat(notSecure()).as("the error page").isEmpty();
            PageTestAccess.historyRead(page, History.EMPTY);
            items.proceed(URI.create("jasper-about://error-continue/?token=" + AboutScheme.parameter(error, "proceed").orElseThrow()));
            assertThat(page.address()).isEqualTo(BAD_SSL);
            assertThat(notSecure()).as("while it loads, before it commits").isEmpty();
            PageTestAccess.addressChanged(page, BAD_SSL + "page");
            assertThat(notSecure()).hasValueSatisfying(text -> {
                assertThat(text.text()).isEqualTo("Not secure");
                assertThat(text.severity()).contains(Nodes.Severity.WARNING);
            });
            Nodes.Field field = Views.field(view, BrowserToolbar.ADDRESS);
            assertThat(field.leadingImage()).isEmpty();
            assertThat(field.leadingIcon()).isEmpty();
            assertThat(field.value()).isEqualTo(BAD_SSL + "page");
            item.load(URI.create("https://example.org/"));
            assertThat(notSecure()).as("the page shown until the next one commits").isPresent();
            PageTestAccess.loadFailed(page, "https://example.org/", "ERR_ABORTED");
            assertThat(notSecure()).as("a load that never committed").isPresent();
            PageTestAccess.addressChanged(page, "https://example.org/");
            assertThat(notSecure()).as("a secure page").isEmpty();
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).leadingIcon()).isEqualTo(BrowserItems.GLOBE);
            item.load(URI.create(BAD_SSL + "next"));
            assertThat(notSecure()).as("a load of an allowed host that has not committed").isEmpty();
            PageTestAccess.addressChanged(page, "http://self-signed.badssl.com/");
            assertThat(notSecure()).as("no certificate at http").isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    @Test void theErrorPageIsInTheWorkspaceThemeAndFollowsItsChanges() throws Exception {
        onUiThread(() -> {
            open();
            var context = items.context();
            var errors = new ErrorPage(context.appearance());
            URI address = URI.create("jasper-about://error/?code=ERR_FAILED");
            String before = errors.html(address);
            assertThat(before).isEqualTo(ErrorPage.html(address, context.appearance().workspacePalette(),
                context.appearance().workspaceVariant() == Variant.DARK));
            host.setVariant(context.appearance().variant() == Variant.DARK ? Variant.LIGHT : Variant.DARK);
            host.flush();
            assertThat(errors.html(address)).isNotEqualTo(before).contains("<h1>Page Can’t Be Loaded</h1>");
        });
    }

    /** The toolbar's "Not secure" text, when it shows. */
    private Optional<Nodes.Text> notSecure() {
        return Optional.ofNullable((Nodes.Text) Views.node(view.spec().root(), BrowserToolbar.NOT_SECURE));
    }

    /** The error page the page was given commits, as Chromium writes its address: what it commits. */
    private String commitErrorPage() {
        String committed = page.address().replace("jasper-about:error?", "jasper-about://error/?");
        PageTestAccess.addressChanged(page, committed);
        return committed;
    }

    private void open() {
        host = new FakePluginHost();
        var context = new AtomicReference<PluginContext>();
        var settings = new AtomicReference<BrowserSettings.Declared>();
        host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), plugin -> {
            context.set(plugin);
            settings.set(BrowserSettings.declare(plugin.settings()));
        });
        items = new BrowserItems(context.get(), settings.get()::read, new DataStores(memory.make(), () -> false), started -> { });
        item = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
        page = item.page();
        view = host.contentView(ID, "example.com").orElseThrow();
        PageTestAccess.useHistoryJumper(page, (jumped, id) -> jumps.add(id));
        page.onNavigate(navigations::incrementAndGet);
    }

    private static void onUiThread(Runnable test) throws Exception {
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                test.run();
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        if (failure.get() instanceof Error error) throw error;
        if (failure.get() instanceof Exception exception) throw exception;
    }
}
