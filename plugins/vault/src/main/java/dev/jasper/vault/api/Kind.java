package dev.jasper.vault.api;

/**
 * What a credential carries, and so which {@link Credential} accessors have a value: {@link #ACCOUNT_PASSWORD} a
 * username and a password; {@link #ACCOUNT_KEY} a username and a private key file, with the key's passphrase when it
 * has one; {@link #ACCOUNT_KEY_AND_PASSWORD} all of these; {@link #SSH_KEY} a key without a username, either stored in
 * Vault (key bytes, with the passphrase when it has one) or a key file Vault refers to by its path.
 */
public enum Kind { ACCOUNT_PASSWORD, ACCOUNT_KEY, ACCOUNT_KEY_AND_PASSWORD, SSH_KEY }
