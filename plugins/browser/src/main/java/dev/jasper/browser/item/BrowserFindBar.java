package dev.jasper.browser.item;

import dev.jasper.browser.page.PageFind;
import dev.jasper.browser.page.PageFind.Result;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.KeyStroke;

/**
 * A browser item's find bar (spec section 10), as SDK nodes the kit draws, modelled on the Editor's: the query field,
 * whose Enter goes to the next match and Shift+Enter to the one before; the Match Case and Regular Expression toggles,
 * each of which runs the search again; the status ("N matches", "1 match", "No matches" or "Invalid pattern"); and the
 * Previous, Next and Close buttons. Escape anywhere in the bar closes it, and closing it clears every highlight and gives
 * the page the focus back. A regular expression is JavaScript's, as the page's {@link PageFind} runs it. Jasper's Find
 * opens the bar with a one-line selection of the page as its query, else its last query; Find Next and Find Previous
 * go to the next and previous match, and with the bar closed show it and search for its query again. Each search shows
 * its result once it settles, and later changes as they come: a frame's late answer, a changed document, and the end
 * of the search with the page's next document, after which Enter searches again. UI thread.
 */
final class BrowserFindBar {
    /** The bar and its nodes; each button reports its own id. */
    static final String ROOT = "browser.find", FIND = "browser.find.query", CASE = "browser.find.case", REGEX = "browser.find.regex",
        STATUS = "browser.find.status", PREVIOUS = "browser.find.previous", NEXT = "browser.find.next", CLOSE = "browser.find.close";
    private static final boolean MAC_OS = System.getProperty("os.name", "").startsWith("Mac");
    private final Supplier<Optional<PageFind>> target;
    private final Runnable changed;
    private final Consumer<String> focus;
    // The page's find while the bar shows, and what tells the bar of its changes.
    private PageFind captured;
    private Subscription watching = () -> { };
    // The search whose result the status waits for, and how many times the bar has been opened: a selection is the
    // query only for the opening that asked for it.
    private CompletableFuture<Result> searching;
    private long opened;
    private String query = "", status = "";
    private boolean matchCase, regex, visible, edited;

    /**
     * A closed find bar.
     *
     * @param target the page's find, or empty when there is none
     * @param changed told whenever the bar's nodes change, to show them
     * @param focus told the node to take the focus: the query field, its text then selected, or as the bar closes
     *     {@link BrowserItem#PAGE}, the page
     */
    BrowserFindBar(Supplier<Optional<PageFind>> target, Runnable changed, Consumer<String> focus) {
        this.target = target;
        this.changed = changed;
        this.focus = focus;
    }

    /**
     * The bar, hidden while it is closed: one compact row for the item's DOCUMENT split.
     *
     * @return the bar's row
     */
    Nodes.Stack node() {
        var field = new Nodes.Field(FIND, ControlState.READY, Nodes.FieldKind.SEARCH, query, "Find", "Find", NEXT, Optional.empty(),
            Nodes.FieldPresentation.EMBEDDED, PREVIOUS, CLOSE, "");
        return new Nodes.Stack(ROOT, new ControlState(visible, true, false, ""), Nodes.LayoutRole.INLINE, List.of(field,
            toggle(CASE, "match.case", "Match Case", matchCase), toggle(REGEX, "regex", "Regular Expression", regex),
            new Nodes.Text(STATUS, ControlState.READY, status, Nodes.TextRole.CAPTION),
            icon(PREVIOUS, "chevron.up", "Previous Match (Shift+Enter)"), icon(NEXT, "chevron.down", "Next Match (Enter)"),
            icon(CLOSE, "close", "Close (Escape)")), CLOSE);
    }

