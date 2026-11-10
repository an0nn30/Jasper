package dev.jasper.browser.tools;

import com.sun.net.httpserver.HttpServer;
import dev.jasper.browser.tools.SavePayload.Kind;
import dev.jasper.browser.tools.SavePayload.Resource;
import dev.jasper.browser.tools.SavePayload.Snapshot;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.testing.FakePluginHost.MarkedDownload;
import dev.jasper.sdk.ui.Platform;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Save Page As's fetches and writes (plan 7's Task 7; rulings F12, F13, F14, F26, D5 and D6; Review Focus 4), against a
 * server on the loopback address alone, the only network these tests reach: every resource of the snapshot fetched once,
 * from Jasper's process, without cookies or {@code Accept-Encoding} and with the page's origin as referrer; stylesheets'
 * references fetched too and named bare; what fails, stalls, passes a cap, is refused or is not http stays absolute and
 * the save finishes; a public page's resources on a private address are never asked for; every file written gets the
 * downloaded-file mark through the fake host; and {@code index.html} comes last. Everything is written under
 * {@code @TempDir}.
 */
class PageSaverTest {
    private static final String P = "jasper-save-0123456789abcdef-";
    private static final Duration STALL = Duration.ofSeconds(2);
    private static final BooleanSupplier CURRENT = () -> true, RUNNING = () -> true;
    @TempDir Path temp;
    private SaveServer server;
    private FakePluginHost host;
    private Platform platform;
    private final List<HttpClient> clients = new ArrayList<>();
    /** The names the save looked up, in order: none while the proxy is on (D5). */
    private final List<String> lookups = new CopyOnWriteArrayList<>();
    private final Map<String, List<InetAddress>> names = new ConcurrentHashMap<>();

    @BeforeEach void serve() throws Exception {
        server = new SaveServer();
        SwingUtilities.invokeAndWait(() -> {
            try {
                host = new FakePluginHost(Files.createDirectories(temp.resolve("plugins")));
            } catch (IOException failed) {
                throw new java.io.UncheckedIOException(failed);
            }
            var context = host.start(new PluginInfo("dev.jasper.browser", "Browser", "0.2.0", Set.of()), Set.of(), Set.of(), plugin -> { });
            platform = context.platform();
        });
    }

    @AfterEach void stop() throws Exception {
        clients.forEach(HttpClient::shutdownNow);
        server.close();
        SwingUtilities.invokeAndWait(() -> host.close());
    }

