package dev.jasper.browser.links;

import dev.jasper.browser.page.LoadFailure;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageBrowser;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.page.ServerCertificate;
import java.awt.Point;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.cef.browser.CefFrame;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec section 14: what the client's request handler does with a certificate error of a page's main frame; where a link
 * a page's DevTools open goes; and where a link Chromium would open elsewhere comes from. Chromium's side is played by
 * the test: the certificate it reports, the browser it calls about (made without Chromium,
 * {@code PageTestAccess.devToolsBrowser}), and its thread, whose hand-offs to the UI thread the test runs. No Chromium.
 */
class PageNavigationTest {
    private static final String URL = "https://self-signed.badssl.com/";
    private static final ServerCertificate CERTIFICATE = new ServerCertificate("self-signed.badssl.com",
        "d53a1b43dbe103e328aa1f2e50c9ee35d35d116b19c8912f86aeff71eda21e61", List.of("CN=*.badssl.com"), true);

    @Test void anAllowedCertificateGoesOnAndTellsThePageNothing() throws Exception {
        onUiThread(() -> {
            Page page = page();
            page.allowCertificates(certificate -> certificate.equals(CERTIFICATE));
            var handedOff = new ArrayList<Runnable>();
            assertThat(PageNavigation.goesOn(page, URL, "ERR_CERT_AUTHORITY_INVALID", Optional.of(CERTIFICATE), handedOff::add)).isTrue();
            assertThat(handedOff).isEmpty();
        });
    }

    @Test void anyOtherIsCancelledAndThePageShowsItWithTheFailureThatFollows() throws Exception {
        onUiThread(() -> {
            Page page = page();
            var failures = new ArrayList<LoadFailure>();
            page.onLoadFailure(failures::add);
            var handedOff = new ArrayList<Runnable>();
            assertThat(PageNavigation.goesOn(page, URL, "ERR_CERT_AUTHORITY_INVALID", Optional.of(CERTIFICATE), handedOff::add)).as("none allowed yet").isFalse();
            assertThat(handedOff).hasSize(1);
            handedOff.forEach(Runnable::run);
            PageTestAccess.loadFailed(page, URL, "ERR_CERT_AUTHORITY_INVALID");
            assertThat(failures).containsExactly(new LoadFailure(URL, "ERR_CERT_AUTHORITY_INVALID", Optional.of(CERTIFICATE)));
            handedOff.clear();
            assertThat(PageNavigation.goesOn(page, URL, "ERR_CERT_AUTHORITY_INVALID", Optional.empty(), handedOff::add)).as("no certificate to allow").isFalse();
            assertThat(handedOff).isEmpty();
        });
    }

    /** As Chrome: a revoked, invalid or malformed certificate has no way past it, even one allowed before. */
    @Test void aFatalCertificateErrorGoesOnPastNoCertificateEvenAnAllowedOne() throws Exception {
        onUiThread(() -> {
            Page page = page();
            page.allowCertificates(certificate -> true);
            var failures = new ArrayList<LoadFailure>();
            page.onLoadFailure(failures::add);
            for (String fatal : List.of("ERR_CERT_REVOKED", "ERR_CERT_INVALID", "ERR_CERT_CONTAINS_ERRORS")) {
                var handedOff = new ArrayList<Runnable>();
                assertThat(PageNavigation.goesOn(page, URL, fatal, Optional.of(CERTIFICATE), handedOff::add)).as(fatal).isFalse();
                handedOff.forEach(Runnable::run);
                PageTestAccess.loadFailed(page, URL, fatal);
            }
            assertThat(failures).as("each shows its certificate on the error page").allSatisfy(failure ->
                assertThat(failure.certificate()).contains(CERTIFICATE));
        });
    }

    @Test void aClosedPageAllowsNothing() throws Exception {
        onUiThread(() -> {
            Page page = page();
            page.allowCertificates(certificate -> true);
            page.close();
            assertThat(PageNavigation.goesOn(page, URL, "ERR_CERT_AUTHORITY_INVALID", Optional.of(CERTIFICATE), task -> { })).isFalse();
        });
    }

