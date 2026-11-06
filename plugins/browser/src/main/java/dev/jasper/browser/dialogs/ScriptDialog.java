package dev.jasper.browser.dialogs;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.page.DialogPlace;
import dev.jasper.browser.page.Page;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.ViewSpec;
import java.awt.EventQueue;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;

/**
 * One {@code alert()} or {@code confirm()} of a page's script (spec section 5): Jasper's modal inside the window of the
 * page's item ({@link PageModal}), owned by the item's tab, built from the SDK's nodes. Its heading is "Web Page Alert"
 * or "Web Page Confirmation", the page's message shows as plain text, and its buttons are OK, or Cancel and OK. It shows
 * only while its item is its window's active item, waiting until then, so a page the user is not looking at cannot
 * cover the window; and a page whose dialogs come one straight after another is refused them by its
 * {@link DialogFlood} until it loads again. Jasper's own pages, whose origin is {@code jasper-about:}, are never refused
 * as a flood: their {@code confirm()}s ask before each row is deleted, as iTerm2's pages do, however quickly the user
 * deletes rows. The script is answered exactly once: true for OK; false for Cancel, Escape, the dialog's own close, its
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
    // What OK and Cancel, Escape among them, answer.
    private static final Map<String, Boolean> ANSWERS = Map.of(OK, true, CANCEL, false);
    private final Page page;
    private final Kind kind;
    // Whether the dialog is one of Jasper's own pages', which no flood guard refuses.
    private final boolean jaspers;
    private final String message;
    private final BiConsumer<Boolean, String> reply;
    private final LongSupplier clock;

    private ScriptDialog(Page page, Kind kind, String origin, String message, BiConsumer<Boolean, String> reply, LongSupplier clock) {
        this.page = page;
        this.kind = kind;
        jaspers = AboutScheme.page(origin).isPresent();
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
     * @param origin the origin of the document that asks, as Chromium names it; a {@code jasper-about:} one is Jasper's
     *     own page, whose dialogs no flood guard refuses; null for none
     * @param message the page's message, or null for none
     * @param reply told once whether the user chose OK, and no text
     */
    static void ask(Page page, Kind kind, String origin, String message, BiConsumer<Boolean, String> reply) {
        ask(page, kind, origin, message, reply, () -> System.nanoTime() / 1_000_000);
    }

    /**
     * {@link #ask(Page, Kind, String, String, BiConsumer)} timed by {@code clock}, which the page's flood guard reads.
     *
     * @param clock a monotonic clock's milliseconds
     */
    static void ask(Page page, Kind kind, String origin, String message, BiConsumer<Boolean, String> reply, LongSupplier clock) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(reply, "reply");
        Objects.requireNonNull(clock, "clock");
        EventQueue.invokeLater(new ScriptDialog(page, kind, origin, message, reply, clock)::start);
    }

    /** UI thread. */
    private void start() {
        Optional<DialogPlace> found = page == null ? Optional.empty() : page.dialogPlace();
        if (found.isEmpty()) {
            reply.accept(false, "");
            return;
        }
        DialogFlood flood = jaspers ? null : DialogFlood.of(page);
        if (flood != null && !flood.admit(clock.getAsLong())) {
            reply.accept(false, "");
            return;
        }
        String title = kind == Kind.ALERT ? ALERT_TITLE : CONFIRM_TITLE;
        new PageModal<>(page, found.get(), title, spec(title), ANSWERS, false, ok -> {
            reply.accept(ok, "");
            if (flood != null) flood.closed(clock.getAsLong());
        }).show();
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
