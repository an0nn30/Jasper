package dev.jasper.browser.dialogs;

import dev.jasper.browser.page.DialogPlace;
import dev.jasper.browser.page.Page;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.awt.EventQueue;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;

/**
 * One {@code alert()} or {@code confirm()} of a page's script (spec section 5): Jasper's modal inside the window of the
 * page's item, owned by the item's tab, built from the SDK's nodes. Its heading is "Web Page Alert" or "Web Page
 * Confirmation", the page's message shows as plain text, and its buttons are OK, or Cancel and OK. It shows only while
 * its item is its window's active item, waiting until then, so a page the user is not looking at cannot cover the
 * window; and a page whose dialogs come one straight after another is refused them by its {@link DialogFlood} until it
 * loads again. The script is answered exactly once: true for OK; false for Cancel, Escape, the dialog's own close, its
 * item's close, Chromium cancelling the page's dialogs, a refusal, or a page with nowhere to show it.
 */
final class ScriptDialog {
    /** What the script asked. */
    enum Kind { ALERT, CONFIRM }

    static final String ALERT_TITLE = "Web Page Alert", CONFIRM_TITLE = "Web Page Confirmation";
    static final String FORM = "page-dialog", MESSAGE = "page-dialog.message", OK_BUTTON = "page-dialog.ok",
        CANCEL_BUTTON = "page-dialog.cancel", OK = "page-dialog.answer.ok", CANCEL = "page-dialog.answer.cancel";
    // The most of a page's message the dialog shows: the page's own text, of any length.
    static final int LONGEST_MESSAGE = 10_000;
    private static final System.Logger LOG = System.getLogger(ScriptDialog.class.getName());
    // Each open page's flood guard, which its next main-frame load start rearms. UI thread.
    private static final Map<Page, DialogFlood> FLOODS = new WeakHashMap<>();
    private final Page page;
    private final Kind kind;
    private final String message;
    private final BiConsumer<Boolean, String> reply;
    private final LongSupplier clock;
    private final AtomicBoolean answered = new AtomicBoolean();
    private DialogPlace place;
    private DialogFlood flood;
    private PluginDialog dialog;
    // While it waits for its item to become active.
    private Subscription waiting;
    private List<Subscription> ends = List.of();

    private ScriptDialog(Page page, Kind kind, String message, BiConsumer<Boolean, String> reply, LongSupplier clock) {
        this.page = page;
        this.kind = kind;
        this.message = shown(message);
        this.reply = reply;
        this.clock = clock;
    }

    /**
     * Shows the dialog over {@code page}'s item, on the UI thread, once that item is its window's active item, and gives
     * {@code reply} the answer once it is closed. Any thread: Chromium asks on its own.
     *
     * @param page the page whose script asks, or null for none, which is answered at once as cancelled
     * @param kind alert or confirm
     * @param message the page's message, or null for none
     * @param reply told once whether the user chose OK, and no text
     */
    static void ask(Page page, Kind kind, String message, BiConsumer<Boolean, String> reply) {
        ask(page, kind, message, reply, () -> System.nanoTime() / 1_000_000);
    }

