package dev.jasper.browser.item;

import dev.jasper.browser.permissions.AudioMode;
import dev.jasper.browser.permissions.Decision;
import java.util.Objects;
import java.util.Optional;

/**
 * One item's audio decisions (spec section 12), document by document, for the page's top-level site: the first time
 * a page whose site has no stored choice plays sound, it is muted and the audio bar asks; a stored Block mutes it
 * without the bar; a stored Allow plays it. The bar's Allow answers play the page's sound and its Deny answers keep it
 * muted; Allow Once and Deny Once last until the page's next main-frame load (the plan's call), and the Always answers
 * give the decision to store. An answer counts only while the bar shows, so one from a bar its document has taken away
 * changes nothing. Unmute Current Page acts as Allow Once. Before its first document, and for a document without a
 * site, which has no choice to ask for or store, a page is muted without the bar. Pure; UI thread.
 */
final class AudioGate {
    /** What a page's first sound in a document does. */
    enum Effect {
        /** Nothing: the page plays, or it was heard before. */
        NONE,
        /** The page is muted, and the bar asks. */
        SHOW_BAR,
        /** The page is muted, without the bar. */
        MUTED
    }

    /** The audio bar's answers. */
    enum AudioAnswer {
        /** Plays the page's sound until its next main-frame load. */
        ALLOW_ONCE,
        /** Plays the page's sound, and stores Allowed for its site. */
        ALLOW_ALWAYS,
        /** Keeps the page muted until its next main-frame load. */
        DENY_ONCE,
        /** Keeps the page muted, and stores Blocked for its site. */
        DENY_ALWAYS
    }

    private String origin = "";
    private AudioMode mode = AudioMode.BLOCK;
    // Whether the document's sound was heard, whether it was muted, and whether the bar asks.
    private boolean heard, muted, barShown;

    /** A gate before its page's first document. */
    AudioGate() { }

    /**
     * The page's main frame starts loading a document of {@code origin}: its mode is the stored one, its sound has not
     * been heard, and the Once answers and the bar go.
     *
     * @param origin the document's top-level site, as {@code Origins.of} names it, or empty for none
     * @param stored the mode its item's store holds for the site; {@link AudioMode#BLOCK} for none
     */
    void document(String origin, AudioMode stored) {
        this.origin = Objects.requireNonNull(origin, "origin");
        mode = Objects.requireNonNull(stored, "stored");
        heard = false;
        muted = false;
        barShown = false;
    }

    /**
     * The page plays sound, as a frame of its document reports it.
     *
     * @return {@link Effect#SHOW_BAR} the first time in {@link AudioMode#ASK}, {@link Effect#MUTED} the first time in
     *     {@link AudioMode#BLOCK}; {@link Effect#NONE} in {@link AudioMode#ALLOW}, and once the document was heard
     */
    Effect playing() {
        if (heard) return Effect.NONE;
        heard = true;
        return switch (mode) {
            case ASK -> {
                muted = true;
                barShown = true;
                yield Effect.SHOW_BAR;
            }
            case BLOCK -> {
                muted = true;
                yield Effect.MUTED;
            }
            case ALLOW -> Effect.NONE;
        };
    }

    /**
     * The bar is answered {@code answer}: an Allow answer plays the page's sound and makes the document's mode
     * {@link AudioMode#ALLOW}, a Deny answer keeps it muted and makes it {@link AudioMode#BLOCK}, and the bar goes.
     * Nothing while the bar does not show.
     *
     * @param answer the answer
     * @return the decision to store for the site: Allowed for Allow Always, Blocked for Deny Always; empty for the Once
     *     answers, and while the bar does not show
     */
    Optional<Decision> answer(AudioAnswer answer) {
        Objects.requireNonNull(answer, "answer");
        if (!barShown) return Optional.empty();
        barShown = false;
        boolean allowed = answer == AudioAnswer.ALLOW_ONCE || answer == AudioAnswer.ALLOW_ALWAYS;
        mode = allowed ? AudioMode.ALLOW : AudioMode.BLOCK;
        muted = !allowed;
        return switch (answer) {
            case ALLOW_ALWAYS -> Optional.of(Decision.ALLOWED);
            case DENY_ALWAYS -> Optional.of(Decision.BLOCKED);
            case ALLOW_ONCE, DENY_ONCE -> Optional.empty();
        };
    }

    /**
     * Unmute Current Page, as Allow Once: the page's sound plays, the document's mode is {@link AudioMode#ALLOW}, and the
     * bar goes.
     */
    void unmute() {
        muted = false;
        barShown = false;
        mode = AudioMode.ALLOW;
    }

    /**
     * Whether the page's sound was muted, so that the menu offers Unmute Current Page.
     *
     * @return whether it was
     */
    boolean muted() {
        return muted;
    }

    /**
     * Whether the bar asks.
     *
     * @return whether it shows
     */
    boolean barShown() {
        return barShown;
    }

    /**
     * The document's top-level site, which the bar names and the Always answers are stored for.
     *
     * @return the site, or empty for none
     */
    String origin() {
        return origin;
    }

    /**
     * The document's audio mode: the stored one, or what an answer or Unmute made it since.
     *
     * @return the mode
     */
    AudioMode mode() {
        return mode;
    }
}
