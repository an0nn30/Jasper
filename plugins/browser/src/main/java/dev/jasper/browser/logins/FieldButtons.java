package dev.jasper.browser.logins;

import dev.jasper.browser.json.StrictJson;
import dev.jasper.browser.page.LastPress;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageQuery;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ContentArea;
import dev.jasper.sdk.ui.UiAnchor;
import java.awt.Dimension;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The Credential Vault's buttons on one page's login fields (plan 8). {@link #SCRIPT}, {@code login-fields.js}, runs in
 * each http or https document of the page's main frame and shows a button inside the field the user focuses: a
 * password field's opens the Vault's picker for a password, a username field's for a username and a password. Its click
 * sends a {@value #PICK} query, which this class answers. Any script of the page can send one, so each is untrusted
 * (R38): the picker opens, one at a time, only for an http or https document, only at a field on the page's canvas, and
 * only after a click in the page inside that field (L20), so a page cannot move a field under a click meant for
 * something else; it is searched for the asking origin's host, never a text the page gives. The page is sent only what
 * the user chose, and only while it shows the origin that asked.
 *
 * <p>The picker closes, with its anchor, as the query is cancelled (the page scrolled, which the script reports by
 * cancelling it, or its frame navigated), as the anchor closes (the page's canvas was resized, hidden or moved), as the
 * page closes (F15), and when its item asks ({@link #closePicker}: its zoom changed, or it navigated). The page is then
 * told nothing was chosen, unless its query is over. A failure is logged with the exception alone, never a username,
 * a password or the page's host. Names no Chromium type. UI thread, where the Vault is called.
 */
public final class FieldButtons {
    /** The field buttons' page script, {@code login-fields.js}, which a page's main frame runs as a document starts. */
    public static final String SCRIPT = source();
    /** The bridge op a button's click sends. */
    public static final String PICK = "login.pick";
    /** What a page is told nothing was chosen with. */
    static final String NOTHING = "{}";
    private static final String MALFORMED_WHY = "Jasper does not understand this request.",
        NOT_WEB = "The password manager fills only web pages.", OPEN = "The password manager is open already.",
        NO_CLICK = "The password manager opens only after a click in the page.", OFF_PAGE = "The field is not on the page.";
    // The members a pick has, and the largest a coordinate or size may be, either way.
    private static final Set<String> MEMBERS = Set.of("op", "field", "x", "y", "width", "height");
    private static final long LIMIT = 1_000_000;
    private static final System.Logger LOG = System.getLogger(FieldButtons.class.getName());
    private final VaultLogins vault;
    private final BiFunction<String, char[], String> writer;
    // The page whose queries the buttons answer, from its first; and the pick under way, or null.
    private Page page;
    private Pick open;

    /**
     * A field's place on the page, in CSS pixels from the viewport's top left, as {@code login-fields.js} measures it.
     *
     * @param x the left edge
     * @param y the top edge
     * @param width the width, not negative
     * @param height the height, not negative
     */
    public record FieldRect(int x, int y, int width, int height) {
        /**
         * Refuses a negative size.
         *
         * @throws IllegalArgumentException when {@code width} or {@code height} is negative
         */
        public FieldRect {
            if (width < 0 || height < 0) throw new IllegalArgumentException("A field's size must not be negative");
        }
    }

    /** The two fields a pick fills, as the request names them. */
    private enum Field {
        PASSWORD, USERNAME;

        static Optional<Field> of(Object name) {
            if ("password".equals(name)) return Optional.of(PASSWORD);
            if ("username".equals(name)) return Optional.of(USERNAME);
            return Optional.empty();
        }
    }

    /**
     * The buttons of one page, answered from {@code vault}.
     *
     * @param vault the Credential Vault's logins
     */
    public FieldButtons(VaultLogins vault) {
        this(vault, FieldButtons::reply);
    }

    /**
     * As {@link #FieldButtons(VaultLogins)}, the answer written by {@code writer} from the username to fill, {@code ""}
     * for none, and the password array, as {@link #reply} writes it: a test's records it.
     */
    FieldButtons(VaultLogins vault, BiFunction<String, char[], String> writer) {
        this.vault = Objects.requireNonNull(vault, "vault");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    /**
     * The field's place a pick names: exactly the members {@code op}, {@code field}, {@code x}, {@code y},
     * {@code width} and {@code height}, the last four integers within ±1,000,000, the sizes not negative (F2).
     *
     * @param request the request, as the page sent it
     * @return the place; empty for a request with a member missing, another member, a member that is no integer, as a
     *     fractional number, a {@code null} or a text is not, or a negative size
     */
    public static Optional<FieldRect> rect(Map<String, Object> request) {
        if (!request.keySet().equals(MEMBERS)) return Optional.empty();
        long[] values = new long[4];
        List<String> names = List.of("x", "y", "width", "height");
        for (int at = 0; at < values.length; at++) {
            if (!(request.get(names.get(at)) instanceof Long value) || value < -LIMIT || value > LIMIT) return Optional.empty();
            values[at] = value;
        }
        if (values[2] < 0 || values[3] < 0) return Optional.empty();
        return Optional.of(new FieldRect((int) values[0], (int) values[1], (int) values[2], (int) values[3]));
    }

    /**
     * The area of the page's canvas a field covers: {@code css} scaled by {@code zoom} and rounded outwards, then clipped
     * to the canvas, which an area anchor must lie inside.
     *
     * @param css the field's place, in CSS pixels
     * @param zoom the zoom the page shows at: the item's percent divided by 100
     * @param view the size of the page's canvas
     * @return the area; empty when nothing of the field is left on the canvas
     * @throws IllegalArgumentException for a zoom that is not a positive finite number
     */
    public static Optional<ContentArea> area(FieldRect css, double zoom, Dimension view) {
        Objects.requireNonNull(css, "css");
        Objects.requireNonNull(view, "view");
        if (!(zoom > 0) || !Double.isFinite(zoom)) throw new IllegalArgumentException("A zoom must be a positive number");
        long left = Math.max(0, (long) Math.floor(css.x() * zoom)), top = Math.max(0, (long) Math.floor(css.y() * zoom));
        long right = Math.min(view.width, (long) Math.ceil(((long) css.x() + css.width()) * zoom));
        long bottom = Math.min(view.height, (long) Math.ceil(((long) css.y() + css.height()) * zoom));
        if (right <= left || bottom <= top) return Optional.empty();
        return Optional.of(new ContentArea((int) left, (int) top, (int) (right - left), (int) (bottom - top)));
    }

    /**
     * Answers {@code query}, a {@value #PICK} of the page's, in this order (F3, L20): {@link PageQuery#MALFORMED} for
     * another op, an unknown field or a place {@link #rect} refuses; {@link PageQuery#REFUSED} for an origin that is
     * opaque or not http or https; {@link PageQuery#BUSY} while this page's picker is open; nothing more for a query that
     * is over, and {@link PageQuery#GONE} for one whose page shows another origin (L18); {@link PageQuery#REFUSED} when
     * {@code area} gives nothing, as for a field off the canvas; {@link PageQuery#REFUSED} without a press within the
     * activation that lies inside that area; {@link PageQuery#REFUSED} when {@code anchor} refuses the area with an
     * {@link IllegalArgumentException} or gives a closed anchor; and {@link PageQuery#REFUSED} when the activation does
     * not allow it, which is spent then, and only then, as the picker is about to open. The Vault's picker then opens
     * on the anchor, searched for the origin's host without one leading {@code www.} (F26), and the query is answered
     * later, once: a password field {@code {"password": …}}, a username field {@code {"username": …, "password": …}},
     * each without an empty member, and {@code {}} when nothing was chosen. The answer's text is made only while the
     * query is live, so no password's text is made for a page that would not be sent it; that text, a Java string,
     * cannot be zeroed.
     *
     * @param query the page's query
     * @param window the window the item shows in, which the Vault's prompts open over
     * @param area the area of the page's canvas a field's place covers, in the canvas's coordinates, or nothing
     * @param anchor an anchor on such an area; throws {@link IllegalArgumentException} for one it refuses
     * @throws IllegalArgumentException for a query of another page than the buttons' first query's
     */
    public void query(PageQuery query, WindowHandle window, Function<FieldRect, Optional<ContentArea>> area,
                      Function<ContentArea, UiAnchor> anchor) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(anchor, "anchor");
        watch(query.page());
        Map<String, Object> request = query.request();
        Optional<Field> field = Field.of(request.get("field"));
        Optional<FieldRect> rect = rect(request);
        if (!PICK.equals(request.get("op")) || field.isEmpty() || rect.isEmpty()) {
            query.fail(PageQuery.MALFORMED, MALFORMED_WHY);
            return;
        }
        Optional<String> search = search(query.origin());
        if (search.isEmpty()) {
            query.fail(PageQuery.REFUSED, NOT_WEB);
            return;
        }
        if (open != null) {
            query.fail(PageQuery.BUSY, OPEN);
            return;
        }
        if (!query.live()) {
            query.answer(NOTHING); // sends nothing once it is over, and GONE once its document has gone
            return;
        }
        Optional<ContentArea> covered = area.apply(rect.get());
        if (covered.isEmpty()) {
            query.fail(PageQuery.REFUSED, OFF_PAGE);
            return;
        }
        if (query.press().filter(press -> inside(press, covered.get())).isEmpty()) {
            query.fail(PageQuery.REFUSED, NO_CLICK);
            return;
        }
        UiAnchor at;
        try {
            at = anchor.apply(covered.get());
        } catch (IllegalArgumentException refused) {
            at = null;
        }
        if (at == null || !at.isOpen()) {
            query.fail(PageQuery.REFUSED, OFF_PAGE);
            return;
        }
        if (!query.consumeActivation()) {
            at.close();
            query.fail(PageQuery.REFUSED, NO_CLICK);
            return;
        }
        var pick = new Pick(query, at, field.get());
        open = pick;
        pick.start(window, search.get());
    }

    /**
     * Closes the picker open for the page, and its anchor, as its item's zoom changes or it navigates: the page is told
     * nothing was chosen. Nothing when none is open.
     */
    public void closePicker() {
        if (open != null) open.close();
    }

    /** The buttons answer {@code asking}'s queries: the first time, its close is heard, once (F15). */
    private void watch(Page asking) {
        if (page == asking) return;
        if (page != null) throw new IllegalArgumentException("A login query of another page");
        page = asking;
        page.onClose(this::closePicker);
    }

    /** Whether {@code press} lies inside {@code area}, its right and bottom edges outside it. */
    private static boolean inside(LastPress press, ContentArea area) {
        return press.x() >= area.x() && press.y() >= area.y() && (long) press.x() < (long) area.x() + area.width()
            && (long) press.y() < (long) area.y() + area.height();
    }

    /**
     * The answer for a login: {@code {"username": …, "password": …}}, each member only when it is not empty, written
     * with {@link StrictJson#write}. The password's text it makes, a Java string, cannot be zeroed.
     *
     * @param username the username to fill, or {@code ""} for none, as from a password field
     * @param password the password array, which it does not keep
     * @return the JSON
     */
    static String reply(String username, char[] password) {
        var members = new LinkedHashMap<String, Object>();
        if (!username.isEmpty()) members.put("username", username);
        if (password.length > 0) members.put("password", new String(password));
        return StrictJson.write(members);
    }

    /**
     * The picker's starting search for {@code origin}: its host, lower case, an IPv6 host in its brackets, without one
     * leading {@code www.} where a host is left after it; empty for an opaque origin or one that is not http or https.
     */
    static Optional<String> search(Optional<String> origin) {
        if (origin.isEmpty()) return Optional.empty();
        String text = origin.get();
        int start = text.startsWith("https://") ? 8 : text.startsWith("http://") ? 7 : -1;
        if (start < 0) return Optional.empty();
        String authority = text.substring(start);
        int end = authority.startsWith("[") ? authority.indexOf(']') + 1 : authority.indexOf(':');
        String host = (end < 0 ? authority : authority.substring(0, end)).toLowerCase(Locale.ROOT);
        if (host.isEmpty() || host.chars().anyMatch(c -> "/?#@ ".indexOf(c) >= 0)) return Optional.empty();
        return Optional.of(host.startsWith("www.") && host.length() > 4 ? host.substring(4) : host);
    }

    /** The picker's pick for one query: until it ends, the page's picker is open, and the page is busy. */
    private final class Pick {
        private final PageQuery query;
        private final UiAnchor anchor;
        private final Field field;
        private List<Subscription> heard = List.of();
        private CompletableFuture<Boolean> picking;
        // The answer, made while the chosen login was open; null for none.
        private String reply;
        private boolean closed, ended;

        Pick(PageQuery query, UiAnchor anchor, Field field) {
            this.query = query;
            this.anchor = anchor;
            this.field = field;
        }

        /**
         * The picker opens over {@code window}, searched for {@code search}; the query's cancel and the anchor's close
         * close it.
         */
        void start(WindowHandle window, String search) {
            heard = List.of(query.onCancelled(this::close), anchor.onClosed(this::close));
            if (closed) {
                ended(null);
                return;
            }
            try {
                picking = vault.pick(window, anchor, search, this::fill);
            } catch (RuntimeException failure) {
                ended(failure);
                return;
            }
            picking.whenComplete((used, failure) -> ended(failure));
            if (closed) picking.cancel(false);
        }

        /**
         * The user chose a login, whose username and own password array are handed over while it is open: the answer is
         * made, but only while the query is live and the pick open.
         */
        private void fill(String username, char[] password) {
            if (closed || !query.live()) return;
            reply = writer.apply(field == Field.USERNAME ? username : "", password);
        }

        /** The pick is withdrawn, which closes the picker, and so is its anchor. Once. */
        void close() {
            if (closed) return;
            closed = true;
            if (picking != null) picking.cancel(false);
            anchor.close();
        }

        /**
         * The pick ended: the page is no longer busy, its anchor closes, and the query is answered, once, with what was
         * chosen or {@code {}}; a failure is logged with the exception alone, and a withdrawn pick is none.
         */
        private void ended(Throwable failure) {
            if (ended) return;
            ended = true;
            if (open == this) open = null;
            heard.forEach(Subscription::close);
            closed = true;
            anchor.close();
            Throwable cause = failure instanceof CompletionException wrapped && wrapped.getCause() != null
                ? wrapped.getCause() : failure;
            if (cause != null && !(cause instanceof CancellationException))
                LOG.log(System.Logger.Level.WARNING, "The password manager could not fill a page's field", cause);
            String answer = reply == null ? NOTHING : reply;
            reply = null;
            query.answer(answer);
        }
    }

    private static String source() {
        try (InputStream in = FieldButtons.class.getResourceAsStream("login-fields.js")) {
            if (in == null) throw new IllegalStateException("The Browser plugin has no login-fields.js");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
