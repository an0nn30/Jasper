package dev.jasper.browser.logins;

import dev.jasper.browser.dialogs.SignInDialog;
import dev.jasper.browser.dialogs.SignInDialog.Login;
import dev.jasper.browser.dialogs.SignInDialog.Outcome;
import dev.jasper.browser.page.DialogPlace;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.SignIn;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.UiAnchor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The plugin's HTTP sign-ins (plan 8, spec section 13): each {@link SignIn} Chromium asks of a page goes to
 * {@link SignInDialog} over the page's item, one dialog per page at a time, the others waiting in the order asked, and
 * beyond {@link #WAITING_LIMIT} waiting a page's next one is cancelled at once, so a page cannot queue dialogs without
 * end. OK and a login chosen in the Credential Vault's picker sign in; anything else cancels. The dialog offers the
 * Vault's Password Manager, and "Remember this password", as the {@link Challenge} allows: for a Basic challenge from a
 * server, outside /dev/null, with the Vault, and Remember only for the top-level navigation's own (F5). Without the
 * Vault the dialog still shows, with neither, so pages can still sign in.
 *
 * <p>Each challenge is stamped with the page's document as it arrives; the page's next main-frame load start cancels
 * every challenge stamped earlier, shown or waiting, and closes its dialog (F8), as Chromium never says the request
 * behind one went away; a main frame's own challenge comes before its commit, so its answer always lands first. A user
 * name and password given for one challenge also answer the page's waiting challenges of the same server or proxy,
 * port, realm and scheme (F30), which Chromium does not resupply itself. The page's flood guard refuses its dialogs
 * once they come one straight after another, but not the top-level challenge of a navigation Jasper started for the
 * user ({@link #navigating}) while that navigation is pending and the user has cancelled none of its dialogs (L27,
 * L28). {@link #closed} cancels every challenge of the page, shown or waiting, exactly once, and withdraws a pick under
 * way; {@link #close()} does so for every page as the plugin stops. A password reaches Chromium as a {@code String},
 * which its callback takes and which cannot be zeroed; every array is zeroed once used. Names no Chromium type. UI
 * thread.
 */
public final class HttpSignIns {
    /** The most challenges a page has waiting behind its dialog: one more is cancelled at once. */
    public static final int WAITING_LIMIT = 8;
    private final Optional<VaultLogins> vault;
    // Where a failed save is told (the plan's Task 6).
    private final NotificationGroup notices;
    // The pages that were challenged or sent elsewhere, each until it closes.
    private final Map<Page, PageSignIns> pages = new IdentityHashMap<>();
    // Once the plugin stops, every challenge is cancelled at once.
    private boolean stopped;

    /**
     * The plugin's sign-ins, with the Vault's logins when the Vault is present.
     *
     * @param vault the Credential Vault's logins; empty without the Vault
     * @param notices where a failed save is told
     */
    public HttpSignIns(Optional<VaultLogins> vault, NotificationGroup notices) {
        this.vault = Objects.requireNonNull(vault, "vault");
        this.notices = Objects.requireNonNull(notices, "notices");
    }

    /**
     * {@code page} was challenged for one of its requests: the sign-in dialog shows over the page's item once the
     * page's earlier challenges have been answered, unless {@link #WAITING_LIMIT} wait already, when it is cancelled at
     * once. A page that has closed, or a challenge once the plugin stopped, has it cancelled.
     *
     * @param page the page whose request was challenged
     * @param place where its dialogs show
     * @param devNull whether the page's item is in /dev/null mode, whose dialog offers no Vault
     * @param signIn the sign-in Chromium waits for
     */
    public void challenged(Page page, DialogPlace place, boolean devNull, SignIn signIn) {
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(place, "place");
        Objects.requireNonNull(signIn, "signIn");
        if (stopped || page.dialogPlace().isEmpty()) {
            signIn.cancel();
            return;
        }
        PageSignIns signIns = of(page);
        Challenge challenge = Challenge.of(signIn);
        signIns.add(new Asked(signIn, challenge, place, devNull, page.document(), challenge.topLevel() && signIns.navigating));
    }

    /**
     * Jasper sends {@code page} elsewhere for the user ({@link Page#onNavigate}), which a page's script cannot do:
     * until the page's next main-frame load start, a top-level challenge that arrives shows its dialog even once the
     * page's flood guard has tripped (L27), so the user's own navigation is never refused for what the page did before
     * it. A main-frame load failure ends that, and so does a Cancel of one of those dialogs, which then refuses those
     * still waiting too (L28): a navigation that never commits gives a page at most one dialog past the trip. Nothing
     * for a page that has closed, or once the plugin stopped.
     *
     * @param page the page
     */
    public void navigating(Page page) {
        Objects.requireNonNull(page, "page");
        if (stopped || page.dialogPlace().isEmpty()) return;
        of(page).navigating = true;
    }

    /**
     * The page's main frame got the final response to a navigation, which nothing here waits for yet: the plan's Task 6
     * saves a remembered password on it.
     *
     * @param page the page
     * @param url the response's address
     * @param status its HTTP status
     */
    public void responded(Page page, String url, int status) {
        Objects.requireNonNull(page, "page");
    }

    /**
     * {@code page}'s item closes: each of its challenges, shown or waiting, is cancelled once, a pick under way is
     * withdrawn and its login zeroed unused, and the page is forgotten. Nothing for a page with none.
     *
     * @param page the page
     */
    public void closed(Page page) {
        PageSignIns signIns = pages.remove(Objects.requireNonNull(page, "page"));
        if (signIns != null) signIns.end();
    }

    /**
     * The plugin stops: every page's challenges, shown or waiting, are cancelled once, as {@link #closed} cancels one
     * page's, before Chromium closes; each that comes later is cancelled at once.
     */
    public void close() {
        stopped = true;
        List<PageSignIns> open = List.copyOf(pages.values());
        pages.clear();
        for (PageSignIns signIns : open) signIns.end();
    }

    /**
     * For a test: what answers {@code page}'s shown challenge with an outcome as its dialog would, even once the page's
     * sign-ins have ended.
     *
     * @throws IllegalStateException when none shows
     */
    Consumer<Outcome> shownAnswer(Page page) {
        PageSignIns signIns = pages.get(page);
        Asked asked = signIns == null ? null : signIns.shown;
        if (asked == null) throw new IllegalStateException("No challenge shows for the page");
        return outcome -> signIns.answered(asked, outcome);
    }

    /** {@code page}'s sign-ins, made the first time they are asked for. */
    private PageSignIns of(Page page) {
        PageSignIns signIns = pages.get(page);
        if (signIns == null) {
            signIns = new PageSignIns(page);
            pages.put(page, signIns);
        }
        return signIns;
    }

    /** One challenge of a page, as it arrived. */
    private static final class Asked {
        final SignIn signIn;
        final Challenge challenge;
        final DialogPlace place;
        final boolean devNull;
        // The page's document the challenge arrived in (F8).
        final long document;
        // Whether it arrived as a top-level challenge while the user's navigation was pending (L27); it shows past the
        // trip only if that navigation still is, its dialogs uncancelled, when it shows (L28).
        final boolean usersNavigation;
        // Ends its dialog, once it is shown or waiting to show; null before.
        Subscription dialog;

        Asked(SignIn signIn, Challenge challenge, DialogPlace place, boolean devNull, long document, boolean usersNavigation) {
            this.signIn = signIn;
            this.challenge = challenge;
            this.place = place;
            this.devNull = devNull;
            this.document = document;
            this.usersNavigation = usersNavigation;
        }

        /** Whether {@code other} asks for the same protection space: proxy or not, host, port, realm and scheme (F30). */
        boolean sameSpace(Asked other) {
            return challenge.proxy() == other.challenge.proxy() && challenge.host().equals(other.challenge.host())
                && challenge.port() == other.challenge.port() && signIn.realm().equals(other.signIn.realm())
                && challenge.scheme().equals(other.challenge.scheme());
        }
    }

    /** One page's challenges: the one whose dialog shows or is about to, and those waiting after it, in order. */
    private final class PageSignIns {
        private final Page page;
        private final List<Subscription> heard;
        private final ArrayDeque<Asked> waiting = new ArrayDeque<>();
        private Asked shown;
        // Whether a navigation Jasper started for the user is pending since the page's last main-frame load start, with
        // no main-frame load failure and no Cancel of its dialogs since.
        private boolean navigating;
        private boolean ended;

        PageSignIns(Page page) {
            this.page = page;
            heard = List.of(page.onLoadStart(address -> loadStarted()), page.onLoadFailure(failure -> navigating = false),
                page.onClose(() -> closed(page)));
        }

        void add(Asked asked) {
            if (ended) {
                asked.signIn.cancel();
                return;
            }
            if (shown != null && waiting.size() >= WAITING_LIMIT) {
                asked.signIn.cancel();
                return;
            }
            waiting.add(asked);
            next();
        }

        /** Shows the next challenge's dialog, while none shows. */
        private void next() {
            if (ended || shown != null || waiting.isEmpty()) return;
            Asked asked = waiting.poll();
            shown = asked;
            Challenge challenge = asked.challenge;
            boolean present = vault.isPresent();
            boolean offers = challenge.offersVault(asked.devNull, present);
            Function<UiAnchor, CompletableFuture<Optional<Login>>> manager = offers
                ? anchor -> pick(vault.get(), asked.place.owner().window(), anchor, search(challenge.host()))
                : anchor -> CompletableFuture.completedFuture(Optional.empty());
            // The dialog shows on a later turn, so what ends it is held at once.
            asked.dialog = SignInDialog.show(page, asked.place, challenge.host(), challenge.port(), challenge.realm(),
                challenge.proxy(), offers, challenge.remembers(asked.devNull, present), asked.usersNavigation && navigating,
                manager, outcome -> answered(asked, outcome));
        }

        /** {@code asked}'s dialog was answered: it signs in or is cancelled, and the next challenge's dialog shows. */
        void answered(Asked asked, Outcome outcome) {
            if (shown == asked) shown = null;
            switch (outcome) {
                case Outcome.Ok ok -> proceed(asked, ok.user(), ok.password());
                case Outcome.Picked picked -> proceed(asked, picked.user(), picked.password());
                case Outcome.Cancelled cancelled -> {
                    // L28: the user said no to the navigation's sign-in, whose 401 page then commits anyway.
                    if (asked.usersNavigation) navigating = false;
                    asked.signIn.cancel();
                }
            }
            next();
        }

        /**
         * Signs {@code asked} in with {@code user} and {@code password}, and so each waiting challenge of the same
         * protection space (F30); the array is zeroed after the last of them.
         */
        private void proceed(Asked asked, String user, char[] password) {
            try {
                if (ended) {
                    asked.signIn.cancel();
                    return;
                }
                String given = new String(password); // Chromium's callback takes a String, which cannot be zeroed
                asked.signIn.proceed(user, given);
                for (Iterator<Asked> each = waiting.iterator(); each.hasNext(); ) {
                    Asked other = each.next();
                    if (!asked.sameSpace(other)) continue;
                    each.remove();
                    other.signIn.proceed(user, given);
                }
            } finally {
                Arrays.fill(password, (char) 0);
            }
        }

        /** A main-frame load start: each challenge of an earlier document is cancelled, its dialog closed (F8). */
        private void loadStarted() {
            navigating = false;
            long now = page.document();
            var stale = new ArrayList<Asked>();
            for (Iterator<Asked> each = waiting.iterator(); each.hasNext(); ) {
                Asked asked = each.next();
                if (asked.document >= now) continue;
                each.remove();
                stale.add(asked);
            }
            for (Asked asked : stale) asked.signIn.cancel();
            if (shown != null && shown.document < now) shown.dialog.close();
        }

        /** The page's item closes: every challenge is cancelled, once, and the page is heard no more. */
        void end() {
            if (ended) return;
            ended = true;
            heard.forEach(Subscription::close);
            List<Asked> dropped = List.copyOf(waiting);
            waiting.clear();
            Asked current = shown;
            shown = null;
            for (Asked asked : dropped) asked.signIn.cancel();
            if (current != null) {
                current.dialog.close();
                // A host whose dialog closes later would answer it then; Chromium's request must not wait for that.
                current.signIn.cancel();
            }
        }
    }

    /**
     * Opens the Vault's picker on {@code anchor} for a login whose user name and password the dialog then owns: a copy
     * of the password, as the Vault zeroes its own as the credential closes. Cancelling the answer withdraws the pick,
     * and a login that arrives after that is zeroed.
     */
    private static CompletableFuture<Optional<Login>> pick(VaultLogins vault, WindowHandle owner, UiAnchor anchor, String search) {
        var chosen = new Login[1];
        CompletableFuture<Boolean> picking = vault.pick(owner, anchor, search,
            (user, password) -> chosen[0] = new Login(user, password.clone()));
        var result = new CompletableFuture<Optional<Login>>();
        result.whenComplete((login, failure) -> {
            if (result.isCancelled()) picking.cancel(false);
        });
        picking.whenComplete((used, failure) -> {
            Login login = chosen[0];
            chosen[0] = null;
            if (login != null && failure == null && Boolean.TRUE.equals(used) && result.complete(Optional.of(login))) return;
            if (login != null) Arrays.fill(login.password(), (char) 0);
            result.complete(Optional.empty());
        });
        return result;
    }

    /** The picker's starting search for a challenger's host: without one leading {@code www.}, if a host is left (F26). */
    static String search(String host) {
        return host.startsWith("www.") && host.length() > 4 ? host.substring(4) : host;
    }
}
