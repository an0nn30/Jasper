package dev.jasper.browser.engine;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.cef.CefSettings;

/** The settings and command-line arguments Jasper starts Chromium with. Loaded only once Chromium starts. */
final class ChromiumOptions {
    private ChromiumOptions() { }

    /**
     * Pages render off screen; Chromium's sandbox stays on, as in Chrome; cookies, the cache and the log live in the
     * plugin's data directory, and the log keeps warnings and errors only.
     *
     * @param settings JCEF's settings, changed in place
     * @param dataDir the plugin's data directory
     */
    static void apply(CefSettings settings, Path dataDir) {
        settings.windowless_rendering_enabled = true;
        settings.no_sandbox = false;
        settings.cache_path = dataDir.resolve("chromium").toAbsolutePath().toString();
        settings.persist_session_cookies = true;
        settings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_WARNING;
        settings.log_file = dataDir.resolve("chromium.log").toAbsolutePath().toString();
    }

    /**
     * JCEF's own arguments, then {@code --disable-component-update}: Chromium comes with Jasper's runtime and updates
     * with it.
     *
     * @param appArgs the arguments the runtime's JCEF configuration gives
     * @return the arguments Chromium starts with
     */
    static List<String> arguments(List<String> appArgs) {
        return Stream.concat(appArgs.stream(), Stream.of("--disable-component-update")).toList();
    }
}
