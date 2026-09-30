package dev.jasper.app.themestore;

import com.formdev.flatlaf.json.Json;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The JetBrains Marketplace's public plugin API, for Theme-tagged plugins: search, details, the latest release, icons and
 * downloads. Every method blocks; call them on a worker, never the EDT. Requests carry the search text and nothing about
 * the user. Redirects are followed only to https JetBrains hosts, and every body is capped.
 */
public final class MarketplaceClient implements AutoCloseable {
    /** The Marketplace. */
    public static final URI DEFAULT_BASE = URI.create("https://plugins.jetbrains.com");
    /** The largest download accepted. */
    public static final long MAX_DOWNLOAD = 50L << 20;
    /** Listings asked for per search page. */
    public static final int PAGE_SIZE = 20;
    private static final int MAX_JSON = 4 << 20;
    private static final int MAX_ICON = 512 << 10;
    private static final int MAX_REDIRECTS = 5;

    /** One plugin in a search result. */
    public record Listing(int id, String xmlId, String name, String preview, long downloads, double rating, boolean freemium,
                          String iconPath, String vendor, String link) { }

    /** One page of search results; {@code nextOffset} asks for the page after it. */
    public record Page(List<Listing> listings, int total, int nextOffset) {
        public Page { listings = List.copyOf(listings); }
        /** Whether the Marketplace has more results after this page. */
        public boolean more() { return nextOffset < total; }
    }

    /** A plugin's page: its description is the Marketplace's HTML. */
    public record Details(int id, String xmlId, String name, String descriptionHtml, String vendor, String link) { }

    /** A plugin release: {@code file} is its path under {@code /files/}. */
    public record Release(long updateId, String version, String file) { }

    /** A failed call: offline, a timeout, an HTTP error, a refused redirect or a response Jasper cannot read. */
    public static final class MarketplaceException extends Exception {
        MarketplaceException(String message) { super(message); }
        MarketplaceException(String message, Throwable cause) { super(message, cause); }
    }

    private final URI base;
    private final Predicate<URI> trusted;
    private final long maxDownload;
    private final HttpClient http;

    public MarketplaceClient() { this(DEFAULT_BASE, MarketplaceClient::jetbrains, MAX_DOWNLOAD); }

    MarketplaceClient(URI base, Predicate<URI> trusted, long maxDownload) {
        this.base = Objects.requireNonNull(base);
        this.trusted = Objects.requireNonNull(trusted);
        this.maxDownload = maxDownload;
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /** Theme-tagged plugins matching {@code query} (the most downloaded when blank), from {@code offset}; paid ones are left out. */
    public Page search(String query, int offset) throws MarketplaceException {
        String text = query == null ? "" : query.strip();
        int from = Math.max(0, offset);
        String path = "/api/searchPlugins?tags=Theme&max=" + PAGE_SIZE + "&offset=" + from
            + (text.isEmpty() ? "&orderBy=downloads" : "&search=" + URLEncoder.encode(text, StandardCharsets.UTF_8));
        Object root = parse(get(path, MAX_JSON), "a search result");
        if (!(root instanceof Map<?, ?> map) || !(map.get("plugins") instanceof List<?> plugins))
            throw new MarketplaceException("The Marketplace sent a search result Jasper cannot read.");
        var listings = new ArrayList<Listing>();
        for (Object item : plugins) {
            if (!(item instanceof Map<?, ?> plugin) || "PAID".equals(plugin.get("pricingModel"))) continue;
            // One malformed listing is left out; the rest of the page still shows.
            try { listings.add(listing(plugin)); } catch (IllegalArgumentException malformed) { }
        }
        int total;
        try { total = map.get("total") == null ? from + plugins.size() : (int) number(map.get("total")); }
        catch (IllegalArgumentException malformed) { total = from + plugins.size(); }
        return new Page(listings, total, from + PAGE_SIZE);
    }

    /** The plugin's page. */
    public Details details(int id) throws MarketplaceException {
        Object root = parse(get("/api/plugins/" + id, MAX_JSON), "plugin details");
        if (!(root instanceof Map<?, ?> json)) throw new MarketplaceException("The Marketplace sent plugin details Jasper cannot read.");
        try {
            String vendor = json.get("vendor") instanceof Map<?, ?> v ? text(v, "publicName", text(v, "name", ""))
                : text(json, "vendor", "");
            return new Details(id, required(json, "xmlId"), required(json, "name"), text(json, "description", ""), vendor, text(json, "link", ""));
        } catch (IllegalArgumentException malformed) {
            throw new MarketplaceException("The Marketplace sent plugin details Jasper cannot read.", malformed);
        }
    }

    /** The plugin's newest release, or empty when it has none. */
    public Optional<Release> latest(int id) throws MarketplaceException {
        Object root = parse(get("/api/plugins/" + id + "/updates?size=1", MAX_JSON), "a release list");
        if (!(root instanceof List<?> list)) throw new MarketplaceException("The Marketplace sent a release list Jasper cannot read.");
        if (list.isEmpty()) return Optional.empty();
        try {
            if (!(list.getFirst() instanceof Map<?, ?> first)) throw new IllegalArgumentException("not an object");
            return Optional.of(new Release((long) number(first.get("id")), required(first, "version"), required(first, "file")));
        } catch (IllegalArgumentException malformed) {
            throw new MarketplaceException("The Marketplace sent a release Jasper cannot read.", malformed);
        }
    }

    /** An icon's bytes (usually SVG); {@code path} is a listing's {@code iconPath}. */
    public byte[] icon(String path) throws MarketplaceException {
        if (!path.startsWith("/") || path.contains("..")) throw new MarketplaceException("Not a Marketplace icon: " + path);
        return get(path, MAX_ICON);
    }

    /** Streams the release's file to {@code target}, which it creates or replaces; the caller deletes it on failure. */
    public void download(Release release, Path target) throws MarketplaceException {
        String file = release.file();
        if (!file.matches("[A-Za-z0-9._+/-]+") || file.contains("..") || file.startsWith("/"))
            throw new MarketplaceException("The Marketplace named a file Jasper will not download: " + file);
        var response = open("/files/" + file);
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[64 << 10];
            long total = 0;
            for (int read; (read = in.read(buffer)) != -1; ) {
                total += read;
                if (total > maxDownload)
                    throw new MarketplaceException("The download is larger than " + (maxDownload >> 20) + " MB; Jasper stopped it.");
                out.write(buffer, 0, read);
            }
        } catch (IOException failure) {
            throw new MarketplaceException("The download failed: " + message(failure), failure);
        }
    }

