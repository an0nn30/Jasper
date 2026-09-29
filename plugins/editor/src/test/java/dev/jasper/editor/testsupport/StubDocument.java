package dev.jasper.editor.testsupport;

import dev.jasper.editor.api.Content;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.api.Revision;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import static java.nio.charset.StandardCharsets.UTF_8;

/** A document for tests: its read returns {@code "text\n"} at once, or fails when told to. */
public final class StubDocument implements EditorDocument {
    private final String key, name, location;
    private final Optional<String> host;
    private IOException failure;
    /** How many times the document was read. */
    public int reads;

    /**
     * @param key the document key
     * @param name the tab title
     * @param location the full location
     * @param host the host, or null for a local document
     */
    public StubDocument(String key, String name, String location, String host) {
        this.key = Objects.requireNonNull(key); this.name = Objects.requireNonNull(name);
        this.location = Objects.requireNonNull(location); this.host = Optional.ofNullable(host);
    }

    /** @return this document, whose reads now fail with {@code failure} */
    public StubDocument failing(IOException failure) { this.failure = failure; return this; }

    @Override public String key() { return key; }
    @Override public String name() { return name; }
    @Override public String location() { return location; }
    @Override public Optional<String> host() { return host; }

    @Override public CompletableFuture<Content> read(long limitBytes) {
        reads++;
        if (failure != null) return CompletableFuture.failedFuture(failure);
        byte[] bytes = "text\n".getBytes(UTF_8);
        return CompletableFuture.completedFuture(new Content(bytes, new Revision(1_000, bytes.length)));
    }

    @Override public CompletableFuture<Optional<Revision>> revision() {
        return CompletableFuture.completedFuture(Optional.of(new Revision(1_000, 5)));
    }

    @Override public CompletableFuture<Revision> write(byte[] content) {
        return CompletableFuture.completedFuture(new Revision(2_000, content.length));
    }
}
