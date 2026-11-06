package dev.jasper.browser.dialogs;

import dev.jasper.browser.page.DialogPlace;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.permissions.Decision;
import dev.jasper.browser.permissions.PermissionRequest;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.ViewSpec;
import java.awt.AWTError;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * A site's request for a permission, asked of the user (spec section 12): Jasper's modal over the page's item
 * ({@link PageModal}), headed by the permission, such as "Allow Camera", saying which site wants what, such as "The
 * website https://example.com wants to use your camera.", with Block and, right-most, Allow. Escape and the prompt's
 * own close dismiss it, neither blocking nor allowing; Enter presses nothing, so no keystroke a page times a prompt for
 * allows it. Allow and Block, from the mouse or the keyboard, count only once the platform's double-click interval has
 * passed since the prompt showed, or since another of the page's modals closed while it showed (ruling V19), so a page
 * cannot time a click onto them, such as a double-click lure's second click; and the page's script dialogs wait while
 * it has a prompt asked, so none can stack over it. Like a page's alerts, it shows only while its item is its window's
 * active item.
 *
 * <p>A page shows one prompt at a time. A request equal to one the page has asked already, shown or waiting, shares
 * that prompt and its answer; the others wait, in the order asked, each shown once the one before is answered. The
 * page's close, Chromium's reset of its dialogs and its main-frame load start dismiss the prompt shown and those waiting.
 * A page's prompts share its alerts' {@link DialogFlood} (ruling F25): the third of them to open within a second of the
 * one before closing is refused unshown, and so is every later one, until the page loads again. Each asker is answered
 * exactly once: the decision for Allow or Block, and empty for anything else. UI thread.
 */
public final class PermissionPrompt {
    static final String FORM = "permission-prompt", MESSAGE = "permission-prompt.message", BLOCK_BUTTON = "permission-prompt.block",
        ALLOW_BUTTON = "permission-prompt.allow", BLOCK = "permission-prompt.answer.block", ALLOW = "permission-prompt.answer.allow",
        DISMISS = "permission-prompt.answer.dismiss";
    private static final Map<String, Optional<Decision>> ANSWERS = Map.of(ALLOW, Optional.of(Decision.ALLOWED),
        BLOCK, Optional.of(Decision.BLOCKED), DISMISS, Optional.empty());
    /** The double-click interval a platform that names none gets, in milliseconds. */
    static final long CLICK_INTERVAL = 500;
    // The prompts of each page that has any, shown or waiting; a page's entry goes once it has none.
    private static final Map<Page, Prompts> PAGES = new IdentityHashMap<>();
    // What the prompts asked from now on read: the system's monotonic clock, or a test's.
    private static volatile LongSupplier clock = PermissionPrompt::millis;

    private PermissionPrompt() { }

    /**
     * Asks the user whether {@code request}'s site may have its permission, over {@code page}'s item, once the prompts
     * the page asked before have been answered.
     *
     * @param place where it shows: over the page's item's tab
     * @param page the page whose site asks
     * @param request the site and the permission; not audio playback, which the audio bar asks for
     * @param answer told once: Allowed or Blocked as the user chose, or empty when the prompt was dismissed, refused as a
     *     flood, or the page has closed
     * @throws UnsupportedOperationException for audio playback
     */
    public static void ask(DialogPlace place, Page page, PermissionRequest request, Consumer<Optional<Decision>> answer) {
        ask(place, page, request, answer, clock, platformInterval());
    }

    /**
     * {@link #ask(DialogPlace, Page, PermissionRequest, Consumer)} timed by {@code clock}, which the page's flood guard
     * and the prompt's double-click interval read, with that interval.
     *
     * @param clock a monotonic clock's milliseconds
     * @param interval how long after the prompt shows, or after another of the page's modals closes while it shows,
     *     Allow and Block start to count, in milliseconds
     */
    static void ask(DialogPlace place, Page page, PermissionRequest request, Consumer<Optional<Decision>> answer, LongSupplier clock,
                    long interval) {
        Objects.requireNonNull(place, "place");
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(answer, "answer");
        Objects.requireNonNull(clock, "clock");
        var pending = new Pending(place, request, answer);
        if (page.dialogPlace().isEmpty()) { // the page has closed
            pending.answer(Optional.empty());
            return;
        }
        PAGES.computeIfAbsent(page, shown -> new Prompts(shown, clock, interval)).add(pending);
    }

