package dev.jasper.browser.pages;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.json.StrictJson;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The three onboarding pages (spec section 1): {@code jasper-about:onboarding-intro} ("Meet the Jasper Browser"),
 * {@code onboarding-features} ("What Can It Do?", a row for each of {@link #FEATURES}) and {@code onboarding-setup}
 * ("Let’s Get You Set Up", a switch for each {@link SetupSwitch}, and Start Browsing). The welcome page leads to the
 * intro until the {@link Onboarding} is finished, which Start Browsing does.
 *
 * <p>The intro has no queries. The features page answers {@code {"op":"features"}} with
 * {@code {"features":[{"title","text"}]}}, each text in the platform's keys. The setup page answers
 * {@code {"op":"switches"}} with {@code {"switches":[{"id","title","description","on"}]}}, {@code {"op":"switch","id","on":"true"|"false"}}
 * with {@code {"on":<the switch's state now>}}, and {@code {"op":"finish"}} with {@code {}}. A switch is read and set on
 * the UI thread. Anything else fails with "Jasper does not understand this request.".
 */
public final class OnboardingPages {
    /** The names of the three pages. */
    public static final String INTRO_PAGE = "onboarding-intro", FEATURES_PAGE = "onboarding-features", SETUP_PAGE = "onboarding-setup";
    private static final String NOT_UNDERSTOOD = "Jasper does not understand this request.";

    /**
     * One row of the features page.
     *
     * @param title the row's heading
     * @param macOs the row's text on macOS, with Cmd for the shortcut key
     * @param other the row's text on Linux and Windows, with Ctrl for it
     */
    public record Feature(String title, String macOs, String other) {
        /** Checks that every part is given. */
        public Feature {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(macOs, "macOs");
            Objects.requireNonNull(other, "other");
        }
    }

    /**
     * One switch of the setup page.
     *
     * @param id the switch's name, which the page sends back
     * @param title the switch's label
     * @param description what it does
     * @param on whether it is on now; read on the UI thread
     * @param set turns it on or off; called on the UI thread
     */
    public record SetupSwitch(String id, String title, String description, BooleanSupplier on, Consumer<Boolean> set) {
        /** Checks that every part is given. */
        public SetupSwitch {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(on, "on");
            Objects.requireNonNull(set, "set");
        }
    }

    /** What the Browser can do, as the features page lists it: each row a feature that is built. */
    public static final List<Feature> FEATURES = List.of(
        new Feature("Pages beside your terminals", "Open a page in a tab, a split or a window of its own, and move it like any pane.",
            "Open a page in a tab, a split or a window of its own, and move it like any pane."),
        new Feature("Links from the terminal",
            "Cmd-click a link in a terminal to open it in a browser tab, or Cmd+Shift-click to open it in a split. Open Links From Terminal, in Settings › Tools › Browser, chooses whether Jasper asks first or sends links to your default browser.",
            "Ctrl-click a link in a terminal to open it in a browser tab, or Ctrl+Shift-click to open it in a split. Open Links From Terminal, in Settings › Tools › Browser, chooses whether Jasper asks first or sends links to your default browser."),
        new Feature("An address field that searches", "Type an address or a search, with suggestions from your search engine, bookmarks and history.",
            "Type an address or a search, with suggestions from your search engine, bookmarks and history."),
        new Feature("Find in page", "Search the page with Match case and regular expressions.",
            "Search the page with Match case and regular expressions."),
        new Feature("Bookmarks and history", "Bookmark and tag pages, and open them or any site you visited from the command palette.",
            "Bookmark and tag pages, and open them or any site you visited from the command palette."),
        new Feature("Snippets in page fields", "Insert a snippet into the page field you are typing in.",
            "Insert a snippet into the page field you are typing in."),
        new Feature("Copy on select", "With Copy on select on, selecting text in a page copies it.",
            "With Copy on select on, selecting text in a page copies it."),
        new Feature("/dev/null mode", "Browse without keeping history, cookies or other data: turn it on in Settings › Tools › Browser › Privacy & Data.",
            "Browse without keeping history, cookies or other data: turn it on in Settings › Tools › Browser › Privacy & Data."),
        new Feature("Site permissions", "Allow or block each site's camera, microphone, notifications and sound, and review them on one page.",
            "Allow or block each site's camera, microphone, notifications and sound, and review them on one page."));

    private final AboutPages pages;
    private final AboutScheme about;
    private final Onboarding onboarding;
    private final boolean macOs;
    private final List<SetupSwitch> switches;
    private final Executor ui;

    /**
     * The pages, not yet registered.
     *
     * @param pages the pages' templates
     * @param about the pages' registry
     * @param onboarding finished by the setup page's Start Browsing
     * @param macOs whether the features show their macOS text
     * @param switches the setup page's switches, none when no setting is offered yet
     * @param ui runs a task on the UI thread, where the switches are read and set
     */
    public OnboardingPages(AboutPages pages, AboutScheme about, Onboarding onboarding, boolean macOs, List<SetupSwitch> switches, Executor ui) {
        this.pages = Objects.requireNonNull(pages, "pages");
        this.about = Objects.requireNonNull(about, "about");
        this.onboarding = Objects.requireNonNull(onboarding, "onboarding");
        this.macOs = macOs;
        this.switches = List.copyOf(switches);
        this.ui = Objects.requireNonNull(ui, "ui");
    }

    /** Registers the three pages, and the queries of the features and setup pages, before any is rendered. */
    public void register() {
        about.register(INTRO_PAGE, () -> pages.render(INTRO_PAGE, Map.of()));
        about.register(FEATURES_PAGE, () -> pages.render(FEATURES_PAGE, Map.of()));
        about.registerQueries(FEATURES_PAGE, (browser, request) -> features(request));
        about.register(SETUP_PAGE, () -> pages.render(SETUP_PAGE, Map.of()));
        about.registerQueries(SETUP_PAGE, (browser, request) -> setup(request));
    }

    private CompletableFuture<String> features(String request) {
        try {
            if (!"features".equals(op(request))) return notUnderstood();
            var rows = new ArrayList<Object>();
            for (Feature feature : FEATURES) rows.add(Map.of("title", feature.title(), "text", macOs ? feature.macOs() : feature.other()));
            return CompletableFuture.completedFuture(StrictJson.write(Map.of("features", rows)));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private CompletableFuture<String> setup(String request) {
        try {
            Map<String, Object> asked = StrictJson.object(request, 1).orElse(null);
            return switch (asked == null || !(asked.get("op") instanceof String op) ? "" : op) {
                case "switches" -> CompletableFuture.supplyAsync(this::listed, ui);
                case "switch" -> flip(asked);
                case "finish" -> {
                    onboarding.finish();
                    yield CompletableFuture.completedFuture("{}");
                }
                default -> notUnderstood();
            };
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    /** The switches as the page reads them. UI thread. */
    private String listed() {
        var rows = new ArrayList<Object>();
        for (SetupSwitch one : switches) {
            var row = new LinkedHashMap<String, Object>();
            row.put("id", one.id());
            row.put("title", one.title());
            row.put("description", one.description());
            row.put("on", one.on().getAsBoolean());
            rows.add(row);
        }
        return StrictJson.write(Map.of("switches", rows));
    }

    /** A {@code switch} request: the named switch set as asked, and answered with its state now. */
    private CompletableFuture<String> flip(Map<String, Object> asked) {
        if (!(asked.get("id") instanceof String id) || !(asked.get("on") instanceof String written)
            || !written.equals("true") && !written.equals("false")) return notUnderstood();
        SetupSwitch named = switches.stream().filter(one -> one.id().equals(id)).findFirst().orElse(null);
        if (named == null) return notUnderstood();
        return CompletableFuture.supplyAsync(() -> {
            named.set().accept(Boolean.parseBoolean(written));
            return StrictJson.write(Map.of("on", named.on().getAsBoolean()));
        }, ui);
    }

    private static String op(String request) {
        Map<String, Object> asked = StrictJson.object(request, 1).orElse(null);
        return asked != null && asked.get("op") instanceof String op ? op : "";
    }

    private static CompletableFuture<String> notUnderstood() {
        return CompletableFuture.failedFuture(new IllegalArgumentException(NOT_UNDERSTOOD));
    }
}
