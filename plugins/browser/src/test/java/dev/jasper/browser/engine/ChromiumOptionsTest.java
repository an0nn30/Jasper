package dev.jasper.browser.engine;

import java.nio.file.Path;
import java.util.List;
import org.cef.CefSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/** The settings and arguments Chromium starts with. A {@link CefSettings} is plain Java: making one starts nothing. */
class ChromiumOptionsTest {
    @TempDir Path data;

    @Test void aFreshCefSettingsGetsJaspersValues() {
        CefSettings settings = new CefSettings();
        // The opposite of what Jasper wants, so each value below is one apply wrote.
        settings.windowless_rendering_enabled = false;
        settings.no_sandbox = false;
        settings.persist_session_cookies = false;
        ChromiumOptions.apply(settings, data);
        assertThat(settings.windowless_rendering_enabled).as("pages render off screen").isTrue();
        assertThat(settings.no_sandbox).as("Chromium's sandbox is off, as JetBrains configures JCEF").isTrue();
        assertThat(settings.cache_path).isEqualTo(data.resolve("chromium").toAbsolutePath().toString());
        assertThat(settings.persist_session_cookies).isTrue();
        assertThat(settings.log_severity).isEqualTo(CefSettings.LogSeverity.LOGSEVERITY_WARNING);
        assertThat(settings.log_file).isEqualTo(data.resolve("chromium.log").toAbsolutePath().toString());
    }

    @Test void theArgumentsAreJcefsOwnWithComponentUpdatesOff() {
        assertThat(ChromiumOptions.arguments(List.of("--a", "--b=1"))).containsExactly("--a", "--b=1", "--disable-component-update");
        assertThat(ChromiumOptions.arguments(List.of())).containsExactly("--disable-component-update");
    }
}
