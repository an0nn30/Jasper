package dev.jasper.app.residency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Spec 5.3: handoff protocol 2. */
class LaunchRequestTest {
    @TempDir Path home;

    private static String b64(String text) { return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)); }

    private static String line(String... fields) { return String.join("\t", fields) + "\n"; }

    /** Windows paths cannot hold a tab or a newline, so there is nothing to carry. */
    @Test @DisabledOnOs(OS.WINDOWS) void aRequestSurvivesEncodingWithSeparatorsAndCommasInItsPaths() {
        var request = new LaunchRequest("tok-en_123", Path.of("/Users/a b\tc/Jasper.app/Contents/app/jasper-app.jar"),
            1_726_500_000_000L, LaunchRequest.Intent.OPEN_PATHS, Path.of("/work/dir\nx"),
            List.of(Path.of("/work/notes ü.txt"), Path.of("/work/a,b"), Path.of("/work/t\tab")), List.of("--safe-mode", "--config"));
        String encoded = request.encode();
        assertThat(encoded).startsWith("jasper\t2\ttok-en_123\t").endsWith("\n");
        assertThat(encoded.chars().filter(c -> c == '\t').count()).isEqualTo(8);
        assertThat(encoded.chars().filter(c -> c == '\n').count()).isEqualTo(1);
        assertThat(LaunchRequest.decode(encoded)).isEqualTo(request);
    }

    @Test void aRaiseCarriesNoPathsOrFlagsAndAPathsRequestIsOpenPaths() {
        var raise = LaunchRequest.of(Path.of("/app.jar").toAbsolutePath(), 7L, home, List.of(), List.of());
        assertThat(raise.intent()).isEqualTo(LaunchRequest.Intent.RAISE);
        assertThat(raise.token()).as("filled in when it is sent").isEmpty();
        assertThat(raise.encode()).endsWith("\traise\t" + b64(home.toString()) + "\t\t\n");
        assertThat(LaunchRequest.decode(raise.withToken("t").encode())).isEqualTo(raise.withToken("t"));
        var open = LaunchRequest.of(null, 0L, home, List.of(home.resolve("a.txt")), List.of("--plugin-dir"));
        assertThat(open.intent()).isEqualTo(LaunchRequest.Intent.OPEN_PATHS);
        assertThat(open.codeLocation()).as("an unknown code location").isEqualTo(Path.of(""));
        assertThat(LaunchRequest.decode(open.withToken("t").encode())).isEqualTo(open.withToken("t"));
    }

    @Test void anythingThisBuildCannotUnderstandDecodesToNullRatherThanThrowing() {
        String jar = b64("/x.jar"), folder = b64(home.toString()), path = b64(home.resolve("a.txt").toString()), flag = b64("--safe-mode");
        assertThat(LaunchRequest.decode(line("jasper", "2", "t", jar, "1", "open_paths", folder, path, flag))).isNotNull();
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
                line("jasper", "2", "t", jar, "1", "open_paths", folder, path, ",")}) {
            assertThat(LaunchRequest.decode(malformed)).as(malformed.strip()).isNull();
        }
    }

    @Test void theRecordRejectsWhatTheWireRejects() {
        Path jar = Path.of("/x.jar");
        assertThatIllegalArgumentException().as("a raise names no paths").isThrownBy(() ->
            new LaunchRequest("t", jar, 1, LaunchRequest.Intent.RAISE, home, List.of(home), List.of()));
        assertThatIllegalArgumentException().as("open paths names some").isThrownBy(() ->
            new LaunchRequest("t", jar, 1, LaunchRequest.Intent.OPEN_PATHS, home, List.of(), List.of()));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new LaunchRequest("t", jar, 1, LaunchRequest.Intent.OPEN_PATHS, home, List.of(Path.of("notes.txt")), List.of()));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new LaunchRequest("t", jar, 1, LaunchRequest.Intent.RAISE, Path.of("relative"), List.of(), List.of()));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new LaunchRequest("t", jar, 1, LaunchRequest.Intent.RAISE, home, List.of(), List.of("--background")));
    }

    @Test void anotherBuildIsAnotherCodeLocationOrTimeButAMatchingUnknownLocationIsNot() {
        Path source = Path.of("/app.jar");
        assertThat(LaunchRequest.of(source, 100L, home, List.of(), List.of()).anotherBuild(source, 100L)).isFalse();
        assertThat(LaunchRequest.of(Path.of("/other.jar"), 100L, home, List.of(), List.of()).anotherBuild(source, 100L)).isTrue();
        assertThat(LaunchRequest.of(source, 999L, home, List.of(), List.of()).anotherBuild(source, 100L)).isTrue();
        assertThat(LaunchRequest.of(null, 0L, home, List.of(), List.of()).anotherBuild(null, 0L)).isFalse();
    }

    @Test void aRequestLongerThanTheLineDoesNotFit() {
        var many = Stream.iterate(0, i -> i + 1).limit(20_000).map(i -> home.resolve("a-long-file-name-number-" + i + ".txt")).toList();
        assertThat(LaunchRequest.of(null, 0L, home, List.of(home), List.of()).fits()).isTrue();
        assertThat(LaunchRequest.of(null, 0L, home, many, List.of()).fits()).isFalse();
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
