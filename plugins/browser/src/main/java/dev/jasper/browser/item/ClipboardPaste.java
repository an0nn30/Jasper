package dev.jasper.browser.item;

import dev.jasper.sdk.ui.Platform;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * A paste of text another plugin inserts into a page with no field the insertion reaches, such as a cross-origin frame's
 * (spec section 15, as iTerm2 does it): the text goes on the clipboard and is pasted into the page, and
 * {@value #RESTORE_MILLIS} ms later the clipboard gets back the text it held before, if it still holds the pasted text.
 * Only text comes back: a clipboard that held none, or only an image, keeps the pasted text. Chromium pastes
 * asynchronously, so a page whose renderer is busy for longer than the wait pastes the text put back, which may be one the
 * user copied elsewhere; the wait is one constant, so a paste that never touches the clipboard can replace this one
 * with a small change. UI thread, as the plugin's clipboard is.
 */
final class ClipboardPaste {
    /** How long after the paste the clipboard gets its text back: iTerm2 waits 100 ms, which a busy page can outlast. */
    static final int RESTORE_MILLIS = 500;
    private final Platform platform;
    private final BiConsumer<Long, Runnable> after;

    /**
     * Pastes with the plugin's clipboard.
     *
     * @param platform the plugin's platform, whose clipboard it reads and writes; it needs {@code clipboard.read}
     * @param after runs a task on the UI thread after a delay in milliseconds
     */
    ClipboardPaste(Platform platform, BiConsumer<Long, Runnable> after) {
        this.platform = Objects.requireNonNull(platform, "platform");
        this.after = Objects.requireNonNull(after, "after");
    }

    /**
     * Puts {@code text} on the clipboard and runs {@code intoPage}, which pastes it; {@value #RESTORE_MILLIS} ms later
     * puts back the text the clipboard held, if it still holds {@code text}, then runs {@code then}. A clipboard that
     * cannot take {@code text} would have the page paste what it holds: then nothing is pasted and nothing follows, and
     * the platform says the copy failed. A plugin that stops within the wait puts nothing back.
     *
     * @param text the text
     * @param intoPage pastes the clipboard into the page
     * @param then what follows the paste, once the clipboard has its text back, such as Return
     */
    void paste(String text, Runnable intoPage, Runnable then) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(intoPage, "intoPage");
        Objects.requireNonNull(then, "then");
        Optional<String> saved = platform.clipboardText();
        if (!platform.copy(text)) return;
        intoPage.run();
        after.accept((long) RESTORE_MILLIS, () -> {
            restore(saved, text);
            then.run();
        });
    }

    /** The clipboard gets {@code saved} back if it still holds {@code pasted}. */
    private void restore(Optional<String> saved, String pasted) {
        if (saved.isEmpty()) return;
        try {
            if (platform.clipboardText().filter(pasted::equals).isPresent()) platform.copy(saved.get());
        } catch (IllegalStateException stopped) { // the plugin stopped meanwhile, and its context reads and writes no clipboard
        }
    }
}