    /**
     * A link or popup a page's DevTools open, as a URL clicked in their Console, goes to the page they inspect, in the tab
     * next to its own, when that page could open it itself (4a's R36); a page's own links are not DevTools'.
     */
    @Test void aLinkThePagesDevToolsOpenGoesNextToThePagesTabWhenThePageCouldOpenIt() throws Exception {
        onUiThread(() -> {
            Page page = page();
            var opened = new ArrayList<String>();
            page.onOpenItem((url, placement) -> {
                opened.add(placement + " " + url);
                return Optional.empty();
            });
            var handedOff = new ArrayList<Runnable>();
            var devTools = PageTestAccess.devToolsBrowser(PageTestAccess.devTools(page, new Point()));
            assertThat(PageNavigation.fromDevTools(devTools, "https://example.org/docs", handedOff::add)).isTrue();
            assertThat(PageNavigation.fromDevTools(devTools, "file:///etc/hosts", handedOff::add)).isTrue();
            assertThat(PageNavigation.fromDevTools(devTools, "jasper-about:welcome", handedOff::add)).isTrue();
            assertThat(PageNavigation.fromDevTools(PageTestAccess.pageBrowser(page), "https://example.org/", handedOff::add))
                .as("the page's own browser").isFalse();
            assertThat(opened).as("on the UI thread").isEmpty();
            handedOff.forEach(Runnable::run);
            assertThat(opened).as("an https page opens no local address").containsExactly("NEXT_TAB https://example.org/docs");
        });
    }

    /**
     * Plan 4b's W6: the page opens a DevTools link with Jasper's rights only when the document it shows could open it:
     * its committed address decides, not one it was only asked to load.
     */
    @Test void aDevToolsLinkOpensFromTheDocumentThePageShows() throws Exception {
        onUiThread(() -> {
            Page page = page();
            var opened = new ArrayList<String>();
            page.onOpenItem((url, placement) -> {
                opened.add(placement + " " + url);
                return Optional.empty();
            });
            PageTestAccess.addressChanged(page, "https://example.com/");
            page.load(URI.create("file:///tmp/a.html"));
            page.openFromDevTools("file:///etc/hosts");
            assertThat(opened).as("asked to load a file: page, still showing a web page").isEmpty();
            PageTestAccess.addressChanged(page, "file:///tmp/a.html");
            page.openFromDevTools("file:///etc/hosts");
            assertThat(opened).containsExactly("NEXT_TAB file:///etc/hosts");
        });
    }

    /**
     * Plan 4b's P48: a link Chromium would open elsewhere comes from the address of the frame it names, or, without one,
     * the page's committed address, never one the page was only asked to load.
     */
    @Test void aLinkComesFromItsFramesAddressOrElseThePagesCommittedOne() throws Exception {
        onUiThread(() -> {
            Page page = page();
            PageBrowser browser = PageTestAccess.pageBrowser(page);
            assertThat(PageNavigation.sender(browser, frame("file:///tmp/a.html"))).isEqualTo("file:///tmp/a.html");
            assertThat(PageNavigation.sender(browser, null)).as("before a navigation commits").isEmpty();
            page.load(URI.create("file:///tmp/a.html"));
            assertThat(PageNavigation.sender(browser, null)).as("asked to load, not committed").isEmpty();
            PageTestAccess.addressChanged(page, "file:///tmp/a.html");
            assertThat(PageNavigation.sender(browser, null)).isEqualTo("file:///tmp/a.html");
            assertThat(PageNavigation.sender(browser, frame(""))).isEqualTo("file:///tmp/a.html");
            PageBrowser devTools = PageTestAccess.devToolsBrowser(PageTestAccess.devTools(page, new Point()));
            assertThat(PageNavigation.sender(devTools, null)).as("no page's").isEmpty();
        });
    }

    /** A frame that answers only its address. */
    private static CefFrame frame(String url) {
        return (CefFrame) Proxy.newProxyInstance(CefFrame.class.getClassLoader(), new Class<?>[] {CefFrame.class}, (proxy, method, args) -> {
            if (method.getName().equals("getURL")) return url;
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private static Page page() {
        return new Page(URI.create("https://example.com/"), () -> { }, PageTestAccess.noFavicons(), Runnable::run);
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
