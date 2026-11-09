package dev.jasper.browser.dialogs;

import dev.jasper.browser.page.DialogPlace;
import dev.jasper.browser.page.Page;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.awt.EventQueue;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * One modal a page asks the user, such as its script's {@code confirm()} or a site's permission prompt (spec sections 5
 * and 12): Jasper's dialog over the page's item, owned by the item's tab and built from the SDK's nodes, an overlay of
 * the window. It shows only while its item is its window's active item, waiting until then, so a page the user is not
 * looking at cannot cover the window. An action of its view that it maps gives its answer and closes it; Escape reports
 * the form's cancel action, which it maps too. Its {@link Gate} may hold it back besides, as a script dialog waits while
 * its page's permission prompt is asked, and may ignore an action for now, as a prompt ignores Allow until the
 * double-click interval has passed. Anything else that ends it dismisses it: its own close, the page's close with its
 * item, Chromium's reset of the page's dialogs, {@link #dismiss()}, or a dialog that cannot open or show. It is answered
 * exactly once. UI thread.
 *
 * @param <T> what it answers
 */
final class PageModal<T> {
    /** What else decides when a modal shows and which of its actions answer it: by default, nothing. */
    interface Gate {
        /** A gate that holds nothing and lets every action answer. */
        Gate OPEN = new Gate() { };

        /**
         * Whether the modal waits now, besides for its item to be active.
         *
         * @return whether it waits
         */
        default boolean holds() {
            return false;
        }

        /**
         * Has {@code released} told, on a later turn of the UI thread, once what holds the modal may have let go.
         *
         * @param released told once
         * @return ends the wait
         */
        default Subscription onRelease(Runnable released) {
            return () -> { };
        }

        /** The modal's dialog is about to show. */
        default void showing() { }

        /**
         * Whether {@code action}, one the modal maps, answers it now; one that does not is ignored.
         *
         * @param action the action its view reported
         * @return whether it answers
         */
        default boolean accepts(String action) {
            return true;
        }
    }


    private static final System.Logger LOG = System.getLogger(PageModal.class.getName());
    private final Page page;
    private final DialogPlace place;
    private final String title;
    private final ViewSpec content;
    private final Map<String, T> answers;
    private final T dismissed;
    private final Consumer<T> answer;
    private final Gate gate;
    private final AtomicBoolean answered = new AtomicBoolean();
    private boolean started;
    private PluginDialog dialog;
    // While it waits for its item to become active, and while its gate holds it.
    private Subscription waiting, held;
    private List<Subscription> ends = List.of();

    /**
     * A modal for {@code page}, not shown yet: {@link #show()} shows it.
     *
     * @param page the page that asks
     * @param place where it shows: over the page's item's tab
     * @param title the dialog's title
     * @param content its view
     * @param answers the answer each action of its view gives, the form's cancel action among them
     * @param dismissed the answer when anything else ends it
     * @param answer told the answer, once
     */
    PageModal(Page page, DialogPlace place, String title, ViewSpec content, Map<String, T> answers, T dismissed, Consumer<T> answer) {
        this(page, place, title, content, answers, dismissed, answer, Gate.OPEN);
    }

    /**
     * As {@link #PageModal(Page, DialogPlace, String, ViewSpec, Map, Object, Consumer)}, shown and answered as
     * {@code gate} lets it.
     *
     * @param gate what holds it back, and which actions answer it when
     */
    PageModal(Page page, DialogPlace place, String title, ViewSpec content, Map<String, T> answers, T dismissed, Consumer<T> answer,
              Gate gate) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.page = Objects.requireNonNull(page, "page");
        this.place = Objects.requireNonNull(place, "place");
        this.title = Objects.requireNonNull(title, "title");
        this.content = Objects.requireNonNull(content, "content");
        this.answers = Map.copyOf(answers);
        this.dismissed = Objects.requireNonNull(dismissed, "dismissed");
        this.answer = Objects.requireNonNull(answer, "answer");
    }

    /**
     * Shows the modal once its item is its window's active item, at once when it is. In Jasper a modal overlay's show
     * returns only once it has closed, so this may too. Whichever comes first then ends it, shown or waiting: its
     * answer, its page's close or Chromium's reset of the page's dialogs. Once, and never after {@link #dismiss()}; a
     * page closed already dismisses it.
     */
    void show() {
        if (started || answered.get()) return;
        started = true;
        ends = List.of(page.onClose(this::dismiss), page.onDialogsReset(this::dismiss));
        showWhenActive();
    }

    /** Ends the modal, answered as dismissed: closes it when it shows, or else ends its wait. Nothing once answered. */
    void dismiss() {
        if (dialog != null) dialog.close();
        else finish(dismissed);
    }

    /**
     * Shows the modal once its gate no longer holds it and its item is its window's active item, waiting for whichever
     * has not come yet. Activation is told in the middle of selecting a tab, so the modal, whose show blocks, is shown
     * after that has finished.
     */
    private void showWhenActive() {
        if (answered.get() || dialog != null) return;
        if (gate.holds()) {
            if (held == null) held = gate.onRelease(() -> {
                held = null;
                showWhenActive();
            });
            return;
        }
        if (!place.owner().isActive()) {
            if (waiting == null)
                waiting = place.owner().onActive(active -> { if (active) EventQueue.invokeLater(this::showWhenActive); });
            return;
        }
        stopWaiting();
        open();
    }

    private void open() {
        try {
            dialog = place.windows().dialog(new DialogSpec(title, place.owner(), true, DialogSpec.Presentation.OVERLAY));
        } catch (RuntimeException refused) { // the item's tab closed, or the plugin stops
            LOG.log(System.Logger.Level.DEBUG, "A page's dialog could not open", refused);
            finish(dismissed);
            return;
        }
        dialog.onClosed(() -> finish(dismissed));
        UiView view = null;
        try {
            view = place.kit().create(content, this::event);
            dialog.setContent(view);
            view = null; // the dialog's now, which disposes it as it closes
            gate.showing();
            dialog.show(); // a modal overlay returns once it closes
        } catch (RuntimeException failed) {
            LOG.log(System.Logger.Level.WARNING, "A page's dialog could not show", failed);
            if (view != null) view.close();
            dialog.close();
        }
    }

    private void event(UiEvent event) {
        if (!(event instanceof UiEvent.Action action) || answered.get() || !answers.containsKey(action.id())) return;
        if (!gate.accepts(action.id())) return;
        finish(answers.get(action.id()));
        dialog.close();
    }

    /** The modal is over: its answer is told, the first time only, and it stops waiting and listening. */
    private void finish(T given) {
        if (!answered.compareAndSet(false, true)) return;
        stopWaiting();
        if (held != null) held.close();
        held = null;
        for (Subscription end : ends) end.close();
        answer.accept(given);
    }

    private void stopWaiting() {
        if (waiting == null) return;
        waiting.close();
        waiting = null;
    }
}
