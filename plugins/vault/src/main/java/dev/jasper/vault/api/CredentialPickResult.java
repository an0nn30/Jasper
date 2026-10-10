package dev.jasper.vault.api;
import java.util.Objects;
/** Explicit picker outcome. Only Chosen carries Vault's existing one-time, consumer-bound authorization. */
public sealed interface CredentialPickResult {
    record Chosen(CredentialDescriptor credential) implements CredentialPickResult {public Chosen{Objects.requireNonNull(credential);}}
    record Cleared() implements CredentialPickResult {}
    record Cancelled() implements CredentialPickResult {}
}
