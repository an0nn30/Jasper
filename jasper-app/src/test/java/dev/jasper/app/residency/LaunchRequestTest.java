package dev.jasper.app.residency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Spec 5.3: handoff protocol 3, which carries web addresses (browser plan 4c), and protocol 2 before it. */
class LaunchRequestTest {
    @TempDir Path home;

    private static String b64(String text) { return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)); }

    private static String line(String... fields) { return String.join("\t", fields) + "\n"; }

    /** Windows paths cannot hold a tab or a newline, so there is nothing to carry. */
    @Test @DisabledOnOs(OS.WINDOWS) void aRequestSurvivesEncodingWithSeparatorsAndCommasInItsPaths() {
        var request = new LaunchRequest("tok-en_123", Path.of("/Users/a b\tc/Jasper.app/Contents/app/jasper-app.jar"),
            1_726_500_000_000L, LaunchRequest.Intent.OPEN_PATHS, Path.of("/work/dir\nx"),
            List.of(Path.of("/work/notes ü.txt"), Path.of("/work/a,b"), Path.of("/work/t\tab")),
            List.of(URI.create("https://example.com/a,b?q=%09"), URI.create("HTTP://Example.com/ü")), List.of("--safe-mode", "--config"));
        String encoded = request.encode();
        assertThat(encoded).startsWith("jasper\t3\ttok-en_123\t").endsWith("\n");
        assertThat(encoded.chars().filter(c -> c == '\t').count()).isEqualTo(9);
        assertThat(encoded.chars().filter(c -> c == '\n').count()).isEqualTo(1);
        assertThat(LaunchRequest.decode(encoded)).isEqualTo(request);
    }

    @Test void aRaiseCarriesNoPathsOrFlagsAndAPathsRequestIsOpenPaths() {
        var raise = LaunchRequest.of(Path.of("/app.jar").toAbsolutePath(), 7L, home, List.of(), List.of(), List.of());
        assertThat(raise.intent()).isEqualTo(LaunchRequest.Intent.RAISE);
        assertThat(raise.token()).as("filled in when it is sent").isEmpty();
        assertThat(raise.encode()).endsWith("\traise\t" + b64(home.toString()) + "\t\t\t\n");
        assertThat(LaunchRequest.decode(raise.withToken("t").encode())).isEqualTo(raise.withToken("t"));
        var open = LaunchRequest.of(null, 0L, home, List.of(home.resolve("a.txt")), List.of(), List.of("--plugin-dir"));
        assertThat(open.intent()).isEqualTo(LaunchRequest.Intent.OPEN_PATHS);
        assertThat(open.codeLocation()).as("an unknown code location").isEqualTo(Path.of(""));
        assertThat(LaunchRequest.decode(open.withToken("t").encode())).isEqualTo(open.withToken("t"));
    }

    /** Browser plan 4c: web addresses alone are OPEN_PATHS too, and round-trip, in order, beside paths. */
    @Test void webAddressesRoundTripBesidePathsAndAloneAreOpenPaths() {
        var addresses = List.of(URI.create("https://example.com/a"), URI.create("http://localhost:8080/b?c=1#d"));
        var only = LaunchRequest.of(null, 0L, home, List.of(), addresses, List.of());
        assertThat(only.intent()).isEqualTo(LaunchRequest.Intent.OPEN_PATHS);
        assertThat(only.paths()).isEmpty();
        assertThat(only.encode()).startsWith("jasper\t3\t").endsWith("\t" + b64("https://example.com/a") + ","
            + b64("http://localhost:8080/b?c=1#d") + "\n");
        assertThat(LaunchRequest.decode(only.withToken("t").encode())).isEqualTo(only.withToken("t"));
        var both = LaunchRequest.of(null, 0L, home, List.of(home.resolve("a.txt")), addresses, List.of("--safe-mode"));
        LaunchRequest decoded = LaunchRequest.decode(both.withToken("t").encode());
        assertThat(decoded).isEqualTo(both.withToken("t"));
        assertThat(decoded.urls()).containsExactlyElementsOf(addresses);
        assertThat(decoded.paths()).containsExactly(home.resolve("a.txt"));
        var most = Stream.iterate(1, n -> n + 1).limit(LaunchRequest.MAX_URLS).map(n -> URI.create("https://example.com/" + n)).toList();
        assertThat(LaunchRequest.decode(LaunchRequest.of(null, 0L, home, List.of(), most, List.of()).withToken("t").encode()).urls())
            .hasSize(32);
    }

    /** Browser plan 4c: a launch of the build before still hands over its protocol-2 request, which carries no web addresses. */
    @Test void aProtocolTwoRequestStillDecodesWithNoWebAddresses() {
        String jar = b64("/x.jar"), folder = b64(home.toString()), path = b64(home.resolve("a.txt").toString()), flag = b64("--safe-mode");
        LaunchRequest open = LaunchRequest.decode(line("jasper", "2", "t", jar, "1", "open_paths", folder, path, flag));
        assertThat(open).isEqualTo(new LaunchRequest("t", Path.of("/x.jar"), 1L, LaunchRequest.Intent.OPEN_PATHS, home,
            List.of(home.resolve("a.txt")), List.of(), List.of("--safe-mode")));
        LaunchRequest raise = LaunchRequest.decode(line("jasper", "2", "t", jar, "1", "raise", folder, "", ""));
        assertThat(raise.intent()).isEqualTo(LaunchRequest.Intent.RAISE);
        assertThat(raise.urls()).isEmpty();
    }

    @Test void anythingThisBuildCannotUnderstandDecodesToNullRatherThanThrowing() {
        String jar = b64("/x.jar"), folder = b64(home.toString()), path = b64(home.resolve("a.txt").toString()), flag = b64("--safe-mode");
        String web = b64("https://example.com/a");
        assertThat(LaunchRequest.decode(line("jasper", "2", "t", jar, "1", "open_paths", folder, path, flag))).isNotNull();
        assertThat(LaunchRequest.decode(line("jasper", "3", "t", jar, "1", "open_paths", folder, path, flag, web))).isNotNull();
        assertThat(LaunchRequest.decode(line("jasper", "3", "t", jar, "1", "open_paths", folder, "", "", web))).isNotNull();
        String tooMany = String.join(",", Stream.iterate(1, n -> n + 1).limit(33).map(n -> b64("https://example.com/" + n)).toList());
        for (String malformed : new String[]{
                "", "\n", "garbage\n", "jasper\t2\tt\n",
                line("jasper", "2", "t", jar, "1", "open_paths", folder, path, flag, "extra"),
                line("notjasper", "2", "t", jar, "1", "open_paths", folder, path, flag),
                line("jasper", "1", "t", jar, "1", "open_paths", folder, path, flag),
                line("jasper", "3", "t", jar, "1", "open_paths", folder, path, flag),
                line("jasper", "x", "t", jar, "1", "open_paths", folder, path, flag),
                line("jasper", "2", "t", jar, "x", "open_paths", folder, path, flag),
                line("jasper", "2", "t", "!not base64!", "1", "open_paths", folder, path, flag),
                line("jasper", "2", "t", "AA==", "1", "open_paths", folder, path, flag),
                line("jasper", "2", "t", jar, "1", "dance", folder, path, flag),
                line("jasper", "2", "t", jar, "1", "raise", folder, path, flag),
                line("jasper", "2", "t", jar, "1", "open_paths", folder, "", flag),
                line("jasper", "2", "t", jar, "1", "open_paths", folder, b64("notes.txt"), flag),
                line("jasper", "2", "t", jar, "1", "open_paths", b64("relative"), path, flag),
                line("jasper", "2", "t", jar, "1", "open_paths", folder, path + ",", flag),
                line("jasper", "2", "t", jar, "1", "open_paths", folder, path, b64("--background")),
                line("jasper", "2", "t", jar, "1", "open_paths", folder, path, ","),
                line("jasper", "2", "t", jar, "1", "open_paths", folder, path, flag, web),
                line("jasper", "3", "t", jar, "1", "raise", folder, "", "", web),
                line("jasper", "3", "t", jar, "1", "open_paths", folder, "", "", ""),
                line("jasper", "3", "t", jar, "1", "open_paths", folder, path, flag, web, "extra"),
                line("jasper", "3", "t", jar, "1", "open_paths", folder, path, flag, b64("ftp://example.com/f")),
                line("jasper", "3", "t", jar, "1", "open_paths", folder, path, flag, b64("/x.html")),
                line("jasper", "3", "t", jar, "1", "open_paths", folder, path, flag, b64("https:example.com")),
                line("jasper", "3", "t", jar, "1", "open_paths", folder, path, flag, b64("http:/notes")),
                line("jasper", "3", "t", jar, "1", "open_paths", folder, path, flag, b64("https://exa mple.com")),
                line("jasper", "3", "t", jar, "1", "open_paths", folder, path, flag, web + ","),
                line("jasper", "3", "t", jar, "1", "open_paths", folder, path, flag, tooMany)}) {
            assertThat(LaunchRequest.decode(malformed)).as(malformed.strip()).isNull();
        }
    }

    @Test void theRecordRejectsWhatTheWireRejects() {
        Path jar = Path.of("/x.jar");
        URI web = URI.create("https://example.com/a");
        assertThatIllegalArgumentException().as("a raise names no paths").isThrownBy(() ->
            new LaunchRequest("t", jar, 1, LaunchRequest.Intent.RAISE, home, List.of(home), List.of(), List.of()));
        assertThatIllegalArgumentException().as("nor web addresses").isThrownBy(() ->
            new LaunchRequest("t", jar, 1, LaunchRequest.Intent.RAISE, home, List.of(), List.of(web), List.of()));
        assertThatIllegalArgumentException().as("open paths names some").isThrownBy(() ->
            new LaunchRequest("t", jar, 1, LaunchRequest.Intent.OPEN_PATHS, home, List.of(), List.of(), List.of()));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new LaunchRequest("t", jar, 1, LaunchRequest.Intent.OPEN_PATHS, home, List.of(Path.of("notes.txt")), List.of(), List.of()));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new LaunchRequest("t", jar, 1, LaunchRequest.Intent.RAISE, Path.of("relative"), List.of(), List.of(), List.of()));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new LaunchRequest("t", jar, 1, LaunchRequest.Intent.RAISE, home, List.of(), List.of(), List.of("--background")));
        for (String notWeb : new String[]{"ftp://example.com/f", "file:///x.html", "/x.html", "https:example.com", "http:/notes"})
            assertThatIllegalArgumentException().as(notWeb).isThrownBy(() ->
                new LaunchRequest("t", jar, 1, LaunchRequest.Intent.OPEN_PATHS, home, List.of(), List.of(URI.create(notWeb)), List.of()));
        var tooMany = Stream.iterate(1, n -> n + 1).limit(33).map(n -> URI.create("https://example.com/" + n)).toList();
        assertThatIllegalArgumentException().as("at most 32 web addresses").isThrownBy(() ->
            new LaunchRequest("t", jar, 1, LaunchRequest.Intent.OPEN_PATHS, home, List.of(), tooMany, List.of()));
    }

    @Test void anotherBuildIsAnotherCodeLocationOrTimeButAMatchingUnknownLocationIsNot() {
        Path source = Path.of("/app.jar");
        assertThat(LaunchRequest.of(source, 100L, home, List.of(), List.of(), List.of()).anotherBuild(source, 100L)).isFalse();
        assertThat(LaunchRequest.of(Path.of("/other.jar"), 100L, home, List.of(), List.of(), List.of()).anotherBuild(source, 100L)).isTrue();
        assertThat(LaunchRequest.of(source, 999L, home, List.of(), List.of(), List.of()).anotherBuild(source, 100L)).isTrue();
        assertThat(LaunchRequest.of(null, 0L, home, List.of(), List.of(), List.of()).anotherBuild(null, 0L)).isFalse();
    }

    @Test void aRequestLongerThanTheLineDoesNotFit() {
        var many = Stream.iterate(0, i -> i + 1).limit(20_000).map(i -> home.resolve("a-long-file-name-number-" + i + ".txt")).toList();
        assertThat(LaunchRequest.of(null, 0L, home, List.of(home), List.of(), List.of()).fits()).isTrue();
        assertThat(LaunchRequest.of(null, 0L, home, many, List.of(), List.of()).fits()).isFalse();
    }

    @Test void responsesRoundTripAndAnythingUnrecognisedIsAProtocolError() {
        assertThat(LaunchRequest.Response.OK.line()).isEqualTo("ok\n");
        assertThat(LaunchRequest.Response.UPDATED.line()).isEqualTo("updated\n");
        assertThat(LaunchRequest.Response.TOKEN.line()).isEqualTo("refused\ttoken\n");
        assertThat(LaunchRequest.Response.PROTOCOL.line()).isEqualTo("refused\tprotocol\n");
        for (var response : LaunchRequest.Response.values())
            assertThat(LaunchRequest.Response.of(response.line())).isEqualTo(response);
        for (String reply : new String[]{"", "yes\n", "refused\n", "refused\tok\n", "ok extra\n", "refused\tstale\n", "UPDATED\n"})
            assertThat(LaunchRequest.Response.of(reply)).as(reply.strip()).isEqualTo(LaunchRequest.Response.PROTOCOL);
    }
}
