package dev.jasper.browser.tools;

import dev.jasper.browser.json.StrictJson;
import dev.jasper.browser.tools.SavePayload.Kind;
import dev.jasper.browser.tools.SavePayload.Resource;
import dev.jasper.browser.tools.SavePayload.Snapshot;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Save Page As's answer from {@code save-page.js} (plan 7's Task 7, F27): the snapshot's JSON in parts, each a bridge
 * request {@code {op: "savePage", save: prefix, part, parts, text}}, taken in order under the save's prefix, at most 64,
 * and read whole with {@link StrictJson} once the last is in. A part under another prefix is ignored; anything else
 * that is not the save's answer fails the save. Pure.
 */
class SavePayloadTest {
    private static final String PREFIX = "jasper-save-0123456789abcdef-";

    @Test void partsInOrderMakeTheSnapshot() throws IOException {
        String whole = snapshot("<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\"></head><body><img src=\"" + PREFIX + "r0-\">"
            + "<p style=\"" + PREFIX + "a0-\">caf\u00e9 \uD83D\uDE00</p></body></html>");
        var payload = new SavePayload(PREFIX);
        List<String> parts = split(whole, 7);
        for (int at = 0; at < parts.size() - 1; at++) assertThat(payload.add(part(at, parts.size(), parts.get(at)))).as("part " + at).isEmpty();
        Snapshot snapshot = payload.add(part(parts.size() - 1, parts.size(), parts.getLast())).orElseThrow();
        assertThat(snapshot.html()).contains("<img src=\"" + PREFIX + "r0-\">", "caf\u00e9 \uD83D\uDE00");
        assertThat(snapshot.resources()).containsExactly(new Resource(URI.create("https://example.com/logo.png"), Kind.OTHER),
            new Resource(URI.create("https://example.com/site.css"), Kind.STYLE), new Resource(URI.create("https://example.com/app.js"), Kind.SCRIPT));
        assertThat(snapshot.styles()).containsExactly("body { background: url(bg.png) }");
        assertThat(snapshot.attributes()).containsExactly("color: red");
        assertThat(snapshot.base()).isEqualTo(URI.create("https://example.com/news/"));
        assertThat(payload.add(part(0, 1, whole))).as("after the last, nothing more is taken").isEmpty();
    }

    @Test void onePartIsEnough() throws IOException {
        String whole = snapshot("<html></html>");
        assertThat(new SavePayload(PREFIX).add(part(0, 1, whole))).isPresent();
    }

    /** D28's rule for the save: only the answer under this save's prefix counts; another save's, or a forged one, is ignored. */
    @Test void aForeignPrefixIsIgnored() throws IOException {
        var payload = new SavePayload(PREFIX);
        String whole = snapshot("<html></html>");
        assertThat(payload.add(request("jasper-save-ffffffffffffffff-", 0, 1, whole))).isEmpty();
        assertThat(payload.add("{\"op\":\"savePage\",\"part\":0,\"parts\":1,\"text\":\"\"}")).as("no prefix").isEmpty();
        assertThat(payload.add("{\"op\":\"savePage\",\"save\":7,\"part\":0,\"parts\":1,\"text\":\"\"}")).as("no string").isEmpty();
        assertThat(payload.add(part(0, 1, whole))).as("its own still counts").isPresent();
    }

    /** F27: a page past 64 parts is too large to save, whatever part says so. */
    @Test void moreThanSixtyFourPartsAreRefused() throws IOException {
        assertThatThrownBy(() -> new SavePayload(PREFIX).add(part(0, 65, "{"))).isInstanceOf(IOException.class)
            .hasMessage("the page is too large to save");
        var payload = new SavePayload(PREFIX);
        assertThat(payload.add(part(0, 64, "{"))).isEmpty();
        assertThatThrownBy(() -> payload.add(part(1, 65, "x"))).as("a count that changes").isInstanceOf(IOException.class);
        assertThat(SavePayload.MOST_PARTS).isEqualTo(64);
    }

