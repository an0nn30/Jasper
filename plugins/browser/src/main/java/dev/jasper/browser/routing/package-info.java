/**
 * The Browser as Jasper's web browser (spec section 9). {@link dev.jasper.browser.routing.BrowserRole}, registered with
 * {@code Platform.browser}, opens the links Jasper hands it where {@code LinkDecision} says, from the link settings: in
 * a browser item, through {@code LinkPrompt}'s "Open in Jasper?", or back to Jasper.
 * {@link dev.jasper.browser.routing.WebAddressItems} adds Open URL in Split Right and Split Down to the terminal's
 * right-click menu. Depends on {@code item}, {@code settings} and {@code engine}'s plain {@code Chromium.State}, and names
 * no Chromium type.
 */
package dev.jasper.browser.routing;
