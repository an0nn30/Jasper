package dev.jasper.remote.trust;

/** Jasper's own known_hosts could not be parsed; connections are refused rather than treating it as empty. */
public final class CorruptTrustFileException extends RuntimeException {
    public CorruptTrustFileException(String message) { super(message); }
}
