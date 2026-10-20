package dev.jasper.editor.markdown.resources;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DestinationsTest {
    @Test void keepsPlusAndDecodesExactlyOnce() {
        assertThat(Destinations.parse("a+b%20c.md#intro")).isEqualTo(new Destination.Relative("a+b c.md", Optional.of("intro")));
        assertThat(Destinations.parse("a%2520b.md")).isEqualTo(new Destination.Relative("a%20b.md", Optional.empty()));
        assertThat(Destinations.parse("../caf%C3%A9/\u65e5\u672c.md#h%C3%A9+llo"))
            .isEqualTo(new Destination.Relative("../caf\u00e9/\u65e5\u672c.md", Optional.of("h\u00e9+llo")));
        assertThat(Destinations.parse("%F0%9F%8C%B2.md")).isEqualTo(new Destination.Relative("\uD83C\uDF32.md", Optional.empty()));
        assertThat(Destinations.parse("dir/line%E2%80%A8.md"))
            .isEqualTo(new Destination.Relative("dir/line\u2028.md", Optional.empty()));
        assertThat(Destinations.parse("dir/line%E2%80%A9.md"))
            .isEqualTo(new Destination.Relative("dir/line\u2029.md", Optional.empty()));
        assertThat(Destinations.parse("a b.md")).isEqualTo(new Destination.Relative("a b.md", Optional.empty()));
        assertThat(Destinations.parse("./../file.md")).isEqualTo(new Destination.Relative("./../file.md", Optional.empty()));
    }

    @Test void headingOnlyLinksDecodeTheirIdentifierOnce() {
        assertThat(Destinations.parse("#a+b%20c%2520")).isEqualTo(new Destination.Fragment("a+b c%20"));
        assertThat(Destinations.parse("#")).isEqualTo(new Destination.Fragment(""));
    }

    @Test void absoluteWebUrisRetainTheirEscapesAndQueries() {
        String url = "HTTPS://example.com/a%20b?q=one+two#intro";
        assertThat(Destinations.parse(url)).isEqualTo(new Destination.Web(URI.create(url)));
    }

    @Test void rejectsSchemeAndDrivePrefixesWithUnicodeLineSeparators() {
        for (String separator : List.of("\u2028", "\u2029", "%E2%80%A8", "%E2%80%A9")) {
            for (String prefix : List.of("C:/outside/", "C%3A/outside/", "C:", "C%3A", "javascript:", "javascript%3A")) {
                String destination = prefix + "line" + separator + ".md";
                assertThat(Destinations.parse(destination)).as(destination).isInstanceOf(Destination.Unsupported.class);
            }
        }
    }

    @Test void refusesUnsafeOrMalformedDestinations() {
        for (String path : List.of("", "//host/file.md", "%2F%2Fhost/file.md", "%6Aavascript%3Aalert(1)", "/file.md", "%2Ffile.md", "C:/file.md", "C:file.md",
                "\\\\host\\file.md", "a\\b.md", "a%5Cb.md", "file.md?q=1", "file.md%3Fq=1", "file%23name.md",
                "file:/tmp/a", "javascript:alert(1)", "data:image/png,x", "mailto:a@b", "custom:foo", "https:relative",
                "https:///file", "http://", "a%", "a%2", "a%GG", "a%\uFF14\uFF11.md", "bad%FF.md", "%C0%AF", "%E2%82", "%ED%A0%80",
                "bad\u0000.md", "bad%00.md", "bad%1F.md", "bad%7F.md", "bad%C2%85.md", "bad\n.md", "bad\uD800.md",
                "#bad%FF", "file.md#bad%00")) {
            assertThat(Destinations.parse(path)).as(path).isInstanceOf(Destination.Unsupported.class);
        }
        assertThat(Destinations.parse(null)).isInstanceOf(Destination.Unsupported.class);
    }
}
