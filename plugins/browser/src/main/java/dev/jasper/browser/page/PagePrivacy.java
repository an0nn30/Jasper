package dev.jasper.browser.page;

import dev.jasper.browser.permissions.Decision;
import dev.jasper.browser.permissions.Permission;
import dev.jasper.browser.permissions.PermissionRequest;
import java.awt.EventQueue;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A page's privacy hooks, in plain types (spec section 12): how what the page asks of the user's privacy reaches its
 * item. Its site's permission requests, such as for the camera, which Chromium's handlers make on Chromium's thread, go
 * to the item on the UI thread, and the item answers each once, from its store or after asking the user. The decisions
 * its item's store holds are read on any thread, as a document starts loading and as its scripts ask. The notifications
 * its scripts show and close go to the item on the UI thread. A page whose item gave no handler, or that has closed, is
 * refused, and has no stored decision. Every method runs on the UI thread, except where it says otherwise.
 */
public final class PagePrivacy {
    private static final System.Logger LOG = System.getLogger(PagePrivacy.class.getName());
    private BiConsumer<PermissionRequest, Consumer<Boolean>> permissions;
    // Read on Chromium's thread too.
    private volatile BiFunction<String, Permission, Optional<Decision>> decisions;
    private Predicate<Notice> shows;
    private Consumer<String> closes;
    private volatile boolean closed;

    /**
     * A notification a page's script asks to show: as its script wrote it, for the item to show as its own.
     *
     * @param origin the site of the document that asked, as {@link dev.jasper.browser.permissions.Origins#of} names it
     * @param id the script's name for it, which its close names
     * @param title its title, as the script gave it
     * @param body its body, as the script gave it
     */
    public record Notice(String origin, String id, String title, String body) {
        /** Checks that every part is given. */
        public Notice {
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(body, "body");
        }
    }

    /** The hooks of a page; its item gives the handlers. */
    PagePrivacy() { }

    /**
     * Has {@code handler} answer each permission request of the page, on the UI thread: it is told the request and what
     * to give the answer, true for allowed, which it must give once. It replaces the one before; the page's close ends it.
     *
     * @param handler the page's item's
     */
    public void onPermissionRequest(BiConsumer<PermissionRequest, Consumer<Boolean>> handler) {
        Objects.requireNonNull(handler, "handler");
        if (!closed) permissions = handler;
    }

    /**
     * Has {@code stored} give the decisions the page's item's store holds, by site and permission, from now on: on any
     * thread, at once. It replaces the one before; the page's close ends it.
     *
     * @param stored the decision stored for a site's permission, or empty for none; any thread, returning at once
     */
    public void onStoredDecision(BiFunction<String, Permission, Optional<Decision>> stored) {
        Objects.requireNonNull(stored, "stored");
        if (!closed) decisions = stored;
    }

    /**
     * The decision the page's item's store holds for {@code permission} of {@code origin}. Any thread.
     *
     * @param origin the site, as {@link dev.jasper.browser.permissions.Origins#of} names it
     * @param permission the permission
     * @return the decision; empty when none is stored, and with no store given or once the page has closed
     */
    public Optional<Decision> storedDecision(String origin, Permission permission) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(permission, "permission");
        BiFunction<String, Permission, Optional<Decision>> stored = closed ? null : decisions;
        if (stored == null) return Optional.empty();
        Optional<Decision> decision = stored.apply(origin, permission);
        return decision == null ? Optional.empty() : decision;
    }

    /**
     * Has {@code show} show each notification the page's scripts ask for, answering whether it did, and {@code close}
     * take away the one a script names, on the UI thread. They replace the ones before; the page's close ends them.
     *
     * @param show shows a notification, or answers false when it does not
     * @param close takes away the notification the page named so, if it showed one
     */
    public void onNotifications(Predicate<Notice> show, Consumer<String> close) {
        Objects.requireNonNull(show, "show");
        Objects.requireNonNull(close, "close");
        if (closed) return;
        shows = show;
        closes = close;
    }

    /**
     * The page's script asks to show {@code notice}: the item is told, unless the page has closed.
     *
     * @param notice what to show
     * @return whether the item showed it; false with no handler, a closed page, or a handler that throws, which is logged
     */
    public boolean showNotification(Notice notice) {
        Objects.requireNonNull(notice, "notice");
        Predicate<Notice> show = closed ? null : shows;
        if (show == null) return false;
        try {
            return show.test(notice);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A page's notification could not be shown", failure);
            return false;
        }
    }

    /**
     * The page's script closes the notification it named {@code id}: the item is told, unless the page has closed.
     *
     * @param id the script's name for it
     */
    public void closeNotification(String id) {
        Objects.requireNonNull(id, "id");
        Consumer<String> close = closed ? null : closes;
        if (close == null) return;
        try {
            close.accept(id);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A page's notification could not be closed", failure);
        }
    }

    /**
     * The page's site asks for {@code request}: on the UI thread the handler is told, and {@code answer} is told whether
     * it is allowed, once, on the thread the handler answers on. With no handler, a closed page or a handler that throws,
     * the answer is false. Any thread.
     *
     * @param request the site and the permission
     * @param answer told once whether the site may have it
     */
    public void request(PermissionRequest request, Consumer<Boolean> answer) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(answer, "answer");
        EventQueue.invokeLater(() -> ask(request, once(answer)));
    }

    private void ask(PermissionRequest request, Consumer<Boolean> answer) {
        BiConsumer<PermissionRequest, Consumer<Boolean>> handler = closed ? null : permissions;
        if (handler == null) {
            answer.accept(false);
            return;
        }
        try {
            handler.accept(request, answer);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A page's permission request could not be answered", failure);
            answer.accept(false);
        }
    }

    /** The page closed: its requests are refused from now on, and it has no stored decision and shows nothing. */
    void close() {
        closed = true;
        permissions = null;
        decisions = null;
        shows = null;
        closes = null;
    }

    /** {@code answer}, told the first answer only, a null one as false. */
    private static Consumer<Boolean> once(Consumer<Boolean> answer) {
        var answered = new AtomicBoolean();
        return allowed -> { if (answered.compareAndSet(false, true)) answer.accept(Boolean.TRUE.equals(allowed)); };
    }
}
