package dev.jasper.browser.page;

import dev.jasper.browser.json.StrictJson;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * What a page's text insertion and its Return ask of DevTools, and how it reads the answers (spec section 15, Snippets in
 * page fields): the main frame's id from {@code Page.getFrameTree}, the isolated world's context from
 * {@code Page.createIsolatedWorld}, and whether {@code insert-text.js} reports that the field took the text from
 * {@code Runtime.evaluate}. Each answer is read as untrusted input, within bounds, and anything else is a field that did
 * not take the text. Nothing here needs Chromium; the script itself is checked by hand (Task 14).
 */
class PageTextTest {
    private static final String TREE = """
        {"frameTree":{"frame":{"id":"9C3F2A","loaderId":"L1","url":"https://example.com/","domainAndRegistry":"example.com",
         "securityOrigin":"https://example.com","mimeType":"text/html","adFrameStatus":{"adFrameType":"none"},
         "secureContextType":"Secure","crossOriginIsolatedContextType":"NotIsolated","gatedAPIFeatures":[]},
         "childFrames":[{"frame":{"id":"77AB","parentId":"9C3F2A","url":"https://ads.example.net/"}}]}}""";

    @Test void theMainFrameIsTheFrameTreesRoot() {
        assertThat(PageText.mainFrame(TREE)).contains("9C3F2A");
        for (String text : new String[] {null, "", "{}", "[]", "not json", "{\"frameTree\":{}}", "{\"frameTree\":{\"frame\":{}}}",
            "{\"frameTree\":{\"frame\":{\"id\":\"\"}}}", "{\"frameTree\":{\"frame\":{\"id\":7}}}",
            "{\"frameTree\":{\"frame\":{\"id\":\"" + "A".repeat(PageText.MAX_ID + 1) + "\"}}}", TREE + " x"})
            assertThat(PageText.mainFrame(text)).as(String.valueOf(text)).isEmpty();
        assertThat(PageText.mainFrame(TREE.replace("\"gatedAPIFeatures\":[]", "\"x\":\"" + "a".repeat(PageText.MAX_TREE) + "\"")))
            .as("a tree over the bound").isEmpty();
        String deep = "{\"frameTree\":{\"frame\":{\"id\":\"A\"},\"childFrames\":" + "[{\"childFrames\":".repeat(40) + "[]"
            + "}]".repeat(40) + "}}";
        assertThat(PageText.mainFrame(deep)).as("frames nested past the bound").isEmpty();
    }

    @Test void theIsolatedWorldIsAskedOfTheMainFrameByJaspersName() {
        Map<String, Object> asked = StrictJson.object(PageText.isolatedWorld("9C3F2A\"x"), 2).orElseThrow();
        assertThat(asked).containsExactly(Map.entry("frameId", "9C3F2A\"x"), Map.entry("worldName", PageText.WORLD));
    }

    @Test void theWorldsContextIsAnInteger() {
        assertThat(PageText.context("{\"executionContextId\":12}")).hasValue(12);
        for (String text : new String[] {null, "", "{}", "{\"executionContextId\":\"12\"}", "{\"executionContextId\":1.5}",
            "{\"executionContextId\":4294967296}", "{\"executionContextId\":12} x"})
            assertThat(PageText.context(text)).as(String.valueOf(text)).isEqualTo(OptionalInt.empty());
    }

    /** F12, F30: the script runs in the world's context, its answer by value, with no user activation. */
    @Test void theScriptRunsInTheWorldWithTheTextAsAStringLiteralAndNoActivation() {
        String text = "echo \"hi\" \\ </script> 😀\n";
        Map<String, Object> asked = StrictJson.object(PageText.evaluation(12, text), 2).orElseThrow();
        assertThat(asked).containsEntry("contextId", 12L).containsEntry("returnByValue", true).containsEntry("userGesture", false)
            .containsEntry("awaitPromise", false).containsEntry("silent", true).hasSize(6);
        String expression = (String) asked.get("expression");
        assertThat(expression).startsWith(PageText.SOURCE).endsWith("\n(" + StrictJson.quote(text) + ");");
        assertThat(PageText.SOURCE).startsWith("/*").contains("(function (text) {", "execCommand('insertText'", "setRangeText")
            .endsWith("})");
    }

    @Test void onlyTrueByValueIsAFieldThatTookTheText() {
        assertThat(PageText.inserted("{\"result\":{\"type\":\"boolean\",\"value\":true}}")).isTrue();
        for (String text : new String[] {null, "", "{}", "{\"result\":{\"type\":\"boolean\",\"value\":false}}",
            "{\"result\":{\"type\":\"string\",\"value\":\"true\"}}", "{\"result\":{\"type\":\"boolean\"}}",
            "{\"result\":{\"type\":\"boolean\",\"value\":true},\"exceptionDetails\":{\"text\":\"Uncaught\"}}",
            "{\"result\":{\"type\":\"boolean\",\"value\":true}} x",
            "{\"result\":{\"type\":\"boolean\",\"value\":true},\"x\":\"" + "a".repeat(PageText.MAX_ANSWER) + "\"}"})
            assertThat(PageText.inserted(text)).as(String.valueOf(text)).isFalse();
    }

    /** F30: Return goes down with its text, which submits a form, and comes up. */
    @Test void returnIsAKeyDownWithItsTextThenAKeyUp() {
        Map<String, Object> down = StrictJson.object(PageText.RETURN_DOWN, 1).orElseThrow();
        Map<String, Object> up = StrictJson.object(PageText.RETURN_UP, 1).orElseThrow();
        assertThat(down).containsEntry("type", "keyDown").containsEntry("key", "Enter").containsEntry("code", "Enter")
            .containsEntry("windowsVirtualKeyCode", 13L).containsEntry("text", "\r").containsEntry("unmodifiedText", "\r");
        assertThat(up).containsEntry("type", "keyUp").containsEntry("key", "Enter").containsEntry("code", "Enter")
            .containsEntry("windowsVirtualKeyCode", 13L).doesNotContainKey("text");
    }

    @Test void withoutABrowserNothingIsInsertedOrPressed() {
        assertThat(PageText.insert(null, "x")).isCompletedWithValue(false);
        assertThatCode(() -> PageText.pressReturn(null)).doesNotThrowAnyException();
    }
}