    /**
     * The page and its resources: one file for an address used twice, stylesheets' {@code url()} and {@code @import}
     * references fetched and named bare, a redirect followed, {@code data:} left alone; a 404, a stall in the body and an
     * {@code ftp:} address left absolute, each {@code &} written {@code &amp;}; then the marks, {@code index.html} last.
     */
    @Test void aPageIsSavedWithItsResources() throws Exception {
        URI page = server.at("/page/");
        String html = "<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\"><link rel=\"stylesheet\" href=\"" + P + "r1-\"><style>" + P + "s0-"
            + "</style><script src=\"" + P + "r2-\"></script></head><body style=\"" + P + "a0-\"><img src=\"" + P + "r0-\" srcset=\"" + P
            + "r0- 1x, " + P + "r3- 2x\"><img src=\"" + P + "r0-\"><img src=\"" + P + "r4-\"><img src=\"" + P + "r5-\"><img src=\"" + P
            + "r6-\"><img src=\"data:image/gif;base64,R0lGOD==\"><p>" + P + "r99- " + P + "x1-</p></body></html>";
        var snapshot = new Snapshot(P, html, List.of(other("/page/logo.png"), new Resource(server.at("/page/site.css"), Kind.STYLE),
            new Resource(server.at("/page/app.js"), Kind.SCRIPT), other("/page/missing?a=1&b=2"), other("/page/stall.png"),
            new Resource(URI.create("ftp://127.0.0.1:" + server.port() + "/page/file.png"), Kind.OTHER), other("/page/moved.png#frag")),
            List.of(".h { background: url(logo.png) } .k { background: url('other.png') } .s { content: \"</style>\" }"),
            List.of("background: url(missing2?a=1&b=2); color: red"), page);
        Path folder = temp.resolve("Downloads").resolve("127.0.0.1");

        PageSaver.Saved saved = saver(page, false, false).save(folder, snapshot, client(Optional.empty()));

        String origin = "http://127.0.0.1:" + server.port();
        assertThat(Files.readString(folder.resolve("index.html"), StandardCharsets.UTF_8)).isEqualTo("<!DOCTYPE html>\n<html><head>"
            + "<meta charset=\"utf-8\"><link rel=\"stylesheet\" href=\"resources/site.css\"><style>.h { background: url(\"resources/logo.png\") }"
            + " .k { background: url(\"resources/other.png\") } .s { content: \"<\\/style>\" }</style><script src=\"resources/app.js\"></script>"
            + "</head><body style=\"background: url(&quot;" + origin + "/page/missing2?a=1&amp;b=2&quot;); color: red\"><img src=\"resources/"
            + "logo.png\" srcset=\"resources/logo.png 1x, " + origin + "/page/missing?a=1&amp;b=2 2x\"><img src=\"resources/logo.png\"><img src=\""
            + origin + "/page/stall.png\"><img src=\"ftp://127.0.0.1:" + server.port() + "/page/file.png\"><img src=\"resources/moved.png#frag\">"
            + "<img src=\"data:image/gif;base64,R0lGOD==\"><p>" + P + "r99- " + P + "x1-</p></body></html>");
        Path resources = folder.resolve("resources");
        assertThat(listed(resources)).containsExactlyInAnyOrder("logo.png", "site.css", "app.js", "moved.png", "more.css", "bg.png",
            "icon.svg", "dot.gif", "other.png");
        assertThat(Files.readString(resources.resolve("site.css"))).as("named bare, data: left alone").isEqualTo("@import \"more.css\";\n"
            + "body { background: url(\"bg.png\") }\n.i { background: url(\"icon.svg#a\") }\n.d { background: url(data:image/png;base64,AA==) }\n");
        assertThat(Files.readString(resources.resolve("more.css"))).isEqualTo("p { background: url(\"dot.gif\") }");
        assertThat(Files.readString(resources.resolve("moved.png"))).isEqualTo("PNG-2");
        assertThat(Files.readString(resources.resolve("logo.png"))).isEqualTo("PNG-1");
        assertThat(server.hits("/page/logo.png")).as("one URL, one fetch").isEqualTo(1);
        assertThat(server.hits("/page/file.png")).as("ftp: is not fetched").isZero();
        assertThat(server.hits("/page/stall.png")).isEqualTo(1);
        assertThat(server.requests()).as("every request").allSatisfy(request -> {
            assertThat(request.acceptEncoding()).as("no Accept-Encoding: the caps count what arrives").isNull();
            assertThat(request.cookie()).isNull();
            assertThat(request.referer()).isEqualTo(origin + "/");
        });
        List<MarkedDownload> marks = host.markedDownloads();
        assertThat(marks).hasSize(10).allSatisfy(mark -> assertThat(mark.referrer()).hasValue(page));
        assertThat(marks.getLast()).as("index.html last, from the page").isEqualTo(new MarkedDownload(folder.resolve("index.html"), page,
            Optional.of(page)));
        assertThat(marks.subList(0, 9)).extracting(mark -> mark.file().getFileName() + " " + mark.source()).containsExactlyInAnyOrder(
            "logo.png " + server.at("/page/logo.png"), "site.css " + server.at("/page/site.css"), "app.js " + server.at("/page/app.js"),
            "moved.png " + server.at("/page/moved.png"), "more.css " + server.at("/page/more.css"), "bg.png " + server.at("/page/bg.png"),
            "icon.svg " + server.at("/img/icon.svg"), "dot.gif " + server.at("/page/dot.gif"), "other.png " + server.at("/page/other.png"));
        assertThat(saved).isEqualTo(new PageSaver.Saved(folder.resolve("index.html"), 10, Optional.empty()));
    }

    /**
     * Ruling F12: a public page's resources on this computer, its link, a private network or an unspecified address are
     * never asked for, however the address is written or whatever a name resolves to while the proxy is off; they stay
     * absolute, and the save finishes.
     */
    @Test void aPublicPagesPrivateResourcesAreNeverFetched() throws Exception {
        URI page = URI.create("https://example.com/");
        names.put("example.com", List.of(InetAddress.getByAddress("example.com", new byte[] {93, (byte) 184, (byte) 215, 14})));
        names.put("intranet.invalid", List.of(InetAddress.getByAddress("intranet.invalid", new byte[] {10, 1, 2, 3})));
        int port = server.port();
        List<String> refused = List.of("http://127.0.0.1:" + port + "/x.png", "http://localhost:" + port + "/y.png",
            "http://LOCALHOST.:" + port + "/y2.png", "http://app.localhost:" + port + "/y3.png", "http://[::1]:" + port + "/z.png",
            "http://[::ffff:127.0.0.1]:" + port + "/m.png", "http://0177.0.0.1:" + port + "/o.png", "http://2130706433:" + port + "/d.png",
            "http://127.1:" + port + "/s.png", "http://0.0.0.0:" + port + "/u.png", "http://10.0.0.1/a.png", "http://172.16.5.4/p.png",
            "http://192.168.1.1/r.png", "http://100.64.0.1/c.png", "http://169.254.169.254/latest/meta-data", "http://[fe80::1]/l.png",
            "http://[fd00::1]/f.png", "http://[::]/n.png", "http://intranet.invalid/i.png", "http://unknown.invalid/k.png");
        var resources = refused.stream().map(url -> new Resource(URI.create(url), Kind.OTHER)).toList();
        var html = new StringBuilder();
        for (int at = 0; at < resources.size(); at++) html.append("<img src=\"").append(P).append('r').append(at).append("-\">");
        Path folder = temp.resolve("example.com");

        saver(page, false, false).save(folder, new Snapshot(P, html.toString(), resources, List.of(), List.of(), page), client(Optional.empty()));

        assertThat(server.requests()).as("nothing reached this computer's server").isEmpty();
        String written = Files.readString(folder.resolve("index.html"));
        for (String url : refused) assertThat(written).contains("<img src=\"" + url + "\">");
        assertThat(lookups).as("the resources' names looked up while the proxy is off, each refusal once; never a literal address, "
            + "nor the page's own name (D39)").containsExactlyInAnyOrder("intranet.invalid", "unknown.invalid");
        assertThat(listed(folder.resolve("resources"))).isEmpty();
    }

