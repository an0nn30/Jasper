/**
 * EDT model of what extensions contribute to the chrome: actions, toolbar entries, menu sections and
 * status entries, in app-native types. The plugin runtime writes it, every window renders it; neither
 * sees the other. The application owns the single instance; contributors close their own entries.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.lifecycle.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.contributions;
