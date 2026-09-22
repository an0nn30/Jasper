package dev.jasper.vault.crypto;

/** The authentication tag failed: a wrong password, a foreign device secret or a modified file. */
public final class WrongPasswordException extends RuntimeException {
    public WrongPasswordException() { super("The password is wrong or the vault file was modified"); }
}
