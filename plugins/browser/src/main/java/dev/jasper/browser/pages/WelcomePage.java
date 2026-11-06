package dev.jasper.browser.pages;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.json.StrictJson;
import dev.jasper.browser.store.HistoryStore;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;

/**
 * {@code jasper-about:welcome} (spec section 14), the default start page: "Welcome Back", Top Sites, the sites visited
 * most, and Quick Actions for the bookmarks, the history, Settings (the {@code jasper-about:settings} action) and the
 * site permissions. Its template is {@code welcome.html} and its script {@code welcome.js}, which asks
 * {@code {"op":"top-sites"}} and is answered {@code {"sites":[{"url","title","host"}]}}: at most {@value #TOP_SITES}
 * sites, most visited first as {@link HistoryStore#topSites} gives them, from the asking browser's history,
 * {@code title} empty when a site has none and {@code host} the address's host or, for an address with none, the
 * address. Until the onboarding ({@link OnboardingPages}) is finished the page redirects to its first page.
 */
public final class WelcomePage {
    /** The page's name, and the name of the action that opens Settings › Browser. */
    public static final String NAME = "welcome", SETTINGS = "settings";
    /** How many sites the page shows at most. */
    static final int TOP_SITES = 8;
    private static final String NOT_UNDERSTOOD = "Jasper does not understand this request.";
    private static final String NO_HISTORY = "Jasper could not answer this request.";

    private WelcomePage() { }

    /**
     * Registers the page, its queries, each answered from the history of the browser that asks, and its redirect to
     * {@code jasper-about:onboarding-intro} while {@code onboarding} is not finished. The queries come before the page is
     * first rendered. The {@link #SETTINGS} action is the plugin's own to register.
     *
     * @param about the pages' registry
     * @param pages the pages' templates
     * @param history given the asking browser's identifier, the history whose top sites the page shows there, or empty
     *     for a browser with none, which is refused; asked on the plugin's background executor
     * @param onboarding whether the onboarding is finished; asked on Chromium's IO thread, so it answers from memory
     */
    public static void register(AboutScheme about, AboutPages pages, IntFunction<Optional<HistoryStore>> history, Onboarding onboarding) {
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(onboarding, "onboarding");
        about.register(NAME, () -> pages.render(NAME, Map.of()));
        about.registerQueries(NAME, (browser, request) -> history.apply(browser).map(found -> answer(found, request))
            .orElseGet(() -> CompletableFuture.failedFuture(new IllegalStateException(NO_HISTORY))));
        about.redirect(NAME, () -> onboarding.finished() ? Optional.empty() : Optional.of(AboutScheme.NAME + ":" + OnboardingPages.INTRO_PAGE));
    }

    /**
     * The answer to one of the page's requests. Any thread: it only hands the work to the history's own thread.
     *
     * @param history the history the page shows
     * @param request what the page asked, as JSON
     * @return the answer's JSON; failed with "Jasper does not understand this request." for any request but
     *     {@code {"op":"top-sites"}}, or with the history's own reason when it cannot answer. Never an exception
     */
    public static CompletableFuture<String> answer(HistoryStore history, String request) {
        Objects.requireNonNull(history, "history");
        try {
            Map<String, Object> asked = StrictJson.object(request, 1).orElse(null);
            if (asked == null || !"top-sites".equals(asked.get("op"))) return CompletableFuture.failedFuture(new IllegalArgumentException(NOT_UNDERSTOOD));
            return history.topSites(TOP_SITES).thenApply(WelcomePage::sites);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static String sites(java.util.List<HistoryStore.Visit> visits) {
        var sites = new ArrayList<Object>(visits.size());
        for (HistoryStore.Visit visit : visits) {
            var site = new LinkedHashMap<String, Object>();
            site.put("url", visit.url());
            site.put("title", visit.title());
            site.put("host", host(visit.url()));
            sites.add(site);
        }
        return StrictJson.write(Map.of("sites", sites));
    }

    /** The address's host, or the whole address when it has none, as a {@code file:} address has not. */
    private static String host(String address) {
        try {
            String host = URI.create(address).getHost();
            return host == null || host.isEmpty() ? address : host;
        } catch (IllegalArgumentException notAnAddress) {
            return address;
        }
    }
}
