package dev.jasper.app.themestore;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.jasper.app.appearance.ThemeFixtures;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** A local HTTP server standing in for plugins.jetbrains.com: it answers by path and records every request. */
final class FakeMarketplace implements AutoCloseable {
    interface Handler { void handle(HttpExchange exchange) throws IOException; }

    private final HttpServer server;
    private final Map<String, Handler> routes = new ConcurrentHashMap<>();
    /** Each request's path and query, in order. */
    final List<String> requests = new CopyOnWriteArrayList<>();

    FakeMarketplace() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                requests.add(exchange.getRequestURI().toString());
                Handler handler = routes.get(exchange.getRequestURI().getPath());
                if (handler == null) exchange.sendResponseHeaders(404, -1);
                else handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    URI base() { return URI.create("http://127.0.0.1:" + server.getAddress().getPort()); }

    MarketplaceClient client() { return client(MarketplaceClient.MAX_DOWNLOAD); }

    MarketplaceClient client(long maxDownload) {
        return new MarketplaceClient(base(), uri -> "127.0.0.1".equals(uri.getHost()), maxDownload);
    }

    FakeMarketplace json(String path, String body) { return bytes(path, 200, body.getBytes(UTF_8)); }

    FakeMarketplace bytes(String path, int status, byte[] body) {
        routes.put(path, exchange -> {
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        return this;
    }

    FakeMarketplace redirect(String path, String location) {
        routes.put(path, exchange -> {
            exchange.getResponseHeaders().add("Location", location);
            exchange.sendResponseHeaders(302, -1);
        });
        return this;
    }

    /** Serves plugin {@code id} providing one dark theme, {@code themeName}, whose latest release is {@code version}. */
    FakeMarketplace theme(Path scratch, int id, String xmlId, String name, String themeName, String version) throws IOException {
        Path jar = ThemeFixtures.plugin(scratch.resolve(id + "-" + version + ".jar"), themeName, true, "#282a36", "282a36");
        String file = id + "/99/" + xmlId + "-" + version + ".jar";
        json("/api/searchPlugins", search(1, plugin(id, xmlId, name, "FREE")));
        json("/api/plugins/" + id, details(id, xmlId, name, "<p>A dark theme</p><img src='https://example.invalid/x.png'>"));
        json("/api/plugins/" + id + "/updates", release(99, version, file));
        bytes("/files/" + file, 200, Files.readAllBytes(jar));
        bytes("/files/" + id + "/icon/default.svg", 200,
            "<svg xmlns='http://www.w3.org/2000/svg' width='16' height='16'/>".getBytes(UTF_8));
        return this;
    }

    static String search(int total, String... plugins) {
        return "{\"plugins\":[" + String.join(",", plugins) + "],\"total\":" + total + "}";
    }

    /** A search listing shaped like the real one; numbers are plain JSON numbers here, strings are accepted too. */
    static String plugin(int id, String xmlId, String name, String pricing) {
        return "{\"id\":" + id + ",\"xmlId\":\"" + xmlId + "\",\"link\":\"/plugin/" + id + "\",\"name\":\"" + name
            + "\",\"preview\":\"About " + name + "\",\"downloads\":1234567,\"pricingModel\":\"" + pricing
            + "\",\"icon\":\"/files/" + id + "/icon/default.svg\",\"rating\":4.42,"
            + "\"vendor\":{\"name\":\"Vendor " + id + "\",\"isVerified\":false}}";
    }

    static String release(long updateId, String version, String file) {
        return "[{\"id\":" + updateId + ",\"version\":\"" + version + "\",\"file\":\"" + file + "\"}]";
    }

    /** Plugin details; like the real API, the vendor's {@code name} is an id and {@code publicName} is what people read. */
    static String details(int id, String xmlId, String name, String html) {
        return "{\"id\":" + id + ",\"name\":\"" + name + "\",\"xmlId\":\"" + xmlId + "\",\"link\":\"/plugin/" + id
            + "\",\"description\":\"" + html.replace("\"", "\\\"") + "\","
            + "\"vendor\":{\"name\":\"uuid-" + id + "\",\"publicName\":\"Vendor " + id + "\"}}";
    }

    @Override public void close() { server.stop(0); }
}
