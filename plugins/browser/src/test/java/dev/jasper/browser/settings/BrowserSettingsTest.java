package dev.jasper.browser.settings;

import dev.jasper.browser.item.SearchEngine;
import dev.jasper.browser.settings.BrowserSettings.LinkOpening;
import dev.jasper.browser.settings.BrowserSettings.LocalFileOpening;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.settings.Applies;
import dev.jasper.sdk.settings.Choice;
import dev.jasper.sdk.settings.EditorHint;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import dev.jasper.sdk.settings.SettingsPanel;
import dev.jasper.sdk.testing.FakePluginHost;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Settings › Browser (spec section 17): Start page, Page zoom and Downloads folder under General, the two link settings
 * under Link Opening, and the search engine and its custom URLs under Search Engine, with the spec's labels and choices,
 * stored as their tokens in the plugin's settings file.
 */
class BrowserSettingsTest {
    private static final String ID = "dev.jasper.browser";

    @Test void theRowsAreStartPagePageZoomAndDownloadsFolderThenTheLinkAndSearchSettingsWithTheSpecsLabelsChoicesAndDefaults() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var recording = new AtomicReference<Recording>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), plugin -> {
                recording.set(new Recording(plugin.settings()));
                BrowserSettings.declare(recording.get());
            });
            List<SettingSpec<?>> specs = recording.get().specs;
            assertThat(specs).extracting(SettingSpec::key).containsExactly("start_page", "page_zoom", "downloads_folder", "open_links_from_terminal",
                "open_local_web_files", "search_engine", "custom_search_url", "custom_suggestions_url", "devnull_mode", "ad_blocking",
                "filter_list_url", "http_proxy", "proxy_host", "proxy_port");
            assertThat(specs).extracting(SettingSpec::label).containsExactly("Start page", "Page zoom", "Downloads folder",
                "Open Links From Terminal", "Open Local Web Files", "Default Search Engine", "Custom Search URL", "Custom Search Suggestions URL",
                "Use /dev/null mode (no history, cookies, or other data saved)", "Enable Ad Blocking", "Filter List URL", "Enable HTTP Proxy",
                "Proxy Server", "Proxy Port");
            assertThat(specs).extracting(SettingSpec::section).containsExactly("General", "General", "General", "Link Opening", "Link Opening",
                "Search Engine", "Search Engine", "Search Engine", "Privacy & Data", "Ad Blocking", "Ad Blocking", "Proxy", "Proxy", "Proxy");
            assertThat(specs).allSatisfy(spec -> assertThat(spec.description()).isNotBlank());
            assertThat(specs.get(1).kind()).isEqualTo(SettingKind.integer(25, 500));
            assertThat(specs.get(1).unit()).isEqualTo("%");
            assertThat(specs.get(1).defaultValue()).isEqualTo(100);
            assertThat(specs).filteredOn(spec -> spec != specs.get(1)).allSatisfy(spec -> assertThat(spec.unit()).isEmpty());
            assertThat(choices(specs.get(3))).containsExactly("ask|Always Ask|ASK", "builtin|Built-in Browser|BUILT_IN", "default|Default Browser|DEFAULT_BROWSER");
            assertThat(choices(specs.get(4))).containsExactly("ask|Always Ask|ASK", "builtin|Built-in Browser|BUILT_IN", "editor|Default Editor|DEFAULT_EDITOR");
            assertThat(choices(specs.get(5))).containsExactly("google|Google|GOOGLE", "duckduckgo|DuckDuckGo|DUCKDUCKGO", "bing|Bing|BING",
                "kagi|Kagi|KAGI", "custom|Custom|CUSTOM");
            assertThat(specs.get(3).defaultValue()).isEqualTo(LinkOpening.ASK);
            assertThat(specs.get(4).defaultValue()).isEqualTo(LocalFileOpening.ASK);
            assertThat(specs.get(5).defaultValue()).isEqualTo(SearchEngine.GOOGLE);
            assertThat(specs.get(6).defaultValue()).isEqualTo("");
            assertThat(specs.get(7).defaultValue()).isEqualTo("");
            assertThat(specs.get(8).kind()).isEqualTo(SettingKind.bool());
            assertThat(specs.get(8).defaultValue()).isEqualTo(false);
            assertThat(specs.get(8).applies()).isEqualTo(Applies.LIVE);
            assertThat(specs.get(8).description()).isEqualTo("Browser tabs, windows and panes opened after you turn this on keep cookies, "
                + "history and all other browser data in memory, and lose them when that tab and the tabs it opened have all closed.");
            // Plan 6 Task 10: Ad Blocking, off by default, and EasyList's content blocker list.
            assertThat(specs.get(9).kind()).isEqualTo(SettingKind.bool());
            assertThat(specs.get(9).defaultValue()).isEqualTo(false);
            assertThat(specs.get(10).defaultValue()).isEqualTo("https://easylist-downloads.adblockplus.org/easylist_content_blocker.json");
            assertThat(specs.get(10).description()).isEqualTo("A content blocker list in WebKit's format, downloaded automatically every 24 hours.");
            // Plan 6 Task 11: the HTTP proxy, off by default, at 127.0.0.1 and port 8118.
            assertThat(specs.get(11).kind()).isEqualTo(SettingKind.bool());
            assertThat(specs.get(11).defaultValue()).isEqualTo(false);
            assertThat(specs.get(11).description()).isEqualTo("Route browser traffic through a CONNECT proxy server.");
            assertThat(specs.get(12).defaultValue()).isEqualTo("127.0.0.1");
            assertThat(specs.get(13).kind()).isEqualTo(SettingKind.integer(1, 65535));
            assertThat(specs.get(13).defaultValue()).isEqualTo(8118);
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Spec section 17 and plan 4b's P28: each custom URL is an http or https address with %s where the terms go, or
     * empty, the default; anything else is refused with one message.
     */
    @Test void aCustomUrlThatIsNoWebAddressWithThePlaceholderIsRefused() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            host.setSetting(ID, "search_engine", "custom");
            host.setSetting(ID, "custom_search_url", "https://search.example.com/?q=");
            host.setSetting(ID, "custom_suggestions_url", "search.example.com/suggest?q=%s");
            var declared = new AtomicReference<BrowserSettings.Declared>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(),
                plugin -> declared.set(BrowserSettings.declare(plugin.settings())));
            String refused = "The URL must start with https:// or http:// and contain %s";
            assertThat(host.settingsReports(ID)).containsExactly("custom_search_url: " + refused, "custom_suggestions_url: " + refused);
            assertThat(declared.get().read()).isEqualTo(new BrowserSettings(BrowserSettings.WELCOME, 100, LinkOpening.ASK, LocalFileOpening.ASK,
                SearchEngine.CUSTOM, "", "", false, false, URI.create(BrowserSettings.EASYLIST)));
            host.setSetting(ID, "custom_search_url", "https://search.example.com/?q=%s");
            host.setSetting(ID, "custom_suggestions_url", "https://search.example.com/suggest?q=%s");
            assertThat(host.settingsReports(ID)).isEmpty();
            assertThat(declared.get().read().customSearchUrl()).isEqualTo("https://search.example.com/?q=%s");
            assertThat(declared.get().read().customSuggestionsUrl()).isEqualTo("https://search.example.com/suggest?q=%s");
            host.setSetting(ID, "custom_search_url", "ftp://search.example.com/?q=%s");
            host.setSetting(ID, "custom_suggestions_url", "");
            assertThat(host.settingsReports(ID)).containsExactly("custom_search_url: " + refused);
            assertThat(declared.get().read().customSearchUrl()).as("the default").isEmpty();
            assertThat(declared.get().read().customSuggestionsUrl()).isEmpty();
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Plan 6 Task 8: the Browser's own Settings page shows the declared settings by section, as the generated page did,
     * so each declaration is recorded under its section. The sections run General, Link Opening, Search Engine, Privacy
     * &amp; Data, Ad Blocking and Proxy, and one without a setting would be left out.
     */
    @Test void theDeclaredSettingsAreRecordedBySectionInTheSectionsOrder() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var declared = new AtomicReference<BrowserSettings.Declared>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(),
                plugin -> declared.set(BrowserSettings.declare(plugin.settings())));
            List<BrowserSettings.Section> sections = declared.get().sections();
            assertThat(sections).extracting(BrowserSettings.Section::title)
                .containsExactly("General", "Link Opening", "Search Engine", "Privacy & Data", "Ad Blocking", "Proxy");
            assertThat(sections).extracting(section -> section.settings().stream().map(Setting::key).toList()).containsExactly(
                List.of("start_page", "page_zoom", "downloads_folder"), List.of("open_links_from_terminal", "open_local_web_files"),
                List.of("search_engine", "custom_search_url", "custom_suggestions_url"), List.of("devnull_mode"),
                List.of("ad_blocking", "filter_list_url"), List.of("http_proxy", "proxy_host", "proxy_port"));
            assertThat(BrowserSettings.SECTIONS).containsExactly("General", "Link Opening", "Search Engine", "Privacy & Data",
                "Ad Blocking", "Proxy");
            assertThatThrownBy(() -> sections.getFirst().settings().clear()).as("a snapshot no caller changes")
                .isInstanceOf(UnsupportedOperationException.class);
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Plan 7 Task 2 and spec section 17: Downloads folder, under General after Page zoom, is empty, its default, for the
     * system's Downloads folder, {@code ~} or {@code ~/folder} under the home folder, or an absolute folder; anything
     * else is refused for the default.
     */
    @Test void theDownloadsFolderIsEmptyAHomeFolderOrAnAbsoluteFolder(@TempDir Path temp) throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var recording = new AtomicReference<Recording>();
            var declared = new AtomicReference<BrowserSettings.Declared>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), plugin -> {
                recording.set(new Recording(plugin.settings()));
                declared.set(BrowserSettings.declare(recording.get()));
            });
            SettingSpec<?> spec = recording.get().specs.get(2);
            assertThat(spec.key()).isEqualTo("downloads_folder");
            assertThat(spec.label()).isEqualTo("Downloads folder");
            assertThat(spec.section()).isEqualTo("General");
            assertThat(spec.defaultValue()).isEqualTo("");
            assertThat(spec.editor()).contains(EditorHint.directory());
            assertThat(spec.applies()).isEqualTo(Applies.LIVE);
            assertThat(spec.description()).isEqualTo("Where downloaded files go, without asking. Empty uses the system's Downloads folder.");
            assertThat(spec.keywords()).contains("downloads", "download", "folder", "save");
            String refusal = "Enter an absolute folder path, or leave it empty.";
            assertThat(((SettingKind.Text) spec.kind()).invalidMessage()).isEqualTo(refusal);

            Setting<String> folder = declared.get().downloadsFolder();
            assertThat(folder.key()).isEqualTo("downloads_folder");
            assertThat(folder.get()).as("the system's folder by default").isEmpty();
            for (String value : List.of(temp.toString(), "~", "~/Saved", "")) {
                host.setSetting(ID, "downloads_folder", value);
                assertThat(host.settingsReports(ID)).as(value).isEmpty();
                assertThat(folder.get()).isEqualTo(value);
            }
            for (String value : List.of("relative/dir", "Saved", "~user/Saved", " ")) {
                host.setSetting(ID, "downloads_folder", value);
                assertThat(host.settingsReports(ID)).as(value).containsExactly("downloads_folder: " + refusal);
                assertThat(folder.get()).as(value + ": the default").isEmpty();
            }
            assertThat(declared.get().sections().getFirst().settings()).extracting(Setting::key)
                .containsExactly("start_page", "page_zoom", "downloads_folder");
            assertThat(host.failures()).isEmpty();
        } });
    }

    /** The settings file is seeded with Downloads folder commented out, after Page zoom, in the file's columns. */
    @Test void theSettingsFileSeedsTheDownloadsFolderCommentedOutAfterPageZoom() throws Exception {
        List<String> lines;
        try (var in = BrowserSettings.class.getResourceAsStream("/settings.toml")) {
            assertThat(in).as("the Browser's settings template").isNotNull();
            lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
        assertThat(lines.get(2)).startsWith("# page_zoom = 100 ");
        assertThat(lines.get(3)).isEqualTo("# downloads_folder = \"\"                 # where downloaded files go, without asking: "
            + "an absolute folder, ~ or ~/folder; empty uses the system's Downloads folder");
        assertThat(lines.get(3).indexOf(" # ")).as("the comment column").isEqualTo(lines.get(2).indexOf(" # "));
        assertThat(lines.get(4)).startsWith("# open_links_from_terminal = ");
    }

    @Test void devNullModeIsReadFromItsKey() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var declared = new AtomicReference<BrowserSettings.Declared>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(),
                plugin -> declared.set(BrowserSettings.declare(plugin.settings())));
            assertThat(declared.get().read().devNullMode()).as("off by default").isFalse();
            host.setSetting(ID, "devnull_mode", true);
            assertThat(declared.get().read().devNullMode()).isTrue();
            assertThat(host.failures()).isEmpty();
        } });
    }

    @Test void aSnapshotReadsTheFileTheRememberedChoiceIsWrittenAsItsTokenAndChangesAreHeard() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            host.setSetting(ID, "open_local_web_files", "editor");
            var declared = new AtomicReference<BrowserSettings.Declared>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(),
                plugin -> declared.set(BrowserSettings.declare(plugin.settings())));
            assertThat(declared.get().read()).isEqualTo(new BrowserSettings(BrowserSettings.WELCOME, 100, LinkOpening.ASK,
                LocalFileOpening.DEFAULT_EDITOR, SearchEngine.GOOGLE, "", "", false, false, URI.create(BrowserSettings.EASYLIST)));
            var heard = new CopyOnWriteArrayList<LinkOpening>();
            declared.get().onLinksChanged(heard::add);

            assertThat(declared.get().remember(LinkOpening.BUILT_IN)).isCompleted();
            assertThat(host.savedSettings(ID)).containsEntry("open_links_from_terminal", "builtin");
            assertThat(declared.get().remember(LocalFileOpening.BUILT_IN)).isCompleted();
            assertThat(host.savedSettings(ID)).containsEntry("open_local_web_files", "builtin");
            assertThat(declared.get().remember(LinkOpening.DEFAULT_BROWSER)).isCompleted();
            assertThat(host.savedSettings(ID)).containsEntry("open_links_from_terminal", "default");
            assertThat(declared.get().read().links()).isEqualTo(LinkOpening.DEFAULT_BROWSER);
            host.setSetting(ID, "open_links_from_terminal", "ask");
            assertThat(heard).containsExactly(LinkOpening.BUILT_IN, LinkOpening.DEFAULT_BROWSER, LinkOpening.ASK);

            host.setSetting(ID, "open_links_from_terminal", "sometimes");
            assertThat(host.settingsReports(ID)).singleElement().asString().startsWith("open_links_from_terminal");
            assertThat(declared.get().read().links()).as("an unknown token is the default").isEqualTo(LinkOpening.ASK);
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Spec sections 10 and 17, with plan 4b's P33: Page zoom is a whole percent from 25 to 500, Chromium's smallest and
     * largest zoom; anything else is refused for the default, 100.
     */
    @Test void pageZoomIsAWholePercentFromTwentyFiveToFiveHundred() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            host.setSetting(ID, "page_zoom", 150L);
            var declared = new AtomicReference<BrowserSettings.Declared>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(),
                plugin -> declared.set(BrowserSettings.declare(plugin.settings())));
            assertThat(declared.get().read().pageZoom()).isEqualTo(150);
            for (long zoom : new long[] { 25, 500 }) {
                host.setSetting(ID, "page_zoom", zoom);
                assertThat(host.settingsReports(ID)).isEmpty();
                assertThat(declared.get().read().pageZoom()).isEqualTo((int) zoom);
            }
            for (long zoom : new long[] { 24, 5, 501, 0, -100 }) {
                host.setSetting(ID, "page_zoom", zoom);
                assertThat(host.settingsReports(ID)).singleElement().asString().startsWith("page_zoom: ");
                assertThat(declared.get().read().pageZoom()).as("the default").isEqualTo(100);
            }
            assertThatThrownBy(() -> new BrowserSettings(BrowserSettings.WELCOME, 24, LinkOpening.ASK, LocalFileOpening.ASK,
                SearchEngine.GOOGLE, "", "", false, false, URI.create(BrowserSettings.EASYLIST))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new BrowserSettings(BrowserSettings.WELCOME, 501, LinkOpening.ASK, LocalFileOpening.ASK,
                SearchEngine.GOOGLE, "", "", false, false, URI.create(BrowserSettings.EASYLIST))).isInstanceOf(IllegalArgumentException.class);
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Plan 6 Task 10 and ruling F10: the Filter List URL is an https address, or an http one on this computer, as TLS is
     * the list's only integrity; anything else is refused for the default. Enable Ad Blocking and the URL are read in the
     * snapshot, their changes heard on the UI thread, and Enable Ad Blocking saved as the onboarding's switch saves it.
     */
    @Test void theFilterListUrlIsHttpsOrThisComputersAndAdBlockingIsHeardAndSaved() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var declared = new AtomicReference<BrowserSettings.Declared>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(),
                plugin -> declared.set(BrowserSettings.declare(plugin.settings())));
            assertThat(declared.get().read().adBlocking()).isFalse();
            assertThat(declared.get().read().filterListUrl()).isEqualTo(URI.create(BrowserSettings.EASYLIST));
            var turned = new CopyOnWriteArrayList<Boolean>();
            var moved = new CopyOnWriteArrayList<URI>();
            declared.get().onAdBlockingChanged(turned::add);
            declared.get().onFilterListUrlChanged(moved::add);
            String refused = "Enter an https address, or an http address on this computer.";
            for (String address : List.of("https://lists.example/blocker.json", "http://localhost:8080/list.json", "http://127.0.0.1/list.json")) {
                host.setSetting(ID, "filter_list_url", address);
                assertThat(host.settingsReports(ID)).as(address).isEmpty();
                assertThat(declared.get().read().filterListUrl()).isEqualTo(URI.create(address));
            }
            for (String address : List.of("http://lists.example/list.json", "ftp://lists.example/list.json", "lists.example/list.json",
                    "https://user:secret@lists.example/list.json", "")) {
                host.setSetting(ID, "filter_list_url", address);
                assertThat(host.settingsReports(ID)).as(address).containsExactly("filter_list_url: " + refused);
                assertThat(declared.get().read().filterListUrl()).as("the default").isEqualTo(URI.create(BrowserSettings.EASYLIST));
            }
            assertThat(moved).startsWith(URI.create("https://lists.example/blocker.json"), URI.create("http://localhost:8080/list.json"),
                URI.create("http://127.0.0.1/list.json"), URI.create(BrowserSettings.EASYLIST));
            assertThat(declared.get().setAdBlocking(true)).isCompleted();
            assertThat(host.savedSettings(ID)).containsEntry("ad_blocking", true);
            assertThat(declared.get().read().adBlocking()).isTrue();
            host.setSetting(ID, "ad_blocking", false);
            assertThat(turned).containsExactly(true, false);
            assertThatThrownBy(() -> new BrowserSettings(BrowserSettings.WELCOME, 100, LinkOpening.ASK, LocalFileOpening.ASK, SearchEngine.GOOGLE,
                "", "", false, true, URI.create("http://lists.example/list.json"))).isInstanceOf(IllegalArgumentException.class);
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Plan 6 Task 11: Proxy Server takes a host name or an IP address, an IPv6 one without brackets, and nothing else: no
     * scheme, path, user info, port or space, as Chromium's {@code host:port} is made from it, and anything else is
     * refused for the default. Proxy Port runs from 1 to 65535. The proxy server is empty while Enable HTTP Proxy is off,
     * and otherwise {@code host:port}, an IPv6 address in brackets; its address is the same, unresolved, as no setting
     * looks a name up. A change of what it gives is heard on the UI thread, once, and no other change is.
     */
    @Test void theProxyServerIsAHostNameOrAnIpAddressAndAPortAndIsHeardWhenWhatItGivesChanges() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var declared = new AtomicReference<BrowserSettings.Declared>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(),
                plugin -> declared.set(BrowserSettings.declare(plugin.settings())));
            BrowserSettings.Declared settings = declared.get();
            assertThat(settings.proxyServer()).as("off by default").isEmpty();
            assertThat(settings.proxyAddress()).isEmpty();
            var heard = new CopyOnWriteArrayList<String>();
            settings.onProxyChanged(() -> heard.add(settings.proxyServer().orElse("off")
                + (SwingUtilities.isEventDispatchThread() ? "" : " off the UI thread")));
            host.setSetting(ID, "proxy_host", "proxy.local");
            host.setSetting(ID, "proxy_port", 3128L);
            assertThat(heard).as("off, the server changes nothing").isEmpty();
            host.setSetting(ID, "http_proxy", true);
            assertThat(settings.proxyServer()).contains("proxy.local:3128");
            assertThat(settings.proxyAddress()).contains(java.net.InetSocketAddress.createUnresolved("proxy.local", 3128));
            assertThat(settings.proxyAddress().orElseThrow().isUnresolved()).isTrue();
            host.setSetting(ID, "proxy_port", 8118L);

            Map<String, String> allowed = new LinkedHashMap<>();
            allowed.put("127.0.0.1", "127.0.0.1:8118");
            allowed.put("proxy.local", "proxy.local:8118");
            allowed.put("::1", "[::1]:8118");
            allowed.put("Proxy-1.Example.COM", "Proxy-1.Example.COM:8118");
            allowed.put("localhost", "localhost:8118");
            allowed.put("10.0.0.255", "10.0.0.255:8118");
            allowed.put("2001:db8::8a2e:370:7334", "[2001:db8::8a2e:370:7334]:8118");
            allowed.put("::ffff:192.0.2.1", "[::ffff:192.0.2.1]:8118");
            for (var each : allowed.entrySet()) {
                host.setSetting(ID, "proxy_host", each.getKey());
                assertThat(host.settingsReports(ID)).as(each.getKey()).isEmpty();
                assertThat(settings.proxyServer()).as(each.getKey()).contains(each.getValue());
            }
            assertThat(settings.proxyAddress()).as("an IPv6 address's host has no brackets")
                .contains(java.net.InetSocketAddress.createUnresolved("::ffff:192.0.2.1", 8118));

            String refused = "Enter a host name or IP address.";
            for (String value : List.of("http://x", "a b", "", " proxy.local", "proxy.local ", "user@proxy.local", "user:secret@proxy.local",
                    "proxy.local/path", "proxy.local:8118", "[::1]", "[::1]:8118", "fe80::1%en0", "1::2::3", ":::", "999.1.1.1", "1.2.3",
                    "01.2.3.4", "1.2.3.4.5", "-proxy.local", "proxy-.local", "proxy..local", ".proxy.local", "proxy.local.", "proxy_1.local",
                    "pr\u00f6xy.local", "a".repeat(64) + ".local", "proxy.123", "proxy.local\tx", "proxy.local?x", "proxy.local#x")) {
                host.setSetting(ID, "proxy_host", value);
                assertThat(host.settingsReports(ID)).as(value).containsExactly("proxy_host: " + refused);
                assertThat(settings.proxyServer()).as(value + ": the default").contains("127.0.0.1:8118");
            }
            host.setSetting(ID, "proxy_host", "a".repeat(63) + ".local");
            assertThat(host.settingsReports(ID)).as("a label of 63 characters").isEmpty();
            for (long port : new long[] { 0, -1, 65536 }) {
                host.setSetting(ID, "proxy_port", port);
                assertThat(host.settingsReports(ID)).as(port + "").singleElement().asString().startsWith("proxy_port: ");
                assertThat(settings.proxyServer()).as(port + ": the default").contains("a".repeat(63) + ".local:8118");
            }
            host.setSetting(ID, "proxy_port", 65535L);
            host.setSetting(ID, "proxy_host", "::1");
            host.setSetting(ID, "proxy_port", 1L);
            assertThat(settings.proxyServer()).contains("[::1]:1");
            host.setSetting(ID, "http_proxy", false);
            assertThat(settings.proxyServer()).isEmpty();
            assertThat(settings.proxyAddress()).isEmpty();
            host.setSetting(ID, "proxy_host", "proxy.local");
            assertThat(heard).as("each change of what it gives, once, and nothing else").containsExactly("proxy.local:3128", "proxy.local:8118",
                "127.0.0.1:8118", "proxy.local:8118", "[::1]:8118", "Proxy-1.Example.COM:8118", "localhost:8118", "10.0.0.255:8118",
                "[2001:db8::8a2e:370:7334]:8118", "[::ffff:192.0.2.1]:8118", "127.0.0.1:8118", "a".repeat(63) + ".local:8118",
                "a".repeat(63) + ".local:65535", "[::1]:65535", "[::1]:1", "off");
            assertThat(host.failures()).isEmpty();
        } });
    }

    private static List<String> choices(SettingSpec<?> spec) {
        var kind = (SettingKind.OneOf<?>) spec.kind();
        return kind.choices().stream().map(BrowserSettingsTest::choice).toList();
    }

    private static String choice(Choice<?> choice) {
        return choice.token() + "|" + choice.displayName() + "|" + choice.value();
    }

    /** The plugin's settings, recording each spec declared. */
    private static final class Recording implements PluginSettings {
        final List<SettingSpec<?>> specs = new CopyOnWriteArrayList<>();
        private final PluginSettings settings;

        Recording(PluginSettings settings) {
            this.settings = settings;
        }

        @Override public <T> Setting<T> declare(SettingSpec<T> spec) {
            specs.add(spec);
            return settings.declare(spec);
        }

        @Override public Path file() { return settings.file(); }

        @Override public Subscription onChanged(Runnable handler) { return settings.onChanged(handler); }

        @Override public void page(SettingsPanel panel) { settings.page(panel); }

        @Override public void show() { settings.show(); }
    }
}
