/**
 * The tool window dock model: the six slots, one window's {@code DockState}, the {@code DockGeometry} that lays it out
 * and the {@code DockDropResolver} that finds where a dragged window would drop; and the {@code WorkspaceDropResolver}
 * that finds where a dragged tab would drop on a workspace. JDK-only: no Swing or AWT components, and from
 * {@code java.awt} only the {@code Rectangle} and {@code Point} values; {@code verifyApplicationArchitecture} enforces
 * it. Each window owns its state and confines it to the EDT.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.layout (its group ids and sides).
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.dock;
