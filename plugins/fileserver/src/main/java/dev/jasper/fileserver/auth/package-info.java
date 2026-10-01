/**
 * Turns a definition's login into credentials for one run: none, a session password generated now, or a Vault
 * account. Only {@link dev.jasper.fileserver.auth.LoginResolver} names {@code dev.jasper.vault.api} types, and it calls
 * them only when the Vault is installed. May depend on {@code definition}, the SDK and the Vault API.
 */
package dev.jasper.fileserver.auth;
