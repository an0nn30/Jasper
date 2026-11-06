package dev.jasper.browser.item;

import dev.jasper.browser.dialogs.PermissionPrompt;
import dev.jasper.browser.page.DialogPlace;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.permissions.Decision;
import dev.jasper.browser.permissions.PermissionRequest;
import dev.jasper.sdk.Subscription;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * One item's answers to its page's permission requests (spec section 12), such as for the camera: the decision its
 * store holds for the site answers at once; otherwise the page's {@link PermissionPrompt} asks the user. Allow stores
 * Allowed in the item's store and answers true, Block stores Blocked and answers false, and a prompt dismissed answers
 * false and stores nothing; the same request is then refused without a prompt until the page's next main-frame load
 * (ruling F25), so a page cannot ask again at once. A prompt the load itself takes away counts as no dismissal. A
 * /dev/null item's decisions stay in its group's store. Once the item closes, or the plugin stops, which takes the
 * item's prompts away while Chromium still runs (ruling F43), every request is refused. UI thread.
 */
final class SitePermissions implements AutoCloseable {
    private final Page page;
    private final DataStore store;
    private final BooleanSupplier stopped;
    private final Subscription loadStarts;
    // The requests whose prompt the user dismissed in the page's current document, which its next load start forgets.
    private final Set<PermissionRequest> dismissed = new HashSet<>();
    // Which document the page's main frame shows, counted by its load starts.
    private long document;
    // Whether a prompt was ever asked for, so that an item whose page asked nothing loads nothing of the dialogs package.
    private boolean prompted;
    private boolean closed;

    /**
     * Answers {@code page}'s permission requests from now on, from {@code store}.
     *
     * @param page the item's page
     * @param store the item's data store
     * @param stopped whether the plugin has stopped
     */
    SitePermissions(Page page, DataStore store, BooleanSupplier stopped) {
        this.page = Objects.requireNonNull(page, "page");
        this.store = Objects.requireNonNull(store, "store");
        this.stopped = Objects.requireNonNull(stopped, "stopped");
        loadStarts = page.onLoadStart(url -> {
            document++;
            dismissed.clear();
        });
        page.privacy().onPermissionRequest(this::ask);
    }

    /** The page's site asks for {@code request}: {@code answer} is told once whether it may. */
    private void ask(PermissionRequest request, Consumer<Boolean> answer) {
        if (closed || stopped.getAsBoolean()) {
            answer.accept(false);
            return;
        }
        Optional<Decision> stored = store.permissions().decision(request.origin(), request.permission());
        if (stored.isPresent()) {
            answer.accept(stored.get() == Decision.ALLOWED);
            return;
        }
        Optional<DialogPlace> place = page.dialogPlace();
        if (dismissed.contains(request) || place.isEmpty()) {
            answer.accept(false);
            return;
        }
        long askedIn = document;
        prompted = true;
        PermissionPrompt.ask(place.get(), page, request, decision -> {
            if (decision.isPresent()) store.permissions().set(request.origin(), request.permission(), decision.get());
            else if (askedIn == document && !closed) dismissed.add(request);
            answer.accept(decision.filter(Decision.ALLOWED::equals).isPresent());
        });
    }

    /**
     * The item closes, or the plugin stops: the page's prompts are dismissed, each answered false, and every request is
     * refused from now on. Once.
     */
    @Override public void close() {
        if (closed) return;
        closed = true;
        loadStarts.close();
        if (prompted) PermissionPrompt.dismiss(page);
    }
}
