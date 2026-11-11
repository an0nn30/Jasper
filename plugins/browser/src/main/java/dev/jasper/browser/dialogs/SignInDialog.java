package dev.jasper.browser.dialogs;

import dev.jasper.browser.page.DialogPlace;
import dev.jasper.browser.page.Page;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiAnchor;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Jasper's dialog for an HTTP sign-in one of a page's requests is challenged for (plan 8, spec section 13): a modal
 * over the page's item ({@link PageModal}), titled "Sign In", whose first line says who asks, such as "The website
 * “intranet.example” requires a user name and password for “Corp”.", over a User name field and a Password field and,
 * when the sign-in can be remembered, "Remember this password", off. Its buttons are Password Manager, when the dialog
 * offers it, then Cancel and OK, the primary; Enter in either field is OK, and Escape is Cancel. Password Manager opens
 * the caller's picker on its button: a login chosen there closes the dialog with it, a picker closed with none keeps
 * the dialog, and a press while a pick runs does nothing. Like a page's alerts, it shows only while its item is its
 * window's active item, waiting until then; it waits while the page has a permission prompt asked (ruling V19); and it
 * shares the page's {@link DialogFlood}, which refuses the third of the page's dialogs to open within a second of the
 * one before closing, and every later one, until the page loads again (F4), so a page that retries a sign-in after
 * each Cancel cannot hold the window. The top-level sign-in of a navigation Jasper started for the user shows even then
 * (L27): a page cannot start one, so it opens no loop, and the user's own navigation is never refused for what the page
 * did. It names no Chromium type and no Vault type.
 *
 * <p>The caller is answered exactly once: {@link Outcome.Ok} with what was typed, {@link Outcome.Picked} with the
 * login chosen, or {@link Outcome.Cancelled} for Cancel, Escape, the dialog's own close, its item's close, Chromium's
 * reset of the page's dialogs, closing what {@link #show} returned, a refusal, or a page with nowhere to show it. As it
 * closes, its password field is cleared, the pick under way is withdrawn, and every password array it still holds is
 * zeroed; an answer's array is the caller's. UI thread.
 */
public final class SignInDialog {
    /** The dialog's title. */
    public static final String TITLE = "Sign In";
    static final String USER_LABEL = "User name", PASSWORD_LABEL = "Password", REMEMBER_LABEL = "Remember this password",
        MANAGER_LABEL = "Password Manager";
    static final String FORM = "sign-in", HEADING = "sign-in.heading", USER_ROW = "sign-in.user-row", USER = "sign-in.user",
        PASSWORD_ROW = "sign-in.password-row", PASSWORD = "sign-in.password", REMEMBER_ROW = "sign-in.remember-row",
        REMEMBER = "sign-in.remember", MANAGER_BUTTON = "sign-in.password-manager", CANCEL_BUTTON = "sign-in.cancel",
        OK_BUTTON = "sign-in.ok", OK = "sign-in.answer.ok", CANCEL = "sign-in.answer.cancel",
        MANAGER = "sign-in.answer.password-manager";
    private static final String OPEN_QUOTE = "“", CLOSE_QUOTE = "”";
    private static final System.Logger LOG = System.getLogger(SignInDialog.class.getName());
    // What OK and Cancel, Escape among them, answer; the dialog's other ends answer CLOSED.
    private static final Map<String, Press> ANSWERS = Map.of(OK, Press.OK, CANCEL, Press.CANCEL);
    // What the dialogs shown from now on read: the system's monotonic clock, or a test's.
    private static volatile LongSupplier clock = SignInDialog::millis;
    private final Page page;
    private final DialogPlace place;
    private final String heading;
    private final boolean offersVault, remembers, usersNavigation;
    private final Function<UiAnchor, CompletableFuture<Optional<Login>>> passwordManager;
    private final Consumer<Outcome> answered;
    private final LongSupplier times;
    private PageModal<Press> modal;
    private DialogFlood flood;
    private UiView view;
    // The user name as last edited, and whether "Remember this password" is ticked.
    private String user = "";
    private boolean remember, done;
    // The pick under way and the anchor it opened on, or null; and the login it chose, which the close hands on.
    private CompletableFuture<Optional<Login>> picking;
    private UiAnchor pickedOn;
    private Login chosen;

    /** How the modal ended: OK, Cancel or Escape, or anything else. */
    private enum Press { OK, CANCEL, CLOSED }

