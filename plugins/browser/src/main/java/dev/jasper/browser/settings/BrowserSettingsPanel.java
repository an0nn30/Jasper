package dev.jasper.browser.settings;

import dev.jasper.browser.adblock.FilterList;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The Browser's page in Settings › Tools › Browser (spec section 17): every declared setting as Settings' standard row,
 * by section, as the generated page grouped them, and in Privacy &amp; Data, after them, iTerm2's Clear All Cookies,
 * Clear Website Data and Manage Site Permissions. Each clearing asks first, through the plugin's prompt, and runs only
 * once the user accepts; Clear Website Data tells its success once the clearing has completed, and a clearing that
 * fails is told by the clearing itself. Each clearing runs once at a time: while it runs, its button is busy and asks
 * nothing. Manage Permissions opens the persistent store's site permissions page. In Ad Blocking, after its settings,
 * Filter Statistics says how many rules the filter list has and when it was updated, that it is updating, or why it
 * could not be updated, with Update Now, which downloads the list at once without asking and is busy while the list
 * updates; a list 7 days or more without an update adds a warning (spec section 12). UI thread.
 */
public final class BrowserSettingsPanel implements SettingsPanel {
    static final String PAGE = "browser.settings", CLEAR_COOKIES = "browser.settings.clear-cookies",
        CLEAR_DATA = "browser.settings.clear-data", PERMISSIONS = "browser.settings.permissions", FILTERS = "browser.settings.filters",
        STALE = "browser.settings.filters.stale";
    private static final String COOKIES_TITLE = "Clear Cookies", COOKIES_QUESTION = "This will remove all cookies from all websites. Continue?";
    // iTerm2's text, naming all it deletes (ruling V24), with what the next start's wipe does to sign-ins made before it
    // (the user's V1).
    private static final String DATA_TITLE = "Clear Website Data", DATA_QUESTION = "This will remove all browsing data including history, "
        + "bookmarks, site permissions, cookies, cache and local storage. The next time Jasper starts, it also signs you out of any site "
        + "you sign in to before then. Continue?",
        DATA_CLEARED = "All website data has been cleared successfully!";
    // A clearing's button while it runs: busy, and pressed it does nothing.
    private static final ControlState CLEARING = new ControlState(true, false, true, "");
    private static final String PERMISSIONS_DESCRIPTION =
        "View and revoke permissions granted to websites (camera, microphone, notifications, audio playback).";
    // When the filter list was updated, in this computer's time zone, as the Indexer's page writes its times.
    private static final DateTimeFormatter UPDATED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());

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

    /**
     * The filter list's part of the page: Ad Blocking's Filter Statistics row and its Update Now.
     *
     * @param status the list's status now
     * @param staleDays how many whole days the list has gone without an update, from the seventh day; empty before
     * @param updateNow downloads the list at once; its future completes with the status once the download has ended
     */
    public record FilterStatistics(Supplier<FilterList.Status> status, Supplier<OptionalInt> staleDays,
                                   Supplier<CompletableFuture<FilterList.Status>> updateNow) {
        /** Requires every part. */
        public FilterStatistics {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(staleDays, "staleDays");
            Objects.requireNonNull(updateNow, "updateNow");
        }
    }

    private final UiKit kit;
    private final BrowserSettings.Declared settings;
    private final Actions actions;
    private final FilterStatistics filters;
    private SettingsPanelHost host;
    private UiView view;
    private String query = "";
    // The clearings under way, which outlive an opening of Settings; null before the first.
    private CompletableFuture<Void> cookiesClearing, dataClearing;
    // The Update Now under way; null before the first.
    private CompletableFuture<FilterList.Status> updating;

    /**
     * The page over {@code settings}, whose buttons do {@code actions}.
     *
     * @param kit the plugin's kit
     * @param settings the Browser's declared settings
     * @param actions what the buttons do
     * @param filters the filter list's statistics and Update Now
     */
    public BrowserSettingsPanel(UiKit kit, BrowserSettings.Declared settings, Actions actions, FilterStatistics filters) {
        this.kit = Objects.requireNonNull(kit, "kit");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.filters = Objects.requireNonNull(filters, "filters");
    }

    /** The filter list changed: the page, while it shows, shows its status again, on the UI thread. Any thread. */
    public void filtersChanged() {
        EventQueue.invokeLater(this::render);
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
            case CLEAR_COOKIES -> {
                if (!running(cookiesClearing)) actions.show().accept(new Prompt(COOKIES_TITLE, COOKIES_QUESTION, true, this::clearCookies));
            }
            case CLEAR_DATA -> {
                if (!running(dataClearing)) actions.show().accept(new Prompt(DATA_TITLE, DATA_QUESTION, true, this::clearWebsiteData));
            }
            case PERMISSIONS -> actions.managePermissions().run();
            case FILTERS -> updateNow();
            default -> { }
        }
    }

    /** Clears the cookies, unless a clearing of them runs. UI thread. */
    private void clearCookies() {
        if (running(cookiesClearing)) return;
        CompletableFuture<Void> clearing = actions.clearCookies().get();
        cookiesClearing = clearing;
        follow(clearing);
    }

    /**
     * Clears the website data, unless a clearing of it runs, and tells its success once it has completed; a failure is
     * the clearing's to tell. UI thread.
     */
    private void clearWebsiteData() {
        if (running(dataClearing)) return;
        CompletableFuture<Void> clearing = actions.clearWebsiteData().get();
        dataClearing = clearing;
        clearing.whenComplete((cleared, failure) -> {
            if (failure == null) EventQueue.invokeLater(() -> actions.show().accept(new Prompt(DATA_TITLE, DATA_CLEARED, false, () -> { })));
        });
        follow(clearing);
    }

    /** Update Now: the list downloads at once, unless it updates already, and the row shows its status once it has. UI thread. */
    private void updateNow() {
        if (running(updating) || filters.status().get().updating()) return;
        CompletableFuture<FilterList.Status> update = filters.updateNow().get();
        updating = update;
        render();
        update.whenComplete((status, failure) -> EventQueue.invokeLater(this::render));
    }

    /** Shows {@code clearing}'s button busy until it has ended. */
    private void follow(CompletableFuture<Void> clearing) {
        render();
        clearing.whenComplete((cleared, failure) -> EventQueue.invokeLater(this::render));
    }

    private static boolean running(CompletableFuture<?> work) {
        return work != null && !work.isDone();
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
                rows.add(row(CLEAR_COOKIES, "Clear All Cookies", "", "Clear Cookies", running(cookiesClearing)));
                rows.add(row(CLEAR_DATA, "Clear Website Data", "", "Clear All Data", running(dataClearing)));
                rows.add(row(PERMISSIONS, "Manage Site Permissions", PERMISSIONS_DESCRIPTION, "Manage Permissions", false));
            }
            boolean mixed = false;
            if (section.title().equals(BrowserSettings.AD_BLOCKING)) {
                FilterList.Status status = filters.status().get();
                rows.add(row(FILTERS, "Filter Statistics", statistics(status), "Update Now", status.updating() || running(updating)));
                OptionalInt stale = filters.staleDays().get();
                if (stale.isPresent()) {
                    // iTerm2's "haven't been updated for N days", completed.
                    rows.add(new Nodes.Text(STALE, ControlState.READY, "Ad block rules haven't been updated for " + stale.getAsInt() + " days",
                        Nodes.TextRole.DESCRIPTION, Optional.of(Nodes.Severity.WARNING), OptionalInt.empty()));
                    mixed = true;
                }
            }
            String id = PAGE + "." + section.title().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
            sections.add(new Nodes.Section(id, ControlState.READY, section.title(), new Nodes.Card(id + ".card", ControlState.READY,
                mixed ? Nodes.CardKind.MIXED : Nodes.CardKind.SETTINGS, rows)));
        }
        return new Nodes.Stack(PAGE, ControlState.READY, Nodes.LayoutRole.SETTINGS_PAGE, sections);
    }

    /**
     * What Filter Statistics says of the list: that it is updating; why its last update failed; or how many rules it
     * has, and when it was updated, in this computer's time zone.
     */
    private static String statistics(FilterList.Status status) {
        if (status.updating()) return "Updating…";
        if (status.error().isPresent()) return "Could not update the filter list: " + status.error().get();
        String rules = String.format(Locale.ROOT, "%,d %s", status.rules(), status.rules() == 1 ? "rule" : "rules");
        return status.updated().map(updated -> rules + " · Updated " + UPDATED.format(updated)).orElse(rules);
    }

    /** A row of the page's own, arranged as Settings' standard rows, with its button where their control is, busy while it runs. */
    private Nodes.SettingRow row(String id, String title, String description, String button, boolean busy) {
        var press = new Nodes.Button(id, busy ? CLEARING : ControlState.READY, button, "", Nodes.ButtonKind.SECONDARY, id, "");
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
