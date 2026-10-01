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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Answers every request of one HTTP server from its share. Runs on the engine's worker threads. */
final class FileHandler implements HttpHandler {
    static final int BUFFER = 64 * 1024;
    static final int FORM_LIMIT = 64 * 1024;
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
            } catch (Multipart.Malformed malformed) {
                text(exchange, 400, malformed.getMessage());
            } catch (IllegalArgumentException bad) {
                text(exchange, 400, bad.getMessage());
            } catch (IOException failure) {
                // A response already started (a download the client cancelled): the transfer ends as incomplete, no error.
                if (exchange.getResponseCode() != -1) return;
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
            case "PUT", "DELETE", "POST" -> {
                if (!sameOrigin(exchange)) { text(exchange, 403, "Requests from other sites are refused"); return; }
                if (share.readOnly()) throw new ReadOnlyShareException();
                switch (exchange.getRequestMethod()) {
                    case "PUT" -> put(exchange, path);
                    case "DELETE" -> delete(exchange, path);
                    default -> post(exchange, path);
                }
            }
            default -> {
                exchange.getResponseHeaders().set("Allow", share.readOnly() ? "GET, HEAD" : "GET, HEAD, PUT, DELETE, POST");
                text(exchange, 405, "Method not allowed");
            }
        }
    }

    private void get(HttpExchange exchange, String raw, String path, boolean body) throws IOException {
        ShareEntry entry = share.attributes(path);
        if (entry.directory()) {
            if (!raw.endsWith("/")) {
                exchange.getResponseHeaders().set("Location", HttpPaths.href(ShareRoot.segments(path), true));
                send(exchange, 301, -1);
                return;
            }
            listing(exchange, path, body);
            return;
        }
        file(exchange, path, entry, body);
    }

    private void listing(HttpExchange exchange, String path, boolean body) throws IOException {
        byte[] page = Listing.render(ShareRoot.segments(path), share.list(path), !share.readOnly(), ZoneId.systemDefault()).getBytes(StandardCharsets.UTF_8);
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/html; charset=utf-8");
        headers.set("Content-Security-Policy", Listing.POLICY);
        headers.set("Cache-Control", "no-store");
        headers.set("Referrer-Policy", "same-origin"); // the listing's form posts must carry their Origin; no-referrer makes it "null"
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

    /** A browser form from another site carries its Origin; curl and the share's own pages pass. */
    private static boolean sameOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null) return true;
        String host = exchange.getRequestHeaders().getFirst("Host");
        return host != null && (origin.equalsIgnoreCase("http://" + host) || origin.equalsIgnoreCase("https://" + host));
    }

    private void put(HttpExchange exchange, String path) throws IOException {
        boolean existed;
        try { existed = !share.attributes(path).directory(); }
        catch (NoSuchFileException absent) { existed = false; }
        long total = -1;
        String length = exchange.getRequestHeaders().getFirst("Content-Length");
        if (length != null) {
            try { total = Long.parseLong(length.strip()); } catch (NumberFormatException unknown) { total = -1; }
        }
        receive(exchange, exchange.getRequestBody(), path, total);
        send(exchange, existed ? 204 : 201, -1);
    }

    private void delete(HttpExchange exchange, String path) throws IOException {
        share.delete(path);
        events.changed(remote(exchange), "deleted " + display(path));
        send(exchange, 204, -1);
    }

    /** The listing's forms: an upload of any number of files, or a delete of one entry. Answers with the folder. */
    private void post(HttpExchange exchange, String path) throws IOException {
        if (!share.attributes(path).directory()) throw new IllegalArgumentException("Forms are posted to a folder");
        List<String> folder = ShareRoot.segments(path);
        String contentType = Optional.ofNullable(exchange.getRequestHeaders().getFirst("Content-Type")).orElse("");
        String type = contentType.toLowerCase(Locale.ROOT);
        if (type.startsWith("multipart/form-data")) {
            Multipart.read(exchange.getRequestBody(), Multipart.boundary(contentType), (fileName, content) -> {
                String name = Multipart.baseName(fileName);
                if (!name.isEmpty()) receive(exchange, content, child(folder, name), -1);
            });
        } else if (type.startsWith("application/x-www-form-urlencoded")) {
            Map<String, String> form = form(exchange.getRequestBody());
            String name = form.getOrDefault("name", "");
            if (!"DELETE".equals(form.get("_method")) || name.isEmpty()) throw new IllegalArgumentException("This form is not understood");
            String target = child(folder, name);
            share.delete(target);
            events.changed(remote(exchange), "deleted " + display(target));
        } else {
            throw new IllegalArgumentException("This form is not understood");
        }
        exchange.getResponseHeaders().set("Location", HttpPaths.href(folder, true));
        send(exchange, 303, -1);
    }

    /** Receives into a temporary file and replaces the target only once the whole body has arrived. */
    private void receive(HttpExchange exchange, InputStream in, String path, long total) throws IOException {
        try (ShareRoot.Upload upload = share.upload(path)) {
            var transfer = events.transfer(remote(exchange), ServerEvent.Direction.UPLOAD, display(path), total, exchange::close);
            boolean complete = false;
            try {
                byte[] buffer = new byte[BUFFER];
                long received = 0;
                OutputStream out = upload.stream();
                for (int read; (read = in.read(buffer)) >= 0; ) {
                    out.write(buffer, 0, read);
                    received += read;
                    transfer.add(read);
                }
                if (total >= 0 && received != total) throw new IOException("The upload ended before all of its data arrived");
                upload.commit();
                complete = true;
            } finally {
                transfer.finish(complete);
            }
        }
    }

    private static String child(List<String> folder, String name) {
        return "/" + String.join("/", folder) + (folder.isEmpty() ? "" : "/") + name;
    }

    private static Map<String, String> form(InputStream in) throws IOException {
        byte[] bytes = in.readNBytes(FORM_LIMIT + 1);
        if (bytes.length > FORM_LIMIT) throw new IllegalArgumentException("The form is too large");
        var form = new HashMap<String, String>();
        for (String pair : new String(bytes, StandardCharsets.US_ASCII).split("&")) {
            if (pair.isEmpty()) continue;
            int equals = pair.indexOf('=');
            String key = URLDecoder.decode(equals < 0 ? pair : pair.substring(0, equals), StandardCharsets.UTF_8);
            String value = equals < 0 ? "" : URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
            form.putIfAbsent(key, value);
        }
        return form;
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
