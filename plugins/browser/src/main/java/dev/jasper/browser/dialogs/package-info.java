/**
 * A page's dialogs. {@link dev.jasper.browser.dialogs.PageDialogs} holds the client's one JavaScript dialog handler and
 * one file dialog handler, which hand each page's {@code alert()} and {@code confirm()} to {@code ScriptDialog},
 * Jasper's modal over the page's item built from the SDK's nodes ({@code PageModal}), and its file inputs to
 * {@code FileInput}, Jasper's chooser over the item. {@link dev.jasper.browser.dialogs.PermissionPrompt} asks the user
 * for a site's permission in a modal of the same kind, one at a time for each page, and a page's alerts and prompts
 * share one flood guard ({@code DialogFlood}). They reach the item through the
 * {@link dev.jasper.browser.page.DialogPlace} its {@link dev.jasper.browser.page.Page} was given, and answer the page
 * once. Depends on {@code engine}, {@code page} and {@code permissions}; it loads only once a page first asks for
 * Chromium.
 */
package dev.jasper.browser.dialogs;
