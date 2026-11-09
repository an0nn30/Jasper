package dev.jasper.browser.adblock;

import dev.jasper.browser.json.StrictJson;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A filter list's cache in the plugin's data folder, under {@code adblock/}: {@code rules.json}, the compiled list as
 * {@link ContentRules#encode} writes it, and {@code state.json}, its address ({@code url}), when it was downloaded
 * ({@code updated}) and how many rules it kept ({@code rules}) and skipped ({@code skipped}). Each file is written to a
 * temporary file of its owner's alone beside it, then moved over the old one at once, so no reader sees half of one,
 * and the data folder is made its owner's alone before the cache first reads or writes there (plan 5's H37, F11).
 * Clear Website Data leaves the folder alone. Making one does no I/O. Background executor; one call at a time.
 */
final class ListCache {
    /** The list's folder in the data folder, and its two files there. */
    static final String FOLDER = "adblock", RULES = "rules.json", STATE = "state.json";
    private static final int STATE_LIMIT = 64 * 1024;

    /**
     * What {@code state.json} records of the list cached.
     *
     * @param url its address, as it was downloaded from
     * @param updated when it was downloaded
     */
    record Cached(String url, Instant updated) {
        /** Requires every part. */
        Cached {
            Objects.requireNonNull(url, "url");
            Objects.requireNonNull(updated, "updated");
        }
    }

    /**
     * The cache read.
     *
     * @param state its state
     * @param rules its compiled list's text, for {@link BlockerJson#read}
     */
    record Contents(Cached state, String rules) { }

    private final Path folder;
    private final Runnable restrict;

    /**
     * The cache of the plugin's data folder.
     *
     * @param dataDir the plugin's data folder, which holds the list's folder
     * @param restrict makes the data folder its owner's alone; run before each read and write there
     */
    ListCache(Path dataDir, Runnable restrict) {
        folder = Objects.requireNonNull(dataDir, "dataDir").resolve(FOLDER);
        this.restrict = Objects.requireNonNull(restrict, "restrict");
    }

    /**
     * Reads the cache, the data folder first made its owner's alone.
     *
     * @return the cache; empty when nothing is cached
     * @throws IOException when it cannot be read, its state is not one {@link #write} writes, or its rules are missing
     */
    Optional<Contents> read() throws IOException {
        restrict.run();
        Path file = folder.resolve(STATE);
        if (!Files.exists(file)) return Optional.empty();
        if (Files.size(file) > STATE_LIMIT) throw new IOException("The filter list's state is larger than " + STATE_LIMIT + " bytes");
        Map<String, Object> state = StrictJson.object(Files.readString(file, StandardCharsets.UTF_8), 1)
            .orElseThrow(() -> new IOException("The filter list's state is not a JSON object"));
        if (!(state.get("url") instanceof String address) || !(state.get("updated") instanceof String updated))
            throw new IOException("The filter list's state has no url or updated");
        Instant when;
        try {
            when = Instant.parse(updated);
        } catch (DateTimeParseException unreadable) {
            throw new IOException("The filter list's state has no time it was updated", unreadable);
        }
        Path rules = folder.resolve(RULES);
        if (Files.size(rules) > 4L * ListDownload.MAX_BYTES)
            throw new IOException("The filter list's cache is larger than " + 4 * (ListDownload.MAX_BYTES >> 20) + " MiB");
        return Optional.of(new Contents(new Cached(address, when), Files.readString(rules, StandardCharsets.UTF_8)));
    }

    /**
     * Caches a list: its compiled rules, then its state, each through a temporary file of its owner's alone, the data
     * folder first made its owner's alone. A list whose rules would not read back within {@link BlockerJson}'s bound, as
     * escapes can lengthen a hostile list's selectors, is refused before anything is written.
     *
     * @param address where it was downloaded from
     * @param updated when
     * @param compiled the list compiled
     * @throws IOException when it cannot be written, or a {@link ListDownload.TooLarge} for rules too long to read back
     */
    void write(URI address, Instant updated, ContentRules.Compiled compiled) throws IOException {
        String rules = BlockerJson.write(ContentRules.encode(compiled.rules()));
        if (rules.length() > BlockerJson.MAX_LENGTH) throw new ListDownload.TooLarge();
        restrict.run();
        Files.createDirectories(folder);
        replace(RULES, rules);
        var state = new LinkedHashMap<String, Object>();
        state.put("url", address.toString());
        state.put("updated", updated.toString());
        state.put("rules", (long) compiled.rules().size());
        state.put("skipped", (long) compiled.skipped());
        replace(STATE, StrictJson.write(state));
    }

    /** Writes {@code text} as the list's file {@code name}, through a temporary file moved over it at once. */
    private void replace(String name, String text) throws IOException {
        // POSIX file systems make a temporary file rw-------.
        Path temporary = Files.createTempFile(folder, name, ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            Files.move(temporary, folder.resolve(name), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
