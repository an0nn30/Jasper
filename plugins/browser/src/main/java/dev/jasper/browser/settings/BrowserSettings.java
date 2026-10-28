package dev.jasper.browser.settings;

import dev.jasper.browser.item.AddressInput;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import java.net.URI;
import java.util.Objects;

/**
 * Settings › Browser, as one snapshot (spec section 17).
 *
 * @param startPage what a new browser tab, window or pane loads, as typed into its address field
 */
public record BrowserSettings(String startPage) {
    /** Jasper's welcome page, the default start page. */
    public static final String WELCOME = "jasper-about:welcome";
    static final String START_PAGE = "start_page";

    /** Requires every value. */
    public BrowserSettings {
        Objects.requireNonNull(startPage, "startPage");
    }

    /**
     * Declares the Browser's settings, which Settings › Browser shows. UI thread, while the plugin starts.
     *
     * @param settings the plugin's settings
     * @return the declared settings, which read the current values
     */
    public static Declared declare(PluginSettings settings) {
        return new Declared(settings.declare(SettingSpec.of(START_PAGE,
                SettingKind.text(value -> AddressInput.resolve(value).isPresent(), "Enter a web address."), WELCOME)
            .label("Start page").section("General")
            .description("What a new browser tab, window or pane opens: a web address, or jasper-about:welcome for Jasper's welcome page.")
            .keywords("home", "homepage", "new tab", "browser", "welcome")));
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

        private Declared(Setting<String> startPage) {
            this.startPage = startPage;
        }

        /**
         * The settings as they are now.
         *
         * @return the snapshot
         */
        public BrowserSettings read() {
            return new BrowserSettings(startPage.get());
        }
    }
}
