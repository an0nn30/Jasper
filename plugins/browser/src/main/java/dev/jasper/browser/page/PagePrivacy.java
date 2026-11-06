package dev.jasper.browser.page;

import dev.jasper.browser.permissions.PermissionRequest;
import java.awt.EventQueue;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A page's privacy hooks, in plain types (spec section 12): how what the page asks of the user's privacy reaches its
 * item. Its site's permission requests, such as for the camera, which Chromium's handlers make on Chromium's thread, go
 * to the item on the UI thread, and the item answers each once, from its store or after asking the user. A page whose
 * item gave no handler, or that has closed, is refused. Every method runs on the UI thread, except where it says
 * otherwise.
 */
public final class PagePrivacy {
    private static final System.Logger LOG = System.getLogger(PagePrivacy.class.getName());
    private BiConsumer<PermissionRequest, Consumer<Boolean>> permissions;
    private boolean closed;

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
        } catch (RuntimeException failed) {
            LOG.log(System.Logger.Level.WARNING, "A page's permission request could not be answered", failed);
            answer.accept(false);
        }
    }

    /** The page closed: its requests are refused from now on. */
    void close() {
        closed = true;
        permissions = null;
    }

    /** {@code answer}, told the first answer only, a null one as false. */
    private static Consumer<Boolean> once(Consumer<Boolean> answer) {
        var answered = new AtomicBoolean();
        return allowed -> { if (answered.compareAndSet(false, true)) answer.accept(Boolean.TRUE.equals(allowed)); };
    }
}