    /**
     * Takes what the bar's nodes report while it shows.
     *
     * @param event what a node reported
     * @return whether it was the bar's
     */
    boolean event(UiEvent event) {
        if (!visible) return false;
        if (event instanceof UiEvent.Edited edit && edit.id().equals(FIND)) {
            query = edit.text();
            edited = true;
            search();
            changed.run();
            return true;
        }
        if (!(event instanceof UiEvent.Action action)) return false;
        switch (action.id()) {
            case NEXT -> next();
            case PREVIOUS -> previous();
            case CLOSE -> close();
            case CASE -> {
                matchCase = !matchCase;
                search();
                changed.run();
            }
            case REGEX -> {
                regex = !regex;
                search();
                changed.run();
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * Opens the bar, or focuses it again, with the page's selection as its query when that is one line, else its last
     * query, and searches for it unless that search stands. Typing before the selection arrives keeps what is typed.
     */
    void open() {
        if (!show()) return;
        PageFind find = captured;
        long asked = ++opened;
        find.selection().thenAccept(selected -> {
            if (asked != opened || !visible || captured != find || edited) return;
            boolean seeded = !selected.isEmpty() && !selected.equals(query);
            if (seeded) query = selected;
            if (seeded || find.result().isEmpty()) search();
            changed.run();
            if (seeded) focus.accept(FIND);
        });
    }

    /** Goes to the next match; with the bar closed, shows it and searches again. */
    void next() {
        step(true);
    }

    /** Goes to the match before; with the bar closed, shows it and searches again. */
    void previous() {
        step(false);
    }

    /** Closes the bar: the page's highlights clear and the page takes the focus. The query stays for the next opening. */
    void close() {
        if (!visible) return;
        visible = false;
        opened++;
        searching = null;
        watching.close();
        watching = () -> { };
        if (captured != null) captured.clear();
        captured = null;
        status = "";
        changed.run();
        focus.accept(BrowserItem.PAGE);
    }

    /**
     * Find's key: Cmd+F on macOS, Ctrl+F elsewhere.
     *
     * @param macOs whether the keys are macOS's
     * @return the key
     */
    static KeyStroke findKey(boolean macOs) {
        return KeyStroke.getKeyStroke(KeyEvent.VK_F, macOs ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK);
    }

    /**
     * Find Next's and Find Previous's keys, as in Editor tabs: Cmd+G and Shift+Cmd+G on macOS; F3 and Shift+F3 elsewhere,
     * where Ctrl+Shift+G is Jasper's own Find Next.
     *
     * @param backwards Find Previous rather than Find Next
     * @param macOs whether the keys are macOS's
     * @return the key
     */
    static KeyStroke findAgainKey(boolean backwards, boolean macOs) {
        int shift = backwards ? InputEvent.SHIFT_DOWN_MASK : 0;
        return macOs ? KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.META_DOWN_MASK | shift) : KeyStroke.getKeyStroke(KeyEvent.VK_F3, shift);
    }

    /** {@link #findKey(boolean)} on this platform. */
    static KeyStroke findKey() {
        return findKey(MAC_OS);
    }

    /** {@link #findAgainKey(boolean, boolean)} on this platform. */
    static KeyStroke findAgainKey(boolean backwards) {
        return findAgainKey(backwards, MAC_OS);
    }

    /** Shows the bar on the page's find and focuses its query; false when the page has none. */
    private boolean show() {
        PageFind find = target.get().orElse(null);
        if (find == null) return false;
        if (find != captured) {
            watching.close();
            captured = find;
            watching = find.onChange(this::resultChanged);
        }
        visible = true;
        edited = false;
        changed.run();
        focus.accept(FIND);
        return true;
    }

    private void step(boolean forward) {
        if (!visible) {
            if (show()) {
                search();
                changed.run();
            }
            return;
        }
        if (query.isEmpty()) return;
        if (captured.result().isEmpty()) search(); // the page's next document ended the search
        else status = text(forward ? captured.next() : captured.previous());
        changed.run();
    }

    /** Searches the page for the query; an empty one clears the page. The status shows the result once it settles. */
    private void search() {
        searching = null;
        if (query.isEmpty()) {
            captured.clear();
            status = "";
            return;
        }
        CompletableFuture<Result> asked = captured.find(query, matchCase, regex);
        searching = asked;
        asked.thenAccept(result -> {
            if (asked != searching || !visible) return;
            searching = null;
            status = text(result);
            changed.run();
        });
    }

    /**
     * The page's find changed on its own after its search settled: the status follows; blank once the search has ended,
     * even while it had not settled.
     */
    private void resultChanged() {
        if (!visible || captured == null) return;
        Optional<Result> now = captured.result();
        if (now.isEmpty()) {
            searching = null;
            status = "";
        } else if (searching == null) {
            status = text(now.get());
        } else {
            return;
        }
        changed.run();
    }

    /** What the status says of {@code result}. */
    private static String text(Result result) {
        if (result.invalid()) return "Invalid pattern";
        return result.count() == 0 ? "No matches" : result.count() == 1 ? "1 match" : result.count() + " matches";
    }

    private static Nodes.Button icon(String id, String icon, String tooltip) {
        return new Nodes.Button(id, ControlState.READY, "", icon, Nodes.ButtonKind.GHOST, id, tooltip);
    }

    private static Nodes.Button toggle(String id, String icon, String tooltip, boolean selected) {
        return new Nodes.Button(id, ControlState.READY, "", icon, Nodes.ButtonKind.GHOST, id, tooltip, Nodes.ButtonPresentation.STANDARD,
            Optional.empty(), Optional.of(selected));
    }
}
