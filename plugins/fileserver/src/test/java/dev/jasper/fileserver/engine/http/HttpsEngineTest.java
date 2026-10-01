package dev.jasper.fileserver.engine.http;

import dev.jasper.fileserver.Ports;
import dev.jasper.fileserver.TestClock;
import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.engine.tls.TlsMaterial;
import dev.jasper.fileserver.share.ShareRoot;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Clock;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpsEngineTest {
    @TempDir Path temp;
    HttpEngine engine;

    @AfterEach void stop() { if (engine != null) engine.stop(); }

    ServerDefinition definition(Path shared, int port, boolean tls) {
        var base = ServerDefinition.create("secure", Protocol.HTTP, shared);
        return new ServerDefinition(base.id(), base.name(), Protocol.HTTP, shared, base.bind(), port, base.passiveRange(), false,
            base.login(), List.of(), tls, false, false);
    }

    static HttpClient trusting(TlsMaterial tls) throws Exception {
        KeyStore trusted = KeyStore.getInstance("PKCS12");
        trusted.load(null, null);
        trusted.setCertificateEntry("server", tls.certificate());
        var managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init(trusted);
        var context = SSLContext.getInstance("TLS");
        context.init(null, managers.getTrustManagers(), null);
        return HttpClient.newBuilder().sslContext(context).build();
    }

    @Test void servesOverHttpsWithTheSharedCertificate() throws Exception {
        Path shared = Files.createDirectories(temp.resolve("shared"));
        Files.writeString(shared.resolve("a.txt"), "secret-free");
        var tls = new TlsMaterial(temp.resolve("tls"), Clock.systemUTC(), () -> "test-host");
        int port = Ports.free();
        engine = new HttpEngine(definition(shared, port, true), new ShareRoot(shared, true), ResolvedLogin.anonymous(),
            new ServerEvents(Clock.systemUTC(), e -> { }), new LoginThrottle(new TestClock()), List::of, tls);
        engine.start();
        assertThat(engine.addresses()).containsExactly(URI.create("https://127.0.0.1:" + port + "/"));
        var response = trusting(tls).send(HttpRequest.newBuilder(URI.create("https://127.0.0.1:" + port + "/a.txt")).build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(response.body()).isEqualTo("secret-free");
        assertThatThrownBy(() -> HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("https://127.0.0.1:" + port + "/a.txt")).build(),
            HttpResponse.BodyHandlers.ofString())).as("an untrusted certificate is refused by default").isInstanceOf(SSLException.class);
    }

    @Test void aServerThatFailsBeforeBindingLeavesThePortFree() throws Exception {
        Path shared = Files.createDirectories(temp.resolve("shared"));
        Files.createDirectories(temp.resolve("tls"));
        Files.writeString(temp.resolve("tls/server.p12"), "broken");
        Files.writeString(temp.resolve("tls/password"), "broken");
        var tls = new TlsMaterial(temp.resolve("tls"), Clock.systemUTC(), () -> "");
        int port = Ports.free();
        engine = new HttpEngine(definition(shared, port, true), new ShareRoot(shared, true), ResolvedLogin.anonymous(),
            new ServerEvents(Clock.systemUTC(), e -> { }), new LoginThrottle(new TestClock()), List::of, tls);
        assertThatThrownBy(engine::start).isInstanceOf(EngineFailure.class)
            .hasMessage("The TLS certificate in " + temp.resolve("tls") + " can't be read");
        try (var socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            assertThat(socket.isBound()).as("the port was never taken").isTrue();
        }
        assertThat(engine.addresses()).isEmpty();
    }

    @Test void aTlsDefinitionNeedsTheMaterial() throws Exception {
        Path shared = Files.createDirectories(temp.resolve("shared"));
        engine = new HttpEngine(definition(shared, Ports.free(), true), new ShareRoot(shared, true), ResolvedLogin.anonymous(),
            new ServerEvents(Clock.systemUTC(), e -> { }), new LoginThrottle(new TestClock()), List::of);
        assertThatThrownBy(engine::start).isInstanceOf(IllegalStateException.class).hasMessage("HTTPS needs the TLS material");
    }
}
