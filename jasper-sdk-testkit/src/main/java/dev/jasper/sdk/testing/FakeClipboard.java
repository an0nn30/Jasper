package dev.jasper.sdk.testing;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The clipboard every fake plugin's {@code Platform} writes to, as the application's clipboard behaves: a secret copy is
 * cleared when its time is up ({@link #expireSecrets()}) or its plugin stops, if the clipboard still holds it.
 */
public final class FakeClipboard {
    private final List<String> copies = new CopyOnWriteArrayList<>();
    private String text;
    private boolean failWrites;
    private String pendingSecret;
    private String pendingOwner;

    FakeClipboard() { }

    /**
     * What the clipboard holds.
     *
     * @return its text, or empty before anything was copied
     */
    public synchronized Optional<String> text() { return Optional.ofNullable(text); }

    /**
     * Puts text on the clipboard as the user would in another program; a pending secret is then left alone.
     *
     * @param value the text
     */
    public synchronized void setText(String value) { text = Objects.requireNonNull(value, "value"); }

    /**
     * Every copy plugins made, in order.
     *
     * @return lines {@code copy|<plugin id>|<text>} and {@code secret|<plugin id>|<text>}
     */
    public List<String> copies() { return List.copyOf(copies); }

    /**
     * Makes writes fail as while another program holds the clipboard: copies return false and post the host's error.
     *
     * @param value whether writes fail
     */
    public synchronized void failWrites(boolean value) { failWrites = value; }

    /** Runs the pending secret's 30-second clear now. */
    public synchronized void expireSecrets() { clearPending(); }

    synchronized void copy(String owner, String value, boolean secret) {
        if (failWrites) throw new IllegalStateException("The clipboard is held by another program");
        text = value;
        copies.add((secret ? "secret|" : "copy|") + owner + "|" + value);
        pendingSecret = secret ? value : null;
        pendingOwner = secret ? owner : null;
    }

    synchronized void ownerStopped(String owner) { if (owner.equals(pendingOwner)) clearPending(); }

    private void clearPending() {
        if (pendingSecret != null && pendingSecret.equals(text)) text = "";
        pendingSecret = null;
        pendingOwner = null;
    }
}
