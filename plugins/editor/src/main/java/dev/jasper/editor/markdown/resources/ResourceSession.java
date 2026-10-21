package dev.jasper.editor.markdown.resources;

import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.markdown.MarkdownResult;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/** EDT-owned per-tab cache and consent, scoped to the original provider handle and document identity. */
public final class ResourceSession implements AutoCloseable {
    private static final int CHUNK = 128;
    /** One view's borrowed image, or a placeholder reason. */
    public record ImageState(BufferedImage image, String reason) { }
    /** A bounded set of view changes. Clear removes every previously borrowed image before releasing ownership. */
    public record Update(boolean clear, Map<Integer, ImageState> images) {
        /** Copies the updates so deferred consumers cannot observe mutation. */
        public Update { images = Map.copyOf(images); }
    }
    private final ImageLoader loader;
    private final Supplier<EditorDocument> document;
    private final Consumer<Update> install;
    private final Runnable stateChanged;
    private final Object context = new Object();
    private final Map<Key, Entry> cache = new HashMap<>();
    private EditorDocument identity;
    private MarkdownResult result;
    private Snapshot snapshot;
    private long generation;
    private boolean active, closed, external, reportedConsent, reportedFailure;
    private record Key(Object context, Object identity, Destination destination) { }
    private record Item(int id, String name) { }
    private static final class Snapshot {
        final MarkdownResult result;
        final long generation;
        final EditorDocument identity;
        final Map<String, Destination> destinations = new HashMap<>();
        final Map<Key, List<Item>> byKey = new HashMap<>();
        int offset, failures;
        boolean web;
        Snapshot(MarkdownResult result, long generation, EditorDocument identity) {
            this.result = result; this.generation = generation; this.identity = identity;
        }
    }
    private static final class Entry {
        CompletableFuture<ImageLoader.LoadedImage> future;
        ImageLoader.LoadedImage loaded;
        String failure;
    }
    /** Creates a tab resource owner with bounded batch updates and a callback for consent/failure transitions. */
    public ResourceSession(ImageLoader loader, Supplier<EditorDocument> document, Consumer<Update> install, Runnable stateChanged) {
        this.loader = Objects.requireNonNull(loader); this.document = Objects.requireNonNull(document);
        this.install = Objects.requireNonNull(install); this.stateChanged = Objects.requireNonNull(stateChanged);
        identity = document.get();
    }
    /** Compatibility adapter for consumers that receive individual image and placeholder updates. */
    public ResourceSession(ImageLoader loader, Supplier<EditorDocument> document,
                           BiConsumer<Integer, BufferedImage> install, BiConsumer<Integer, String> placeholder) {
        this(loader, document, individual(install, placeholder), () -> {});
    }
    private static Consumer<Update> individual(BiConsumer<Integer, BufferedImage> install, BiConsumer<Integer, String> placeholder) {
        var ids = new HashSet<Integer>();
        return update -> {
            if (update.clear()) { for (int id : ids) install.accept(id, null); ids.clear(); }
            update.images().forEach((id, image) -> {
                ids.add(id); placeholder.accept(id, image.reason());
                if (image.image() != null) install.accept(id, image.image());
            });
        };
    }
    /** Grants web access for this document identity, including URLs introduced by later edits. */
    public void allowExternal() { edt(); if (!closed && !external) { external = true; if (result != null) refresh(result); } }
    /** @return whether current resources include web images awaiting consent */
    public boolean needsConsent() { return !external && snapshot != null && snapshot.web; }
    /** @return whether a current image could be retried */
    public boolean hasFailures() { return snapshot != null && snapshot.failures > 0; }
    /** Attaches resource identifiers only after the matching rendered document has been installed. */
    public void refresh(MarkdownResult value) {
        edt(); if (closed) return;
        if (identity != document.get()) identityChanged();
        reset(); result = Objects.requireNonNull(value);
        if (active) begin();
    }
    /** Suspends hidden views and releases their pixels before visible tabs need the shared budget. */
    public void active(boolean value) {
        edt(); if (closed || active == value) return;
        active = value;
        if (!active) { reset(); clearCache(); }
        else if (result != null) begin();
    }
    /** Clears failed loads while retaining successful images. */
    public void retryFailed() {
        edt(); if (closed) return;
        cache.entrySet().removeIf(entry -> entry.getValue().failure != null);
        if (result != null) refresh(result);
    }
    /** Invalidates asynchronous identifiers during an edit while retaining successful cache entries. */
    public void invalidate() { edt(); if (!closed) { reset(); result = null; } }
    /** A successful text reload also refreshes provider-relative images, even when the text is unchanged. */
    public void reload() {
        edt(); if (closed) return;
        reset(); result = null;
        for (var iterator = cache.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            if (entry.getKey().destination instanceof Destination.Relative || entry.getValue().failure != null) {
                release(entry.getValue()); iterator.remove();
            }
        }
    }
    /** Save As changes the base and revokes the old identity's consent. */
    public void identityChanged() {
        edt(); if (closed) return;
        reset(); clearCache(); result = null; external = false; identity = document.get();
    }
    private void reset() {
        generation++; snapshot = null;
        install.accept(new Update(true, Map.of()));
        for (var iterator = cache.values().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            if (entry.loaded == null && entry.failure == null) { release(entry); iterator.remove(); }
        }
        reportState();
    }
    private void reportState() {
        boolean consent = needsConsent(), failure = hasFailures();
        if (consent == reportedConsent && failure == reportedFailure) return;
        reportedConsent = consent; reportedFailure = failure; stateChanged.run();
    }
    private void begin() {
        snapshot = new Snapshot(result, generation, identity);
        indexChunk(snapshot);
    }
    private boolean current(Snapshot expected) {
        return !closed && active && snapshot == expected && generation == expected.generation
            && identity == expected.identity && document.get() == expected.identity;
    }
    private Key key(Destination destination) {
        if (destination instanceof Destination.Relative relative) destination = new Destination.Relative(relative.path(), Optional.empty());
        return new Key(context, identity, destination);
    }
    private void indexChunk(Snapshot expected) {
        if (!current(expected)) return;
        var updates = new HashMap<Integer, ImageState>();
        int end = Math.min(expected.offset + CHUNK, expected.result.images().size());
        while (expected.offset < end) {
            var image = expected.result.images().get(expected.offset++);
            var destination = expected.destinations.computeIfAbsent(image.destination(), Destinations::parse);
            var key = key(destination);
            var item = new Item(image.id(), filename(image));
            var items = expected.byKey.computeIfAbsent(key, ignored -> new ArrayList<>());
            boolean first = items.isEmpty(); items.add(item);
            if (destination instanceof Destination.Web) expected.web = true;
            if (destination instanceof Destination.Web && !external) updates.put(item.id, state(item, null, "Load external images to display"));
            else {
                var entry = cache.get(key);
                if (entry == null) { entry = start(expected, key); cache.put(key, entry); }
                if (first && entry.failure != null) expected.failures++;
                updates.put(item.id, state(item, entry.loaded, entry.failure));
            }
        }
        if (!updates.isEmpty()) install.accept(new Update(false, updates));
        if (!current(expected)) return;
        reportState();
        if (!current(expected)) return;
        if (expected.offset < expected.result.images().size()) SwingUtilities.invokeLater(() -> indexChunk(expected));
        else {
            for (var iterator = cache.entrySet().iterator(); iterator.hasNext();) {
                var entry = iterator.next();
                if (!expected.byKey.containsKey(entry.getKey())) { release(entry.getValue()); iterator.remove(); }
            }
        }
    }
    private Entry start(Snapshot expected, Key key) {
        var entry = new Entry();
        try {
            if (key.destination instanceof Destination.Relative relative) {
                if (identity == null) throw new IllegalArgumentException("Save this document to load relative images");
                var handle = identity.resolveRelative(relative.path()).orElseThrow(() -> new IllegalArgumentException("Provider cannot resolve relative images"));
                entry.future = loader.load(handle);
            } else if (key.destination instanceof Destination.Web web) entry.future = loader.load(web.uri());
            else throw new IllegalArgumentException(key.destination instanceof Destination.Unsupported unsupported ? unsupported.reason() : "An image needs a file or web URL");
            entry.future.whenComplete((loaded, error) -> SwingUtilities.invokeLater(() -> {
                if (!current(expected) || cache.get(key) != entry) { if (loaded != null) loaded.close(); return; }
                entry.future = null;
                if (error == null) entry.loaded = loaded;
                else { entry.failure = reason(error); expected.failures++; }
                presentChunk(expected, key, entry, 0, expected.byKey.get(key).size());
                reportState();
            }));
        } catch (RuntimeException error) { entry.failure = reason(error); }
        return entry;
    }
    private void presentChunk(Snapshot expected, Key key, Entry entry, int offset, int limit) {
        if (!current(expected) || cache.get(key) != entry) return;
        var items = expected.byKey.get(key);
        int end = Math.min(offset + CHUNK, limit);
        var updates = new HashMap<Integer, ImageState>();
        for (int i = offset; i < end; i++) { var item = items.get(i); updates.put(item.id, state(item, entry.loaded, entry.failure)); }
        if (!updates.isEmpty()) install.accept(new Update(false, updates));
        if (end < limit) SwingUtilities.invokeLater(() -> presentChunk(expected, key, entry, end, limit));
    }
    private static String filename(MarkdownResult.Image image) {
        if (!image.alt().isBlank()) return "";
        String path = image.destination().split("[?#]", 2)[0];
        return path.substring(path.lastIndexOf('/') + 1);
    }
    private static ImageState state(Item item, ImageLoader.LoadedImage loaded, String reason) {
        if (loaded != null) return new ImageState(loaded.image(), "");
        if (reason == null) reason = "Loading image";
        return new ImageState(null, item.name.isBlank() ? reason : item.name + ": " + reason);
    }
    private static String reason(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        String message = error.getMessage();
        return message == null || message.isBlank() ? "Could not load image" : message;
    }
    private void clearCache() { for (var entry : cache.values()) release(entry); cache.clear(); }
    private static void release(Entry entry) {
        if (entry.future != null) { entry.future.cancel(true); entry.future = null; }
        if (entry.loaded != null) { entry.loaded.close(); entry.loaded = null; }
    }
    private static void edt() { if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Resources must be changed on EDT"); }
    @Override public void close() { edt(); if (closed) return; reset(); clearCache(); result = null; closed = true; }
}
