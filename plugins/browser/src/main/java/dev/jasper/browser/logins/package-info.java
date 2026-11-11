/**
 * Saved logins, from the Credential Vault, an optional plugin. {@link dev.jasper.browser.logins.VaultLogins} is the
 * Browser's only class that names the Vault's exported API ({@code dev.jasper.vault.api}): its picker, the logins the
 * Browser may fill in without a prompt, saving a login and the Vault's manager. The plugin finds it once at its start,
 * only when the Vault is present, and hands it to the {@code item} package's browser items, which own what this package
 * does for their pages. {@link dev.jasper.browser.logins.LoginName} names the login a sign-in is saved under.
 * {@link dev.jasper.browser.logins.FieldButtons} is one page's buttons on its login fields: its {@code login-fields.js}
 * shows them in the page's http and https documents, and it answers their {@code login.pick} queries, which the
 * {@code page} package's {@link dev.jasper.browser.page.PageQuery} carries, with the Vault's picker at the field.
 * {@link dev.jasper.browser.logins.Challenge} judges an HTTP sign-in the {@code page} package hands over as a
 * {@link dev.jasper.browser.page.SignIn}: whether it is framed, as the {@code permissions} package names origins, and
 * the top-level navigation's own, what the sign-in dialog offers, and the login name, from a realm cleaned of what
 * could pass for Jasper's own words. {@link dev.jasper.browser.logins.HttpSignIns}, which the plugin makes once and its
 * browser items hand their pages' sign-ins to, with or without the Vault, shows the {@code dialogs} package's
 * {@link dev.jasper.browser.dialogs.SignInDialog} for each, one per page at a time, and gives Chromium what it answers;
 * with the Vault it signs a top-level challenge in with a remembered login without the dialog, but only in a navigation
 * the user began, with the page's user activation or through Jasper itself, never one a page's script starts on its
 * own, and once until the answer to that very navigation, from the login's own origin, commits a page; and it saves
 * one answered with "Remember this password" once the response to the same navigation succeeds.
 * Names no Chromium type. May depend on the {@code page}, {@code permissions}, {@code dialogs} and {@code json}
 * packages, the SDK, the JDK and {@code dev.jasper.vault.api}; the {@code settings} package names none of its types.
 */
package dev.jasper.browser.logins;