    /**
     * A login the caller's picker chose: a user name and a password, whose array the dialog owns once given, and zeroes
     * unless it hands it on.
     *
     * @param user the user name, which may be empty
     * @param password the password, which may be empty
     */
    public record Login(String user, char[] password) {
        /**
         * Rejects nulls.
         *
         * @throws NullPointerException for a null user name or password
         */
        public Login {
            Objects.requireNonNull(user, "user");
            Objects.requireNonNull(password, "password");
        }

        /** Leaves out the user name and the password, so no log line carries them. */
        @Override public String toString() {
            return "Login[]";
        }
    }

    /** How the dialog was answered. A password array an answer carries is the caller's, to zero once used. */
    public sealed interface Outcome {
        /**
         * OK, with what was typed.
         *
         * @param user the user name
         * @param password a copy of the password typed
         * @param remember whether "Remember this password" was ticked; false when the dialog had no such toggle
         */
        record Ok(String user, char[] password, boolean remember) implements Outcome {
            /** Leaves out the user name and the password, so no log line carries them. */
            @Override public String toString() {
                return "Ok[remember=" + remember + "]";
            }
        }

        /**
         * A login the picker chose.
         *
         * @param user its user name
         * @param password its password
         */
        record Picked(String user, char[] password) implements Outcome {
            /** Leaves out the user name and the password, so no log line carries them. */
            @Override public String toString() {
                return "Picked[]";
            }
        }

        /** No sign-in. */
        record Cancelled() implements Outcome { }
    }

    private SignInDialog(Page page, DialogPlace place, String heading, boolean offersVault, boolean remembers,
                         boolean usersNavigation, Function<UiAnchor, CompletableFuture<Optional<Login>>> passwordManager,
                         Consumer<Outcome> answered, LongSupplier times) {
        this.page = page;
        this.place = place;
        this.heading = heading;
        this.offersVault = offersVault;
        this.remembers = remembers;
        this.usersNavigation = usersNavigation;
        this.passwordManager = passwordManager;
        this.answered = answered;
        this.times = times;
    }

    /**
     * Shows the sign-in dialog over {@code page}'s item, from a later turn of the UI thread, once that item is its
     * window's active item and the page has no permission prompt asked, and tells {@code answered} how it was answered,
     * once. The page's flood guard may refuse it first, which answers it as cancelled, unless it is the user's
     * navigation's own.
     *
     * @param page the page whose request was challenged
     * @param place where it shows: over the page's item's tab
     * @param host the challenger's host, as Chromium gives it: the proxy's for a proxy
     * @param port the challenger's port, which the heading names for a proxy
     * @param realm the realm, already cleaned of what could pass for Jasper's own words; blank for none
     * @param proxy whether a proxy challenged
     * @param offersVault whether the dialog has Password Manager
     * @param remembers whether it has "Remember this password"
     * @param usersNavigation whether it is the top-level sign-in of a navigation Jasper started for the user, which the
     *     page's flood guard admits even once a run of the page's dialogs has tripped it (L27)
     * @param passwordManager opens the picker on the anchor of the Password Manager button, which it may close: a
     *     future that completes on the UI thread, with a login once one was chosen, or empty when none was; the
     *     dialog's close cancels it
     * @param answered told how the dialog was answered, once, on the UI thread
     * @return ends the dialog, shown or waiting, answered {@link Outcome.Cancelled}; nothing once it was answered
     */
    public static Subscription show(Page page, DialogPlace place, String host, int port, String realm, boolean proxy,
                                    boolean offersVault, boolean remembers, boolean usersNavigation,
                                    Function<UiAnchor, CompletableFuture<Optional<Login>>> passwordManager,
                                    Consumer<Outcome> answered) {
        return show(page, place, host, port, realm, proxy, offersVault, remembers, usersNavigation, passwordManager, answered,
            clock);
    }

    /**
     * As {@link #show(Page, DialogPlace, String, int, String, boolean, boolean, boolean, boolean, Function, Consumer)},
     * timed by {@code times}, which the page's flood guard reads.
     *
     * @param times a monotonic clock's milliseconds
     */
    static Subscription show(Page page, DialogPlace place, String host, int port, String realm, boolean proxy,
                             boolean offersVault, boolean remembers, boolean usersNavigation,
                             Function<UiAnchor, CompletableFuture<Optional<Login>>> passwordManager, Consumer<Outcome> answered,
                             LongSupplier times) {
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(place, "place");
        Objects.requireNonNull(passwordManager, "passwordManager");
        Objects.requireNonNull(answered, "answered");
        Objects.requireNonNull(times, "times");
        var dialog = new SignInDialog(page, place, heading(host, port, realm, proxy), offersVault, remembers, usersNavigation,
            passwordManager, answered, times);
        EventQueue.invokeLater(dialog::start);
        return dialog::dismiss;
    }

