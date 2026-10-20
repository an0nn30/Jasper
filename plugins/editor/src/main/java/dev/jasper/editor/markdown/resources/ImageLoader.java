package dev.jasper.editor.markdown.resources;

import dev.jasper.editor.api.EditorDocument;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.swing.SwingUtilities;

/** Four plugin-wide loads, bounded before allocation, with cancellable provider and HTTP reads. */
public final class ImageLoader implements AutoCloseable {
    private static final int ENCODED_LIMIT = 8 * 1024 * 1024;
    private final ImageBudget budget;
    private final Duration deadline;
    private final ExecutorService workers = Executors.newFixedThreadPool(4, Thread.ofPlatform().daemon().name("editor-images-", 0).factory());
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("editor-image-deadline").factory());
    private HttpClient client;
    private final Set<Load> loads = new HashSet<>();
    private boolean closed;

    /** @param budget the shared decoded-pixel budget */
    public ImageLoader(ImageBudget budget) { this(budget, Duration.ofSeconds(20)); }
    ImageLoader(ImageBudget budget, Duration deadline) { this.budget = Objects.requireNonNull(budget); this.deadline = deadline; }

    /** Reads through the original provider; provider entry points always run on the EDT. */
    public CompletableFuture<LoadedImage> load(EditorDocument document) { return enqueue(Objects.requireNonNull(document), null); }
    /** Loads an explicitly consented HTTP(S) image. */
    public CompletableFuture<LoadedImage> load(URI uri) {
        try { return enqueue(null, checkedUri(uri, null)); }
        catch (IllegalArgumentException invalid) { return CompletableFuture.failedFuture(invalid); }
    }
    private synchronized CompletableFuture<LoadedImage> enqueue(EditorDocument document, URI uri) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Image loader closed"));
        var load = new Load(document, uri);
        loads.add(load);
        load.result.whenComplete((value, error) -> { if (load.result.isCancelled()) load.cancel(); });
        load.task = workers.submit(load);
        return load.result;
    }
    private synchronized HttpClient client() {
        if (closed) throw new IllegalStateException("Image loader closed");
        if (client == null) client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();
        return client;
    }
    static URI checkedUri(URI uri, URI previous) {
        if (!(Destinations.parse(uri.toASCIIString()) instanceof Destination.Web) || uri.getRawUserInfo() != null)
            throw new IllegalArgumentException("Unsupported image URL");
        if (previous != null && previous.getScheme().equalsIgnoreCase("https") && !uri.getScheme().equalsIgnoreCase("https"))
            throw new IllegalArgumentException("HTTPS image redirect cannot downgrade to HTTP");
        return uri;
    }
    /** A cache entry owns this image and its reservation; views only borrow its pixels. */
    public static final class LoadedImage implements AutoCloseable {
        private final BufferedImage image;
        private final ImageBudget.Lease lease;
        private LoadedImage(BufferedImage image, ImageBudget.Lease lease) { this.image = image; this.lease = lease; }
        /** @return the decoded first frame */
        public BufferedImage image() { return image; }
        @Override public void close() { lease.close(); }
    }
    private LoadedImage decode(byte[] bytes) throws IOException {
        if (bytes.length > ENCODED_LIMIT) throw new IOException("Image exceeds 8 MiB");
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported or corrupt image");
            var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("png", "jpeg", "jpg", "gif").contains(format)) throw new IOException("Unsupported image format");
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 16_000_000) throw new IOException("Image exceeds 16 million pixels");
                var lease = budget.reserve(width, height).orElseThrow(() -> new IOException("Image memory budget exhausted"));
                try {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Image load cancelled");
                    var parameters = reader.getDefaultReadParam();
                    var raw = reader.getRawImageType(0);
                    // PNG can otherwise retain 16-bit RGB(A), exceeding our four-byte pixel reservation.
                    // Supply the decode destination directly, so no second full-sized image is allocated.
                    if (raw != null && raw.getColorModel().getPixelSize() > 32) {
                        parameters.setDestination(new BufferedImage(width, height,
                            raw.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB));
                    }
                    var image = reader.read(0, parameters);
                    if (image == null) throw new IOException("Corrupt image");
                    return new LoadedImage(image, lease);
                } catch (Throwable error) { lease.close(); throw error; }
            } finally { reader.dispose(); }
        }
    }
    private final class Load implements Runnable {
        final EditorDocument document;
        final URI uri;
        final CompletableFuture<LoadedImage> result = new CompletableFuture<>();
        Future<?> task, request;
        InputStream stream;
        boolean cancelled;
        Load(EditorDocument document, URI uri) { this.document = document; this.uri = uri; }
        synchronized void check() throws InterruptedIOException { if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Image load cancelled"); }
        synchronized void request(Future<?> value) { request = value; if (cancelled) value.cancel(true); }
        synchronized void stream(InputStream value) throws IOException { stream = value; if (cancelled) { value.close(); throw new InterruptedIOException("Image load cancelled"); } }
        void cancel() {
            InputStream body;
            synchronized (this) { cancelled = true; if (request != null) request.cancel(true); if (task != null) task.cancel(true); body = stream; }
            if (body != null) try { body.close(); } catch (IOException ignored) { }
            synchronized (ImageLoader.this) { loads.remove(this); }
        }
        @Override public void run() {
            ScheduledFuture<?> timeout = null;
            try {
                check();
                byte[] bytes;
                if (document != null) {
                    var provider = new CompletableFuture<byte[]>();
                    request(provider);
                    SwingUtilities.invokeLater(() -> {
                        synchronized (this) {
                            if (cancelled) return;
                            try {
                                var read = document.read(ENCODED_LIMIT);
                                request(read);
                                read.whenComplete((content, error) -> { if (error != null) provider.completeExceptionally(error); else provider.complete(content.bytes()); });
                            } catch (RuntimeException error) { provider.completeExceptionally(error); }
                        }
                    });
                    bytes = provider.get();
                } else {
                    timeout = timer.schedule(() -> { result.completeExceptionally(new IOException("Image request timed out")); cancel(); }, deadline.toNanos(), TimeUnit.NANOSECONDS);
                    bytes = fetch();
                }
                check();
                var image = decode(bytes);
                if (!result.complete(image)) image.close();
            } catch (Exception error) {
                if (error instanceof ExecutionException && error.getCause() != null) result.completeExceptionally(error.getCause());
                else result.completeExceptionally(error);
            } finally {
                if (timeout != null) timeout.cancel(false);
                InputStream body;
                synchronized (this) { body = stream; stream = null; request = null; }
                if (body != null) try { body.close(); } catch (IOException ignored) { }
                synchronized (ImageLoader.this) { loads.remove(this); }
            }
        }
        private byte[] fetch() throws Exception {
            URI next = uri;
            for (int redirects = 0;; redirects++) {
                check();
                var response = client().sendAsync(HttpRequest.newBuilder(next).timeout(deadline).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
                request(response);
                var received = response.get();
                try (var body = received.body()) {
                    stream(body);
                    int status = received.statusCode();
                    if (Set.of(301,302,303,307,308).contains(status)) {
                        if (redirects == 3) throw new IOException("Too many image redirects");
                        String location = received.headers().firstValue("Location").orElseThrow(() -> new IOException("Missing image redirect"));
                        next = checkedUri(next.resolve(location), next);
                        continue;
                    }
                    if (status != 200) throw new IOException("Image server returned " + status);
                    if (received.headers().firstValueAsLong("Content-Length").orElse(0) > ENCODED_LIMIT) throw new IOException("Image exceeds 8 MiB");
                    var bytes = new ByteArrayOutputStream();
                    byte[] block = new byte[8192];
                    int count;
                    while ((count = body.read(block)) != -1) {
                        check();
                        if (count > ENCODED_LIMIT - bytes.size()) throw new IOException("Image exceeds 8 MiB");
                        bytes.write(block, 0, count);
                    }
                    return bytes.toByteArray();
                }
            }
        }
    }
    @Override public void close() {
        List<Load> pending;
        synchronized (this) { if (closed) return; closed = true; pending = List.copyOf(loads); }
        for (var load : pending) load.result.cancel(true);
        workers.shutdownNow(); timer.shutdownNow();
        synchronized (this) { if (client != null) client.shutdownNow(); }
    }
}
