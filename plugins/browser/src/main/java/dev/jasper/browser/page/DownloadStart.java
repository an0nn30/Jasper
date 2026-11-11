package dev.jasper.browser.page;

import java.util.Objects;
import java.util.Optional;

/**
 * A download Chromium is about to start, in plain values: what {@link PageDownloads} reads on Chromium's thread as
 * Chromium asks where the file goes, for the {@code downloads} package, which answers with a {@link DownloadTarget} or
 * refuses it.
 *
 * @param browser the identifier of the browser that started the download, which its updates carry (ruling F21)
 * @param id Chromium's id for the download, unique within that browser's request context
 * @param suggestedName the file name the server or the page suggests, as Chromium gives it; never null
 * @param url the address the file comes from; never null
 * @param pageUrl the address the browser's main frame shows, the page that led to the file; never null
 * @param page the page whose browser started it; empty for a browser that is no page's, as DevTools' is, whose download
 *     holds nothing
 * @param ask whether the user asked where to save it, as Save Link As… and Save Image As… ask through the page's
 *     {@link DownloadAsk}, so Chromium shows the save chooser; false for a browser that is no page's
 * @param activated whether the page had a user activation as the download started, read and not consumed: within
 *     5 seconds of a press or key it got (ruling D1); false for a browser that is no page's
 * @param document the page's download allowance, which a load Jasper starts for the user and a main-frame document
 *     started with the page's user activation renew (rulings D1, D20 and D46); zero for a browser that is no page's
 * @param inMemory whether the browser keeps its data in memory, a /dev/null page's or its DevTools', so no address of
 *     the download may reach the disk (ruling F7)
 */
public record DownloadStart(int browser, int id, String suggestedName, String url, String pageUrl, Optional<Page> page, boolean ask,
                            boolean activated, long document, boolean inMemory) {
    /** Reads a missing text as empty, and requires the page's presence or absence. */
    public DownloadStart {
        suggestedName = suggestedName == null ? "" : suggestedName;
        url = url == null ? "" : url;
        pageUrl = pageUrl == null ? "" : pageUrl;
        Objects.requireNonNull(page, "page");
    }
}
