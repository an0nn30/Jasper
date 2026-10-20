package dev.jasper.vault.api;

/**
 * Whether a vault file exists and whether it is open: {@link #NO_VAULT} while there is no vault file, {@link #LOCKED}
 * while it exists but is locked, {@link #UNLOCKED} while it is open and its credentials can be listed and fetched.
 */
public enum LockState { NO_VAULT, LOCKED, UNLOCKED }
