package dev.jasper.fileserver.engine.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.jasper.fileserver.engine.ServerAddresses;
import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.IsFolderException;
import dev.jasper.fileserver.share.PathRefusedException;
import dev.jasper.fileserver.share.ReadOnlyShareException;
import dev.jasper.fileserver.share.ShareEntry;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/** Answers every request of one HTTP server from its share. Runs on the engine's worker threads. */
final class FileHandler implements HttpHandler {
    static final int BUFFER = 64 * 1024;
    private final ShareRoot share;
    private final ServerEvents events;
    private final HostCheck hosts;

    FileHandler(ShareRoot share, ServerEvents events, HostCheck hosts) { this.share = share; this.events = events; this.hosts = hosts; }

    static String remote(HttpExchange exchange) {
        InetSocketAddress address = exchange.getRemoteAddress();
        return address == null || address.getAddress() == null ? "" : ServerAddresses.hostText(address.getAddress());
    }

    @Override public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            try {
                dispatch(exchange);
            } catch (PathRefusedException | ReadOnlyShareException refused) {
                text(exchange, 403, refused.getMessage());
            } catch (NoSuchFileException missing) {
                text(exchange, 404, "Not found");
            } catch (IsFolderException | FileAlreadyExistsException | DirectoryNotEmptyException conflict) {
                text(exchange, 409, conflict instanceof DirectoryNotEmptyException ? "The folder is not empty" : conflict.getMessage());
            } catch (IllegalArgumentException bad) {
                text(exchange, 400, bad.getMessage());
            } catch (IOException failure) {
                events.error(remote(exchange), failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
                text(exchange, 500, "The server could not complete the request");
            }
        }
    }

    private void dispatch(HttpExchange exchange) throws IOException {
        if (!hosts.allows(exchange.getRequestHeaders().getFirst("Host"))) {
            text(exchange, 421, "Use this server's address, such as its IP address");
            return;
        }
        String raw = exchange.getRequestURI().getRawPath();
        if (raw == null || !raw.startsWith("/")) throw new IllegalArgumentException("A bad path");
        String path = HttpPaths.decode(raw);
        switch (exchange.getRequestMethod()) {
            case "GET" -> get(exchange, raw, path, true);
            case "HEAD" -> get(exchange, raw, path, false);
            default -> {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                text(exchange, 405, "Method not allowed");
            }
        }
    }

    private void get(HttpExchange exchange, String raw, String path, boolean body) throws IOException {
        ShareEntry entry = share.attributes(path);
        if (entry.directory()) {
            if (!raw.endsWith("/")) {
                exchange.getResponseHeaders().set("Location", raw + "/");
                send(exchange, 301, -1);
                return;
            }
            listing(exchange, path, body);
            return;
        }
        file(exchange, path, entry, body);
    }

    private void listing(HttpExchange exchange, String path, boolean body) throws IOException {
        byte[] page = Listing.render(ShareRoot.segments(path), share.list(path), ZoneId.systemDefault()).getBytes(StandardCharsets.UTF_8);
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/html; charset=utf-8");
        headers.set("Content-Security-Policy", Listing.POLICY);
        headers.set("Cache-Control", "no-store");
        headers.set("Referrer-Policy", "no-referrer");
        if (!body) { send(exchange, 200, -1); return; }
        send(exchange, 200, page.length);
        try (OutputStream out = exchange.getResponseBody()) { out.write(page); }
    }

    private void file(HttpExchange exchange, String path, ShareEntry entry, boolean body) throws IOException {
        long size = entry.size();
        var headers = exchange.getResponseHeaders();
        String type = URLConnection.getFileNameMap().getContentTypeFor(entry.name());
        headers.set("Content-Type", type == null ? "application/octet-stream" : type);
        headers.set("Content-Disposition", HttpPaths.contentDisposition(entry.name()));
        headers.set("Accept-Ranges", "bytes");
        headers.set("Last-Modified", DateTimeFormatter.RFC_1123_DATE_TIME.format(entry.modified().atZone(ZoneOffset.UTC)));
        Optional<Range> range;
        try { range = Range.parse(exchange.getRequestHeaders().getFirst("Range"), size); }
        catch (Range.Unsatisfiable unsatisfiable) {
            headers.set("Content-Range", "bytes */" + size);
            send(exchange, 416, -1);
            return;
        }
        long start = range.map(Range::start).orElse(0L);
        long length = range.map(Range::length).orElse(size);
        int code = range.isPresent() ? 206 : 200;
        range.ifPresent(r -> headers.set("Content-Range", "bytes " + r.start() + "-" + (r.start() + r.length() - 1) + "/" + size));
        if (!body || length == 0) { send(exchange, code, -1); return; }
        try (SeekableByteChannel channel = share.openRead(path)) {
            channel.position(start);
            send(exchange, code, length);
            var transfer = events.transfer(remote(exchange), ServerEvent.Direction.DOWNLOAD, display(path), length, exchange::close);
            boolean complete = false;
            try (OutputStream out = exchange.getResponseBody()) {
                var buffer = ByteBuffer.allocate(BUFFER);
                long remaining = length;
                while (remaining > 0) {
                    buffer.clear().limit((int) Math.min(buffer.capacity(), remaining));
                    int read = channel.read(buffer);
                    if (read < 0) throw new IOException("The file shrank while it was sent");
                    out.write(buffer.array(), 0, read);
                    remaining -= read;
                    transfer.add(read);
                }
                complete = true;
            } finally {
                transfer.finish(complete);
            }
        }
    }

    /** The share path as the activity log shows it, such as "/docs/a.txt". */
    static String display(String path) throws PathRefusedException {
        return "/" + String.join("/", ShareRoot.segments(path));
    }

    static void send(HttpExchange exchange, int code, long length) throws IOException {
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(code, length);
    }

    /** A short plain-text answer; does nothing once a response has started. */
    static void text(HttpExchange exchange, int code, String message) throws IOException {
        if (exchange.getResponseCode() != -1) return;
        byte[] body = (message + "\n").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        send(exchange, code, body.length);
        try (OutputStream out = exchange.getResponseBody()) { out.write(body); }
    }
}
