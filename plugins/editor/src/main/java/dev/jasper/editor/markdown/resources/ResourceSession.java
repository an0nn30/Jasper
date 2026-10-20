package dev.jasper.editor.markdown.resources;

import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.markdown.MarkdownResult;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/** EDT-owned per-tab cache and consent, scoped to the original provider handle and document identity. */
public final class ResourceSession implements AutoCloseable {
    private final ImageLoader loader;
    private final Supplier<EditorDocument> document;
    private final BiConsumer<Integer, BufferedImage> install;
    private final BiConsumer<Integer, String> placeholder;
    private final Object context = new Object();
    private final Map<Key, Entry> cache = new HashMap<>();
    private EditorDocument identity;
    private MarkdownResult result;
    private long generation;
    private boolean active, closed, external;
    private record Key(Object context, Object identity, Destination destination) { }
    private static final class Entry {
        CompletableFuture<ImageLoader.LoadedImage> future;
        ImageLoader.LoadedImage loaded;
        String failure;
    }
    /** Creates a tab resource owner; callbacks execute only on the EDT. A null document denotes an untitled file. */
    public ResourceSession(ImageLoader loader, Supplier<EditorDocument> document,
                           BiConsumer<Integer, BufferedImage> install, BiConsumer<Integer, String> placeholder) {
        this.loader = Objects.requireNonNull(loader); this.document = Objects.requireNonNull(document);
        this.install = Objects.requireNonNull(install); this.placeholder = Objects.requireNonNull(placeholder);
        identity = document.get();
    }
    /** Grants web access for this document identity, including URLs introduced by later edits. */
    public void allowExternal() { edt(); if (!closed) { external = true; refreshCurrent(); } }
    /** @return whether the current result contains web images still awaiting consent */
    public boolean needsConsent() { return !external && result != null && result.images().stream().anyMatch(image -> Destinations.parse(image.destination()) instanceof Destination.Web); }
    /** @return whether an image could be retried */
    public boolean hasFailures() { return cache.values().stream().anyMatch(entry -> entry.failure != null); }
    /** Attaches resource identifiers only after the matching rendered document has been installed. */
    public void refresh(MarkdownResult value) {
        edt(); if (closed) return;
        if (identity != document.get()) identityChanged();
        generation++;
        clearViews(); result = Objects.requireNonNull(value);
        var wanted = new HashSet<Key>();
        for (var image : result.images()) wanted.add(key(Destinations.parse(image.destination())));
        for (var iterator = cache.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            if (!wanted.contains(entry.getKey()) || entry.getValue().loaded == null && entry.getValue().failure == null) {
                release(entry.getValue()); iterator.remove();
            }
        }
        refreshCurrent();
    }
    /** Suspends hidden views and releases their pixels before visible tabs need the shared budget. */
    public void active(boolean value) {
        edt(); if (closed || active == value) return;
        active = value;
        if (!active) { generation++; clearViews(); clearCache(); }
        else refreshCurrent();
    }
    /** Clears failed loads while retaining successful images. */
    public void retryFailed() {
        edt(); if (closed) return;
        cache.entrySet().removeIf(entry -> entry.getValue().failure != null);
        refreshCurrent();
    }
    /** Invalidates asynchronous identifiers during an edit while retaining successful cache entries. */
    public void invalidate() {
        edt(); if (closed) return;
        generation++; clearViews(); result = null;
        for (var iterator = cache.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next().getValue();
            if (entry.loaded == null && entry.failure == null) { release(entry); iterator.remove(); }
        }
    }
    /** A successful text reload also refreshes provider-relative images, even when the text is unchanged. */
    public void reload() {
        edt(); if (closed) return;
        generation++; clearViews();
        for (var iterator = cache.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            if (entry.getKey().destination instanceof Destination.Relative || entry.getValue().loaded == null) {
                release(entry.getValue()); iterator.remove();
            }
        }
        result = null;
    }
    /** Save As changes the base and revokes the old identity's consent. */
    public void identityChanged() {
        edt(); if (closed) return;
        generation++; clearViews(); clearCache(); result = null; external = false; identity = document.get();
    }
    private Key key(Destination destination) {
        // The opaque owner key and original handle prevent collisions between identical labels on distinct routes.
        if (destination instanceof Destination.Relative relative) destination = new Destination.Relative(relative.path(), Optional.empty());
        return new Key(context, identity, destination);
    }
    private void refreshCurrent() {
        if (!active || closed || result == null) return;
        for (var image : result.images()) {
            var destination = Destinations.parse(image.destination());
            if (destination instanceof Destination.Web && !external) { placeholder(image.id(), "Load external images to display"); continue; }
            var key = key(destination);
            var entry = cache.get(key);
            if (entry == null) {
                entry = new Entry(); cache.put(key, entry);
                try {
                    if (destination instanceof Destination.Relative relative) {
                        if (identity == null) throw new IllegalArgumentException("Save this document to load relative images");
                        var handle = identity.resolveRelative(relative.path()).orElseThrow(() -> new IllegalArgumentException("Provider cannot resolve relative images"));
                        entry.future = loader.load(handle);
                    } else if (destination instanceof Destination.Web web) entry.future = loader.load(web.uri());
                    else throw new IllegalArgumentException(destination instanceof Destination.Unsupported unsupported ? unsupported.reason() : "An image needs a file or web URL");
                    var pending = entry; long expected = generation; var owner = identity;
                    entry.future.whenComplete((loaded, error) -> SwingUtilities.invokeLater(() -> {
                        if (closed || !active || generation != expected || identity != owner || document.get() != owner || cache.get(key) != pending) {
                            if (loaded != null) loaded.close(); return;
                        }
                        pending.future = null;
                        if (error == null) pending.loaded = loaded;
                        else pending.failure = reason(error);
                        present(key, pending);
                    }));
                } catch (RuntimeException error) { entry.failure = reason(error); }
            }
            present(image.id(), entry);
        }
    }
    private static String reason(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        String message = error.getMessage();
        return message == null || message.isBlank() ? "Could not load image" : message;
    }
    private void present(Key key, Entry entry) {
        if (result != null) for (var image : result.images()) if (key.equals(key(Destinations.parse(image.destination())))) present(image.id(), entry);
    }
    private void present(int id, Entry entry) {
        if (entry.loaded != null) { placeholder(id, ""); install.accept(id, entry.loaded.image()); }
        else placeholder(id, entry.failure == null ? "Loading image" : entry.failure);
    }
    private void placeholder(int id, String reason) {
        if (!reason.isEmpty() && result != null) {
            var image = result.images().stream().filter(value -> value.id() == id).findFirst().orElse(null);
            if (image != null && image.alt().isBlank()) {
                String path = image.destination().split("[?#]", 2)[0];
                String name = path.substring(path.lastIndexOf('/') + 1);
                if (!name.isBlank()) reason = name + ": " + reason;
            }
        }
        placeholder.accept(id, reason);
    }
    private void clearViews() { if (result != null) for (var image : result.images()) install.accept(image.id(), null); }
    private void clearCache() { for (var entry : cache.values()) release(entry); cache.clear(); }
    private static void release(Entry entry) { if (entry.future != null) entry.future.cancel(true); if (entry.loaded != null) entry.loaded.close(); }
    private static void edt() { if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Resources must be changed on EDT"); }
    @Override public void close() { edt(); if (closed) return; generation++; clearViews(); clearCache(); result = null; closed = true; }
}
