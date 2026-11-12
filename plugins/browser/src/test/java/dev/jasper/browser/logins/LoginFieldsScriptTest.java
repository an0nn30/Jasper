package dev.jasper.browser.logins;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan 8 Task 4b: {@code login-fields.js} as text, since no test runs JavaScript (D3); {@code node --check} and the stub
 * harness in {@code .superpowers/sdd/probes/p8-4b/} run it. It is ASCII, its emoji written as escapes; it shows the
 * Vault's buttons titled "Open Password Manager" by iTerm2's rule (a password field, or an {@code autocomplete}
 * username), in a closed shadow root styled by an adopted sheet, never a {@code <style>} or HTML text (F11, F13); it acts
 * only on trusted events, maps the field through the visual viewport (F25), does nothing in an opaque document (L17),
 * cancels its pick as the page scrolls (L6), and logs nothing.
 */
class LoginFieldsScriptTest {
    @Test void itIsAsciiWithNoControlCharacterButTheNewlineAndItsEmojiAreEscapes() throws IOException {
        String script = script();
        assertThat(script.chars().filter(c -> (c < 0x20 && c != '\n') || c >= 0x7F)).isEmpty();
        assertThat(script).contains("\\u{1F511}", "\\u{1F3AB}").startsWith("/*").endsWith("})();\n");
    }

    @Test void itShowsTheVaultsButtonsByITerm2sRuleAndAsksForAPick() throws IOException {
        assertThat(script()).contains("Open Password Manager", "'login.pick'", "autocomplete", "'username'", "'password'",
            "Symbol.for('dev.jasper.browser.logins')", "'focusin'", "'focusout'", "'mousedown'", "'click'");
    }

    @Test void itStylesAClosedShadowRootWithAnAdoptedSheetAndWritesNoHtml() throws IOException {
        assertThat(script()).contains("adoptedStyleSheets", "'closed'", "replaceSync", "position: fixed !important",
                "z-index: 2147483647 !important", "all: initial !important")
            .doesNotContain("<style", "innerHTML", "outerHTML", "insertAdjacentHTML", "document.write");
    }

    @Test void itTrustsOnlyTheUsersEventsMapsThroughTheVisualViewportAndLogsNothing() throws IOException {
        assertThat(script()).contains("isTrusted", "visualViewport", "jasperBridgeQuery", "jasperBridgeCancel", "'null'")
            .doesNotContain("console.", "debugger", "localStorage", "setAttribute('value'");
    }

    @Test void theScriptIsTheOneTheButtonsGive() throws IOException {
        assertThat(FieldButtons.SCRIPT).isEqualTo(script().strip());
    }

    private static String script() throws IOException {
        try (InputStream in = LoginFieldsScriptTest.class.getResourceAsStream("/dev/jasper/browser/logins/login-fields.js")) {
            assertThat(in).as("the resource").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
