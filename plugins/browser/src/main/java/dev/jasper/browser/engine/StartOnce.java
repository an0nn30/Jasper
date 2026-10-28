package dev.jasper.browser.engine;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-process Chromium starts at most once in a Jasper process: JCEF's app is process-wide and cannot start again after
 * its dispose. The first Chromium to ask claims the start; any later one, such as the plugin's after it was turned off
 * and on, is refused before it reaches JCEF.
 */
final class StartOnce {
    /** This process's guard. */
    static final StartOnce PROCESS = new StartOnce();
    private final AtomicBoolean claimed = new AtomicBoolean();

    /** Whether the caller is the first to ask; true for one caller only. */
    boolean claim() { return claimed.compareAndSet(false, true); }
}
