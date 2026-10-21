package dev.jasper.remote.edit;

import dev.jasper.editor.api.Content;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.api.Revision;
import dev.jasper.remote.client.ConnectionIdentity;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A remote document reopened by its key before Remote has read its saved hosts (workspace tabs spec 13): Jasper restores
 * its windows while they load in the background. It finds its route's saved hosts when it is first read, checked or
 * written, and is that {@link RemoteDocument} from then on; a host that is no longer saved fails those operations. The
 * Editor calls it on the UI thread.
 */
final class SavedRemoteDocument implements EditorDocument {
    private final String key;
    private final RemoteDocument.ParsedKey parsed;
    private final Function<RemoteDocument.ParsedKey, Optional<ConnectionIdentity>> identity;
    private final Function<ConnectionIdentity, RemoteDocument> document;
    private final Supplier<CompletableFuture<Void>> hostsLoaded;
    private final Executor ui;
    private RemoteDocument resolved;

    /**
     * @param key the recorded key
     * @param parsed the key parsed
     * @param identity the key's route with the saved hosts as they are now; empty while a host is not known
     * @param document the document on that route
     * @param hostsLoaded completes once the saved hosts have been read
     * @param ui the UI thread
     */
    SavedRemoteDocument(String key, RemoteDocument.ParsedKey parsed, Function<RemoteDocument.ParsedKey, Optional<ConnectionIdentity>> identity,
                        Function<ConnectionIdentity, RemoteDocument> document, Supplier<CompletableFuture<Void>> hostsLoaded, Executor ui) {
        this.key = Objects.requireNonNull(key); this.parsed = Objects.requireNonNull(parsed);
        this.identity = Objects.requireNonNull(identity); this.document = Objects.requireNonNull(document);
        this.hostsLoaded = Objects.requireNonNull(hostsLoaded); this.ui = Objects.requireNonNull(ui);
    }

    @Override public String key() { return key; }
    @Override public String name() { return parsed.path().equals("/") ? parsed.path() : parsed.path().substring(parsed.path().lastIndexOf('/') + 1); }
    @Override public String location() { return now().map(RemoteDocument::location).orElse(parsed.path()); }
    /** The saved host's name once it is known; until then "a remote host", as the Editor's "notes.md on a remote host" reads. */
    @Override public Optional<String> host() { return now().map(RemoteDocument::host).orElse(Optional.of("a remote host")); }

    @Override public CompletableFuture<Content> read(long limitBytes) { return use(found -> found.read(limitBytes)); }
    @Override public CompletableFuture<Content> readQuietly(long limitBytes) { return use(found -> found.readQuietly(limitBytes)); }
    @Override public CompletableFuture<Optional<Revision>> revision() { return use(RemoteDocument::revision); }
    @Override public CompletableFuture<Revision> write(byte[] content) { return use(found -> found.write(content)); }

    /** {@code operation} on the document once its hosts are known: at once when they are, else after they load. */
    private <T> CompletableFuture<T> use(Function<RemoteDocument, CompletableFuture<T>> operation) {
        Optional<RemoteDocument> found = now();
        if (found.isPresent()) return operation.apply(found.get());
        return hostsLoaded.get().handle((done, failure) -> (Void) null).thenComposeAsync(ignored -> now().map(operation)
            .orElseGet(() -> CompletableFuture.failedFuture(new IOException("Its host is no longer saved in Remote"))), ui);
    }

    /** The document, once every host on its route is known. */
    private Optional<RemoteDocument> now() {
        if (resolved == null) identity.apply(parsed).map(document).ifPresent(found -> resolved = found);
        return Optional.ofNullable(resolved);
    }
}
