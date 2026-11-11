package dev.jasper.browser.page;

import dev.jasper.sdk.Subscription;
import java.awt.EventQueue;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A request of a page's top frame that is answered later, as the password manager answers a {@code login.pick} once the
 * user has chosen in its picker (plan 8): the page that asked, the origin of the document that asked, the request as
 * the page sent it through the {@code links} package's opener bridge, and the one reply it gets. Any script of the page
 * can send such a request, so it is untrusted (R38): whoever answers decides what the page gets.
 *
 * <p>The answer, a failure and the cancel share one atomic, so only the first of them sends or cancels. The answer
 * reaches the page only while its main frame still shows the origin that asked, as far as the bridge has heard; once it
 * shows another, or an opaque one, which equals no origin, not even another opaque one, the page is told {@link #GONE}
 * and sent nothing more; {@link #live()} says whether an answer would still reach it. Once Chromium has stopped, the
 * reply sends nothing, and the answer and the failure say so. Chromium cancels the request when the frame that asked
 * navigates or closes, or when the page cancels it, and the page's close cancels it too: after {@link #cancelled()}
 * nothing is sent. It names no Chromium type. Any thread, but where a method says otherwise.
 */
public final class PageQuery {
    /**
     * Why a query failed, as the page's script is told: not a request, not allowed, the document that asked has gone,
     * busy. They are the opener bridge's own codes.
     */
    public static final int MALFORMED = 1, REFUSED = 2, GONE = 3, BUSY = 5;
    private static final String GONE_WHY = "The document that asked is no longer shown.";
    private static final System.Logger LOG = System.getLogger(PageQuery.class.getName());
    private final Page page;
    private final Optional<String> origin;
    private final Map<String, Object> request;
    private final BooleanSupplier activation;
    private final Supplier<Optional<LastPress>> press;
    private final Supplier<Optional<String>> currentOrigin;
    private final BiPredicate<Integer, String> reply;
    // Whether the query was answered, failed or cancelled; and whether its activation was asked for.
    private final AtomicBoolean over = new AtomicBoolean(), activationAsked = new AtomicBoolean();
    // Who hears the cancel, and whether they have been told. UI thread.
    private final List<Runnable> cancelListeners = new CopyOnWriteArrayList<>();
    private boolean toldCancelled;

    /**
     * A query of {@code page}'s, which nothing has answered.
     *
     * @param page the page that asked
     * @param origin the origin of the document that asked, as Chromium's thread saw it when it took the request; empty
     *     when opaque
     * @param request the request's members, as the page sent them
     * @param activation whether the user's activation allows the request, which it consumes; asked once at most
     * @param press the mouse press within the activation as it was when the request came, or empty for none
     * @param currentOrigin the origin of the document the page's main frame shows now; empty when opaque
     * @param reply told 0 and the answer, or a failure's code and why, once; answers whether the page was sent it, which
     *     it is not once Chromium has stopped
     */
    public PageQuery(Page page, Optional<String> origin, Map<String, Object> request, BooleanSupplier activation,
                     Supplier<Optional<LastPress>> press, Supplier<Optional<String>> currentOrigin, BiPredicate<Integer, String> reply) {
        this.page = Objects.requireNonNull(page, "page");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.request = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(request, "request")));
        this.activation = Objects.requireNonNull(activation, "activation");
        this.press = Objects.requireNonNull(press, "press");
        this.currentOrigin = Objects.requireNonNull(currentOrigin, "currentOrigin");
        this.reply = Objects.requireNonNull(reply, "reply");
    }

    /**
     * The page that asked.
     *
     * @return the page
     */
    public Page page() {
        return page;
    }

    /**
     * The origin of the document that asked, as Chromium's thread saw it when it took the request.
     *
     * @return the origin, as the opener bridge writes one; empty when opaque
     */
    public Optional<String> origin() {
        return origin;
    }

    /**
     * What the page asked, which no one has checked but for its op.
     *
     * @return the request's members, in the page's order; unmodifiable
     */
    public Map<String, Object> request() {
        return request;
    }

    /**
     * Consumes the user's activation for this request: whether it allows the request, asked once. Ask it only once the
     * request is otherwise allowed, so a refused request spends nothing.
     *
     * @return whether the activation allowed it the first time; false each time after
     */
    public boolean consumeActivation() {
        return activationAsked.compareAndSet(false, true) && activation.getAsBoolean();
    }

    /**
     * The mouse press that would activate the request, as it was when the request came: its point, on the page's
     * canvas, says where the user clicked, which a password manager's pick must lie in (plan 8's L20). Asking spends
     * nothing.
     *
     * @return the press; empty when no press within the activation came before the request
     */
    public Optional<LastPress> press() {
        return press.get();
    }

    /**
     * Answers the query with {@code json}, once: the page is sent it only while its main frame shows the origin that
     * asked, and is otherwise told {@link #GONE}, as it is when the origin it shows cannot be read.
     *
     * @param json the answer, a JSON text Jasper wrote
     * @return whether the page was sent {@code json}: false once the query was answered, failed or cancelled, when the
     *     document that asked has gone, and once Chromium has stopped
     */
    public boolean answer(String json) {
        Objects.requireNonNull(json, "json");
        if (!over.compareAndSet(false, true)) return false;
        if (!showsAskingOrigin()) {
            reply.test(GONE, GONE_WHY);
            return false;
        }
        return reply.test(0, json);
    }

    /**
     * Fails the query, once.
     *
     * @param code why, such as {@link #REFUSED}: above 0
     * @param why the page script's message
     * @return whether the page was told: false once the query was answered, failed or cancelled, and once Chromium has
     *     stopped
     * @throws IllegalArgumentException for a code of 0 or below, which is no failure's
     */
    public boolean fail(int code, String why) {
        if (code <= 0) throw new IllegalArgumentException("Not a failure's code: " + code);
        Objects.requireNonNull(why, "why");
        if (!over.compareAndSet(false, true)) return false;
        return reply.test(code, why);
    }

    /**
     * Whether an answer would still reach the page now: the query is not yet answered, failed or cancelled, and the
     * page's main frame shows the origin that asked, never an opaque one. Its answerer asks before making what only
     * the document that asked may have, such as a password's text, so none is made for a query that would send nothing.
     *
     * @return whether it is live
     */
    public boolean live() {
        return !over.get() && showsAskingOrigin();
    }

    /**
     * Tells {@code listener} once, on the UI thread, when the query is cancelled; at once when it has been told
     * already. A query answered or failed first is never cancelled. A listener that throws as the cancel is told is
     * logged, and the others are told all the same. UI thread.
     *
     * @param listener told on the UI thread
     * @return the registration
     */
    public Subscription onCancelled(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        if (toldCancelled) {
            listener.run();
            return () -> { };
        }
        cancelListeners.add(listener);
        return () -> cancelListeners.remove(listener);
    }

    /**
     * The query is cancelled, unless it was answered or failed first: nothing is sent from now on, and those who asked
     * {@link #onCancelled} are told, on the UI thread, once. Any thread, as Chromium cancels on its own.
     */
    public void cancelled() {
        if (!over.compareAndSet(false, true)) return;
        if (EventQueue.isDispatchThread()) tellCancelled();
        else EventQueue.invokeLater(this::tellCancelled);
    }

    /** Whether the query was answered, failed or cancelled. */
    boolean over() {
        return over.get();
    }

    /**
     * Whether the page's main frame shows the origin that asked: never an opaque one, which equals no origin, and not
     * when the origin it shows cannot be read.
     */
    private boolean showsAskingOrigin() {
        try {
            return origin.isPresent() && origin.equals(currentOrigin.get());
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A page query could not read the page's origin", failure);
            return false;
        }
    }

    /** Tells those who asked {@link #onCancelled}, each contained: one that throws stops no other, nor the close. */
    private void tellCancelled() {
        toldCancelled = true;
        List<Runnable> told = List.copyOf(cancelListeners);
        cancelListeners.clear();
        for (Runnable listener : told) {
            try {
                listener.run();
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "A page query's cancel listener failed", failure);
            }
        }
    }
}
