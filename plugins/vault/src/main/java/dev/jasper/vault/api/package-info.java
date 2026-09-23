/**
 * The contract other plugins compile against: {@link dev.jasper.vault.api.VaultApi}, its descriptors,
 * credentials, durable managed SSH import requests/results and lock states. Managed credentials own
 * in-memory key bytes; consumers close their copies after authentication. Exported by the plugin descriptor; nothing else in the plugin is.
 */
package dev.jasper.vault.api;
