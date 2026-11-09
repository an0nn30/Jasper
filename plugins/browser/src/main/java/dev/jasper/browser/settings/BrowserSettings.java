package dev.jasper.browser.settings;

import dev.jasper.browser.adblock.ListDownload;
import dev.jasper.browser.item.AddressInput;
import dev.jasper.browser.item.SearchEngine;
import dev.jasper.browser.item.Zoom;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.settings.Choice;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Settings › Tools › Browser, as one snapshot (spec section 17). Each setting is declared under its section, which
 * {@link Declared#sections()} gives the Browser's own Settings page.
 *
 * @param startPage what a new browser tab, window or pane loads, as typed into its address field
 * @param pageZoom the zoom, a whole percent from 25 to 500, that a page shows at until it is zoomed, and that Reset Font
 *     Size returns it to: Page zoom
 * @param links where a web link the user opens in Jasper goes: Open Links From Terminal
 * @param localFiles where a local web file the user opens from a terminal goes: Open Local Web Files
 * @param searchEngine what the address field searches with, and where its suggestions come from: Default Search Engine
 * @param customSearchUrl the Custom engine's search URL, an http or https address with {@code %s} for the terms; empty
 *     for none
 * @param customSuggestionsUrl the Custom engine's suggestion URL, an http or https address with {@code %s} for the
 *     typed text; empty for none
 * @param devNullMode whether browser tabs, windows and panes opened from now on are in /dev/null mode: Use /dev/null mode
 * @param adBlocking whether pages' requests and elements are blocked and hidden as the filter list says: Enable Ad
 *     Blocking
 * @param filterListUrl where the filter list comes from, an https address, or an http one on this computer: Filter
 *     List URL
 */
public record BrowserSettings(String startPage, int pageZoom, LinkOpening links, LocalFileOpening localFiles,
                              SearchEngine searchEngine, String customSearchUrl, String customSuggestionsUrl, boolean devNullMode,
                              boolean adBlocking, URI filterListUrl) {
    /** Jasper's welcome page, the default start page. */
    public static final String WELCOME = "jasper-about:welcome";
    /** EasyList's content blocker list, the default filter list (spec section 12). */
    public static final String EASYLIST = "https://easylist-downloads.adblockplus.org/easylist_content_blocker.json";
    static final String START_PAGE = "start_page", PAGE_ZOOM = "page_zoom", LINKS = "open_links_from_terminal",
        LOCAL_FILES = "open_local_web_files", SEARCH_ENGINE = "search_engine", CUSTOM_SEARCH = "custom_search_url",
        CUSTOM_SUGGESTIONS = "custom_suggestions_url", DEVNULL_MODE = "devnull_mode", AD_BLOCKING_ON = "ad_blocking",
        FILTER_LIST_URL = "filter_list_url", HTTP_PROXY = "http_proxy", PROXY_HOST = "proxy_host",
        PROXY_PORT = "proxy_port";
    static final String GENERAL = "General", LINK_OPENING = "Link Opening", SEARCH = "Search Engine", PRIVACY = "Privacy & Data",
        AD_BLOCKING = "Ad Blocking", PROXY = "Proxy";
    /** The sections, in the order the Settings page shows them. */
    static final List<String> SECTIONS = List.of(GENERAL, LINK_OPENING, SEARCH, PRIVACY, AD_BLOCKING, PROXY);
    private static final String NO_TEMPLATE = "The URL must start with https:// or http:// and contain " + SearchEngine.TERMS;
    // Ruling F10: the list's only integrity is TLS, so http only from this computer.
    private static final String NO_LIST_ADDRESS = "Enter an https address, or an http address on this computer.";
    private static final String NO_PROXY_HOST = "Enter a host name or IP address.";
    // A host name's label: letters, digits and inner hyphens, at most 63 characters (RFC 1123); an IPv4 address's part,
    // 0 to 255 with no leading zero; and what an IPv6 address is written with, which Inet6Address then reads.
    private static final Pattern LABEL = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?");
    private static final String OCTET = "(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])";
    private static final Pattern IPV4 = Pattern.compile(OCTET + "(?:\\." + OCTET + "){3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:.]*:[0-9A-Fa-f:.]*");

    /**
     * A section of the Settings page and its settings, in the order declared.
     *
     * @param title the section's title
     * @param settings its settings
     */
    public record Section(String title, List<Setting<?>> settings) {
        /** Copies the settings. */
        public Section {
            Objects.requireNonNull(title, "title");
            settings = List.copyOf(settings);
        }
    }

    /** Open Links From Terminal: where a web link the user opens in Jasper goes. */
    public enum LinkOpening {
        /** Always Ask, the default: the "Open in Jasper?" prompt. */
        ASK("ask", "Always Ask"),
        /** Built-in Browser: a browser item in the next tab. */
        BUILT_IN("builtin", "Built-in Browser"),
        /** Default Browser: the system browser. */
        DEFAULT_BROWSER("default", "Default Browser");

        private final String token, label;

        LinkOpening(String token, String label) {
            this.token = token;
            this.label = label;
        }
    }

    /** Open Local Web Files: where a local web file the user opens from a terminal goes. */
    public enum LocalFileOpening {
        /** Always Ask, the default: the "Open in Jasper?" prompt. */
        ASK("ask", "Always Ask"),
        /** Built-in Browser: a browser item at the file's {@code file:} address in the next tab. */
        BUILT_IN("builtin", "Built-in Browser"),
        /** Default Editor: the file opens as a click on any other file does. */
        DEFAULT_EDITOR("editor", "Default Editor");

        private final String token, label;

        LocalFileOpening(String token, String label) {
            this.token = token;
            this.label = label;
        }
    }

    /**
     * Requires every value, Page zoom from {@link Zoom#MIN} to {@link Zoom#MAX}, each custom URL empty or a
     * {@link SearchEngine#template template}, and a filter list address the list may come from
     * ({@link ListDownload#fetches}).
     */
    public BrowserSettings {
        Objects.requireNonNull(startPage, "startPage");
        if (pageZoom < Zoom.MIN || pageZoom > Zoom.MAX) throw new IllegalArgumentException("Not a page zoom: " + pageZoom + "%");
        Objects.requireNonNull(links, "links");
        Objects.requireNonNull(localFiles, "localFiles");
        Objects.requireNonNull(searchEngine, "searchEngine");
        Objects.requireNonNull(customSearchUrl, "customSearchUrl");
        Objects.requireNonNull(customSuggestionsUrl, "customSuggestionsUrl");
        if (!template(customSearchUrl) || !template(customSuggestionsUrl)) throw new IllegalArgumentException(NO_TEMPLATE);
        if (!ListDownload.fetches(Objects.requireNonNull(filterListUrl, "filterListUrl"))) throw new IllegalArgumentException(NO_LIST_ADDRESS);
    }

    /**
     * Declares the Browser's settings, which Settings › Tools › Browser shows: Start page and Page zoom, a percent from 25
     * to 500, under General, Open Links From Terminal and Open Local Web Files under Link Opening, then Default Search
     * Engine, Custom Search URL and Custom Search Suggestions URL under Search Engine, each custom URL empty or an http or
     * https address with {@code %s} in it, Use /dev/null mode under Privacy &amp; Data, Enable Ad Blocking and Filter
     * List URL, an https address or an http one on this computer, under Ad Blocking, and Enable HTTP Proxy, Proxy Server,
     * a host name or an IP address, and Proxy Port, from 1 to 65535, under Proxy. Each is recorded under its section
     * ({@link Declared#sections()}). UI thread, while the plugin starts.
     *
     * @param plugin the plugin's settings
     * @return the declared settings, which read the current values
     */
    public static Declared declare(PluginSettings plugin) {
        var settings = new Declaring(plugin);
        Setting<String> startPage = settings.declare(SettingSpec.of(START_PAGE,
                SettingKind.text(AddressInput::resolves, "Enter a web address."), WELCOME)
            .label("Start page").section(GENERAL)
            .description("What a new browser tab, window or pane opens: a web address, or jasper-about:welcome for Jasper's welcome page.")
            .keywords("home", "homepage", "new tab", "browser", "welcome"));
        Setting<Integer> pageZoom = settings.declare(SettingSpec.of(PAGE_ZOOM, SettingKind.integer(Zoom.MIN, Zoom.MAX), 100)
            .label("Page zoom").section(GENERAL).unit("%")
            .description("The zoom web pages show at until you zoom them, and that Reset Font Size returns a page to. Increase "
                + "and Decrease Font Size zoom a page in steps of 5%, and Jasper keeps that zoom with the page.")
            .keywords("zoom", "scale", "magnify", "size", "font size", "browser"));
        Setting<LinkOpening> links = settings.declare(SettingSpec.of(LINKS, SettingKind.choice(List.of(
                choice(LinkOpening.ASK), choice(LinkOpening.BUILT_IN), choice(LinkOpening.DEFAULT_BROWSER))), LinkOpening.ASK)
            .label("Open Links From Terminal").section(LINK_OPENING)
            .description("Where a web link you open in Jasper goes, such as one clicked in a terminal: ask each time, a new tab "
                + "of Jasper's browser, or the system's default browser.")
            .keywords("link", "url", "click", "browser", "default browser", "ask", "prompt"));
        Setting<LocalFileOpening> localFiles = settings.declare(SettingSpec.of(LOCAL_FILES, SettingKind.choice(List.of(
                choice(LocalFileOpening.ASK), choice(LocalFileOpening.BUILT_IN), choice(LocalFileOpening.DEFAULT_EDITOR))), LocalFileOpening.ASK)
            .label("Open Local Web Files").section(LINK_OPENING)
            .description("Where a local web page, image or PDF clicked in a terminal opens: ask each time, a new tab of "
                + "Jasper's browser, or the Default editor.")
            .keywords("file", "html", "pdf", "svg", "image", "local", "editor", "ask", "prompt"));
        var engines = Arrays.stream(SearchEngine.values()).map(value -> Choice.of(value.token(), value.displayName(), value)).toList();
        Setting<SearchEngine> engine = settings.declare(SettingSpec.of(SEARCH_ENGINE, SettingKind.choice(engines), SearchEngine.GOOGLE)
            .label("Default Search Engine").section(SEARCH)
            .description("What the address field searches with, and where the search suggestions it shows as you type come "
                + "from: Google, DuckDuckGo, Bing, Kagi, or Custom, with the URLs below.")
            .keywords("search", "engine", "google", "duckduckgo", "bing", "kagi", "suggestions", "address bar", "omnibox"));
        SettingKind<String> template = SettingKind.text(BrowserSettings::template, NO_TEMPLATE);
        Setting<String> customSearch = settings.declare(SettingSpec.of(CUSTOM_SEARCH, template, "")
            .label("Custom Search URL").section(SEARCH)
            .description("With Custom: the address of a search's results, an http:// or https:// address with %s where the "
                + "search terms go, such as https://search.example.com/?q=%s. Empty searches with Google.")
            .keywords("search", "engine", "custom", "url", "template"));
        Setting<String> customSuggestions = settings.declare(SettingSpec.of(CUSTOM_SUGGESTIONS, template, "")
            .label("Custom Search Suggestions URL").section(SEARCH)
            .description("With Custom: where the address field's search suggestions come from, an http:// or https:// "
                + "address that answers OpenSearch JSON, with %s where the typed text goes. Empty shows none.")
            .keywords("search", "suggestions", "custom", "url", "opensearch", "autocomplete"));
        Setting<Boolean> devNull = settings.declare(SettingSpec.of(DEVNULL_MODE, SettingKind.bool(), false)
            .label("Use /dev/null mode (no history, cookies, or other data saved)").section(PRIVACY)
            .description("Browser tabs, windows and panes opened after you turn this on keep cookies, history and all other "
                + "browser data in memory, and lose them when that tab and the tabs it opened have all closed.")
            .keywords("private", "incognito", "privacy", "history", "cookies", "data", "dev null", "devnull", "memory"));
        Setting<Boolean> adBlocking = settings.declare(SettingSpec.of(AD_BLOCKING_ON, SettingKind.bool(), false)
            .label("Enable Ad Blocking").section(AD_BLOCKING)
            .description("Blocks the ads the filter list below names, and hides the parts of pages that show them.")
            .keywords("ad blocking", "ads", "adblock", "block", "filter", "easylist", "privacy"));
        Setting<String> filterList = settings.declare(SettingSpec.of(FILTER_LIST_URL,
                SettingKind.text(BrowserSettings::listAddress, NO_LIST_ADDRESS), EASYLIST)
            .label("Filter List URL").section(AD_BLOCKING)
            .description("A content blocker list in WebKit's format, downloaded automatically every 24 hours.")
            .keywords("ad blocking", "filter", "list", "url", "easylist", "content blocker", "adblock"));
        Setting<Boolean> proxy = settings.declare(SettingSpec.of(HTTP_PROXY, SettingKind.bool(), false)
            .label("Enable HTTP Proxy").section(PROXY)
            .description("Route browser traffic through a CONNECT proxy server.")
            .keywords("proxy", "http proxy", "connect", "privoxy", "tor", "network", "privacy"));
        Setting<String> proxyHost = settings.declare(SettingSpec.of(PROXY_HOST,
                SettingKind.text(BrowserSettings::proxyHost, NO_PROXY_HOST), "127.0.0.1")
            .label("Proxy Server").section(PROXY)
            .description("The proxy server's host name or IP address. Write an IPv6 address without brackets.")
            .keywords("proxy", "server", "host", "address", "network"));
        Setting<Integer> proxyPort = settings.declare(SettingSpec.of(PROXY_PORT, SettingKind.integer(1, 65535), 8118)
            .label("Proxy Port").section(PROXY)
            .description("The proxy server's port, from 1 to 65535.")
            .keywords("proxy", "port", "network"));
        return new Declared(startPage, pageZoom, links, localFiles, engine, customSearch, customSuggestions, devNull, adBlocking, filterList,
            proxy, proxyHost, proxyPort, settings.sections());
    }

    /** Declares each setting in the plugin's settings and records it under its section, one of {@link #SECTIONS}. */
    private static final class Declaring {
        private final PluginSettings settings;
        private final Map<String, List<Setting<?>>> sections = new LinkedHashMap<>();

        Declaring(PluginSettings settings) {
            this.settings = settings;
        }

        <T> Setting<T> declare(SettingSpec<T> spec) {
            if (!SECTIONS.contains(spec.section())) throw new IllegalArgumentException("Not a section of the Browser's: " + spec.section());
            Setting<T> setting = settings.declare(spec);
            sections.computeIfAbsent(spec.section(), title -> new ArrayList<>()).add(setting);
            return setting;
        }

        /** The sections with a setting, in {@link #SECTIONS}' order. */
        List<Section> sections() {
            return SECTIONS.stream().filter(sections::containsKey).map(title -> new Section(title, sections.get(title))).toList();
        }
    }

    /** A filter list's address: one the list may come from ({@link ListDownload#fetches}). */
    private static boolean listAddress(String value) {
        try {
            return ListDownload.fetches(new URI(value));
        } catch (URISyntaxException unreadable) {
            return false;
        }
    }

    /**
     * A proxy's host: a host name, its labels letters, digits and inner hyphens, at most 63 characters each and 253 in
     * all, whose last label is not all digits; an IPv4 address, each part from 0 to 255 without a leading zero; or an IPv6
     * address, without brackets or a zone. Nothing else, so no scheme, path, user info, port or space reaches Chromium's
     * {@code host:port}. Reads no name server.
     */
    static boolean proxyHost(String value) {
        if (IPV4.matcher(value).matches()) return true;
        if (value.indexOf(':') >= 0) {
            if (!IPV6.matcher(value).matches()) return false;
            try {
                Inet6Address.ofLiteral(value);
                return true;
            } catch (IllegalArgumentException unreadable) {
                return false;
            }
        }
        String[] labels = value.split("\\.", -1);
        return value.length() <= 253 && !labels[labels.length - 1].chars().allMatch(Character::isDigit)
            && Arrays.stream(labels).allMatch(label -> LABEL.matcher(label).matches());
    }

    /** A custom URL: empty, or a {@link SearchEngine#template template}, an http or https address with {@code %s}. */
    private static boolean template(String value) {
        return value.isEmpty() || SearchEngine.template(value);
    }

    private static Choice<LinkOpening> choice(LinkOpening value) {
        return Choice.of(value.token, value.label, value);
    }

    private static Choice<LocalFileOpening> choice(LocalFileOpening value) {
        return Choice.of(value.token, value.label, value);
    }

    /**
     * What a new item loads: the start page, as the address field would load it.
     *
     * @return the address
     */
    public URI startAddress() {
        return AddressInput.resolve(startPage, this).orElse(URI.create(WELCOME));
    }

    /** The Browser's declared settings. */
    public static final class Declared {
        private final Setting<String> startPage;
        private final Setting<Integer> pageZoom;
        private final Setting<LinkOpening> links;
        private final Setting<LocalFileOpening> localFiles;
        private final Setting<SearchEngine> engine;
        private final Setting<String> customSearch, customSuggestions;
        private final Setting<Boolean> devNull, adBlocking, proxy;
        private final Setting<String> filterList, proxyHost;
        private final Setting<Integer> proxyPort;
        private final List<Section> sections;

        private Declared(Setting<String> startPage, Setting<Integer> pageZoom, Setting<LinkOpening> links,
                         Setting<LocalFileOpening> localFiles, Setting<SearchEngine> engine, Setting<String> customSearch,
                         Setting<String> customSuggestions, Setting<Boolean> devNull, Setting<Boolean> adBlocking, Setting<String> filterList,
                         Setting<Boolean> proxy, Setting<String> proxyHost, Setting<Integer> proxyPort,
                         List<Section> sections) {
            this.startPage = startPage;
            this.pageZoom = pageZoom;
            this.links = links;
            this.localFiles = localFiles;
            this.engine = engine;
            this.customSearch = customSearch;
            this.customSuggestions = customSuggestions;
            this.devNull = devNull;
            this.adBlocking = adBlocking;
            this.filterList = filterList;
            this.proxy = proxy;
            this.proxyHost = proxyHost;
            this.proxyPort = proxyPort;
            this.sections = List.copyOf(sections);
        }

        /**
         * Every declared setting, by section: General, Link Opening, Search Engine, Privacy &amp; Data, Ad Blocking and
         * Proxy, each in the order declared; a section without a setting is left out.
         *
         * @return the sections
         */
        public List<Section> sections() {
            return sections;
        }

        /**
         * The settings as they are now.
         *
         * @return the snapshot
         */
        public BrowserSettings read() {
            return new BrowserSettings(startPage.get(), pageZoom.get(), links.get(), localFiles.get(), engine.get(), customSearch.get(),
                customSuggestions.get(), devNull.get(), adBlocking.get(), address(filterList.get()));
        }

        /** The filter list's address {@code value}, which the setting checked; the default, for one it could not have. */
        private static URI address(String value) {
            return listAddress(value) ? URI.create(value) : URI.create(EASYLIST);
        }

        /**
         * Saves Enable Ad Blocking, as the onboarding's Ad Blocker switch does. Any thread.
         *
         * @param on the new value
         * @return the save's completion, once {@link #read()} shows it; it fails when the file cannot be written
         */
        public CompletableFuture<Void> setAdBlocking(boolean on) {
            return adBlocking.set(on);
        }

        /**
         * Runs {@code handler} on the UI thread whenever Enable Ad Blocking changes.
         *
         * @param handler receives the new value
         * @return the registration, which ends with the plugin
         */
        public Subscription onAdBlockingChanged(Consumer<Boolean> handler) {
            return adBlocking.onChanged(Objects.requireNonNull(handler, "handler"));
        }

        /**
         * Runs {@code handler} on the UI thread whenever the Filter List URL changes.
         *
         * @param handler receives the new address
         * @return the registration, which ends with the plugin
         */
        public Subscription onFilterListUrlChanged(Consumer<URI> handler) {
            Objects.requireNonNull(handler, "handler");
            return filterList.onChanged(value -> handler.accept(address(value)));
        }

        /**
         * The HTTP proxy, as Chromium is given it (plan 6's Task 11): {@code host:port}, an IPv6 address written in
         * brackets, such as {@code [::1]:8118}. Any thread.
         *
         * @return the proxy; empty while Enable HTTP Proxy is off
         */
        public Optional<String> proxyServer() {
            return proxyAddress().map(address -> {
                String host = address.getHostString();
                return (host.indexOf(':') >= 0 ? "[" + host + "]" : host) + ":" + address.getPort();
            });
        }

        /**
         * The HTTP proxy's address, as the plugin's own fetches go through it (F3): Proxy Server and Proxy Port,
         * unresolved, as reading the settings looks no name up. Any thread.
         *
         * @return the address; empty while Enable HTTP Proxy is off
         */
        public Optional<InetSocketAddress> proxyAddress() {
            if (!proxy.get()) return Optional.empty();
            return Optional.of(InetSocketAddress.createUnresolved(proxyHost.get(), proxyPort.get()));
        }

        /**
         * Runs {@code handler} on the UI thread whenever {@link #proxyServer()} changes, once for each change: as Enable
         * HTTP Proxy changes, or Proxy Server or Proxy Port while it is on.
         *
         * @param handler what to run; it reads the proxy itself
         * @return the registration, which ends with the plugin
         */
        public Subscription onProxyChanged(Runnable handler) {
            Objects.requireNonNull(handler, "handler");
            var last = new AtomicReference<>(proxyServer());
            Runnable changed = () -> {
                Optional<String> now = proxyServer();
                if (!now.equals(last.getAndSet(now))) handler.run();
            };
            Subscription on = proxy.onChanged(value -> changed.run()), host = proxyHost.onChanged(value -> changed.run()),
                port = proxyPort.onChanged(value -> changed.run());
            return () -> {
                on.close();
                host.close();
                port.close();
            };
        }

        /**
         * Saves Open Links From Terminal, as "Remember my choice" does. Any thread.
         *
         * @param choice the new value
         * @return the save's completion, which fails when the file cannot be written
         */
        public CompletableFuture<Void> remember(LinkOpening choice) {
            return links.set(choice);
        }

        /**
         * Saves Open Local Web Files, as "Remember my choice" does. Any thread.
         *
         * @param choice the new value
         * @return the save's completion, which fails when the file cannot be written
         */
        public CompletableFuture<Void> remember(LocalFileOpening choice) {
            return localFiles.set(choice);
        }

        /**
         * Runs {@code handler} on the UI thread whenever Open Links From Terminal changes, in the file or by
         * {@link #remember(LinkOpening)}.
         *
         * @param handler receives the new value
         * @return the registration, which ends with the plugin
         */
        public Subscription onLinksChanged(Consumer<LinkOpening> handler) {
            return links.onChanged(Objects.requireNonNull(handler, "handler"));
        }
    }
}
