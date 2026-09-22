package dev.jasper.vault.lock;

/** A device-bound vault whose device secret this machine does not have. */
public final class ForeignDeviceException extends RuntimeException {
    public ForeignDeviceException() { super("This vault was created on another machine and is bound to it; its device secret is not on this one"); }
}