    /**
     * The double-click interval a prompt ignores Allow and Block for (ruling V19): the platform's, as
     * {@code awt.multiClickInterval} gives it.
     *
     * @param property the desktop property's value
     * @return its milliseconds when it is a positive number; else {@value #CLICK_INTERVAL}
     */
    static long clickInterval(Object property) {
        return property instanceof Number number && number.longValue() > 0 ? number.longValue() : CLICK_INTERVAL;
    }

    /** The platform's double-click interval now, as {@link #clickInterval(Object)} reads it. */
    private static long platformInterval() {
        Object property;
        try {
            property = Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");
        } catch (RuntimeException | AWTError unavailable) {
            property = null;
        }
        return clickInterval(property);
    }

    /** Has the prompts asked from now on read {@code replacement}, as a test's clock. */
    static void useClock(LongSupplier replacement) {
        clock = Objects.requireNonNull(replacement, "replacement");
    }

    /** Has the prompts asked from now on read the system's monotonic clock again. */
    static void useSystemClock() {
        clock = PermissionPrompt::millis;
    }

    private static long millis() {
        return System.nanoTime() / 1_000_000;
    }

    /**
     * Whether {@code page} has a prompt asked, shown or waiting, which holds its script dialogs back (ruling V19).
     *
     * @param page a page
     * @return whether it has
     */
    static boolean asking(Page page) {
        return PAGES.containsKey(page);
    }

    /**
     * Tells {@code released}, on a later turn of the UI thread, once {@code page} has no prompt asked: at once when it
     * has none.
     *
     * @param page a page
     * @param released told once
     * @return ends the wait
     */
    static Subscription afterPrompts(Page page, Runnable released) {
        Objects.requireNonNull(released, "released");
        Prompts prompts = PAGES.get(Objects.requireNonNull(page, "page"));
        if (prompts == null) {
            EventQueue.invokeLater(released);
            return () -> { };
        }
        return prompts.afterwards(released);
    }

    /**
     * Dismisses {@code page}'s prompts, the one shown and those waiting, as its item closes or the plugin stops: each is
     * answered once, as dismissed.
     *
     * @param page a page
     */
    public static void dismiss(Page page) {
        Prompts prompts = PAGES.get(Objects.requireNonNull(page, "page"));
        if (prompts != null) prompts.dismissAll();
    }

