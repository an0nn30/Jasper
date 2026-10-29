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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * One {@code alert()} or {@code confirm()} of a page's script (spec section 5): Jasper's modal inside the window of the
 * page's item, owned by the item's tab, built from the SDK's nodes. Its heading is "Web Page Alert" or "Web Page
 * Confirmation", the page's message shows as plain text, and its buttons are OK, or Cancel and OK. The script is
 * answered exactly once: true for OK; false for Cancel, Escape, the dialog's own close, its item's close, or a page
 * with nowhere to show it.
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
    private final Page page;
    private final Kind kind;
    private final String message;
    private final BiConsumer<Boolean, String> reply;
    private final AtomicBoolean answered = new AtomicBoolean();
    private PluginDialog dialog;

    private ScriptDialog(Page page, Kind kind, String message, BiConsumer<Boolean, String> reply) {
        this.page = page;
        this.kind = kind;
        this.message = shown(message);
        this.reply = reply;
    }

    /**
     * Shows the dialog over {@code page}'s item, on the UI thread, and gives {@code reply} the answer once it is closed.
     * Any thread: Chromium asks on its own.
     *
     * @param page the page whose script asks, or null for none, which is answered at once as cancelled
     * @param kind alert or confirm
     * @param message the page's message, or null for none
     * @param reply told once whether the user chose OK, and no text
     */
    static void ask(Page page, Kind kind, String message, BiConsumer<Boolean, String> reply) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(reply, "reply");
        EventQueue.invokeLater(new ScriptDialog(page, kind, message, reply)::show);
    }

    /** UI thread. */
    private void show() {
        Optional<DialogPlace> place = page == null ? Optional.empty() : page.dialogPlace();
        if (place.isEmpty()) {
            answer(false);
            return;
        }
        String title = kind == Kind.ALERT ? ALERT_TITLE : CONFIRM_TITLE;
        try {
            dialog = place.get().windows().dialog(new DialogSpec(title, place.get().owner(), true, DialogSpec.Presentation.OVERLAY));
        } catch (RuntimeException refused) { // the item's tab closed, or the plugin stops
            LOG.log(System.Logger.Level.DEBUG, "A page's dialog could not open", refused);
            answer(false);
            return;
        }
        // Whichever close comes first answers the script, once: the dialog's, or its page's with its item.
        dialog.onClosed(() -> answer(false));
        Subscription closing = page.onClose(dialog::close);
        dialog.onClosed(closing::close);
        UiView view = null;
        try {
            view = place.get().kit().create(spec(title), this::event);
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
                answer(true);
                dialog.close();
            }
            case CANCEL -> dialog.close();
            default -> { }
        }
    }

    /** Gives the script its answer, the first time only. */
    private void answer(boolean ok) {
        if (answered.compareAndSet(false, true)) reply.accept(ok, "");
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
