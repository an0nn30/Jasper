package dev.jasper.vault.api;

/** What a credential carries: an account's password, key, both, or a standalone SSH key. */
public enum Kind { ACCOUNT_PASSWORD, ACCOUNT_KEY, ACCOUNT_KEY_AND_PASSWORD, SSH_KEY }