    /**
     * Ruling D5: while the proxy is on, only the literal host is checked and no name is looked up, as the proxy resolves
     * them; a redirect, followed by hand, is checked before it is asked, so one to this computer is never asked for. The
     * fixture plays the proxy: a request through it names the whole address.
     */
    @Test void withTheProxyOnOnlyTheLiteralHostIsCheckedAndEachRedirectBeforeItIsAsked() throws Exception {
        URI page = URI.create("http://news.invalid/");
        int port = server.port();
        server.redirect("/hop", "http://127.0.0.1:" + port + "/secret");
        server.redirect("/hop2", "http://cdn.invalid/ok2.png");
        var resources = List.of(new Resource(URI.create("http://cdn.invalid/ok.png"), Kind.OTHER),
            new Resource(URI.create("http://cdn.invalid/hop"), Kind.OTHER), new Resource(URI.create("http://cdn.invalid/hop2"), Kind.OTHER),
            new Resource(URI.create("http://localhost:" + port + "/local.png"), Kind.OTHER));
        String html = "<img src=\"" + P + "r0-\"><img src=\"" + P + "r1-\"><img src=\"" + P + "r2-\"><img src=\"" + P + "r3-\">";
        Path folder = temp.resolve("news.invalid");

        saver(page, false, true).save(folder, new Snapshot(P, html, resources, List.of(), List.of(), page),
            client(Optional.of(InetSocketAddress.createUnresolved("127.0.0.1", port))));

        assertThat(lookups).as("no name looked up").isEmpty();
        assertThat(server.requests()).extracting(SaveServer.Request::target).containsExactlyInAnyOrder("http://cdn.invalid/ok.png",
            "http://cdn.invalid/hop", "http://cdn.invalid/hop2", "http://cdn.invalid/ok2.png");
        assertThat(Files.readString(folder.resolve("index.html"))).isEqualTo("<img src=\"resources/ok.png\"><img src=\"http://cdn.invalid/hop\">"
            + "<img src=\"resources/hop2.bin\"><img src=\"http://localhost:" + port + "/local.png\">");
        assertThat(server.hits("/secret")).isZero();
    }

    /** Redirects are followed by hand, at most 5, within http and https; a sixth, or one elsewhere, leaves it absolute. */
    @Test void atMostFiveRedirectsAreFollowed() throws Exception {
        URI page = server.at("/");
        for (int at = 0; at < 5; at++) server.redirect("/five" + at, "/five" + (at + 1));
        server.answer("/five5", "text/plain", "five");
        for (int at = 0; at < 6; at++) server.redirect("/six" + at, "six" + (at + 1));
        server.answer("/six6", "text/plain", "six");
        server.redirect("/ftp", "ftp://127.0.0.1/file.txt");
        server.redirect("/nowhere", "");
        var resources = List.of(other("/five0"), other("/six0"), other("/ftp"), other("/nowhere"));
        String html = "<a>" + P + "r0-</a><a>" + P + "r1-</a><a>" + P + "r2-</a><a>" + P + "r3-</a>";
        Path folder = temp.resolve("redirects");

        saver(page, false, false).save(folder, new Snapshot(P, html, resources, List.of(), List.of(), page), client(Optional.empty()));

        assertThat(Files.readString(folder.resolve("resources").resolve("five0.bin"))).isEqualTo("five");
        assertThat(server.hits("/six5")).isEqualTo(1);
        assertThat(server.hits("/six6")).as("the sixth redirect is not followed").isZero();
        assertThat(Files.readString(folder.resolve("index.html"))).isEqualTo("<a>resources/five0.bin</a><a>" + server.at("/six0") + "</a><a>"
            + server.at("/ftp") + "</a><a>" + server.at("/nowhere") + "</a>");
    }

