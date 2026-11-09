package dev.jasper.browser.adblock;

import dev.jasper.browser.json.StrictJson;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A filter list's cache in the data folder: the compiled list and its state, each written through a temporary file of
 * its owner's alone and moved over the old one, read back as written, the data folder made its owner's alone before
 * each read and write (F11); a state it did not write is refused, and a list too long to read back is never written.
 */
class ListCacheTest {
    private static final URI ADDRESS = URI.create("https://lists.example/blocker.json");
    private static final Instant UPDATED = Instant.parse("2026-11-09T10:00:00Z");
    @TempDir Path data;
    private final List<Boolean> restricted = new CopyOnWriteArrayList<>();

    @Test void aListWrittenReadsBackWithItsState() throws Exception {
        ListCache cache = cache();
        ContentRules.Compiled compiled = ContentRules.compile(BlockerJson.read(ListServer.LIST));
        cache.write(ADDRESS, UPDATED, compiled);
        assertThat(restricted).as("H37: the data folder is its owner's before the list's folder is made").containsExactly(false);

        Path folder = data.resolve("adblock");
        Map<String, Object> state = StrictJson.object(Files.readString(folder.resolve("state.json")), 1).orElseThrow();
        assertThat(state).containsExactly(Map.entry("url", ADDRESS.toString()), Map.entry("updated", UPDATED.toString()),
            Map.entry("rules", 3L), Map.entry("skipped", 1L));
        try (var files = Files.list(folder)) {
            assertThat(files.map(file -> file.getFileName().toString()).sorted().toList()).as("no temporary file is left")
                .containsExactly("rules.json", "state.json");
        }
        if (Files.getFileAttributeView(folder, PosixFileAttributeView.class) != null)
            for (String name : List.of("rules.json", "state.json"))
                assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(folder.resolve(name)))).as(name).isEqualTo("rw-------");

        ListCache.Contents read = cache.read().orElseThrow();
        assertThat(read.state()).isEqualTo(new ListCache.Cached(ADDRESS.toString(), UPDATED));
        ContentRules.Compiled back = ContentRules.compile(BlockerJson.read(read.rules()));
        assertThat(back.rules()).as("the compiled list, as encode writes it").isEqualTo(compiled.rules());
        assertThat(back.skipped()).isZero();
        assertThat(restricted).as("and before each read").hasSize(2);

        cache.write(URI.create("https://lists.example/other.json"), UPDATED.plusSeconds(60), ContentRules.compile(BlockerJson.read(ListServer.OTHER)));
        assertThat(cache.read().orElseThrow().state()).as("a new list replaces the old")
            .isEqualTo(new ListCache.Cached("https://lists.example/other.json", UPDATED.plusSeconds(60)));
    }

    @Test void nothingCachedReadsAsNothing() throws Exception {
        assertThat(cache().read()).isEmpty();
        assertThat(restricted).as("the folder restricted first").containsExactly(false);
    }

    @Test void aStateTheCacheDidNotWriteIsRefused() throws Exception {
        Path folder = Files.createDirectories(data.resolve("adblock"));
        Files.writeString(folder.resolve("rules.json"), "[]");
        for (String state : List.of("{\"url\": 1}", "[]", "{\"url\": \"https://a.example/\"}",
                "{\"url\": \"https://a.example/\", \"updated\": \"yesterday\"}", "{\"url\": \"https://a.example/\", \"updated\": \"" + UPDATED + "\"",
                "{\"url\": \"" + "x".repeat(70 * 1024) + "\", \"updated\": \"" + UPDATED + "\"}")) {
            Files.writeString(folder.resolve("state.json"), state);
            assertThatThrownBy(() -> cache().read()).as(state.length() > 80 ? "over 64 KiB" : state).isInstanceOf(IOException.class);
        }
        Files.writeString(folder.resolve("state.json"), "{\"url\": \"https://a.example/\", \"updated\": \"" + UPDATED + "\"}");
        Files.delete(folder.resolve("rules.json"));
        assertThatThrownBy(() -> cache().read()).as("a state without its rules").isInstanceOf(NoSuchFileException.class);
    }

    /**
     * Escapes lengthen what a hostile list's selectors write: a list whose rules would not read back within BlockerJson's
     * bound is refused before anything is written, the old cache kept.
     */
    @Test void aListTooLongToReadBackIsNeverWritten() throws Exception {
        ListCache cache = cache();
        cache.write(ADDRESS, UPDATED, ContentRules.compile(BlockerJson.read(ListServer.LIST)));
        String emoji = new String(Character.toChars(0x1F600)).repeat(500);
        var rules = new ArrayList<String>();
        for (int at = 0; at < 6_000; at++)
            rules.add("{\"trigger\": {\"url-filter\": \".*\"}, \"action\": {\"type\": \"css-display-none\", \"selector\": \"." + at + emoji + "\"}}");
        ContentRules.Compiled hostile = ContentRules.compile(BlockerJson.read("[" + String.join(",", rules) + "]"));
        assertThat(hostile.rules()).hasSize(6_000);
        assertThatThrownBy(() -> cache.write(URI.create("https://hostile.example/"), UPDATED.plusSeconds(1), hostile))
            .isInstanceOf(ListDownload.TooLarge.class);
        assertThat(cache.read().orElseThrow().state()).as("the old cache").isEqualTo(new ListCache.Cached(ADDRESS.toString(), UPDATED));
    }

    private ListCache cache() {
        return new ListCache(data, () -> restricted.add(Files.exists(data.resolve("adblock"))));
    }
}