    @Test void aPartOutOfOrderFails() throws IOException {
        var skipped = new SavePayload(PREFIX);
        assertThatThrownBy(() -> skipped.add(part(1, 3, "x"))).isInstanceOf(IOException.class).hasMessage("the page's answer could not be read");
        var repeated = new SavePayload(PREFIX);
        assertThat(repeated.add(part(0, 3, "{"))).isEmpty();
        assertThatThrownBy(() -> repeated.add(part(0, 3, "{"))).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> new SavePayload(PREFIX).add(part(-1, 3, "x"))).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> new SavePayload(PREFIX).add(part(0, 0, "x"))).isInstanceOf(IOException.class);
    }

    @Test void malformedJsonFails() {
        for (String text : new String[] {"{", "[]", "{\"prefix\":\"" + PREFIX + "\"}", "not json",
                snapshot("<html></html>").replace("\"other\"", "\"font\""),
                snapshot("<html></html>").replace("\"styles\":[", "\"styles\":[1,"),
                snapshot("<html></html>").replace(PREFIX, "jasper-save-ffffffffffffffff-"),
                snapshot("<html></html>").replace("}", ",\"extra\":1}"),
                snapshot("<html></html>").replace("\"url\":\"https://example.com/logo.png\"", "\"url\":\"no address\\u0000\"")})
            assertThatThrownBy(() -> new SavePayload(PREFIX).add(part(0, 1, text))).as(text).isInstanceOf(IOException.class)
                .hasMessage("the page's answer could not be read");
        for (String request : new String[] {"not json", "{\"op\":\"savePage\",\"save\":\"" + PREFIX + "\",\"part\":0,\"parts\":1}",
                "{\"op\":\"savePage\",\"save\":\"" + PREFIX + "\",\"part\":\"0\",\"parts\":1,\"text\":\"\"}",
                "{\"op\":\"reader\",\"save\":\"" + PREFIX + "\",\"part\":0,\"parts\":1,\"text\":\"\"}"})
            assertThatThrownBy(() -> new SavePayload(PREFIX).add(request)).as(request).isInstanceOf(IOException.class);
    }

    /**
     * R46: an address the snapshot names keeps no user name or password, a host Java reads as a registry name's included
     * (ruling D43), and the URI Java reads takes what WHATWG writes.
     */
    @Test void addressesLoseTheirUserInformationAndAreEscaped() throws IOException {
        Map<String, Object> answer = members("<html></html>");
        answer.put("resources", List.of(resource("https://user:pw@example.com/a.png", "other"),
            resource("https://example.com/a|b^c[d].png?q=%zz&r=1", "other"), resource("https://example.com/caf\u00e9.png#x", "other"),
            resource("http://u:p@a_b.example/x.png", "other"), resource("http://u@p@a_b.example:8080/y.png", "other")));
        answer.put("base", "https://user:pw@example.com/");
        Snapshot snapshot = new SavePayload(PREFIX).add(part(0, 1, StrictJson.write(answer))).orElseThrow();
        assertThat(snapshot.resources()).extracting(Resource::url).containsExactly(URI.create("https://example.com/a.png"),
            URI.create("https://example.com/a%7Cb%5Ec%5Bd%5D.png?q=%25zz&r=1"), URI.create("https://example.com/caf%C3%A9.png#x"),
            URI.create("http://a_b.example/x.png"), URI.create("http://a_b.example:8080/y.png"));
        assertThat(snapshot.base()).isEqualTo(URI.create("https://example.com/"));
    }

    @Test void thePrefixIsChecked() {
        for (String bad : new String[] {"", "jasper-save-", "jasper-save-0123456789abcde-", "jasper-save-0123456789ABCDEF-", "x"})
            assertThatThrownBy(() -> new SavePayload(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
    }

    /** {@code whole}'s text in {@code count} parts. */
    private static List<String> split(String whole, int count) {
        var parts = new ArrayList<String>();
        int size = (whole.length() + count - 1) / count;
        for (int at = 0; at < whole.length(); at += size) parts.add(whole.substring(at, Math.min(whole.length(), at + size)));
        return parts;
    }

    private static String part(int part, int parts, String text) {
        return request(PREFIX, part, parts, text);
    }

    private static String request(String prefix, int part, int parts, String text) {
        var request = new LinkedHashMap<String, Object>();
        request.put("op", "savePage");
        request.put("save", prefix);
        request.put("part", (long) part);
        request.put("parts", (long) parts);
        request.put("text", text);
        return StrictJson.write(request);
    }

    /** The snapshot's JSON for {@code html}, as {@code save-page.js} writes it. */
    private static String snapshot(String html) {
        return StrictJson.write(members(html));
    }

    private static Map<String, Object> members(String html) {
        var answer = new LinkedHashMap<String, Object>();
        answer.put("prefix", PREFIX);
        answer.put("html", html);
        answer.put("resources", List.of(resource("https://example.com/logo.png", "other"), resource("https://example.com/site.css", "style"),
            resource("https://example.com/app.js", "script")));
        answer.put("styles", List.of("body { background: url(bg.png) }"));
        answer.put("attributes", List.of("color: red"));
        answer.put("base", "https://example.com/news/");
        return answer;
    }

    private static Map<String, Object> resource(String url, String kind) {
        var resource = new LinkedHashMap<String, Object>();
        resource.put("url", url);
        resource.put("kind", kind);
        return resource;
    }
}