    /** F14: the caps count what arrives; a resource past one stays absolute, and the save finishes. */
    @Test void aResourcePastItsCapStaysAbsolute() throws Exception {
        URI page = server.at("/");
        server.chunked("/big.png", 1500);
        server.announced("/announced.png", 5000);
        server.answer("/sheet.css", "text/css", "a { b: c }".repeat(80));
        server.answer("/fine.png", "image/png", "x".repeat(900));
        var resources = List.of(other("/big.png"), other("/announced.png"), new Resource(server.at("/sheet.css"), Kind.STYLE), other("/fine.png"));
        String html = "<a>" + P + "r0-</a><a>" + P + "r1-</a><a>" + P + "r2-</a><a>" + P + "r3-</a>";
        Path folder = temp.resolve("caps");

        saver(page, false, false, limits(1000, 1_000_000, 500, 500))
            .save(folder, new Snapshot(P, html, resources, List.of(), List.of(), page), client(Optional.empty()));

        assertThat(Files.readString(folder.resolve("index.html"))).isEqualTo("<a>" + server.at("/big.png") + "</a><a>" + server.at("/announced.png")
            + "</a><a>" + server.at("/sheet.css") + "</a><a>resources/fine.png</a>");
        assertThat(listed(folder.resolve("resources"))).as("no temporary file left").containsExactly("fine.png");
    }

    /** Only the first 500 resources are fetched, stylesheets' included; the rest stay absolute. */
    @Test void onlySoManyResourcesAreFetched() throws Exception {
        URI page = server.at("/");
        server.answer("/a.css", "text/css", "x { y: url(c.png) }");
        var resources = List.of(new Resource(server.at("/a.css"), Kind.STYLE), other("/b.png"), other("/d.png"));
        String html = "<a>" + P + "r0-</a><a>" + P + "r1-</a><a>" + P + "r2-</a>";
        Path folder = temp.resolve("count");

        saver(page, false, false, limits(1000, 1_000_000, 2, 500))
            .save(folder, new Snapshot(P, html, resources, List.of(), List.of(), page), client(Optional.empty()));

        assertThat(server.hits("/a.css") + server.hits("/b.png")).isEqualTo(2);
        assertThat(server.hits("/d.png") + server.hits("/c.png")).isZero();
        assertThat(Files.readString(folder.resolve("resources").resolve("a.css"))).isEqualTo("x { y: url(\"" + server.at("/c.png") + "\") }");
        assertThat(PageSaver.Limits.DEFAULT).isEqualTo(new PageSaver.Limits(20L << 20, 200L << 20, 500, 2L << 20, 32L << 20,
            Duration.ofSeconds(15), Duration.ofMinutes(5)));
    }

    /** The total cap counts every resource's bytes as they arrive: past it, what is still arriving stays absolute. */
    @Test void theTotalCapLeavesTheRestAbsolute() throws Exception {
        URI page = server.at("/");
        for (String name : List.of("/a.png", "/b.png", "/c.png")) server.answer(name, "image/png", "z".repeat(900));
        var resources = List.of(other("/a.png"), other("/b.png"), other("/c.png"));
        String html = "<a>" + P + "r0-</a><a>" + P + "r1-</a><a>" + P + "r2-</a>";
        Path folder = temp.resolve("total");

        saver(page, false, false, limits(1000, 1500, 500, 500))
            .save(folder, new Snapshot(P, html, resources, List.of(), List.of(), page), client(Optional.empty()));

        assertThat(listed(folder.resolve("resources")).size()).isLessThanOrEqualTo(1);
        assertThat(Files.readString(folder.resolve("index.html"))).contains(server.at("/").toString());
    }