    /** The prompt's view: the site's request under the permission's heading, with Block, then Allow; Escape dismisses. */
    private static ViewSpec spec(String heading, String text) {
        List<Nodes.Button> actions = List.of(
            new Nodes.Button(BLOCK_BUTTON, ControlState.READY, "Block", "", Nodes.ButtonKind.SECONDARY, BLOCK, ""),
            new Nodes.Button(ALLOW_BUTTON, ControlState.READY, "Allow", "", Nodes.ButtonKind.PRIMARY, ALLOW, ""));
        return new ViewSpec(new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.SHEET, heading, Optional.empty(),
            List.of(new Nodes.Text(MESSAGE, ControlState.READY, text, Nodes.TextRole.BODY)), Optional.empty(), actions, DISMISS,
            Nodes.FormSubmission.NONE));
    }

    /** One request asked of the user, and everyone it answers: those who asked it and those whose equal request joined. */
    private static final class Pending {
        final DialogPlace place;
        final PermissionRequest request;
        final String heading, text;
        final List<Consumer<Optional<Decision>>> answers = new ArrayList<>();
        PageModal<Optional<Decision>> modal;
        boolean answered;

        Pending(DialogPlace place, PermissionRequest request, Consumer<Optional<Decision>> answer) {
            this.place = place;
            this.request = Objects.requireNonNull(request, "request");
            heading = request.permission().heading();
            text = request.permission().asks(request.origin());
            answers.add(answer);
        }

        /** Tells everyone it answers {@code decision}, the first time only. */
        void answer(Optional<Decision> decision) {
            if (answered) return;
            answered = true;
            for (Consumer<Optional<Decision>> told : List.copyOf(answers)) told.accept(decision);
        }
    }

    /** One page's prompts: the one at the front, shown or waiting for its item to be active, and those waiting after it. */
    private static final class Prompts {
        private final Page page;
        private final LongSupplier clock;
        private final long interval;
        private final DialogFlood flood;
        private final List<Subscription> ends;
        private final ArrayDeque<Pending> waiting = new ArrayDeque<>();
        // Told once the page has no prompt left, as its held script dialogs are.
        private final List<Runnable> releases = new ArrayList<>();
        private Pending front;
        private boolean ended;

        Prompts(Page page, LongSupplier clock, long interval) {
            this.page = page;
            this.clock = clock;
            this.interval = interval;
            flood = DialogFlood.of(page);
            ends = List.of(page.onClose(this::dismissAll), page.onDialogsReset(this::dismissAll), page.onLoadStart(url -> dismissAll()));
        }

        void add(Pending pending) {
            for (Pending asked : asked())
                if (asked.request.equals(pending.request)) {
                    asked.answers.addAll(pending.answers);
                    return;
                }
            waiting.add(pending);
            next();
        }

        /** The requests asked and not yet answered: the front one, then those waiting. */
        private List<Pending> asked() {
            var asked = new ArrayList<Pending>(waiting.size() + 1);
            if (front != null) asked.add(front);
            asked.addAll(waiting);
            return asked;
        }

        /**
         * Shows the next prompt, while none is at the front: those the flood guard refuses are answered unshown. Once none
         * is asked, the page's entry goes.
         */
        private void next() {
            if (ended || front != null) return;
            while (!waiting.isEmpty()) {
                Pending pending = waiting.poll();
                if (!flood.admit(clock.getAsLong())) {
                    pending.answer(Optional.empty());
                    continue;
                }
                front = pending;
                pending.modal = new PageModal<>(page, pending.place, pending.heading, spec(pending.heading, pending.text), ANSWERS,
                    Optional.empty(), decision -> closed(pending, decision), new Guard());
                // In Jasper this returns only once the prompt has closed; what comes next was posted meanwhile.
                pending.modal.show();
                return;
            }
            end();
        }

        /**
         * The front prompt's modal ended with {@code decision}: its askers are answered, and the next prompt shows once
         * this one's close has finished, as its show blocks.
         */
        private void closed(Pending pending, Optional<Decision> decision) {
            flood.closed(clock.getAsLong());
            if (front == pending) front = null;
            pending.answer(decision);
            EventQueue.invokeLater(this::next);
        }

        /** The page's close, Chromium's reset of its dialogs, its load start or the plugin's stop: every prompt goes. */
        void dismissAll() {
            if (ended) return;
            List<Pending> dropped = List.copyOf(waiting);
            waiting.clear();
            Pending shown = front;
            if (shown != null) shown.modal.dismiss();
            for (Pending pending : dropped) pending.answer(Optional.empty());
            end();
        }

        /** The page has no prompt left: its entry and its listening go. */
        private void end() {
            if (ended) return;
            ended = true;
            PAGES.remove(page, this);
            for (Subscription end : ends) end.close();
            for (Runnable released : List.copyOf(releases)) EventQueue.invokeLater(released);
            releases.clear();
        }

        /** Tells {@code released} on a later turn once the page has no prompt left. */
        Subscription afterwards(Runnable released) {
            releases.add(released);
            return () -> releases.remove(released);
        }

        /**
         * Ruling V19: the front prompt takes no Allow or Block until the double-click interval has passed since it showed,
         * or since another of the page's modals closed while it showed, so a page cannot time a click onto it. Escape
         * dismisses it at any time, as that grants nothing.
         */
        private final class Guard implements PageModal.Gate {
            private long shown = Long.MAX_VALUE;

            @Override public void showing() {
                shown = clock.getAsLong();
            }

            @Override public boolean accepts(String action) {
                if (action.equals(DISMISS)) return true;
                long since = Math.max(shown, flood.lastClosed());
                return shown != Long.MAX_VALUE && clock.getAsLong() - since >= interval;
            }
        }
    }
}
