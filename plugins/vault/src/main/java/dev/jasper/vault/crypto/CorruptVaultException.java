package dev.jasper.vault.crypto;

/** The file is not a vault this version can read. */
public final class CorruptVaultException extends RuntimeException {
    public CorruptVaultException(String message) { super(message); }
}