    /** Ruling D6: once the proxy changes, the save's client asks nothing more, a redirect included; the save finishes. */
    @Test void aRetiredClientAsksNothingMore() throws Exception {
        URI page = server.at("/");
        var current = new AtomicBoolean(true);
        server.handle("/first", exchange -> {
            current.set(false);
            exchange.getResponseHeaders().add("Location", "/second");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        String html = "<a>" + P + "r0-</a>";
        Path folder = temp.resolve("retired");

        new PageSaver(page, false, false, current::get, RUNNING, platform, limits(), this::lookUp)
            .save(folder, new Snapshot(P, html, List.of(other("/first")), List.of(), List.of(), page), client(Optional.empty()));

        assertThat(server.hits("/second")).isZero();
        assertThat(Files.readString(folder.resolve("index.html"))).isEqualTo("<a>" + server.at("/first") + "</a>");

        Path never = temp.resolve("never");
        new PageSaver(page, false, false, () -> false, RUNNING, platform, limits(), this::lookUp)
            .save(never, new Snapshot(P, html, List.of(other("/logo.png")), List.of(), List.of(), page), client(Optional.empty()));
        assertThat(server.hits("/logo.png")).isZero();
        assertThat(never.resolve("index.html")).isRegularFile();
    }

    /** F26: a file where the folder goes fails the save; an existing folder is saved into, its same-named files replaced. */
    @Test void aFileAtTheFolderFailsAndAnExistingFolderIsSavedInto() throws Exception {
        URI page = server.at("/page/");
        Path file = Files.writeString(temp.resolve("taken"), "mine");
        var snapshot = new Snapshot(P, "<img src=\"" + P + "r0-\">", List.of(other("/page/logo.png")), List.of(), List.of(), page);
        assertThatThrownBy(() -> saver(page, false, false).save(file, snapshot, client(Optional.empty()))).isInstanceOf(IOException.class)
            .hasMessage(file + " is a file");
        assertThat(Files.readString(file)).isEqualTo("mine");

        Path folder = Files.createDirectories(temp.resolve("again"));
        Files.createDirectories(folder.resolve("resources"));
        Files.writeString(folder.resolve("index.html"), "old");
        Files.writeString(folder.resolve("resources").resolve("logo.png"), "old");
        Files.writeString(folder.resolve("resources").resolve("keep.txt"), "kept");
        saver(page, false, false).save(folder, snapshot, client(Optional.empty()));
        assertThat(Files.readString(folder.resolve("index.html"))).isEqualTo("<img src=\"resources/logo.png\">");
        assertThat(Files.readString(folder.resolve("resources").resolve("logo.png"))).isEqualTo("PNG-1");
        assertThat(Files.readString(folder.resolve("resources").resolve("keep.txt"))).isEqualTo("kept");
    }

    /** F26: a symbolic link at {@code resources} or {@code index.html} fails the save, and nothing is written through it. */
    @Test void aSymbolicLinkInTheFolderFailsTheSave() throws Exception {
        URI page = server.at("/page/");
        var snapshot = new Snapshot(P, "<img src=\"" + P + "r0-\">", List.of(other("/page/logo.png")), List.of(), List.of(), page);
        Path elsewhere = Files.createDirectories(temp.resolve("elsewhere"));
        Path linkedResources = Files.createDirectories(temp.resolve("linked-resources"));
        Files.createSymbolicLink(linkedResources.resolve("resources"), elsewhere);
        assertThatThrownBy(() -> saver(page, false, false).save(linkedResources, snapshot, client(Optional.empty())))
            .isInstanceOf(IOException.class).hasMessageContaining("symbolic link");
        Path target = Files.writeString(temp.resolve("target.html"), "mine");
        Path linkedIndex = Files.createDirectories(temp.resolve("linked-index"));
        Files.createSymbolicLink(linkedIndex.resolve("index.html"), target);
        assertThatThrownBy(() -> saver(page, false, false).save(linkedIndex, snapshot, client(Optional.empty())))
            .isInstanceOf(IOException.class).hasMessageContaining("symbolic link");
        assertThat(listed(elsewhere)).isEmpty();
        assertThat(Files.readString(target)).isEqualTo("mine");
    }

    /** Ruling F7 for a save: a /dev/null page's files are marked with no address at all. */
    @Test void anInMemoryPagesFilesAreMarkedWithNoAddress() throws Exception {
        URI page = server.at("/page/");
        Path folder = temp.resolve("devnull");
        saver(page, true, false).save(folder, new Snapshot(P, "<img src=\"" + P + "r0-\">", List.of(other("/page/logo.png")), List.of(), List.of(),
            page), client(Optional.empty()));
        assertThat(host.markedDownloads()).containsExactly(
            new MarkedDownload(folder.resolve("resources").resolve("logo.png"), URI.create("about:internet"), Optional.empty()),
            new MarkedDownload(folder.resolve("index.html"), URI.create("about:internet"), Optional.empty()));
    }

    /** A mark that fails leaves the files saved, and the save tells why once. */
    @Test void aFailedMarkIsToldOnce() throws Exception {
        URI page = server.at("/page/");
        host.failMarks(new IOException("This volume cannot keep a downloaded-file mark"));
        Path folder = temp.resolve("unmarked");
        PageSaver.Saved saved = saver(page, false, false).save(folder, new Snapshot(P, "<img src=\"" + P + "r0-\">", List.of(other("/page/logo.png")),
            List.of(), List.of(), page), client(Optional.empty()));
        assertThat(saved.unmarked()).hasValue("This volume cannot keep a downloaded-file mark");
        assertThat(folder.resolve("resources").resolve("logo.png")).isRegularFile();
        assertThat(folder.resolve("index.html")).isRegularFile();
    }

    /**
     * Ruling D39: a page is a private one, whose save may fetch from any host, only by its host as written; a public
     * name that also resolves to this computer unlocks nothing, while a page at {@code localhost} still saves its own.
     */
    @Test void onlyThePagesLiteralHostMakesItPrivate() throws Exception {
        URI page = URI.create("http://evil.invalid/");
        names.put("evil.invalid", List.of(InetAddress.getByAddress("evil.invalid", new byte[] {93, (byte) 184, (byte) 216, 34}),
            InetAddress.getByAddress("evil.invalid", new byte[] {127, 0, 0, 1})));
        Path folder = temp.resolve("evil");
        saver(page, false, false).save(folder, new Snapshot(P, "<img src=\"" + P + "r0-\">", List.of(other("/secret")), List.of(), List.of(), page),
            client(Optional.empty()));
        assertThat(server.hits("/secret")).isZero();
        assertThat(lookups).as("the page's name is never looked up").isEmpty();
        assertThat(Files.readString(folder.resolve("index.html"))).isEqualTo("<img src=\"" + server.at("/secret") + "\">");

        URI local = URI.create("http://localhost:" + server.port() + "/page/");
        Path mine = temp.resolve("localhost");
        saver(local, false, false).save(mine, new Snapshot(P, "<img src=\"" + P + "r0-\">", List.of(other("/page/logo.png")), List.of(),
            List.of(), local), client(Optional.empty()));
        assertThat(Files.readString(mine.resolve("index.html"))).isEqualTo("<img src=\"resources/logo.png\">");
    }

    /**
     * Ruling D40: a resolved name is judged right before each of its requests, so an answer that changes between two
     * requests, as a rebinding name's does, is refused at the second. The fixture plays a proxy, so the client itself
     * looks no name up; the save's checks go through the test's resolver.
     */
    @Test void aNameIsJudgedBeforeEachOfItsRequests() throws Exception {
        URI page = URI.create("http://news.invalid/");
        var answers = new java.util.concurrent.atomic.AtomicInteger();
        InetAddress outside = InetAddress.getByAddress("flip.invalid", new byte[] {93, (byte) 184, (byte) 216, 34}),
            here = InetAddress.getByAddress("flip.invalid", new byte[] {127, 0, 0, 1});
        server.answer("/a.png", "image/png", "A");
        server.answer("/b.png", "image/png", "B");
        var resources = List.of(new Resource(URI.create("http://flip.invalid/a.png"), Kind.OTHER),
            new Resource(URI.create("http://flip.invalid/b.png"), Kind.OTHER));
        Path folder = temp.resolve("flip");
        new PageSaver(page, false, false, CURRENT, RUNNING, platform, limits(), name -> {
            lookups.add(name);
            return name.equals("flip.invalid") ? List.of(answers.getAndIncrement() == 0 ? outside : here) : List.of();
        }).save(folder, new Snapshot(P, "<a>" + P + "r0-</a><a>" + P + "r1-</a>", resources, List.of(), List.of(), page),
            client(Optional.of(InetSocketAddress.createUnresolved("127.0.0.1", server.port()))));
        assertThat(lookups).containsExactly("flip.invalid", "flip.invalid");
        assertThat(server.hits("/a.png") + server.hits("/b.png")).as("the second answer, this computer, is refused").isEqualTo(1);
    }

    /**
     * Ruling D41: the plugin's stop cancels a save under way: its client is shut down at once, nothing is written, its
     * temporary files go and no mark is asked for, though a body was still arriving.
     */
    @Test void aStopCancelsTheSaveAndWritesNothing() throws Exception {
        URI page = server.at("/page/");
        var running = new AtomicBoolean(true);
        server.handle("/page/one.png", exchange -> {
            byte[] body = "ONE".getBytes(StandardCharsets.US_ASCII);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        Path folder = temp.resolve("stopped");
        HttpClient client = client(Optional.empty());
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            running.set(false);
        });
        long start = System.nanoTime();
        var saver = new PageSaver(page, false, false, CURRENT, running::get, platform, new PageSaver.Limits(20L << 20, 200L << 20, 500,
            2L << 20, 32L << 20, Duration.ofSeconds(20), Duration.ofMinutes(5)), this::lookUp);
        assertThatThrownBy(() -> saver.save(folder, new Snapshot(P, "<img src=\"" + P + "r0-\"><img src=\"" + P + "r1-\">",
            List.of(other("/page/stall.png"), other("/page/one.png")), List.of(), List.of(), page), client))
            .isInstanceOf(IOException.class).hasMessage("the plugin stopped");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).as("at once, not at the resource's deadline").isLessThan(Duration.ofSeconds(5));
        assertThat(client.awaitTermination(Duration.ofSeconds(5))).as("its client shut down").isTrue();
        assertThat(folder).as("nothing written, the folder it made gone").doesNotExist();
        assertThat(host.markedDownloads()).isEmpty();
    }

    /** Ruling D43: at most 32 MiB of stylesheets are kept for their rewrite in all; later ones stay absolute. */
    @Test void theStylesheetsKeptInAllHaveACap() throws Exception {
        URI page = server.at("/");
        for (String name : List.of("/s1.css", "/s2.css", "/s3.css")) server.answer(name, "text/css", "a { b: c }".repeat(80));
        var resources = List.of(new Resource(server.at("/s1.css"), Kind.STYLE), new Resource(server.at("/s2.css"), Kind.STYLE),
            new Resource(server.at("/s3.css"), Kind.STYLE));
        Path folder = temp.resolve("sheets");
        saver(page, false, false, new PageSaver.Limits(20L << 20, 200L << 20, 500, 1000, 1500, STALL, Duration.ofMinutes(5)))
            .save(folder, new Snapshot(P, "<a>" + P + "r0-</a><a>" + P + "r1-</a><a>" + P + "r2-</a>", resources, List.of(), List.of(), page),
                client(Optional.empty()));
        assertThat(listed(folder.resolve("resources")).size()).as("800 bytes each, 1,500 in all").isLessThanOrEqualTo(1);
        String written = Files.readString(folder.resolve("index.html"));
        assertThat(java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(server.at("/").toString()) + "s[123]\\.css").matcher(written)
            .results().count()).isGreaterThanOrEqualTo(2);
    }

