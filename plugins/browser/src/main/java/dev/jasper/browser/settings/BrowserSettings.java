package dev.jasper.browser.settings;

import dev.jasper.browser.item.AddressInput;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.settings.Choice;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Settings › Browser, as one snapshot (spec section 17).
 *
 * @param startPage what a new browser tab, window or pane loads, as typed into its address field
 * @param links where a web link the user opens in Jasper goes: Open Links From Terminal
 * @param localFiles where a local web file the user opens from a terminal goes: Open Local Web Files
 */
public record BrowserSettings(String startPage, LinkOpening links, LocalFileOpening localFiles) {
    /** Jasper's welcome page, the default start page. */
    public static final String WELCOME = "jasper-about:welcome";
    static final String START_PAGE = "start_page", LINKS = "open_links_from_terminal", LOCAL_FILES = "open_local_web_files";
    private static final String GENERAL = "General", LINK_OPENING = "Link Opening";

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

    /** Requires every value. */
    public BrowserSettings {
        Objects.requireNonNull(startPage, "startPage");
        Objects.requireNonNull(links, "links");
        Objects.requireNonNull(localFiles, "localFiles");
    }

    /**
     * Declares the Browser's settings, which Settings › Browser shows: Start page under General, then Open Links From
     * Terminal and Open Local Web Files under Link Opening. UI thread, while the plugin starts.
     *
     * @param settings the plugin's settings
     * @return the declared settings, which read the current values
     */
    public static Declared declare(PluginSettings settings) {
        Setting<String> startPage = settings.declare(SettingSpec.of(START_PAGE,
                SettingKind.text(value -> AddressInput.resolve(value).isPresent(), "Enter a web address."), WELCOME)
            .label("Start page").section(GENERAL)
            .description("What a new browser tab, window or pane opens: a web address, or jasper-about:welcome for Jasper's welcome page.")
            .keywords("home", "homepage", "new tab", "browser", "welcome"));
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
        return new Declared(startPage, links, localFiles);
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
        return AddressInput.resolve(startPage).orElse(URI.create(WELCOME));
    }

    /** The Browser's declared settings. */
    public static final class Declared {
        private final Setting<String> startPage;
        private final Setting<LinkOpening> links;
        private final Setting<LocalFileOpening> localFiles;

        private Declared(Setting<String> startPage, Setting<LinkOpening> links, Setting<LocalFileOpening> localFiles) {
            this.startPage = startPage;
            this.links = links;
            this.localFiles = localFiles;
        }

        /**
         * The settings as they are now.
         *
         * @return the snapshot
         */
        public BrowserSettings read() {
            return new BrowserSettings(startPage.get(), links.get(), localFiles.get());
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