    /**
     * {@link #ask(Page, Kind, String, BiConsumer)} timed by {@code clock}, which the page's flood guard reads.
     *
     * @param clock a monotonic clock's milliseconds
     */
    static void ask(Page page, Kind kind, String message, BiConsumer<Boolean, String> reply, LongSupplier clock) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(reply, "reply");
        Objects.requireNonNull(clock, "clock");
        EventQueue.invokeLater(new ScriptDialog(page, kind, message, reply, clock)::start);
    }

    /** UI thread. */
    private void start() {
        Optional<DialogPlace> found = page == null ? Optional.empty() : page.dialogPlace();
        if (found.isEmpty()) {
            answer(false);
            return;
        }
        place = found.get();
        flood = FLOODS.computeIfAbsent(page, ScriptDialog::flood);
        if (!flood.admit(clock.getAsLong())) {
            answer(false);
            return;
        }
        // Whichever comes first ends a dialog shown or waiting: its page's close with its item, or Chromium's reset.
        ends = List.of(page.onClose(this::dismiss), page.onDialogsReset(this::dismiss));
        showWhenActive();
    }

    /** A page's flood guard, rearmed by each of its main-frame load starts. */
    private static DialogFlood flood(Page page) {
        var flood = new DialogFlood();
        page.onLoadStart(url -> flood.rearm());
        return flood;
    }

    /**
     * Shows the dialog if its item is its window's active item, or else waits until it is. Activation is told in the
     * middle of selecting a tab, so the dialog, whose modal show blocks, is shown after that has finished.
     */
    private void showWhenActive() {
        if (answered.get() || dialog != null) return;
        if (!place.owner().isActive()) {
            if (waiting == null)
                waiting = place.owner().onActive(active -> { if (active) EventQueue.invokeLater(this::showWhenActive); });
            return;
        }
        stopWaiting();
        open();
    }

    private void open() {
        String title = kind == Kind.ALERT ? ALERT_TITLE : CONFIRM_TITLE;
        try {
            dialog = place.windows().dialog(new DialogSpec(title, place.owner(), true, DialogSpec.Presentation.OVERLAY));
        } catch (RuntimeException refused) { // the item's tab closed, or the plugin stops
            LOG.log(System.Logger.Level.DEBUG, "A page's dialog could not open", refused);
            finish(false);
            return;
        }
        dialog.onClosed(() -> finish(false));
        UiView view = null;
        try {
            view = place.kit().create(spec(title), this::event);
            dialog.setContent(view);
            view = null; // the dialog's now, which disposes it as it closes
            dialog.show(); // a modal overlay returns once it closes
        } catch (RuntimeException failed) {
            LOG.log(System.Logger.Level.WARNING, "A page's dialog could not show", failed);
            if (view != null) view.close();
            dialog.close();
        }
    }

    private void event(UiEvent event) {
        if (!(event instanceof UiEvent.Action action) || answered.get()) return;
        switch (action.id()) {
            case OK -> {
                finish(true);
                dialog.close();
            }
            case CANCEL -> dialog.close();
            default -> { }
        }
    }

    /** The page closed, or Chromium cancelled its dialogs: closes the dialog shown, or ends the one waiting. */
    private void dismiss() {
        if (dialog != null) dialog.close();
        else finish(false);
    }

    /** The dialog is over: the script gets its answer, the first time only, and the page's guard its close. */
    private void finish(boolean ok) {
        if (!answer(ok)) return;
        stopWaiting();
        for (Subscription end : ends) end.close();
        flood.closed(clock.getAsLong());
    }

    private void stopWaiting() {
        if (waiting == null) return;
        waiting.close();
        waiting = null;
    }

    /** Gives the script its answer, the first time only; whether this was it. */
    private boolean answer(boolean ok) {
        if (!answered.compareAndSet(false, true)) return false;
        reply.accept(ok, "");
        return true;
    }

    /**
     * The dialog: the message under the title, with OK, or Cancel and OK. OK is the default, and on the right; Escape is
     * Cancel, which for an alert closes it as OK would, answered false.
     */
    private ViewSpec spec(String title) {
        var ok = new Nodes.Button(OK_BUTTON, ControlState.READY, "OK", "", Nodes.ButtonKind.PRIMARY, OK, "");
        List<Nodes.Button> actions = kind == Kind.ALERT ? List.of(ok)
            : List.of(new Nodes.Button(CANCEL_BUTTON, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL, ""), ok);
        return new ViewSpec(new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.SHEET, title, Optional.empty(),
            List.of(new Nodes.Text(MESSAGE, ControlState.READY, message, Nodes.TextRole.BODY)), Optional.empty(), actions, CANCEL));
    }

    /** The page's message as the dialog shows it: at most {@link #LONGEST_MESSAGE} characters, never cut inside one. */
    static String shown(String message) {
        if (message == null) return "";
        if (message.length() <= LONGEST_MESSAGE) return message;
        int end = LONGEST_MESSAGE - 1;
        if (Character.isHighSurrogate(message.charAt(end - 1))) end--;
        return message.substring(0, end) + "…";
    }
}
