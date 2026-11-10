package dev.jasper.browser.tools;

import dev.jasper.browser.json.StrictJson;
import dev.jasper.browser.page.ChallengeFrames;
import dev.jasper.sdk.ui.MenuItem;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * One page's tools in the item's menu (spec sections 6 and 10): reader mode, and distraction removal's state. As each of
 * the page's documents finishes loading, {@code reader.js}'s check runs in its top frame ({@link ToolScripts}), and the
 * document's report of more than {@value #OFFER_LENGTH} characters of reader text offers Reader Mode and Remove
 * Distractions; they show while the offer stands or a mode is on ({@link #menuItems}). Reader Mode lays the article
 * over the page in the theme's colours, and Exit Reader Mode, or Escape in the page, takes it away.
 *
 * <p>The check runs once a document's load ends, and again a second after the last of its same-document address
 * changes, as a single-page app's route changes, which withdraw the offer meanwhile; a change of the fragment alone, as
 * a link to a heading makes, changes nothing. It never runs on Jasper's own {@code jasper-about:} pages or on a
 * challenge provider's address ({@link ChallengeFrames}), which Jasper's page scripts stay out of; and while the page
 * is not showing, as a background tab's, it waits until the page shows. A load start ends both modes and withdraws the
 * offer, as the overlay goes with its document.
 *
 * <p>Each check carries a token, the count of checks so far, which its report carries back: only the latest check's
 * answer counts, once (ruling D28), so neither a forged token, an old document's late answer nor a replayed one offers
 * anything; and an exit counts only from the current document. After the menu shows or hides the article, the page
 * takes the keyboard focus, so the keys reach the overlay. What a page reports is untrusted (4a's R38): a report that
 * is not one of its op's shapes, or longer than {@value #REPORT_LIMIT} characters, is ignored. UI thread.
 */
public final class PageTools implements AutoCloseable {
    /** What Reader Mode and Exit Reader Mode report, and their id. */
    public static final String READER = "browser.reader";
    /** What Remove Distractions and Exit Distraction Removal report, and their id. */
    public static final String DISTRACTIONS = "browser.distractions";
    /** The longest report a page's tools take. */
    static final int REPORT_LIMIT = 256;
    /** How many characters of reader text the offer needs more than. */
    static final int OFFER_LENGTH = 10;
    // How long a document's address must stay put before it is checked again.
    private static final Duration SETTLE = Duration.ofSeconds(1);
    // The ops of the opener bridge that report to the tools.
    private static final String READER_OP = "reader", DISTRACTIONS_OP = "distractions", OFF = "off";
    private static final String JASPER_ABOUT = "jasper-about:";
    private static final MenuItem SEPARATOR = new MenuItem("browser.tools.separator", "", "", "", true, true);
    private static final Runnable NOTHING = () -> { };
    private final BiConsumer<String, String> scripts;
    private final Supplier<String> readerCss;
    // The zoom the page shows at, in percent, as its item shows it.
    private final IntSupplier zoom;
    private final BooleanSupplier showing;
    private final BiFunction<Duration, Runnable, Runnable> later;
    private final Runnable focus, changed;
    // Whether the offer stands, and whether reader mode and distraction removal are on.
    private boolean offered, reading, removing;
    // Whether the document is one no check runs on; whether its load has ended; whether its check waits for the page to
    // show; and whether the tools have closed.
    private boolean skipped, documentLoaded, pending, closed;
    // How many checks have run; how many had when the document started, so its own tokens are the ones above; and the
    // token whose answer counts, 0 for none, as before any check, after an answer and after an address change.
    private long checks, documentStart, answerable;
    // The page's address as last heard, from its load start or an address change: a change of its fragment alone is none.
    private String address = "";
    // Ends the wait for the document's address to settle.
    private Runnable settle = NOTHING;

    /**
     * The tools of one page.
     *
     * @param scripts runs a script in the page's top frame, told the script and the address it goes by
     * @param readerCss the overlay's CSS as the theme is now: its colours on {@link ToolScripts#READER_ROOT}
     * @param zoom the zoom the page shows at, in percent, as its item shows it
     * @param showing whether the page shows now
     * @param later runs a task on the UI thread after a delay, answering what cancels it
     * @param focus gives the page the keyboard focus
     * @param changed told each time the menu's items change
     */
    public PageTools(BiConsumer<String, String> scripts, Supplier<String> readerCss, IntSupplier zoom, BooleanSupplier showing,
                     BiFunction<Duration, Runnable, Runnable> later, Runnable focus, Runnable changed) {
        this.scripts = Objects.requireNonNull(scripts, "scripts");
        this.readerCss = Objects.requireNonNull(readerCss, "readerCss");
        this.zoom = Objects.requireNonNull(zoom, "zoom");
        this.showing = Objects.requireNonNull(showing, "showing");
        this.later = Objects.requireNonNull(later, "later");
        this.focus = Objects.requireNonNull(focus, "focus");
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    /**
     * The page's main frame starts loading the document at {@code url}: both modes end and the offer goes, with the
     * document before, and a check waiting or settling ends.
     *
     * @param url the document's address
     */
    public void loadStarted(String url) {
        if (closed) return;
        boolean had = offered || reading || removing;
        offered = reading = removing = false;
        documentLoaded = pending = false;
        endSettling();
        documentStart = checks;
        answerable = 0;
        address = url == null ? "" : url;
        skipped = skipped(url);
        if (had) changed.run();
    }

    /** The page's main frame finished loading its document: it is checked, unless no check runs on it. */
    public void loaded() {
        if (closed) return;
        documentLoaded = true;
        if (!skipped) check();
    }

    /**
     * The page's main frame has a new address: once its document has loaded, a same-document change, which withdraws
     * the offer and checks the document again once its address has stayed put for a second. A change before the load
     * ends, the commit of a new document among them, does nothing: the load end checks. Nor does a change of the
     * fragment alone (ruling D30).
     *
     * @param url the new address
     */
    public void addressChanged(String url) {
        if (closed || url == null) return;
        String before = address;
        address = url;
        if (!documentLoaded || withoutFragment(before).equals(withoutFragment(url))) return;
        endSettling();
        answerable = 0;
        skipped = skipped(url);
        if (offered) {
            offered = false;
            changed.run();
        }
        if (!skipped) settle = later.apply(SETTLE, this::settled);
    }

    /** The page shows: a check that waited for it runs. */
    public void shown() {
        if (!closed && pending && !skipped) check();
    }

    /**
     * A report of the page's tools, as the opener bridge hands it on: reader mode's check answered, or reader mode or
     * distraction removal ended in the page, as Escape does there. Anything else is ignored.
     *
     * @param op the bridge op it came as: {@code reader} or {@code distractions}
     * @param json the request, a JSON object: {@code {"op": "reader", "check": token, "length": n}}, or
     *     {@code {"op": op, "check": token, "state": "off"}}
     */
    public void reported(String op, String json) {
        if (closed || op == null || json == null || json.length() > REPORT_LIMIT) return;
        Map<String, Object> report = StrictJson.object(json, 1).orElse(null);
        if (report == null || report.size() != 3 || !op.equals(report.get("op")) || !(report.get("check") instanceof Long token)) return;
        boolean exit = OFF.equals(report.get("state"));
        boolean current = token > documentStart && token <= checks;
        switch (op) {
            case READER_OP -> {
                if (report.get("length") instanceof Long length && length >= 0) {
                    if (answerable > 0 && token == answerable && !skipped) {
                        answerable = 0;
                        offer(length > OFFER_LENGTH);
                    }
                } else if (exit && current && reading) {
                    reading = false;
                    changed.run();
                }
            }
            case DISTRACTIONS_OP -> {
                if (exit && current && removing) {
                    removing = false;
                    changed.run();
                }
            }
            default -> { }
        }
    }

    /**
     * Reader Mode or Exit Reader Mode: the article is laid over the page, in the theme's colours as they are now, or
     * taken away; then the page takes the keyboard focus. Nothing while the items are not offered. Reader mode is on
     * only once its script is made, so a show whose CSS cannot be read leaves it off.
     */
    public void toggleReader() {
        if (closed || skipped) return;
        if (reading) {
            String script = ToolScripts.readerHide();
            reading = false;
            scripts.accept(script, ToolScripts.READER_URL);
        } else if (offered) {
            String script = ToolScripts.readerShow(readerCss.get(), checks);
            reading = true;
            scripts.accept(script, ToolScripts.READER_URL);
        } else {
            return;
        }
        focus.run();
        changed.run();
    }

    /**
     * The tools' items, first in the menu: Reader Mode, or Exit Reader Mode while it is on; Remove Distractions, or Exit
     * Distraction Removal while it is on; then a separator. None while the offer does not stand and no mode is on.
     *
     * @return the items, each item's id its action
     */
    public List<MenuItem> menuItems() {
        if (!offered && !reading && !removing) return List.of();
        return List.of(new MenuItem(READER, reading ? "Exit Reader Mode" : "Reader Mode", "", READER, true, false),
            new MenuItem(DISTRACTIONS, removing ? "Exit Distraction Removal" : "Remove Distractions", "", DISTRACTIONS, true, false),
            SEPARATOR);
    }

    /**
     * Whether the page offers the tools: its latest check found more than {@value #OFFER_LENGTH} characters of reader text.
     *
     * @return whether it does
     */
    public boolean offered() {
        return offered;
    }

    /**
     * Whether reader mode is on.
     *
     * @return whether it is
     */
    public boolean reading() {
        return reading;
    }

    /**
     * Whether distraction removal is on.
     *
     * @return whether it is
     */
    public boolean removing() {
        return removing;
    }

    /** The page's item closes: a settling check ends, and the tools do nothing from now on. */
    @Override public void close() {
        closed = true;
        endSettling();
    }

    /** Runs the document's check now, or once the page shows: a new token, whose answer alone counts. */
    private void check() {
        if (!showing.getAsBoolean()) {
            pending = true;
            return;
        }
        pending = false;
        answerable = ++checks;
        scripts.accept(ToolScripts.readerCheck(answerable), ToolScripts.READER_URL);
    }

    /** The document's address stayed put for a second: it is checked again. */
    private void settled() {
        settle = NOTHING;
        if (!closed && documentLoaded && !skipped) check();
    }

    private void endSettling() {
        Runnable cancel = settle;
        settle = NOTHING;
        cancel.run();
    }

    private void offer(boolean now) {
        if (offered == now) return;
        offered = now;
        changed.run();
    }

    private static String withoutFragment(String url) {
        int hash = url.indexOf('#');
        return hash < 0 ? url : url.substring(0, hash);
    }

    /** Whether no check runs on a document at {@code url}: one of Jasper's own pages, or a challenge provider's. */
    private static boolean skipped(String url) {
        return url == null || url.regionMatches(true, 0, JASPER_ABOUT, 0, JASPER_ABOUT.length()) || ChallengeFrames.is(url);
    }
}
