package dev.jasper.browser.settings;

import dev.jasper.browser.item.AddressInput;
import dev.jasper.browser.item.SearchEngine;
import dev.jasper.browser.item.Zoom;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.settings.Choice;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

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
 */
public record BrowserSettings(String startPage, int pageZoom, LinkOpening links, LocalFileOpening localFiles,
                              SearchEngine searchEngine, String customSearchUrl, String customSuggestionsUrl, boolean devNullMode) {
    /** Jasper's welcome page, the default start page. */
    public static final String WELCOME = "jasper-about:welcome";
    static final String START_PAGE = "start_page", PAGE_ZOOM = "page_zoom", LINKS = "open_links_from_terminal",
        LOCAL_FILES = "open_local_web_files", SEARCH_ENGINE = "search_engine", CUSTOM_SEARCH = "custom_search_url",
        CUSTOM_SUGGESTIONS = "custom_suggestions_url", DEVNULL_MODE = "devnull_mode";
    static final String GENERAL = "General", LINK_OPENING = "Link Opening", SEARCH = "Search Engine", PRIVACY = "Privacy & Data",
        AD_BLOCKING = "Ad Blocking", PROXY = "Proxy";
    /** The sections, in the order the Settings page shows them. */
    static final List<String> SECTIONS = List.of(GENERAL, LINK_OPENING, SEARCH, PRIVACY, AD_BLOCKING, PROXY);
    private static final String NO_TEMPLATE = "The URL must start with https:// or http:// and contain " + SearchEngine.TERMS;

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
     * Requires every value, Page zoom from {@link Zoom#MIN} to {@link Zoom#MAX}, and each custom URL empty or a
     * {@link SearchEngine#template template}.
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
    }

    /**
     * Declares the Browser's settings, which Settings › Tools › Browser shows: Start page and Page zoom, a percent from 25
     * to 500, under General, Open Links From Terminal and Open Local Web Files under Link Opening, then Default Search
     * Engine, Custom Search URL and Custom Search Suggestions URL under Search Engine, each custom URL empty or an http or
     * https address with {@code %s} in it, and Use /dev/null mode under Privacy &amp; Data. Each is recorded under its
     * section ({@link Declared#sections()}). UI thread, while the plugin starts.
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
        return new Declared(startPage, pageZoom, links, localFiles, engine, customSearch, customSuggestions, devNull, settings.sections());
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
        private final Setting<Boolean> devNull;
        private final List<Section> sections;

        private Declared(Setting<String> startPage, Setting<Integer> pageZoom, Setting<LinkOpening> links,
                         Setting<LocalFileOpening> localFiles, Setting<SearchEngine> engine, Setting<String> customSearch,
                         Setting<String> customSuggestions, Setting<Boolean> devNull, List<Section> sections) {
            this.startPage = startPage;
            this.pageZoom = pageZoom;
            this.links = links;
            this.localFiles = localFiles;
            this.engine = engine;
            this.customSearch = customSearch;
            this.customSuggestions = customSuggestions;
            this.devNull = devNull;
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
                customSuggestions.get(), devNull.get());
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
