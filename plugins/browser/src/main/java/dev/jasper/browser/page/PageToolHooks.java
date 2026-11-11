package dev.jasper.browser.page;

import dev.jasper.sdk.Subscription;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import org.cef.browser.CefFrame;

/**
 * What a page gives the {@code tools} package (plan 7's Tasks 5 to 7), which its {@link Page} holds and drives, as
 * {@link PageDownloadState} holds its downloads' part: the scripts the tools run in the page's main frame, the main
 * frame's load ends, the reports the tools' page scripts send through the opener bridge, and whether the page's find
 * searches its top frame alone. How a script runs is a seam a test replaces. It reaches the browser only through its
 * page. UI thread.
 */
final class PageToolHooks {
    // Jasper's own pages' scheme, which the tools' scripts stay out of.
    private static final String JASPER_ABOUT = "jasper-about:";
    private final Page page;
    private final List<Runnable> loadEnds = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<String, String>> reports = new CopyOnWriteArrayList<>();
    // Given a script and the address it goes by, runs it in the page's main frame: through its browser, or a test's.
    private BiConsumer<String, String> scriptRunner = this::runInMainFrame;
    // Whether the page's find searches its top frame alone, as its item's reader mode says.
    private BooleanSupplier topFrameOnly = () -> false;

    /** The hooks of {@code page}, which make no call on it until asked. */
    PageToolHooks(Page page) {
        this.page = page;
    }

    /** Tells {@code listener} each time the page's main frame finishes loading its document, until the registration ends. */
    Subscription onLoadEnd(Runnable listener) {
        loadEnds.add(listener);
        return () -> loadEnds.remove(listener);
    }

    /** The page's main frame finished loading its document: those who asked {@link #onLoadEnd} are told. */
    void loadEnded() {
        for (Runnable listener : loadEnds) listener.run();
    }

    /** Tells {@code listener} each report the tools' page scripts send, until the registration ends. */
    Subscription onReport(BiConsumer<String, String> listener) {
        reports.add(listener);
        return () -> reports.remove(listener);
    }

    /** The page's top frame sent the report {@code json} under the bridge op {@code op}: those who asked are told. */
    void reported(String op, String json) {
        for (BiConsumer<String, String> listener : reports) listener.accept(op, json);
    }

    /** Runs {@code script} under the name {@code url} in the page's main frame, as the script runner does. */
    void run(String script, String url) {
        scriptRunner.accept(script, url);
    }

    /** Has the page's find search its top frame alone while {@code when} says so, from now on. */
    void topFrameOnly(BooleanSupplier when) {
        topFrameOnly = Objects.requireNonNull(when, "when");
    }

    /** Has {@code find}, the page's, search its top frame alone while the latest {@link #topFrameOnly} says so. */
    void searchedBy(PageFind find) {
        find.searchTopFrameOnly(() -> topFrameOnly.getAsBoolean());
    }

    /** The page closed: nobody is told anything more. */
    void close() {
        loadEnds.clear();
        reports.clear();
    }

    /**
     * Runs {@code script} in the main frame of the page's browser, then releases only this handle to the frame; nothing
     * before the browser is ready, and nothing in a frame {@link #runsScriptsIn} refuses.
     */
    private void runInMainFrame(String script, String url) {
        PageBrowser ready = page.readyBrowser();
        CefFrame frame = ready == null ? null : ready.getMainFrame();
        if (frame == null) return;
        try {
            if (frame.isValid() && runsScriptsIn(frame.getURL())) frame.executeJavaScript(script, url, 0);
        } finally {
            frame.dispose();
        }
    }

    /**
     * Whether {@link Page#run} runs a script in a main frame at {@code address}: not at a challenge provider's address
     * ({@link ChallengeFrames}), and not on one of Jasper's own {@code jasper-about:} pages, in any case, which Jasper's
     * page scripts stay out of.
     *
     * @param address the main frame's address, as Chromium names it
     * @return whether it does; false for none
     */
    static boolean runsScriptsIn(String address) {
        return address != null && !address.regionMatches(true, 0, JASPER_ABOUT, 0, JASPER_ABOUT.length()) && !ChallengeFrames.is(address);
    }

    /** Has the page run the scripts asked of it with {@code replacement} from now on, as a test's recording one. */
    void useScriptRunner(BiConsumer<String, String> replacement) {
        scriptRunner = Objects.requireNonNull(replacement, "replacement");
    }
}
