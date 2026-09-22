/**
 * EDT model of what extensions contribute to the chrome: actions, toolbar entries, menu sections,
 * status entries, panels and palette scopes, in app-native types. The plugin runtime writes it, every window renders it; neither
 * sees the other. The application owns the single instance; contributors close their own entries.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.lifecycle, dev.jasper.app.palette.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.contributions;
