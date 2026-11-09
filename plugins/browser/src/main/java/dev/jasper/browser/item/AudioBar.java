package dev.jasper.browser.item;

import dev.jasper.browser.item.AudioGate.AudioAnswer;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import java.util.List;
import java.util.Optional;

/**
 * The audio bar across the top of a browser item, between its toolbar and its page (spec section 12), as SDK nodes the
 * kit draws, built as the Editor's on-disk banner is: "Audio was muted. Allow playback by ORIGIN?", then Allow Once,
 * Allow Always, Deny Once and Deny Always, each a secondary button reporting its own id. The bar keeps its place while
 * hidden. Its item's {@link PrivacyMenu} decides when it shows and handles what it reports.
 */
final class AudioBar {
    /** The bar, its text, and its buttons, each of which reports its own id. */
    static final String BAR = "browser.audio", TEXT = "browser.audio.text", ALLOW_ONCE = "browser.audio.allow-once",
        ALLOW_ALWAYS = "browser.audio.allow-always", DENY_ONCE = "browser.audio.deny-once", DENY_ALWAYS = "browser.audio.deny-always";

    private AudioBar() { }

    /**
     * The bar.
     *
     * @param origin the site whose sound was muted, as {@code Origins.of} names it
     * @param shown whether it shows
     * @return the bar's row
     */
    static Nodes.Stack node(String origin, boolean shown) {
        return new Nodes.Stack(BAR, visible(shown), Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Text(TEXT, ControlState.READY, "Audio was muted. Allow playback by " + origin + "?", Nodes.TextRole.BODY),
            button(ALLOW_ONCE, "Allow Once"), button(ALLOW_ALWAYS, "Allow Always"), button(DENY_ONCE, "Deny Once"),
            button(DENY_ALWAYS, "Deny Always")));
    }

    /**
     * The answer a button of the bar reports.
     *
     * @param action an action the item's view reported
     * @return the answer; empty for any other action
     */
    static Optional<AudioAnswer> answer(String action) {
        return switch (action) {
            case ALLOW_ONCE -> Optional.of(AudioAnswer.ALLOW_ONCE);
            case ALLOW_ALWAYS -> Optional.of(AudioAnswer.ALLOW_ALWAYS);
            case DENY_ONCE -> Optional.of(AudioAnswer.DENY_ONCE);
            case DENY_ALWAYS -> Optional.of(AudioAnswer.DENY_ALWAYS);
            default -> Optional.empty();
        };
    }

    private static Nodes.Button button(String id, String label) {
        return new Nodes.Button(id, ControlState.READY, label, "", Nodes.ButtonKind.SECONDARY, id, "");
    }

    private static ControlState visible(boolean shown) {
        return new ControlState(shown, true, false, "");
    }
}
