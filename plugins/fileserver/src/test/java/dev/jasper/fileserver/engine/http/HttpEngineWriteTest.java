package dev.jasper.fileserver.engine.http;

import dev.jasper.fileserver.Ports;
import dev.jasper.fileserver.TestClock;
import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class HttpEngineWriteTest {
    @TempDir Path temp;
    Path shared;
    final List<ServerEvent> events = new CopyOnWriteArrayList<>();
    final HttpClient client = HttpClient.newHttpClient();
    HttpEngine engine;

    @BeforeEach void share() throws IOException {
        shared = Files.createDirectories(temp.resolve("shared"));
        Files.createDirectories(shared.resolve("docs"));
        Files.writeString(shared.resolve("docs/a.txt"), "hello");
        Files.writeString(shared.resolve("docs/keep.txt"), "keep");
    }

    @AfterEach void stop() { if (engine != null) engine.stop(); }

    void start(boolean writable) throws Exception {
        var base = ServerDefinition.create("drop", Protocol.HTTP, shared);
        var definition = new ServerDefinition(base.id(), base.name(), base.protocol(), base.folder(), base.bind(), Ports.free(),
            base.passiveRange(), writable, base.login(), List.of(), false, false, false);
        engine = new HttpEngine(definition, new ShareRoot(shared, !writable), ResolvedLogin.anonymous(),
            new ServerEvents(Clock.systemUTC(), events::add), new LoginThrottle(new TestClock()), List::of);
        engine.start();
    }

    HttpResponse<String> send(String method, String path, HttpRequest.BodyPublisher body, String... headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + engine.port() + path)).method(method, body);
        if (headers.length > 0) request.headers(headers);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    String raw(String head, String body) throws IOException {
        try (var socket = new Socket(InetAddress.getLoopbackAddress(), engine.port())) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            String request = head + "Host: 127.0.0.1:" + engine.port() + "\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().write(bytes);
            socket.getOutputStream().flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    @Test void putCreatesThenReplacesAndReportsTheUpload() throws Exception {
        start(true);
        assertThat(send("PUT", "/docs/new.txt", HttpRequest.BodyPublishers.ofString("abc")).statusCode()).isEqualTo(201);
        assertThat(send("PUT", "/docs/new.txt", HttpRequest.BodyPublishers.ofString("abcd")).statusCode()).isEqualTo(204);
        assertThat(shared.resolve("docs/new.txt")).hasContent("abcd");
        assertThat(send("PUT", "/docs", HttpRequest.BodyPublishers.ofString("x")).statusCode()).isEqualTo(409);
        assertThat(events).filteredOn(e -> e instanceof ServerEvent.Transferred).extracting(e -> ((ServerEvent.Transferred) e).bytes())
            .containsExactlyInAnyOrder(3L, 4L);
        assertThat(events).filteredOn(e -> e instanceof ServerEvent.Transferred)
            .allSatisfy(e -> assertThat(((ServerEvent.Transferred) e).direction()).isEqualTo(ServerEvent.Direction.UPLOAD));
    }

    @Test void anUploadThatDropsMidBodyLeavesTheOriginalAndNoPartialFile() throws Exception {
        start(true);
        try (var socket = new Socket(InetAddress.getLoopbackAddress(), engine.port())) {
            String head = "PUT /docs/a.txt HTTP/1.1\r\nHost: 127.0.0.1:" + engine.port() + "\r\nContent-Length: 100\r\n\r\n";
            socket.getOutputStream().write(head.getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().write("0123456789".getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
        }
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!events.stream().anyMatch(e -> e instanceof ServerEvent.Transferred t && !t.complete()) && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(events).anySatisfy(e -> assertThat(e).isInstanceOfSatisfying(ServerEvent.Transferred.class, t -> assertThat(t.complete()).isFalse()));
        assertThat(shared.resolve("docs/a.txt")).hasContent("hello");
        List<String> names;
        long settled = System.nanoTime() + 5_000_000_000L;
        do {
            try (var listing = Files.list(shared.resolve("docs"))) { names = listing.map(p -> p.getFileName().toString()).sorted().toList(); }
            if (names.equals(List.of("a.txt", "keep.txt"))) break;
            Thread.sleep(10);
        } while (System.nanoTime() < settled);
        assertThat(names).as("the temporary file is gone once the request ends").containsExactly("a.txt", "keep.txt");
    }

    @Test void aCancelledDownloadEndsAsStoppedNotAsAnError() throws Exception {
        start(true);
        Files.write(shared.resolve("big.bin"), new byte[32 * 1024 * 1024]);
        try (var socket = new Socket(InetAddress.getLoopbackAddress(), engine.port())) {
            String head = "GET /big.bin HTTP/1.1\r\nHost: 127.0.0.1:" + engine.port() + "\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(head.getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
            assertThat(socket.getInputStream().readNBytes(1000)).hasSize(1000);
        }
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!events.stream().anyMatch(e -> e instanceof ServerEvent.Transferred) && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(events).anySatisfy(e -> assertThat(e).isInstanceOfSatisfying(ServerEvent.Transferred.class, t -> assertThat(t.complete()).isFalse()));
        Thread.sleep(300); // an error would be reported after the transfer ends; give it time to (not) arrive
        assertThat(events).noneMatch(e -> e instanceof ServerEvent.Failed);
    }

    @Test void deleteRemovesFilesAndRefusesFullFolders() throws Exception {
        start(true);
        assertThat(send("DELETE", "/docs/a.txt", HttpRequest.BodyPublishers.noBody()).statusCode()).isEqualTo(204);
        assertThat(shared.resolve("docs/a.txt")).doesNotExist();
        assertThat(events).anySatisfy(e -> assertThat(e).isEqualTo(new ServerEvent.Changed(e.at(), "127.0.0.1", "deleted /docs/a.txt")));
        assertThat(send("DELETE", "/docs", HttpRequest.BodyPublishers.noBody()).statusCode()).isEqualTo(409);
        assertThat(send("DELETE", "/missing.txt", HttpRequest.BodyPublishers.noBody()).statusCode()).isEqualTo(404);
    }

    @Test void theListingsFormsUploadAndDelete() throws Exception {
        start(true);
        assertThat(send("GET", "/docs/", HttpRequest.BodyPublishers.noBody()).body())
            .contains("enctype=\"multipart/form-data\"", "name=\"_method\" value=\"DELETE\"", "name=\"name\" value=\"a.txt\"");
        var form = new ByteArrayOutputStream();
        form.writeBytes(("--XyZ\r\nContent-Disposition: form-data; name=\"file\"; filename=\"one.txt\"\r\n\r\nfirst\r\n"
            + "--XyZ\r\nContent-Disposition: form-data; name=\"file\"; filename=\"C:\\fakepath\\two.bin\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        form.writeBytes(new byte[]{0, 13, 10, 45, 45, 88, 121, 0});
        form.writeBytes("\r\n--XyZ--\r\n".getBytes(StandardCharsets.UTF_8));
        var upload = send("POST", "/docs/", HttpRequest.BodyPublishers.ofByteArray(form.toByteArray()), "Content-Type", "multipart/form-data; boundary=XyZ");
        assertThat(upload.statusCode()).isEqualTo(303);
        assertThat(upload.headers().firstValue("Location")).contains("/docs/");
        assertThat(shared.resolve("docs/one.txt")).hasContent("first");
        assertThat(Files.readAllBytes(shared.resolve("docs/two.bin"))).containsExactly(0, 13, 10, 45, 45, 88, 121, 0);
        var delete = send("POST", "/docs", HttpRequest.BodyPublishers.ofString("_method=DELETE&name=a.txt"), "Content-Type", "application/x-www-form-urlencoded");
        assertThat(delete.statusCode()).isEqualTo(303);
        assertThat(delete.headers().firstValue("Location")).contains("/docs/");
        assertThat(shared.resolve("docs/a.txt")).doesNotExist();
        assertThat(send("POST", "/docs/keep.txt", HttpRequest.BodyPublishers.ofString("_method=DELETE&name=x"), "Content-Type", "application/x-www-form-urlencoded").statusCode())
            .as("forms go to folders").isEqualTo(400);
        assertThat(send("POST", "/docs/", HttpRequest.BodyPublishers.ofString("--XyZ??"), "Content-Type", "multipart/form-data; boundary=XyZ").statusCode())
            .isEqualTo(400);
    }

    @Test void requestsFromOtherSitesAreRefused() throws Exception {
        start(true);
        String form = "Content-Type: application/x-www-form-urlencoded\r\n";
        assertThat(raw("POST /docs/ HTTP/1.1\r\nOrigin: http://evil.example\r\n" + form, "_method=DELETE&name=a.txt")).startsWith("HTTP/1.1 403");
        assertThat(shared.resolve("docs/a.txt")).exists();
        assertThat(raw("POST /docs/ HTTP/1.1\r\nOrigin: http://127.0.0.1:" + engine.port() + "\r\n" + form, "_method=DELETE&name=a.txt")).startsWith("HTTP/1.1 303");
        assertThat(shared.resolve("docs/a.txt")).doesNotExist();
    }

    @Test void putAndDeleteFromOtherSitesAreRefused() throws Exception {
        start(true);
        assertThat(raw("PUT /docs/new.txt HTTP/1.1\r\nOrigin: http://evil.example\r\n", "abc")).startsWith("HTTP/1.1 403");
        assertThat(shared.resolve("docs/new.txt")).doesNotExist();
        assertThat(raw("DELETE /docs/a.txt HTTP/1.1\r\nOrigin: http://evil.example\r\n", "")).startsWith("HTTP/1.1 403");
        assertThat(shared.resolve("docs/a.txt")).exists();
    }

    @Test void aNullOriginIsRefused() throws Exception {
        start(true);
        String form = "Content-Type: application/x-www-form-urlencoded\r\n";
        assertThat(raw("POST /docs/ HTTP/1.1\r\nOrigin: null\r\n" + form, "_method=DELETE&name=a.txt")).startsWith("HTTP/1.1 403");
        assertThat(shared.resolve("docs/a.txt")).exists();
    }

    @Test void theListingKeepsItsOriginOnFormPosts() throws Exception {
        start(true);
        // A form post from a document with Referrer-Policy no-referrer carries "Origin: null", which the origin check refuses.
        assertThat(send("GET", "/docs/", HttpRequest.BodyPublishers.noBody()).headers().firstValue("Referrer-Policy")).contains("same-origin");
    }

    @Test void aReadOnlyShareRefusesEveryWriteAndOffersNoForms() throws Exception {
        start(false);
        assertThat(send("PUT", "/docs/new.txt", HttpRequest.BodyPublishers.ofString("x")).statusCode()).isEqualTo(403);
        assertThat(send("DELETE", "/docs/a.txt", HttpRequest.BodyPublishers.noBody()).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/docs/", HttpRequest.BodyPublishers.ofString("_method=DELETE&name=a.txt"), "Content-Type", "application/x-www-form-urlencoded").statusCode())
            .isEqualTo(403);
        assertThat(shared.resolve("docs/a.txt")).exists();
        assertThat(send("GET", "/docs/", HttpRequest.BodyPublishers.noBody()).body()).doesNotContain("<form");
    }
}
