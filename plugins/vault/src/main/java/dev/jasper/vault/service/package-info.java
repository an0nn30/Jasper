/**
 * The request queue behind {@link dev.jasper.vault.api.VaultApi}: one prompt per unlock, per
 * (plugin, credential) grant and per pick; the last cancelled waiter dismisses a prompt. UI thread only.
 */
package dev.jasper.vault.service;
