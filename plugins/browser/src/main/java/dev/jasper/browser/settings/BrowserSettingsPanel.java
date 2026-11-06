package dev.jasper.browser.settings;

import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingsPanel;
import dev.jasper.sdk.settings.SettingsPanelHost;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The Browser's page in Settings › Tools › Browser (spec section 17): every declared setting as Settings' standard row,
 * by section, as the generated page grouped them, and in Privacy &amp; Data, after them, iTerm2's Clear All Cookies,
 * Clear Website Data and Manage Site Permissions. Each clearing asks first, through the plugin's prompt, and runs only
 * once the user accepts; Clear Website Data tells its success once the clearing has completed, and a clearing that
 * fails is told by the clearing itself. Manage Permissions opens the persistent store's site permissions page. UI
 * thread.
 */
public final class BrowserSettingsPanel implements SettingsPanel {
    static final String PAGE = "browser.settings", CLEAR_COOKIES = "browser.settings.clear-cookies",
        CLEAR_DATA = "browser.settings.clear-data", PERMISSIONS = "browser.settings.permissions";
    private static final String COOKIES_TITLE = "Clear Cookies", COOKIES_QUESTION = "This will remove all cookies from all websites. Continue?";
    // iTerm2's text, with what the next start's wipe does to sign-ins made before it (the user's V1).
    private static final String DATA_TITLE = "Clear Website Data", DATA_QUESTION = "This will remove all browsing data including cookies, "
        + "cache, and local storage. The next time Jasper starts, it also signs you out of any site you sign in to before then. Continue?",
        DATA_CLEARED = "All website data has been cleared successfully!";
    private static final String PERMISSIONS_DESCRIPTION =
        "View and revoke permissions granted to websites (camera, microphone, notifications, audio playback).";

    /**
     * What the page's buttons do.
     *
     * @param clearCookies clears every cookie of the persistent store; its future completes once they are gone, and
     *     fails, the failure told by the clearing, when they are not
     * @param clearWebsiteData clears the persistent store's website data, history, bookmarks and site permissions; as
     *     {@code clearCookies}
     * @param managePermissions opens the persistent store's site permissions page
     * @param show shows a prompt over the active window
     */
    public record Actions(Supplier<CompletableFuture<Void>> clearCookies, Supplier<CompletableFuture<Void>> clearWebsiteData,
                          Runnable managePermissions, Consumer<Prompt> show) {
        /** Requires every action. */
        public Actions {
            Objects.requireNonNull(clearCookies, "clearCookies");
            Objects.requireNonNull(clearWebsiteData, "clearWebsiteData");
            Objects.requireNonNull(managePermissions, "managePermissions");
            Objects.requireNonNull(show, "show");
        }
    }

    /**
     * A prompt the page shows over the active window.
     *
     * @param title its title
     * @param message what it says
     * @param question whether it asks, with OK and Cancel; else it tells, with OK alone
     * @param accepted what OK runs; Cancel and Escape run nothing
     */
    public record Prompt(String title, String message, boolean question, Runnable accepted) {
        /** Requires every part. */
        public Prompt {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(accepted, "accepted");
        }
    }

    private final UiKit kit;
    private final BrowserSettings.Declared settings;
    private final Actions actions;
    private SettingsPanelHost host;
    private UiView view;
    private String query = "";

    /**
     * The page over {@code settings}, whose buttons do {@code actions}.
     *
     * @param kit the plugin's kit
     * @param settings the Browser's declared settings
     * @param actions what the buttons do
     */
    public BrowserSettingsPanel(UiKit kit, BrowserSettings.Declared settings, Actions actions) {
        this.kit = Objects.requireNonNull(kit, "kit");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.actions = Objects.requireNonNull(actions, "actions");
    }

    @Override public UiView create(SettingsPanelHost host) {
        this.host = host;
        view = kit.create(new ViewSpec(node()), this::event);
        return view;
    }

    @Override public List<String> keywords() {
        return List.of("cookies", "website data", "cache", "permissions", "privacy", "/dev/null", "ad blocking", "filter", "proxy");
    }

    @Override public void highlight(String query) {
        this.query = Objects.requireNonNull(query, "query");
        render();
    }

    @Override public void pendingChanged() {
        render();
    }

    @Override public void dispose() {
        view = null;
        host = null;
    }

    private void event(UiEvent event) {
        if (view == null || !(event instanceof UiEvent.Action action)) return;
        switch (action.id()) {
            case CLEAR_COOKIES -> actions.show().accept(new Prompt(COOKIES_TITLE, COOKIES_QUESTION, true, () -> actions.clearCookies().get()));
            case CLEAR_DATA -> actions.show().accept(new Prompt(DATA_TITLE, DATA_QUESTION, true, this::clearWebsiteData));
            case PERMISSIONS -> actions.managePermissions().run();
            default -> { }
        }
    }

    /** Clears the website data, and tells its success once it has completed; a failure is the clearing's to tell. */
    private void clearWebsiteData() {
        actions.clearWebsiteData().get().whenComplete((cleared, failure) -> {
            if (failure == null) EventQueue.invokeLater(() -> actions.show().accept(new Prompt(DATA_TITLE, DATA_CLEARED, false, () -> { })));
        });
    }

    private void render() {
        if (view != null) view.update(new ViewSpec(node()));
    }

    private Nodes.Stack node() {
        var sections = new ArrayList<Nodes.Node>();
        for (BrowserSettings.Section section : settings.sections()) {
            var rows = new ArrayList<Nodes.Node>();
            for (Setting<?> setting : section.settings()) rows.add(host.row(setting));
            if (section.title().equals(BrowserSettings.PRIVACY)) {
                rows.add(row(CLEAR_COOKIES, "Clear All Cookies", "", "Clear Cookies"));
                rows.add(row(CLEAR_DATA, "Clear Website Data", "", "Clear All Data"));
                rows.add(row(PERMISSIONS, "Manage Site Permissions", PERMISSIONS_DESCRIPTION, "Manage Permissions"));
            }
            String id = PAGE + "." + section.title().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
            sections.add(new Nodes.Section(id, ControlState.READY, section.title(), new Nodes.Card(id + ".card", ControlState.READY,
                Nodes.CardKind.SETTINGS, rows)));
        }
        return new Nodes.Stack(PAGE, ControlState.READY, Nodes.LayoutRole.SETTINGS_PAGE, sections);
    }

    /** A row of the page's own, arranged as Settings' standard rows, with its button where their control is. */
    private Nodes.SettingRow row(String id, String title, String description, String button) {
        var press = new Nodes.Button(id, ControlState.READY, button, "", Nodes.ButtonKind.SECONDARY, id, "");
        var trailing = new Nodes.Stack(id + ":controls", ControlState.READY, Nodes.LayoutRole.SETTINGS_TRAILING, List.of(press));
        return new Nodes.SettingRow(id + ":row", ControlState.READY, title, description, trailing, false, matches(title, description, button));
    }

    /** Whether every word of the search is in one of {@code texts}, ignoring case. */
    private boolean matches(String... texts) {
        var words = Arrays.stream(query.toLowerCase(Locale.ROOT).trim().split("\\s+")).filter(word -> !word.isEmpty()).toList();
        if (words.isEmpty()) return false;
        String text = String.join(" ", texts).toLowerCase(Locale.ROOT);
        return words.stream().allMatch(text::contains);
    }
}
