package dev.jasper.fileserver.auth;

/** A login that cannot be resolved, with a message for the panel. */
public final class LoginUnavailableException extends Exception {
    public LoginUnavailableException(String message) { super(message); }
}
