/**
 * The About and Licenses windows' content: the build's identity and Jasper's license (AboutView), the license list and
 * texts (LicensesView) and the third-party notices index (ThirdPartyNotices). The application's AboutWindows owns each
 * view's window and closes the view when its window closes. The views are EDT only; ThirdPartyNotices is pure.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.platform.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.about;
