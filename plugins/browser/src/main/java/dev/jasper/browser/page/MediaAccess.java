package dev.jasper.browser.page;

import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.permissions.Origins;
import dev.jasper.browser.permissions.Permission;
import dev.jasper.browser.permissions.PermissionRequest;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefMediaAccessCallback;
import org.cef.handler.CefPermissionHandler;

/**
 * Pages' camera and microphone (spec section 12): the client's one permission handler, which answers every request
 * itself, so Chromium asks nothing of its own (ruling F26). A page's request is its top-level site's (ruling V16): the
 * origin of the address its main frame shows, read there and then on Chromium's thread, whichever of its frames asked,
 * so a grant follows the site the user sees and Jasper's prompt names it; a main frame's request from another origin
 * came from a document that has gone, and is cancelled. It asks for the camera, the microphone or both, as
 * {@link #permission} reads Chromium's flags, through the page's {@link PagePrivacy}, which its item answers from its
 * store or with a prompt. An allowed request continues with exactly what it asked for, and any other answer cancels it.
 * A browser that is no page's, as DevTools' and the Markdown previews' are, a page whose address has no site, such as
 * Jasper's own pages, and screen capture are cancelled at once, asking and storing nothing. The preview client has a
 * handler of its own ({@code PreviewHandlers}), which so cancels every request. Chromium asks on its own thread, and the
 * answer comes on the UI thread: JCEF's callback hands each call straight to CEF, whose callback takes it on any
 * thread, and continues one never answered with nothing once it is collected. An answer that comes once Chromium has
 * stopped touches the callback no more (ruling F43).
 *
 * <p>JCEF keeps a client's first permission handler, so this is the only one.
 */
final class MediaAccess implements CefPermissionHandler {
    private static final int AUDIO = CefMediaAccessCallback.MediaPermissionFlags.DEVICE_AUDIO_CAPTURE,
        VIDEO = CefMediaAccessCallback.MediaPermissionFlags.DEVICE_VIDEO_CAPTURE;
    // The Chromiums given the handler, held weakly: each client gets it once, however often a page starts.
    private static final Set<Chromium> PREPARED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final BooleanSupplier stopped;

    /**
     * The handler of a Chromium that has stopped once {@code stopped} says so.
     *
     * @param stopped whether Chromium has stopped; any thread
     */
    MediaAccess(BooleanSupplier stopped) {
        this.stopped = Objects.requireNonNull(stopped, "stopped");
    }

    /**
     * Has Chromium install the handler on its client before any page is made on it; once, however often asked. Called
     * when a page first asks for Chromium, never at the plugin's start: this class names Chromium's types.
     */
    static void prepare(Chromium chromium) {
        if (PREPARED.add(chromium))
            chromium.onClient(client -> client.addPermissionHandler(new MediaAccess(() -> chromium.state() == Chromium.State.STOPPED)));
    }

    /**
     * What a request with Chromium's {@code requested} flags asks for.
     *
     * @param requested {@code CefMediaAccessCallback.MediaPermissionFlags}, OR-ed
     * @return the camera for video, the microphone for audio, both for both; empty with any screen capture flag, any
     *     flag Jasper does not know, or none
     */
    static Optional<Permission> permission(int requested) {
        if (requested == 0 || (requested & ~(AUDIO | VIDEO)) != 0) return Optional.empty();
        if (requested == (AUDIO | VIDEO)) return Optional.of(Permission.CAMERA_AND_MICROPHONE);
        return Optional.of(requested == VIDEO ? Permission.CAMERA : Permission.MICROPHONE);
    }

    @Override public boolean onRequestMediaAccessPermission(CefBrowser browser, CefFrame frame, String requestingOrigin, int requested,
                                                            CefMediaAccessCallback callback) {
        Page page = browser instanceof PageBrowser shown ? shown.owner() : null;
        if (page == null) callback.Cancel();
        else ask(page, mainFrameAddress(browser), frame == null || frame.isMain(), requestingOrigin, requested, callback::Continue,
            callback::Cancel);
        return true;
    }

    /**
     * {@code page}'s top-level site, at {@code address}, asks for what {@code requested} names: {@code allow} is told
     * {@code requested} once the page's item allows it, and {@code cancel} is run when it does not, once, and nothing is
     * run once Chromium has stopped. With no site or no permission, {@code cancel} runs at once and nothing is asked; so
     * it does for a main frame's request from another origin than the address's, which came from a document that has
     * gone, as when a page asks and then goes to a site with a stored Allow.
     *
     * @param page the page whose browser asks
     * @param address the address the page's main frame shows
     * @param mainFrame whether the page's main frame asked, not one of its sub-frames
     * @param requestingOrigin the origin of the document that asked, as Chromium names it
     * @param requested Chromium's flags
     * @param allow continues the request with the flags it is given
     * @param cancel cancels the request
     */
    void ask(Page page, String address, boolean mainFrame, String requestingOrigin, int requested, IntConsumer allow, Runnable cancel) {
        Optional<Permission> permission = permission(requested);
        Optional<String> origin = Origins.of(address);
        if (permission.isEmpty() || origin.isEmpty() || mainFrame && !Origins.of(requestingOrigin).equals(origin)) {
            cancel.run();
            return;
        }
        page.privacy().request(new PermissionRequest(origin.get(), permission.get()), allowed -> {
            if (stopped.getAsBoolean()) return;
            if (allowed) allow.accept(requested);
            else cancel.run();
        });
    }

    /** The address {@code browser}'s main frame shows, as Chromium has it now; empty without one. Chromium's thread. */
    private static String mainFrameAddress(CefBrowser browser) {
        CefFrame main = browser.getMainFrame();
        if (main == null) return "";
        try {
            return Objects.requireNonNullElse(main.getURL(), "");
        } finally {
            main.dispose();
        }
    }
}
