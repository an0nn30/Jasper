package dev.jasper.browser.engine;

import java.util.function.Predicate;
import java.util.function.Supplier;
import org.cef.browser.CefRequestContext;

/**
 * Test-only access to Chromium's start, for tests outside this package that play it without starting Chromium: where
 * its in-memory contexts come from, its start's entry into JCEF, its context's initialization and the hand-over to its
 * thread.
 */
public final class EngineTestAccess {
    private EngineTestAccess() { }

    /**
     * Has {@code chromium} make its in-memory contexts with {@code made}: stand-ins, as none can be made without Chromium.
     *
     * @param chromium the Chromium
     * @param made what makes each context
     */
    public static void makeContextsWith(Chromium chromium, Supplier<CefRequestContext> made) {
        chromium.makeContextsWith(made);
    }

    /**
     * Has {@code chromium}'s start, asked for, enter JCEF, as its worker would, without running it.
     *
     * @param chromium the Chromium
     * @return whether it entered
     */
    public static boolean enter(Chromium chromium) {
        return chromium.enter();
    }

    /**
     * Chromium's context has initialized, with {@code global} as its global context: the work waiting for Chromium's
     * thread runs now, on the caller's thread.
     *
     * @param chromium the Chromium
     * @param global a stand-in global context
     */
    public static void contextInitialized(Chromium chromium, CefRequestContext global) {
        chromium.contextInitialized(global);
    }

    /**
     * JCEF has initialized: work is handed to Chromium's thread with {@code hand} from now on.
     *
     * @param chromium the Chromium
     * @param hand hands a task over, answering whether it took it
     */
    public static void handOverWith(Chromium chromium, Predicate<Runnable> hand) {
        chromium.handOverWith(hand);
    }
}
