/**
 * Saved logins, from the Credential Vault, an optional plugin. {@link dev.jasper.browser.logins.VaultLogins} is the
 * Browser's only class that names the Vault's exported API ({@code dev.jasper.vault.api}): its picker, the logins the
 * Browser may fill in without a prompt, saving a login and the Vault's manager. The plugin finds it once at its start,
 * only when the Vault is present, and hands it to the {@code item} package's browser items, which own what this package
 * does for their pages. {@link dev.jasper.browser.logins.LoginName} names the login a sign-in is saved under.
 * {@link dev.jasper.browser.logins.FieldButtons} is one page's buttons on its login fields: its {@code login-fields.js}
 * shows them in the page's http and https documents, and it answers their {@code login.pick} queries, which the
 * {@code page} package's {@link dev.jasper.browser.page.PageQuery} carries, with the Vault's picker at the field. Names no
 * Chromium type. May depend on the {@code page}, {@code dialogs} and {@code json} packages, the SDK, the JDK and
 * {@code dev.jasper.vault.api}; the {@code settings} package names none of its types.
 */
package dev.jasper.browser.logins;
