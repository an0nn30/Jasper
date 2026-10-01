package dev.jasper.fileserver.engine.http;

import dev.jasper.fileserver.Ports;
import dev.jasper.fileserver.TestClock;
import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.IOException;
import java.net.ConnectException;
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
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpEngineReadTest {
    @TempDir Path temp;
    Path shared;
    final List<ServerEvent> events = new CopyOnWriteArrayList<>();
    final LoginThrottle throttle = new LoginThrottle(new TestClock());
    final HttpClient client = HttpClient.newHttpClient();
    HttpEngine engine;

    @BeforeEach void share() throws IOException {
        shared = Files.createDirectories(temp.resolve("shared"));
        Files.createDirectories(shared.resolve("docs"));
        Files.writeString(shared.resolve("docs/a.txt"), "hello");
        Files.writeString(shared.resolve("x & <y>.txt"), "odd");
        Files.writeString(temp.resolve("outside.txt"), "secret");
    }

    @AfterEach void stop() { if (engine != null) engine.stop(); }

    HttpEngine start(ResolvedLogin login) throws Exception {
        var definition = ServerDefinition.create("docs", Protocol.HTTP, shared);
        definition = new ServerDefinition(definition.id(), definition.name(), definition.protocol(), definition.folder(), definition.bind(),
            Ports.free(), definition.passiveRange(), false, definition.login(), List.of(), false, false, false);
        engine = new HttpEngine(definition, new ShareRoot(shared, true), login, new ServerEvents(Clock.systemUTC(), events::add), throttle, List::of);
        engine.start();
        return engine;
    }

    HttpResponse<String> get(String path, String... headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + engine.port() + path));
        if (headers.length > 0) request.headers(headers);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    String raw(String request) throws IOException {
        try (var socket = new Socket(InetAddress.getLoopbackAddress(), engine.port())) {
            socket.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    @Test void listsFoldersWithEscapedNamesAndSafeHeaders() throws Exception {
        start(ResolvedLogin.anonymous());
        var response = get("/");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("x &amp; &lt;y&gt;.txt", "href=\"/x%20%26%20%3Cy%3E.txt\"", "href=\"/docs/\"")
            .doesNotContain("<y>").doesNotContain("enctype");
        assertThat(response.body().indexOf("docs/")).as("folders first").isLessThan(response.body().indexOf("x &amp;"));
        assertThat(response.headers().firstValue("Content-Security-Policy")).get().asString().contains("default-src 'none'");
        assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        var redirect = get("/docs");
        assertThat(redirect.statusCode()).isEqualTo(301);
        assertThat(redirect.headers().firstValue("Location")).contains("/docs/");
        assertThat(get("/docs/").body()).contains("href=\"/\"", "a.txt");
    }

    @Test void sendsFilesAsAttachmentsWithRanges() throws Exception {
        start(ResolvedLogin.anonymous());
        var whole = get("/docs/a.txt");
        assertThat(whole.statusCode()).isEqualTo(200);
        assertThat(whole.body()).isEqualTo("hello");
        assertThat(whole.headers().firstValue("Content-Disposition")).get().asString().startsWith("attachment;");
        assertThat(whole.headers().firstValue("Accept-Ranges")).contains("bytes");
        var part = get("/docs/a.txt", "Range", "bytes=1-3");
        assertThat(part.statusCode()).isEqualTo(206);
        assertThat(part.body()).isEqualTo("ell");
        assertThat(part.headers().firstValue("Content-Range")).contains("bytes 1-3/5");
        assertThat(get("/docs/a.txt", "Range", "bytes=-2").body()).isEqualTo("lo");
        var unsatisfiable = get("/docs/a.txt", "Range", "bytes=9-");
        assertThat(unsatisfiable.statusCode()).isEqualTo(416);
        assertThat(unsatisfiable.headers().firstValue("Content-Range")).contains("bytes */5");
        var head = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + engine.port() + "/docs/a.txt"))
            .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(head.statusCode()).isEqualTo(200);
        assertThat(head.body()).isEmpty();
        assertThat(events).filteredOn(e -> e instanceof ServerEvent.Transferred).anySatisfy(e -> {
            var done = (ServerEvent.Transferred) e;
            assertThat(done.direction()).isEqualTo(ServerEvent.Direction.DOWNLOAD);
            assertThat(done.path()).isEqualTo("/docs/a.txt");
            assertThat(done.bytes()).isEqualTo(5);
            assertThat(done.complete()).isTrue();
        });
    }

    @Test void refusesMissingEscapingAndForeignRequests() throws Exception {
        start(ResolvedLogin.anonymous());
        assertThat(get("/missing.txt").statusCode()).isEqualTo(404);
        String host = "Host: 127.0.0.1:" + engine.port() + "\r\nConnection: close\r\n\r\n";
        assertThat(raw("GET /../outside.txt HTTP/1.1\r\n" + host)).startsWith("HTTP/1.1 403");
        assertThat(raw("GET /%2e%2e/outside.txt HTTP/1.1\r\n" + host)).startsWith("HTTP/1.1 403");
        assertThat(raw("GET /bad%zz HTTP/1.1\r\n" + host)).startsWith("HTTP/1.1 400");
        assertThat(raw("GET / HTTP/1.1\r\nHost: evil.example\r\nConnection: close\r\n\r\n")).startsWith("HTTP/1.1 421");
        assertThat(raw("GET / HTTP/1.1\r\nHost: localhost:" + engine.port() + "\r\nConnection: close\r\n\r\n")).startsWith("HTTP/1.1 200");
        var patch = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + engine.port() + "/new.txt"))
            .method("PATCH", HttpRequest.BodyPublishers.ofString("x")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(patch.statusCode()).isEqualTo(405);
        assertThat(patch.headers().firstValue("Allow")).contains("GET, HEAD");
    }

    @Test void basicAuthChecksTheLoginThroughTheThrottle() throws Exception {
        start(ResolvedLogin.password("me", "pw".toCharArray()));
        String good = "Basic " + Base64.getEncoder().encodeToString("me:pw".getBytes(StandardCharsets.UTF_8));
        String bad = "Basic " + Base64.getEncoder().encodeToString("me:nope".getBytes(StandardCharsets.UTF_8));
        var challenge = get("/");
        assertThat(challenge.statusCode()).isEqualTo(401);
        assertThat(challenge.headers().firstValue("WWW-Authenticate")).get().asString().startsWith("Basic realm=\"docs\"");
        assertThat(get("/", "Authorization", good).statusCode()).isEqualTo(200);
        assertThat(get("/docs/a.txt", "Authorization", good).body()).isEqualTo("hello");
        assertThat(events).filteredOn(e -> e instanceof ServerEvent.LoginSucceeded).as("logged once per client and user").hasSize(1);
        assertThat(get("/", "Authorization", bad).statusCode()).isEqualTo(401);
        var tooSoon = get("/", "Authorization", good);
        assertThat(tooSoon.statusCode()).as("the right password is not even checked during the delay").isEqualTo(429);
        assertThat(tooSoon.headers().firstValue("Retry-After")).contains("1");
        assertThat(tooSoon.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(events).filteredOn(e -> e instanceof ServerEvent.LoginFailed).singleElement()
            .satisfies(e -> assertThat(((ServerEvent.LoginFailed) e).user()).isEqualTo("me"));
        for (int i = 0; i < 9; i++) throttle.failed("127.0.0.1");
        var lockedOut = get("/", "Authorization", good);
        assertThat(lockedOut.statusCode()).as("locked out").isEqualTo(429);
        assertThat(lockedOut.headers().firstValue("Retry-After")).isPresent();
        assertThat(get("/", "Authorization", good).statusCode()).as("a correct guess does not lift the lockout").isEqualTo(429);
    }

    @Test void aForeignHostIsRefusedBeforeCredentialsAreChecked() throws Exception {
        start(ResolvedLogin.password("me", "pw".toCharArray()));
        String good = Base64.getEncoder().encodeToString("me:pw".getBytes(StandardCharsets.UTF_8));
        String answer = raw("GET / HTTP/1.1\r\nHost: evil.example\r\nAuthorization: Basic " + good + "\r\nConnection: close\r\n\r\n");
        assertThat(answer).startsWith("HTTP/1.1 421");
        assertThat(events).isEmpty();
    }

    @Test void aBusyPortFailsWithAClearMessageAndStopCloses() throws Exception {
        start(ResolvedLogin.anonymous());
        int port = engine.port();
        var definition = ServerDefinition.create("again", Protocol.HTTP, shared);
        definition = new ServerDefinition(definition.id(), definition.name(), definition.protocol(), definition.folder(), definition.bind(),
            port, definition.passiveRange(), false, definition.login(), List.of(), false, false, false);
        var second = new HttpEngine(definition, new ShareRoot(shared, true), ResolvedLogin.anonymous(), new ServerEvents(Clock.systemUTC(), e -> { }), throttle, List::of);
        assertThatThrownBy(second::start).isInstanceOf(EngineFailure.class).hasMessage("Port " + port + " is already in use on 127.0.0.1");
        assertThat(engine.addresses()).containsExactly(URI.create("http://127.0.0.1:" + port + "/"));
        engine.stop();
        engine.stop();
        assertThat(engine.addresses()).isEmpty();
        assertThatThrownBy(() -> new Socket(InetAddress.getLoopbackAddress(), port).close()).isInstanceOf(ConnectException.class);
    }
}