    /** Ruling D43: a script stays a script whatever type it is served as: it is neither rewritten nor named a stylesheet. */
    @Test void aScriptServedAsCssStaysAScript() throws Exception {
        URI page = server.at("/");
        server.answer("/odd", "text/css", "x { y: url(never.png) }");
        Path folder = temp.resolve("script");
        saver(page, false, false).save(folder, new Snapshot(P, "<script src=\"" + P + "r0-\"></script>",
            List.of(new Resource(server.at("/odd"), Kind.SCRIPT)), List.of(), List.of(), page), client(Optional.empty()));
        assertThat(Files.readString(folder.resolve("resources").resolve("odd.js"))).isEqualTo("x { y: url(never.png) }");
        assertThat(server.hits("/never.png")).isZero();
    }

    /** Ruling D43: a save has five minutes in all, here a test's second; what is still to come then stays absolute. */
    @Test void aSaveEndsAtItsOverallDeadline() throws Exception {
        URI page = server.at("/");
        var resources = new ArrayList<Resource>();
        var html = new StringBuilder();
        for (int at = 1; at <= 4; at++) {
            server.announced("/hold" + at + ".png", 1000);
            resources.add(other("/hold" + at + ".png"));
        }
        resources.add(other("/logo.png"));
        for (int at = 0; at < resources.size(); at++) html.append("<a>").append(P).append('r').append(at).append("-</a>");
        Path folder = temp.resolve("overall");
        long start = System.nanoTime();
        saver(page, false, false, new PageSaver.Limits(20L << 20, 200L << 20, 500, 2L << 20, 32L << 20, Duration.ofSeconds(20),
            Duration.ofSeconds(1))).save(folder, new Snapshot(P, html.toString(), resources, List.of(), List.of(), page), client(Optional.empty()));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
        assertThat(server.hits("/logo.png")).as("still to come when the time ran out").isZero();
        assertThat(listed(folder.resolve("resources"))).isEmpty();
        assertThat(Files.readString(folder.resolve("index.html"))).contains(server.at("/hold1.png").toString(), server.at("/logo.png").toString());
    }

