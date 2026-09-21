/**
 * Swing terminal component and reusable actions. Component operations belong on the Event Dispatch Thread; concrete controllers own input, selection, search, repaint and bell state.
 * <p>Depends on the session facade, internal bridge, text/rendering/desktop helpers and shared values. TerminalView assembles package-private controllers without letting internals call back through the facade.
 */
package dev.jasper.terminal.view;
