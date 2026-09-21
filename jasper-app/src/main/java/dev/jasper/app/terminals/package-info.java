/**
 * An app-native directory of terminal windows, tabs and panes for features that must not hold Swing objects:
 * windows register entries whose structure is pulled through suppliers, publish id-only facts as
 * {@link dev.jasper.app.terminals.TerminalEvent}s, and the registry derives the active pane. EDT only. The
 * application owns the {@link dev.jasper.app.terminals.TerminalRegistry}; each window closes its own registration.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.lifecycle.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.terminals;
