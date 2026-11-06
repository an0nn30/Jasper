package dev.jasper.browser.item;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.page.LoadFailure;
import dev.jasper.browser.page.NavigationHistory;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.ServerCertificate;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * One browser item's error pages (spec section 14): the failed loads it showed an {@link ErrorPage} for, by their tokens,
 * and, as Chrome's do, what Back, Reload, Try Again and Continue do while one shows, and whether the toolbar says "Not
 * secure".
 * <ul>
 *   <li>A main-frame load that fails shows an error page, but for one a download or the user stopped and one of Jasper's
 *       own {@code jasper-about:} addresses, whose actions end without a page by design.</li>
 *   <li>While the item's error page shows, the item shows and saves the address that failed; an error page that is none
 *       of the item's is never saved.</li>
 *   <li>Back goes to the page before the failed load, past Chromium's entry for it and each try at it, as the history
 *       was last read; when the history is not at the error page, Back is Chromium's own.</li>
 *   <li>Reload and Try Again load the failed address again: through Chromium's entry for it, just before the error page,
 *       so it loads in place; else as a new load, for an http or https address only, as a page could load one
 *       itself.</li>
 *   <li>Continue, once and only with its token, allows the host with the certificate the item kept from Chromium, for
 *       every item of its store while that lasts, and loads it again.</li>
 *   <li>"Not secure" shows while the page last committed is at an https address of a host so allowed.</li>
 * </ul>
 * An item remembers its last {@value #MAX} error pages, as many as Chromium keeps entries in a page's history. UI
 * thread.
 */
final class ErrorPages {
    /** The most error pages an item remembers. */
    static final int MAX = 50;
    private final Page page;
    private final CertificateAllowance certificates;
    private final Consumer<URI> load;
    // The failures the item showed an error page for, by their pages' tokens, oldest first.
    private final Map<String, ErrorPage.Failure> failures = new LinkedHashMap<>();
    // The address the page's main frame last committed; null before the first.
    private String committed;

    /**
     * The error pages of the item whose page is {@code page}. The page goes on past only the certificates
     * {@code certificates} allows.
     *
     * @param page the item's page
     * @param certificates the certificates the user went on past, its store's
     * @param load loads an address in the item, as its address field would
     */
    ErrorPages(Page page, CertificateAllowance certificates, Consumer<URI> load) {
        this.page = Objects.requireNonNull(page, "page");
        this.certificates = Objects.requireNonNull(certificates, "certificates");
        this.load = Objects.requireNonNull(load, "load");
        // Chromium asks on its own thread; the allowance is safe there.
        page.allowCertificates(certificate -> certificates.allows(certificate.host(), certificate.sha256()));
    }

    /** A load of the page's main frame failed: the page shows an error page for it, unless it shows none for it. */
    void failed(LoadFailure failure) {
        if (AboutScheme.page(failure.url()).isPresent() || ErrorPage.title(failure.code()).isEmpty()) return;
        var shown = ErrorPage.Failure.of(failure);
        failures.put(shown.token(), shown);
        if (failures.size() > MAX) failures.remove(failures.keySet().iterator().next());
        page.showError(shown.address());
    }

    /**
     * The page's main frame committed {@code address}.
     *
     * @return what the item saves: the address, or what failed for one of the item's error pages; empty for an error
     *     page that is none of the item's, for which the item keeps what it saved before
     */
    Optional<String> committed(String address) {
        committed = address;
        if (AboutScheme.page(address).filter(ErrorPage.NAME::equals).isEmpty()) return Optional.of(address);
        return ErrorPage.token(address).map(failures::get).map(ErrorPage.Failure::url);
    }

    /** The failure whose error page the page shows: one of the item's. */
    Optional<ErrorPage.Failure> shown() {
        return ErrorPage.token(page.address()).map(failures::get);
    }

    /** Where the page is, as the item shows it: what failed while its error page shows, else the page's address. */
    String address() {
        return shown().map(ErrorPage.Failure::url).orElseGet(page::address);
    }

    /** Whether Back goes anywhere: from the item's error page, as {@link #back} goes; else as Chromium says. */
    boolean canGoBack() {
        Optional<ErrorPage.Failure> shown = shown().filter(this::historyAt);
        return shown.isPresent() ? backFromError(shown.get()).isPresent() : page.canGoBack();
    }

    /** Back: from the item's error page, to the entry before the failed load; nowhere when there is none. */
    void back() {
        Optional<ErrorPage.Failure> shown = shown().filter(this::historyAt);
        if (shown.isPresent()) backFromError(shown.get()).ifPresent(page::goToHistoryEntry);
        else page.back();
    }

    /** Reload: on the item's error page, loads what failed again; else the page reloads. */
    void reload() {
        shown().ifPresentOrElse(this::retry, page::reload);
    }

    /** An error page's Try Again with {@code token}: while the item shows its error page of that token, it retries. */
    void tryAgain(String token) {
        shown().filter(shown -> shown.token().equals(token)).ifPresent(this::retry);
    }

    /**
     * An error page's Continue with {@code token}: while the item shows its error page whose Continue has that token, and
     * only once, the host and certificate the item kept for it are allowed and it loads again.
     */
    void proceed(String token) {
        Optional<ErrorPage.Failure> shown = shown().filter(failure -> failure.proceed().filter(token::equals).isPresent());
        if (shown.isEmpty() || shown.get().certificate().isEmpty()) return;
        ErrorPage.Failure failure = shown.get();
        ServerCertificate certificate = failure.certificate().get();
        certificates.allow(certificate.host(), certificate.sha256());
        failures.put(failure.token(), failure.spent());
        retry(failure);
    }

    /** Whether the page last committed is at an https address of a host the user went on to past a certificate error. */
    boolean notSecure() {
        if (committed == null || shown().isPresent()) return false;
        return AddressInput.parse(committed).filter(at -> "https".equalsIgnoreCase(at.getScheme())).map(URI::getHost)
            .filter(certificates::allowsAny).isPresent();
    }

    /** Whether the page's history, as last read, is at {@code shown}'s error page. */
    private boolean historyAt(ErrorPage.Failure shown) {
        NavigationHistory.History history = page.history();
        return history.current() >= 0
            && ErrorPage.token(history.entries().get(history.current()).url()).filter(shown.token()::equals).isPresent();
    }

    /** The nearest entry before the error page that is neither {@code shown}'s failed load nor an error page for it. */
    private OptionalInt backFromError(ErrorPage.Failure shown) {
        NavigationHistory.History history = page.history();
        for (int at = history.current() - 1; at >= 0; at--) {
            NavigationHistory.Entry entry = history.entries().get(at);
            if (!ofFailure(entry.url(), shown)) return OptionalInt.of(entry.id());
        }
        return OptionalInt.empty();
    }

    /** Whether an entry at {@code entry} is {@code shown}'s failed load, or one of the item's error pages for that address. */
    private boolean ofFailure(String entry, ErrorPage.Failure shown) {
        String failed = NavigationHistory.entryUrl(shown.url());
        return entry.equals(failed) || ErrorPage.token(entry).map(failures::get)
            .filter(other -> NavigationHistory.entryUrl(other.url()).equals(failed)).isPresent();
    }

    /** Loads what failed for {@code shown} again: in place through Chromium's entry for it, else as a new web load. */
    private void retry(ErrorPage.Failure shown) {
        if (historyAt(shown)) {
            NavigationHistory.History history = page.history();
            int before = history.current() - 1;
            if (before >= 0 && history.entries().get(before).url().equals(NavigationHistory.entryUrl(shown.url()))) {
                page.goToHistoryEntry(history.entries().get(before).id());
                return;
            }
        }
        AddressInput.parse(shown.url()).filter(ErrorPages::web).ifPresent(load);
    }

    /** Whether {@code address} is an http or https one. */
    private static boolean web(URI address) {
        String scheme = address.getScheme() == null ? "" : address.getScheme().toLowerCase(Locale.ROOT);
        return scheme.equals("http") || scheme.equals("https");
    }
}
