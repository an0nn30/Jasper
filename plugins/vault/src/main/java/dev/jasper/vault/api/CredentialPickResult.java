package dev.jasper.vault.api;
import java.util.Objects;
/**
 * What {@link VaultApi#pick(dev.jasper.sdk.terminal.WindowHandle, CredentialPickRequest)} answers. Only
 * {@link Chosen} grants anything: one use of the chosen credential by the plugin that asked.
 */
public sealed interface CredentialPickResult {
    /**
     * The user chose a credential. The plugin's next request for its secret needs no grant prompt; locking the vault
     * forgets that one-time grant.
     *
     * @param credential the chosen credential, without its secret
     */
    record Chosen(CredentialDescriptor credential) implements CredentialPickResult {public Chosen{Objects.requireNonNull(credential);}}
    /** The user chose None, which the chooser offers only when the request allows clearing. */
    record Cleared() implements CredentialPickResult {}
    /**
     * Nothing was chosen: the user dismissed the chooser or cancelled the unlock, there is no vault yet, the anchor
     * closed, or the vault locked.
     */
    record Cancelled() implements CredentialPickResult {}
}
