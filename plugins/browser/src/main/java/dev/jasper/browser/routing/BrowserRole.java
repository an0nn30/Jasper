package dev.jasper.browser.routing;

import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.item.BrowserItem;
import dev.jasper.browser.item.BrowserItems;
import dev.jasper.browser.routing.LinkPrompt.Answer;
import dev.jasper.browser.routing.LinkPrompt.Kind;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.settings.BrowserSettings.LinkOpening;
import dev.jasper.browser.settings.BrowserSettings.LocalFileOpening;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.LinkPlacement;
import dev.jasper.sdk.ui.TabPlacement;
import dev.jasper.sdk.ui.WebBrowser;
import dev.jasper.sdk.ui.WebLink;
import java.awt.EventQueue;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * The Browser as Jasper's web browser (spec section 9), which the plugin registers with {@code Platform.browser}. Each
 * link Jasper hands it goes where {@link LinkDecision} says, from the settings and whether Jasper is the system's
 * default browser, as Jasper answers when the link comes. One it opens becomes a browser item: a tab click in the
 * next tab of the link's window, a split click in that split; and a link whose OSC 8 target names an open item loads
 * there instead, in whatever window it is, without bringing it forward. One it hands back goes to the system browser,
 * or a local file to the Default editor. One that asks shows the "Open in Jasper?" prompt in the link's window once
 * {@link #open} has returned; a link with no window, or whose window already shows a modal such as Settings, goes back
 * to Jasper instead, this once. Once Chromium has failed to start, or has stopped, no page can show: every link goes back
 * to Jasper, as does one answered Open in Jasper. UI thread.
 */
public final class BrowserRole implements WebBrowser {
    private final PluginContext context;
    private final BrowserItems items;
    private final BrowserSettings.Declared settings;
    private final Supplier<Chromium.State> chromium;

    /**
     * The role over the plugin's items.
     *
     * @param context the plugin's context
     * @param items the plugin's browser items
     * @param settings the Browser's settings, which decide each link and keep the prompt's remembered choice
     * @param chromium where the life of the Chromium that shows the items' pages stands
     */
    public BrowserRole(PluginContext context, BrowserItems items, BrowserSettings.Declared settings, Supplier<Chromium.State> chromium) {
        this.context = Objects.requireNonNull(context, "context");
        this.items = Objects.requireNonNull(items, "items");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.chromium = Objects.requireNonNull(chromium, "chromium");
    }

    @Override public boolean open(WebLink link) {
        Objects.requireNonNull(link, "link");
        if (!showsPages()) return false;
        return switch (LinkDecision.decide(link, settings.read(), context.platform().isDefaultBrowser())) {
            case OPEN -> show(link);
            case ASK -> ask(link);
            case HAND_BACK -> false;
        };
    }

    /**
     * Where a link's item opens, for a link no OSC 8 target sends to an open item.
     *
     * @param placement where the link asks to open
     * @return the next tab for a tab, else the same split
     */
    static TabPlacement placement(LinkPlacement placement) {
        return switch (placement) {
            case TAB -> TabPlacement.NEXT_TAB;
            case SPLIT_RIGHT -> TabPlacement.SPLIT_RIGHT;
            case SPLIT_DOWN -> TabPlacement.SPLIT_DOWN;
        };
    }

    /**
     * The prompt is a modal inside the link's window, so a link with no window, or whose window already shows one, goes
     * back to Jasper. Otherwise the prompt shows once Jasper's call has returned, since its modal show waits for the
     * user's answer.
     */
    private boolean ask(WebLink link) {
        if (link.window().isEmpty() || context.windows().hasModal(link.window().get())) return false;
        EventQueue.invokeLater(() -> prompt(link));
        return true;
    }

    /**
     * Shows the prompt for a link that asks. A modal that opened in its window meanwhile, or a window that closed, sends
     * the link where Jasper would have.
     */
    private void prompt(WebLink link) {
        WindowOwner window = link.window().orElseThrow();
        Kind kind = LinkDecision.localFile(link) ? Kind.LOCAL_FILE : Kind.LINK;
        try {
            if (!context.windows().hasModal(window)) {
                LinkPrompt.show(context.kit(), context.windows(), window, kind, (answer, remember) -> answered(link, kind, answer, remember));
                return;
            }
        } catch (RuntimeException refused) { // the window closed, or the plugin stops
            context.log().log(System.Logger.Level.DEBUG, "The link prompt could not show", refused);
        }
        handBack(link);
    }

    private void answered(WebLink link, Kind kind, Answer answer, boolean remember) {
        if (remember) remember(kind, answer);
        if (answer != Answer.OPEN_IN_JASPER || !show(link)) handBack(link);
    }

    /** Saves the prompt's own setting: Open Local Web Files for a local file, Open Links From Terminal for a link. */
    private void remember(Kind kind, Answer answer) {
        boolean builtIn = answer == Answer.OPEN_IN_JASPER;
        CompletableFuture<Void> saved = kind == Kind.LOCAL_FILE
            ? settings.remember(builtIn ? LocalFileOpening.BUILT_IN : LocalFileOpening.DEFAULT_EDITOR)
            : settings.remember(builtIn ? LinkOpening.BUILT_IN : LinkOpening.DEFAULT_BROWSER);
        saved.whenComplete((done, failure) -> {
            if (failure != null)
                context.log().log(System.Logger.Level.WARNING, "The link choice could not be remembered: " + failure.getMessage(), failure);
        });
    }

    /**
     * Opens a link in a browser item: the open item its OSC 8 target names, wherever it is and left where it is, or a
     * new item where the link's placement says, in the link's window (the most recently active one when it has none).
     * False when it opened nothing.
     */
    private boolean show(WebLink link) {
        if (!showsPages()) return false;
        try {
            Optional<BrowserItem> named = link.target().flatMap(items::find);
            if (named.isPresent()) named.get().load(link.address());
            else items.open(link.address(), placement(link.placement()), link.target(), link.window());
            return true;
        } catch (IllegalStateException closing) { // the windows close, or Jasper quits
            context.log().log(System.Logger.Level.WARNING, "A link could not open: " + closing.getMessage(), closing);
            return false;
        }
    }

    /** Whether Chromium may still show pages: it has neither failed to start nor stopped. */
    private boolean showsPages() {
        return switch (chromium.get()) {
            case NOT_STARTED, STARTING, RUNNING -> true;
            case FAILED, STOPPED -> false;
        };
    }

    /** What Jasper does with a link the Browser does not open: the system browser, or the Default editor for a file. */
    private void handBack(WebLink link) {
        try {
            if (LinkDecision.localFile(link)) context.platform().openFile(Path.of(link.address()));
            else context.platform().browse(link.address());
        } catch (RuntimeException failed) { // an address no path takes, or the plugin stops
            context.log().log(System.Logger.Level.WARNING, "A link could not open: " + failed.getMessage(), failed);
        }
    }
}