    /** Ruling D43: saved files are ordinary new files in the folder, with the mode the umask gives, not owner-only. */
    @Test void savedFilesHaveTheUmasksMode() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        URI page = server.at("/page/");
        Path folder = temp.resolve("modes");
        saver(page, false, false).save(folder, new Snapshot(P, "<img src=\"" + P + "r0-\"><link rel=\"stylesheet\" href=\"" + P + "r1-\">",
            List.of(other("/page/logo.png"), new Resource(server.at("/page/more.css"), Kind.STYLE)), List.of(), List.of(), page),
            client(Optional.empty()));
        var ordinary = Files.getPosixFilePermissions(Files.createFile(temp.resolve("ordinary")));
        for (Path file : List.of(folder.resolve("index.html"), folder.resolve("resources").resolve("logo.png"),
                folder.resolve("resources").resolve("more.css")))
            assertThat(Files.getPosixFilePermissions(file)).as(file.toString()).isEqualTo(ordinary);
    }

    /** Ruling D43: a page that is no web page gives index.html's mark no address, as a download's mark does. */
    @Test void aPageThatIsNoWebPageMarksItsIndexWithNoAddress() throws Exception {
        URI page = URI.create("file:///Users/ada/page.html");
        Path folder = temp.resolve("file");
        saver(page, false, false).save(folder, new Snapshot(P, "<p>local</p>", List.of(), List.of(), List.of(), page), client(Optional.empty()));
        assertThat(host.markedDownloads()).containsExactly(new MarkedDownload(folder.resolve("index.html"), URI.create("about:internet"),
            Optional.empty()));
    }

    /** The save's client: no cookie handler, no redirect followed, through the proxy as plan 6's clients go. */
    @Test void theClientFollowsNoRedirectAndKeepsNoCookies() {
        HttpClient client = client(Optional.empty());
        assertThat(client.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
        assertThat(client.cookieHandler()).isEmpty();
        assertThat(client.proxy()).isPresent();
        assertThat(client.connectTimeout()).hasValue(PageSaver.DEADLINE);
        assertThat(PageSaver.DEADLINE).isEqualTo(Duration.ofSeconds(15));
    }

    private PageSaver saver(URI page, boolean inMemory, boolean proxied) {
        return saver(page, inMemory, proxied, limits());
    }

    private PageSaver saver(URI page, boolean inMemory, boolean proxied, PageSaver.Limits limits) {
        return new PageSaver(page, inMemory, proxied, CURRENT, RUNNING, platform, limits, this::lookUp);
    }

    /** The default caps, with {@link #STALL} for each resource. */
    private static PageSaver.Limits limits() {
        return limits(20L << 20, 200L << 20, 500, 2L << 20);
    }

    /** Caps of a test, with 32 MiB of stylesheets, {@link #STALL} for each resource and five minutes for the save. */
    private static PageSaver.Limits limits(long each, long all, int resources, long stylesheet) {
        return new PageSaver.Limits(each, all, resources, stylesheet, 32L << 20, STALL, Duration.ofMinutes(5));
    }

    /** The names a test gives addresses; any other is unknown. Never the system's resolver. */
    private List<InetAddress> lookUp(String name) {
        lookups.add(name);
        return names.getOrDefault(name, List.of());
    }

    private HttpClient client(Optional<InetSocketAddress> proxy) {
        HttpClient client = PageSaver.client(proxy);
        clients.add(client);
        return client;
    }

    private Resource other(String path) {
        return new Resource(server.at(path), Kind.OTHER);
    }

    private static List<String> listed(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) return List.of();
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    /**
     * The pages' server, on the loopback address alone: what each path answers, the requests it was sent with the headers
     * the tests read, and a stall that sends its headers and part of its body, then waits until the server closes.
     */
    private static final class SaveServer implements AutoCloseable {
        record Request(String target, String acceptEncoding, String cookie, String referer) { }
        private final HttpServer server;
        private final Map<String, com.sun.net.httpserver.HttpHandler> paths = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
        private final List<Request> requests = new CopyOnWriteArrayList<>();
        private final CountDownLatch closing = new CountDownLatch(1);

        SaveServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getRawPath();
                requests.add(new Request(exchange.getRequestURI().toString(), exchange.getRequestHeaders().getFirst("Accept-Encoding"),
                    exchange.getRequestHeaders().getFirst("Cookie"), exchange.getRequestHeaders().getFirst("Referer")));
                hits.computeIfAbsent(path, any -> new AtomicInteger()).incrementAndGet();
                var handler = paths.get(path);
                if (handler == null) {
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                } else {
                    handler.handle(exchange);
                }
            });
            answer("/page/logo.png", "image/png", "PNG-1");
            answer("/page/logo2.png", "image/png", "PNG-2");
            redirect("/page/moved.png", "/page/logo2.png");
            answer("/page/site.css", "text/css; charset=utf-8", "@import \"more.css\";\nbody { background: url(bg.png) }\n"
                + ".i { background: url(../img/icon.svg#a) }\n.d { background: url(data:image/png;base64,AA==) }\n");
            answer("/page/more.css", "text/css", "p { background: url(\"dot.gif\") }");
            answer("/page/bg.png", "image/png", "BG");
            answer("/img/icon.svg", "image/svg+xml", "<svg/>");
            answer("/page/dot.gif", "image/gif", "GIF");
            answer("/page/other.png", "image/png", "OTHER");
            answer("/page/app.js", "text/javascript", "console.log(1)");
            answer("/logo.png", "image/png", "PNG-1");
            answer("/b.png", "image/png", "B");
            answer("/c.png", "image/png", "C");
            answer("/d.png", "image/png", "D");
            answer("/ok.png", "image/png", "OK");
            answer("/ok2.png", "image/png", "OK2");
            answer("/secret", "text/plain", "secret");
            answer("/local.png", "image/png", "LOCAL");
            handle("/page/stall.png", exchange -> {
                exchange.sendResponseHeaders(200, 1000);
                try (OutputStream body = exchange.getResponseBody()) {
                    body.write("PNG".getBytes(StandardCharsets.US_ASCII));
                    body.flush();
                    closing.await(30, TimeUnit.SECONDS);
                } catch (IOException | InterruptedException stopped) {
                    // The client gave up.
                }
            });
            server.start();
        }

        URI at(String path) {
            return URI.create("http://127.0.0.1:" + port() + path);
        }

        int port() {
            return server.getAddress().getPort();
        }

        int hits(String path) {
            return hits.getOrDefault(path, new AtomicInteger()).get();
        }

        List<Request> requests() {
            return List.copyOf(requests);
        }

        void handle(String path, com.sun.net.httpserver.HttpHandler handler) {
            paths.put(path, handler);
        }

        void answer(String path, String type, String body) {
            handle(path, exchange -> {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", type);
                exchange.getResponseHeaders().add("Set-Cookie", "session=1; Path=/");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            });
        }

        void redirect(String path, String location) {
            handle(path, exchange -> {
                exchange.getResponseHeaders().add("Location", location);
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            });
        }

        /** A 200 of {@code size} bytes with no length announced. */
        void chunked(String path, int size) {
            handle(path, exchange -> {
                exchange.sendResponseHeaders(200, 0);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write("c".repeat(size).getBytes(StandardCharsets.US_ASCII));
                } catch (IOException stopped) {
                    // The client stopped reading.
                }
            });
        }

        /** A 200 announcing {@code size} bytes, which sends a few and then waits until the server closes. */
        void announced(String path, int size) {
            handle(path, exchange -> {
                exchange.sendResponseHeaders(200, size);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write("a".getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                    closing.await(30, TimeUnit.SECONDS);
                } catch (IOException | InterruptedException stopped) {
                    // The client stopped reading.
                }
            });
        }

        @Override public void close() {
            closing.countDown();
            server.stop(0);
        }
    }
}
