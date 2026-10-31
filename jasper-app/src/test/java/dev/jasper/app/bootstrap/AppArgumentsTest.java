package dev.jasper.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.URI;
import java.nio.file.Path;
import java.util.OptionalLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppArgumentsTest {
    private static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");
    @TempDir Path cwd;

    private AppArguments parse(String... args) { return AppArguments.parse(args, cwd); }

    private void rejected(String[]... cases) {
        for (String[] args : cases)
            assertThatIllegalArgumentException().as(java.util.Arrays.toString(args))
                .isThrownBy(() -> AppArguments.parse(args, cwd)).withMessageContaining("Usage:");
    }

    @Test void absentOverrideAndHelpAreParsedWithoutCreatingFiles() {
        AppArguments plain = parse();
        assertThat(plain.configOverride()).isNull();
        assertThat(plain.help()).isFalse();
        assertThat(plain.paths()).isEmpty();
        assertThat(plain.urls()).isEmpty();
        assertThat(plain.replacing()).isEmpty();
        assertThat(plain.workingDirectory()).isEqualTo(cwd);
        assertThat(plain.given()).isEmpty();
        assertThat(parse("--help").help()).isTrue();
        assertThat(cwd.resolve("config.toml")).doesNotExist();
    }

    @Test void relativeAndAbsoluteOverridesNormalizeAgainstWorkingDirectory() {
        assertThat(parse("--config", "nested/../config.toml").configOverride()).isEqualTo(cwd.resolve("config.toml"));
        AppArguments absolute = parse("--config", cwd.resolve("a/../other.toml").toString(), "--help");
        assertThat(absolute.configOverride()).isEqualTo(cwd.resolve("other.toml"));
        assertThat(absolute.help()).isTrue();
        assertThat(cwd.resolve("other.toml")).doesNotExist();
    }

    @Test void invalidMissingAndDuplicateArgumentsFailClearlyEvenWithHelp() {
        rejected(new String[]{"--wat"}, new String[]{"--config"}, new String[]{"--config", "--help"}, new String[]{"--config", ""},
            new String[]{"--config", "a", "--config", "b"}, new String[]{"--help", "--help"}, new String[]{"--help", "--wat"},
            new String[]{""}, new String[]{" "}, new String[]{"--replacing"}, new String[]{"--replacing", "x"},
            new String[]{"--replacing", "0"}, new String[]{"--replacing", "-3"}, new String[]{"--replacing", "--help"},
            new String[]{"--replacing", "1", "--replacing", "2"});
    }

    @Test void anArgumentThatIsNotAnOptionIsAPathResolvedAgainstTheWorkingDirectory() {
        String absolute = cwd.resolve("x.md").toString();
        AppArguments launch = parse("notes.txt", "--safe-mode", "src/../docs", absolute);
        assertThat(launch.paths()).containsExactly(cwd.resolve("notes.txt"), cwd.resolve("docs"), cwd.resolve("x.md"));
        assertThat(launch.safeMode()).isTrue();
        assertThat(launch.given()).containsExactly("notes.txt", "--safe-mode", "src/../docs", absolute);
        assertThat(parse("-psn_0_42").paths()).as("one dash is a path, not an option").containsExactly(cwd.resolve("-psn_0_42"));
        assertThat(AppArguments.USAGE).endsWith("[<path>|<url>...]");
    }

    /**
     * Browser plan 4c, Review Focus 2: an absolute http or https argument, in any case and with an authority, is a web
     * address and no longer resolves as a path; everything else, a {@code file:} URL included, stays a path, in order.
     */
    @Test void anHttpOrHttpsArgumentIsAWebAddressAndEverythingElseStaysAPath() {
        AppArguments launch = parse("https://example.com", "notes.txt");
        assertThat(launch.urls()).containsExactly(URI.create("https://example.com"));
        assertThat(launch.paths()).containsExactly(cwd.resolve("notes.txt"));
        assertThat(launch.given()).as("as main received them").containsExactly("https://example.com", "notes.txt");
        AppArguments mixed = parse("HTTP://Example.com/a", "src", "--safe-mode", "http://localhost:8080/b?c=1#d");
        assertThat(mixed.urls()).containsExactly(URI.create("HTTP://Example.com/a"), URI.create("http://localhost:8080/b?c=1#d"));
        assertThat(mixed.paths()).containsExactly(cwd.resolve("src"));
        assertThat(mixed.safeMode()).isTrue();
        assertThat(mixed.residencyAllowed()).isFalse();
        assertThat(parse("https://example.com").residencyAllowed()).as("web addresses are no start-up flag").isTrue();
    }

    /**
     * Browser plan 4c, Review Focus 2: a {@code file:} URL is the file it names, which opens as any path does, in every
     * form RFC 8089 gives it: with no authority, an empty one or {@code localhost}, and with a query or a fragment.
     */
    @Test void aFileUrlStaysAPathToTheFileItNames() {
        Path page = cwd.resolve("x.html");
        String raw = page.toUri().getRawPath();
        AppArguments local = parse(page.toUri().toString(), "file:" + raw, "file://localhost" + raw, "FILE://LocalHost" + raw,
            page.toUri() + "?a=1#top", "file://localhost" + raw + "#top");
        assertThat(local.paths()).containsExactly(page, page, page, page, page, page);
        assertThat(local.urls()).isEmpty();
        AppArguments root = parse("file:///x.html");
        assertThat(root.urls()).isEmpty();
        assertThat(root.paths()).as("/x.html; on Windows, on the working directory's drive")
            .containsExactly(cwd.resolve(Path.of(URI.create("file:///x.html"))).normalize());
    }

    /**
     * A {@code file:} URL that names no local file is refused, saying so: one naming another host (on Windows that is the
     * share it names, as Windows reads it), an opaque one and one that does not parse.
     */
    @Test void aFileUrlThatNamesNoLocalFileIsRefused() {
        for (String text : new String[]{"file:x.html", "file:", "file://localhost", "file:///a b.html"})
            assertThatIllegalArgumentException().as(text).isThrownBy(() -> AppArguments.parse(new String[]{text}, cwd))
                .withMessageStartingWith("Not a local file: " + text + "\n").withMessageContaining("Usage:");
        if (WINDOWS) assertThat(parse("file://server/share/x.html").paths()).containsExactly(Path.of("\\\\server\\share\\x.html"));
        else assertThatIllegalArgumentException().isThrownBy(() -> AppArguments.parse(new String[]{"file://server/share/x.html"}, cwd))
            .withMessageStartingWith("Not a local file: file://server/share/x.html\n");
    }

    /**
     * Browser plan 4c: a web address with characters browsers percent-encode (a space, {@code " < > \ ^ ` { | }}, and
     * a {@code %} that starts no escape) after its host is encoded as they encode them, and opens; its other characters
     * and its escapes stay as they are.
     */
    @Test void aWebAddressWithCharactersBrowsersEncodeOpensEncodedAsTheyDo() {
        AppArguments launch = parse("https://example.com/a b", "https://example.com/say \"hi\"", "https://example.com/a|b",
            "https://example.com/{x}?q={y}#{z}", "https://example.com/100%", "https://example.com/%zz%4", "https://example.com/%41%2f",
            "http://localhost:8080/<a>^`\\", "HTTPS://Example.com/search?q=a b&r=ü");
        assertThat(launch.urls()).extracting(URI::toString).containsExactly("https://example.com/a%20b",
            "https://example.com/say%20%22hi%22", "https://example.com/a%7Cb", "https://example.com/%7Bx%7D?q=%7By%7D#%7Bz%7D",
            "https://example.com/100%25", "https://example.com/%25zz%254", "https://example.com/%41%2f",
            "http://localhost:8080/%3Ca%3E%5E%60%5C", "HTTPS://Example.com/search?q=a%20b&r=ü");
        assertThat(launch.paths()).isEmpty();
    }

    /**
     * Browser plan 4c: an argument whose scheme is http or https is never a path, on any platform. One that is still no
     * web address once encoded as browsers do (a space in its host, no {@code //host}, nothing after the scheme) is
     * refused, saying so.
     */
    @Test void anHttpOrHttpsArgumentThatIsNoWebAddressIsRefusedNotAPath() {
        for (String text : new String[]{"https://exa mple.com/", "https:example.com", "http:/notes", "HTTP:", "https://", "http://a\tb/"})
            assertThatIllegalArgumentException().as(text).isThrownBy(() -> AppArguments.parse(new String[]{text}, cwd))
                .withMessageStartingWith("Not a web address: " + text + "\n").withMessageContaining("Usage:");
    }

    /** Another scheme stays a path where a path can say so; Windows has no such path and says that. */
    @Test void otherSchemesStayPaths() {
        for (String text : new String[]{"ftp://example.com/f", "mailto:someone@example.com"}) {
            if (WINDOWS) assertThatIllegalArgumentException().as(text).isThrownBy(() -> AppArguments.parse(new String[]{text}, cwd))
                .withMessageStartingWith("Invalid path: " + text + "\n");
            else assertThat(parse(text).paths()).as(text).containsExactly(cwd.resolve(text).normalize());
        }
        assertThat(parse("https.txt", "http-notes").paths()).containsExactly(cwd.resolve("https.txt"), cwd.resolve("http-notes"));
    }

    /**
     * Browser plan 4c, ruling Q13: {@code --url} is the launch a registered command makes. It opens the one web address
     * after it, encoded as any other, and without one it is a plain launch (an empty {@code %u}); it is no start-up flag.
     */
    @Test void urlOpensTheOneWebAddressAfterIt() {
        AppArguments link = parse("--url", "https://example.com/");
        assertThat(link.urls()).containsExactly(URI.create("https://example.com/"));
        assertThat(link.paths()).isEmpty();
        assertThat(link.residencyAllowed()).isTrue();
        assertThat(link.given()).as("as main received them").containsExactly("--url", "https://example.com/");
        assertThat(parse("--url", "HTTP://Example.com/say \"hi\"").urls()).extracting(URI::toString)
            .containsExactly("HTTP://Example.com/say%20%22hi%22");
        AppArguments plain = parse("--url");
        assertThat(plain.urls()).isEmpty();
        assertThat(plain.paths()).isEmpty();
        assertThat(plain.startupFlags()).isEmpty();
        assertThat(plain.help() || plain.background() || plain.noWindow() || plain.replacing().isPresent()).isFalse();
        assertThat(plain.given()).containsExactly("--url");
    }

    /**
     * Ruling Q13: a link that breaks out of the quotes in {@code "Jasper.exe" --url "%1"} must add nothing Jasper reads.
     * Anything after {@code --url} but one web address, and {@code --url} anywhere but first, refuses the whole launch.
     */
    @Test void urlRefusesAnythingButOneWebAddressAfterIt() {
        for (String[] args : new String[][]{{"--url", "https://x/", "--plugin-dir", "y"}, {"--url", "https://x/", "extra"},
                {"--url", "--plugin-dir", "y"}, {"--url", "https://x/", "https://y/"}, {"--url", "https://x/", "--safe-mode"}})
            assertThatIllegalArgumentException().as(java.util.Arrays.toString(args)).isThrownBy(() -> AppArguments.parse(args, cwd))
                .withMessageStartingWith("--url takes one web address and nothing after it.\n").withMessageContaining("Usage:");
        for (String text : new String[]{"notes.txt", "file:///x", "--plugin-dir", "--help", "", "ftp://example.com/", "https://exa mple.com/"})
            assertThatIllegalArgumentException().as(text).isThrownBy(() -> AppArguments.parse(new String[]{"--url", text}, cwd))
                .withMessageStartingWith("Not a web address: " + text + "\n").withMessageContaining("Usage:");
        for (String[] args : new String[][]{{"--safe-mode", "--url", "https://x/"}, {"--plugin-dir", "y", "--url", "https://x/"},
                {"notes.txt", "--url"}, {"https://x/", "--url", "https://y/"}, {"--help", "--url"}})
            assertThatIllegalArgumentException().as(java.util.Arrays.toString(args)).isThrownBy(() -> AppArguments.parse(args, cwd))
                .withMessageStartingWith("--url must be the first argument.\n").withMessageContaining("Usage:");
    }

    @Test void aLaunchNamesAtMostThirtyTwoWebAddresses() {
        String[] most = IntStream.rangeClosed(1, 32).mapToObj(n -> "https://example.com/" + n).toArray(String[]::new);
        assertThat(parse(most).urls()).hasSize(32);
        String[] more = IntStream.rangeClosed(1, 33).mapToObj(n -> "https://example.com/" + n).toArray(String[]::new);
        rejected(more);
        assertThatIllegalArgumentException().isThrownBy(() -> AppArguments.parse(more, cwd))
            .withMessageStartingWith("Jasper opens at most 32 web addresses at once.");
    }

    @Test void replacingNamesTheProcessARestartReplacesAndStaysOutOfTheUsage() {
        assertThat(parse("--replacing", "4242", "notes.txt").replacing()).isEqualTo(OptionalLong.of(4242));
        assertThat(AppArguments.USAGE).doesNotContain("--replacing");
    }

    @Test void backgroundIsOffByDefaultAndParsesOnceAlongsideOtherOptions() {
        assertThat(parse().background()).isFalse();
        AppArguments login = parse("--config", "c.toml", "--background");
        assertThat(login.background()).isTrue();
        assertThat(login.configOverride()).isEqualTo(cwd.resolve("c.toml"));
        rejected(new String[]{"--background", "--background"}, new String[]{"--background", "--wat"});
        assertThat(AppArguments.USAGE).contains("--background");
    }

    @Test void pluginFlagsParseOnce() {
        AppArguments plain = parse();
        assertThat(plain.safeMode()).isFalse();
        assertThat(plain.pluginDir()).isNull();
        AppArguments dev = parse("--plugin-dir", "build/../out", "--safe-mode");
        assertThat(dev.pluginDir()).isEqualTo(cwd.resolve("out"));
        assertThat(dev.safeMode()).isTrue();
        rejected(new String[]{"--safe-mode", "--safe-mode"}, new String[]{"--plugin-dir"},
            new String[]{"--plugin-dir", "--help"}, new String[]{"--plugin-dir", ""}, new String[]{"--plugin-dir", "a", "--plugin-dir", "b"});
        assertThat(AppArguments.USAGE).contains("--safe-mode", "--plugin-dir <path>");
    }

    @Test void theStartUpFlagsAreWhatASecondLaunchReportsAndResidencyWantsNone() {
        assertThat(parse().startupFlags()).isEmpty();
        assertThat(parse("notes.txt", "--background").startupFlags()).isEmpty();
        assertThat(parse("--plugin-dir", "p", "--config", "c.toml", "--safe-mode").startupFlags())
            .containsExactly("--safe-mode", "--config", "--plugin-dir");
        assertThat(parse().residencyAllowed()).isTrue();
        assertThat(parse("notes.txt").residencyAllowed()).as("paths are no start-up flag").isTrue();
        assertThat(parse("--safe-mode").residencyAllowed()).isFalse();
        assertThat(parse().withHomeOverride(true).residencyAllowed()).as("another home").isFalse();
        AppArguments overridden = parse("notes.txt", "--replacing", "7").withHomeOverride(true);
        assertThat(overridden.homeOverride()).isTrue();
        assertThat(overridden.paths()).containsExactly(cwd.resolve("notes.txt"));
        assertThat(overridden.replacing()).isEqualTo(OptionalLong.of(7));
        assertThat(parse().withHomeOverride(false)).isEqualTo(parse());
    }

    @Test void noWindowParsesOnceAndNeverWithBackground() {
        assertThat(parse().noWindow()).isFalse();
        assertThat(parse("--no-window").noWindow()).isTrue();
        rejected(new String[]{"--no-window", "--no-window"}, new String[]{"--no-window", "--background"});
        assertThat(AppArguments.USAGE).contains("--no-window");
        assertThat(parse("--no-window").withHomeOverride(true).noWindow()).isTrue();
    }
}