    /**
     * The dialog's first line: "The website “HOST” requires a user name and password for “REALM”.", or for a proxy "The
     * proxy “HOST:PORT” requires a user name and password for “REALM”." (F22), each without " for “REALM”" when the
     * realm is blank.
     *
     * @param host the challenger's host
     * @param port the challenger's port, which only a proxy's line names
     * @param realm the realm, as it is shown; blank for none
     * @param proxy whether a proxy challenged
     * @return the line
     */
    public static String heading(String host, int port, String realm, boolean proxy) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(realm, "realm");
        String who = proxy ? "The proxy " + OPEN_QUOTE + host + ":" + port + CLOSE_QUOTE : "The website " + OPEN_QUOTE + host + CLOSE_QUOTE;
        String space = realm.isBlank() ? "" : " for " + OPEN_QUOTE + realm + CLOSE_QUOTE;
        return who + " requires a user name and password" + space + ".";
    }

    /** Has the dialogs shown from now on read {@code replacement}, as a test's clock. */
    static void useClock(LongSupplier replacement) {
        clock = Objects.requireNonNull(replacement, "replacement");
    }

    /** Has the dialogs shown from now on read the system's monotonic clock again. */
    static void useSystemClock() {
        clock = SignInDialog::millis;
    }

    private static long millis() {
        return System.nanoTime() / 1_000_000;
    }

    /**
     * Admits the dialog as {@code ScriptDialog} admits a page's alert, but the user's navigation's own past a tripped
     * flood guard, and shows it: unless it was ended first.
     */
    private void start() {
        if (done) return;
        if (page.dialogPlace().isEmpty()) { // the page has closed
            answer(new Outcome.Cancelled());
            return;
        }
        flood = DialogFlood.of(page);
        // L27: the guard still counts it, so the page's own dialogs after it are refused as before.
        if (!flood.admit(times.getAsLong()) && !usersNavigation) {
            answer(new Outcome.Cancelled());
            return;
        }
        modal = new PageModal<>(page, place, TITLE, spec(), ANSWERS, Press.CLOSED, this::closed, new ScriptDialog.AfterPrompts(page));
        modal.viewMade(made -> view = made);
        modal.otherEvents(this::event);
        modal.show(); // in Jasper this returns only once the dialog has closed
    }

    /** Ends the dialog, shown or waiting, as cancelled; nothing once it was answered. */
    private void dismiss() {
        if (done) return;
        if (modal != null) modal.dismiss();
        else answer(new Outcome.Cancelled());
    }

    /** An event of the view that answers nothing: the user name's edits, the toggle, and Password Manager. */
    private void event(UiEvent event) {
        switch (event) {
            case UiEvent.Edited edited when edited.id().equals(USER) -> user = edited.text();
            case UiEvent.Toggled toggled when remembers && toggled.id().equals(REMEMBER) -> {
                remember = toggled.checked();
                view.update(spec());
            }
            case UiEvent.Action action when offersVault && action.id().equals(MANAGER) -> pick();
            default -> { }
        }
    }

    /** Password Manager: the caller's picker opens on the button, unless a pick is under way. */
    private void pick() {
        if (picking != null || view == null) return;
        UiAnchor anchor;
        try {
            anchor = view.anchor(MANAGER_BUTTON);
        } catch (IllegalArgumentException | IllegalStateException unmounted) {
            return;
        }
        CompletableFuture<Optional<Login>> pick;
        try {
            pick = Objects.requireNonNull(passwordManager.apply(anchor), "pick");
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "The password manager could not open for a sign-in", failure);
            anchor.close();
            return;
        }
        picking = pick;
        pickedOn = anchor;
        pick.whenComplete((login, failure) -> picked(pick, login, failure));
    }

    /**
     * A pick ended: a login closes the dialog as {@link Outcome.Picked}, and none keeps it. A login for a pick that was
     * withdrawn, or once the dialog was answered, is zeroed. A failure is logged with the exception alone.
     */
    private void picked(CompletableFuture<Optional<Login>> pick, Optional<Login> login, Throwable failure) {
        Optional<Login> given = failure == null && login != null ? login : Optional.empty();
        if (picking != pick || done) {
            given.ifPresent(SignInDialog::zero);
            return;
        }
        picking = null;
        closeAnchor();
        Throwable cause = failure instanceof CompletionException wrapped && wrapped.getCause() != null ? wrapped.getCause() : failure;
        if (cause != null && !(cause instanceof CancellationException))
            LOG.log(System.Logger.Level.WARNING, "The password manager failed for a sign-in", cause);
        if (given.isEmpty()) return;
        chosen = given.get();
        clearPassword();
        modal.dismiss();
    }

    /** The modal ended as {@code press} says: the flood guard is told, the pick withdrawn, and the caller answered. */
    private void closed(Press press) {
        flood.closed(times.getAsLong());
        withdrawPick();
        Login login = chosen;
        chosen = null;
        // A login is held only from a pick's end to the close it asks, which answers CLOSED; another end zeroes it.
        if (login != null && press != Press.CLOSED) zero(login);
        Outcome outcome = switch (press) {
            case OK -> typed();
            case CANCEL -> {
                clearPassword();
                yield new Outcome.Cancelled();
            }
            case CLOSED -> {
                clearPassword();
                yield login != null ? new Outcome.Picked(login.user(), login.password()) : new Outcome.Cancelled();
            }
        };
        answer(outcome);
    }

    /** OK: the user name as last edited and a copy of the password, which then leaves the field. */
    private Outcome typed() {
        var copy = new char[][] {new char[0]};
        if (view != null) view.withSecret(PASSWORD, chars -> copy[0] = chars.clone());
        clearPassword();
        return new Outcome.Ok(user, copy[0], remembers && remember);
    }

    /** Tells the caller {@code outcome}, the first time only; a later one's password array is zeroed. */
    private void answer(Outcome outcome) {
        if (done) {
            switch (outcome) {
                case Outcome.Ok ok -> Arrays.fill(ok.password(), (char) 0);
                case Outcome.Picked picked -> Arrays.fill(picked.password(), (char) 0);
                case Outcome.Cancelled cancelled -> { }
            }
            return;
        }
        done = true;
        answered.accept(outcome);
    }

    /** Withdraws the pick under way, if any, which closes its picker. */
    private void withdrawPick() {
        CompletableFuture<Optional<Login>> pick = picking;
        picking = null;
        closeAnchor();
        if (pick != null) pick.cancel(false);
    }

    private void closeAnchor() {
        if (pickedOn != null) pickedOn.close();
        pickedOn = null;
    }

    /** Erases the password field's storage, unless the view has closed, which erased it then. */
    private void clearPassword() {
        if (view == null) return;
        try {
            view.clearSecret(PASSWORD);
        } catch (IllegalStateException closed) {
            // The view closed with its dialog, and its storage with it.
        }
    }

    private static void zero(Login login) {
        Arrays.fill(login.password(), (char) 0);
    }

    /**
     * The dialog: the line saying who asks, the User name and Password rows, "Remember this password" when the
     * sign-in can be remembered, then Password Manager when offered, Cancel and OK, the primary. Enter in either field
     * is OK, and Escape is Cancel.
     */
    private ViewSpec spec() {
        var sections = new ArrayList<Nodes.Node>(List.of(
            new Nodes.Text(HEADING, ControlState.READY, heading, Nodes.TextRole.BODY),
            new Nodes.FormRow(USER_ROW, ControlState.READY, USER_LABEL, "",
                new Nodes.Field(USER, ControlState.READY, Nodes.FieldKind.TEXT, user, "", USER_LABEL, OK), Optional.empty()),
            new Nodes.FormRow(PASSWORD_ROW, ControlState.READY, PASSWORD_LABEL, "",
                new Nodes.Secret(PASSWORD, ControlState.READY, "", PASSWORD_LABEL, "", Nodes.SecretKind.INPUT, "", OK), Optional.empty())));
        if (remembers) {
            var toggle = new Nodes.Toggle(REMEMBER, ControlState.READY, REMEMBER_LABEL, remember, REMEMBER, false);
            sections.add(new Nodes.SettingRow(REMEMBER_ROW, ControlState.READY, REMEMBER_LABEL, "", toggle, false));
        }
        var actions = new ArrayList<Nodes.Button>();
        if (offersVault)
            actions.add(new Nodes.Button(MANAGER_BUTTON, ControlState.READY, MANAGER_LABEL, "", Nodes.ButtonKind.SECONDARY, MANAGER, ""));
        actions.add(new Nodes.Button(CANCEL_BUTTON, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL, ""));
        actions.add(new Nodes.Button(OK_BUTTON, ControlState.READY, "OK", "", Nodes.ButtonKind.PRIMARY, OK, ""));
        return new ViewSpec(new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.SHEET, TITLE, Optional.empty(), sections,
            Optional.empty(), actions, CANCEL));
    }
}
