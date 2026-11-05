package dev.jasper.browser.engine;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The Markdown previews' origins, at {@code jasper-preview://<token>/}: one for each preview, its token 128 random bits,
 * so no page can name another's. Each origin serves a fixed shell, Jasper's {@code preview.css} and {@code preview.js},
 * the bundled Mermaid, the preview's latest document and its current images, and nothing else. Every answer goes out
 * under {@link #POLICY}. This registry is plain Java, so the plugin's start holds it without loading Chromium, and it
 * reads nothing and makes no random source until it is first used; {@link PreviewSchemeFactory} is Chromium's side of
 * the scheme and loads only once Chromium starts.
 */
public final class PreviewScheme {
    /** The scheme's name. */
    public static final String NAME = "jasper-preview";
    /**
     * The content security policy of every answer: only the origin's own two scripts run, and nothing reaches past the
     * origin. Inline styles are allowed because Mermaid writes {@code <style>} into its diagrams.
     */
    public static final String POLICY = "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self'; "
        + "connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";
    /** The answer to a request for nothing an origin serves: 404 and an empty body. */
    static final Answer NOT_FOUND = new Answer(404, "text/plain", new byte[0]);
    private static final System.Logger LOG = System.getLogger(PreviewScheme.class.getName());
    private static final String PREFIX = NAME + "://";
    private static final Pattern TOKEN = Pattern.compile("[0-9a-f]{32}");
    // A meta tag cannot carry frame-ancestors, which the header does. The shell's own id holds an upper-case letter, which
    // no heading id the Editor writes holds (plan 9's N-F18).
    private static final byte[] SHELL = ("<!doctype html><html><head><meta http-equiv=\"Content-Security-Policy\" content=\""
        + POLICY.replace("; frame-ancestors 'none'", "") + "\"><meta charset=\"utf-8\"><meta name=\"color-scheme\" content=\"light dark\">"
        + "<link rel=\"stylesheet\" href=\"/preview.css\"><script src=\"/preview.js\"></script></head>"
        + "<body><main id=\"Jasper-content\" class=\"markdown-body\"></main></body></html>").getBytes(StandardCharsets.UTF_8);
    // The plugin's files every origin serves, by path, with their resource under dev/jasper/browser/ and their type.
    private static final Map<String, File> FILES = Map.of("/preview.css", new File("preview/preview.css", "text/css"),
        "/preview.js", new File("preview/preview.js", "text/javascript"),
        "/mermaid.min.js", new File("third_party/mermaid.min.js", "text/javascript"));
    private final Function<String, byte[]> resources;
    private final Map<String, Origin> origins = new ConcurrentHashMap<>();
    // Each file read on its first request, then held, absent or not.
    private final Map<String, Optional<byte[]>> held = new ConcurrentHashMap<>();
    // Made on the first open: the plugin's start makes none.
    private SecureRandom random;

    /** The previews' origins, serving the plugin's own files. */
    public PreviewScheme() {
        this(PreviewScheme::resource);
    }

    /**
     * As the public constructor, with the plugin's files read from {@code resources}.
     *
     * @param resources a file's bytes by its resource name under {@code dev/jasper/browser/}; null when there is none
     */
    PreviewScheme(Function<String, byte[]> resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    /**
     * A new origin, showing nothing until its documents are set. UI thread.
     *
     * @return the origin, which its preview closes
     */
    public Origin open() {
        while (true) {
            var origin = new Origin(token());
            if (origins.putIfAbsent(origin.token, origin) == null) return origin;
        }
    }

    /**
     * Whether {@code url} is on the origin of {@code token}: {@code jasper-preview://<token>/…}, in any case of the
     * scheme, with no user info or port. Any thread.
     *
     * @param url an address a preview's page asks for
     * @param token an origin's token
     * @return true only for an address on that origin
     */
    public static boolean allows(String url, String token) {
        return token != null && TOKEN.matcher(token).matches() && token.equals(token(url));
    }

    /**
     * What a request gets: the shell at {@code /}, the plugin's {@code /preview.css}, {@code /preview.js} and
     * {@code /mermaid.min.js}, the origin's latest document at {@code /document/<revision>} and its current images at
     * {@code /image/<key>.png}, each with 200 and its type. Anything else, an origin not open, and a method other than
     * GET get {@link #NOT_FOUND}. Chromium writes the address as {@code jasper-preview://<token>/<path>}, the scheme being
     * standard. Chromium's IO thread.
     *
     * @param method the request's method
     * @param url the request's address
     * @return the answer
     */
    public Answer answer(String method, String url) {
        String token = token(url);
        Origin origin = token == null ? null : origins.get(token);
        if (!"GET".equals(method) || origin == null) return NOT_FOUND;
        String path = url.substring(PREFIX.length() + token.length());
        if (path.equals("/")) return new Answer(200, "text/html", SHELL);
        File file = FILES.get(path);
        if (file != null) return held(file.resource()).map(body -> new Answer(200, file.mimeType(), body)).orElse(NOT_FOUND);
        if (path.startsWith("/document/")) return origin.document(number(path.substring("/document/".length())));
        if (path.startsWith("/image/") && path.endsWith(".png"))
            return origin.image(number(path.substring("/image/".length(), path.length() - ".png".length())));
        return NOT_FOUND;
    }

    private synchronized String token() {
        if (random == null) random = new SecureRandom();
        var bytes = new byte[16];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private Optional<byte[]> held(String resource) {
        return held.computeIfAbsent(resource, name -> Optional.ofNullable(resources.apply(name)));
    }

    /** The token of an address on some origin of the scheme, as {@link #allows} reads it; null for any other address. */
    private static String token(String url) {
        if (url == null || !url.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) return null;
        int slash = url.indexOf('/', PREFIX.length());
        if (slash < 0) return null;
        String token = url.substring(PREFIX.length(), slash);
        return TOKEN.matcher(token).matches() ? token : null;
    }

    /** A non-negative decimal number as {@code Long.toString} writes it; -1 for anything else. */
    private static long number(String text) {
        if (text.isEmpty() || text.length() > 19 || (text.length() > 1 && text.charAt(0) == '0')) return -1;
        for (int at = 0; at < text.length(); at++) if (text.charAt(at) < '0' || text.charAt(at) > '9') return -1;
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException tooLarge) {
            return -1;
        }
    }

    private static byte[] resource(String name) {
        try (InputStream in = PreviewScheme.class.getResourceAsStream("/dev/jasper/browser/" + name)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException failure) {
            LOG.log(System.Logger.Level.WARNING, "The Markdown preview's " + name + " could not be read", failure);
            return null;
        }
    }

    /**
     * One request's answer.
     *
     * @param status the HTTP status
     * @param mimeType the body's type
     * @param body the body, which nobody changes
     */
    public record Answer(int status, String mimeType, byte[] body) { }

    /** A file every origin serves: its resource under {@code dev/jasper/browser/}, and its type. */
    private record File(String resource, String mimeType) { }

    /**
     * One preview's origin: what its page may load. Its preview sets the document and images, on any one thread at a
     * time; Chromium's IO thread reads them. Once closed it serves nothing, and setting anything does nothing.
     */
    public final class Origin implements AutoCloseable {
        private final String token;
        private final Map<Integer, byte[]> images = new ConcurrentHashMap<>();
        private volatile Document document;
        private volatile boolean closed;

        private Origin(String token) { this.token = token; }

        /**
         * The origin's token.
         *
         * @return 32 lowercase hexadecimal digits
         */
        public String token() { return token; }

        /**
         * The shell's address, which the preview's page loads first.
         *
         * @return {@code jasper-preview://<token>/}
         */
        public URI shell() { return URI.create(PREFIX + token + "/"); }

        /**
         * Sets the document {@code /document/<revision>} serves, in place of the one before.
         *
         * @param revision the document's revision
         * @param html the document, served as UTF-8
         * @throws IllegalArgumentException for a negative revision
         */
        public void document(long revision, String html) {
            if (revision < 0) throw new IllegalArgumentException("A preview document's revision must not be negative: " + revision);
            byte[] body = html.getBytes(StandardCharsets.UTF_8);
            if (!closed) document = new Document(revision, body);
        }

        /**
         * Sets the image {@code /image/<key>.png} serves.
         *
         * @param key the image's key
         * @param png the image as PNG, held as given: nobody changes it after
         * @throws IllegalArgumentException for a negative key
         */
        public void image(int key, byte[] png) {
            Objects.requireNonNull(png, "png");
            if (key < 0) throw new IllegalArgumentException("A preview image's key must not be negative: " + key);
            if (!closed) images.put(key, png);
        }

        /** Forgets every image set. */
        public void clearImages() {
            images.clear();
        }

        /** Closes the origin: it serves nothing more, and forgets its document and images. Once, however often called. */
        @Override public void close() {
            closed = true;
            origins.remove(token, this);
            document = null;
            images.clear();
        }

        private Answer document(long revision) {
            Document latest = document;
            return latest != null && revision >= 0 && latest.revision() == revision ? new Answer(200, "text/html", latest.body()) : NOT_FOUND;
        }

        private Answer image(long key) {
            byte[] png = key < 0 || key > Integer.MAX_VALUE ? null : images.get((int) key);
            return png == null ? NOT_FOUND : new Answer(200, "image/png", png);
        }
    }

    /** A document and its revision. */
    private record Document(long revision, byte[] body) { }
}