    @Override public void close() { http.close(); }

    /** Whether {@code uri} is https on jetbrains.com or one of its subdomains. */
    static boolean jetbrains(URI uri) {
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null) return false;
        String lower = host.toLowerCase(Locale.ROOT);
        return lower.equals("jetbrains.com") || lower.endsWith(".jetbrains.com");
    }

    private byte[] get(String path, int limit) throws MarketplaceException {
        var response = open(path);
        try (InputStream in = response.body()) {
            byte[] bytes = in.readNBytes(limit + 1);
            if (bytes.length > limit) throw new MarketplaceException("The Marketplace sent more than Jasper expected.");
            return bytes;
        } catch (IOException failure) {
            throw new MarketplaceException("The Marketplace stopped answering: " + message(failure), failure);
        }
    }

    /** A 200 response for {@code path}, following at most five trusted redirects. */
    private HttpResponse<InputStream> open(String path) throws MarketplaceException {
        URI uri = base.resolve(path);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            HttpResponse<InputStream> response;
            try {
                var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).header("User-Agent", "Jasper").GET().build();
                response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (IOException failure) {
                throw new MarketplaceException("Could not reach the JetBrains Marketplace: " + message(failure), failure);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new MarketplaceException("The request was interrupted.", interrupted);
            }
            int status = response.statusCode();
            if (status / 100 == 3) {
                discard(response);
                URI current = uri;
                URI next = response.headers().firstValue("Location").map(current::resolve).orElse(null);
                if (next == null || !trusted.test(next))
                    throw new MarketplaceException("The Marketplace redirected to " + (next == null ? "nowhere" : next.getHost())
                        + ", which Jasper does not trust.");
                uri = next;
                continue;
            }
            if (status != 200) {
                discard(response);
                throw new MarketplaceException("The Marketplace answered HTTP " + status + ".");
            }
            return response;
        }
        throw new MarketplaceException("The Marketplace redirected too many times.");
    }

    private static void discard(HttpResponse<InputStream> response) {
        try { response.body().close(); } catch (IOException ignored) { }
    }

    private static Object parse(byte[] bytes, String what) throws MarketplaceException {
        try { return Json.parse(new StringReader(new String(bytes, StandardCharsets.UTF_8))); }
        catch (Exception malformed) {
            throw new MarketplaceException("The Marketplace sent " + what + " Jasper cannot read.", malformed);
        }
    }

    private static Listing listing(Map<?, ?> json) {
        String vendor = json.get("vendor") instanceof Map<?, ?> v ? text(v, "name", "") : "";
        return new Listing((int) number(json.get("id")), required(json, "xmlId"), required(json, "name"), text(json, "preview", ""),
            json.get("downloads") == null ? 0 : (long) number(json.get("downloads")),
            json.get("rating") == null ? 0 : number(json.get("rating")),
            "FREEMIUM".equals(json.get("pricingModel")), text(json, "icon", ""), vendor, text(json, "link", ""));
    }

    /** A JSON number; FlatLaf's parser hands numbers over as strings. */
    private static double number(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof String text) {
            try { return Double.parseDouble(text); } catch (NumberFormatException malformed) { }
        }
        throw new IllegalArgumentException("not a number: " + value);
    }

    private static String required(Map<?, ?> json, String key) {
        if (json.get(key) instanceof String text && !text.isBlank()) return text;
        throw new IllegalArgumentException("missing " + key);
    }

    private static String text(Map<?, ?> json, String key, String fallback) {
        return json.get(key) instanceof String text ? text : fallback;
    }

    private static String message(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
