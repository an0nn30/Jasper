/**
 * The tool window dock model: the six slots, one window's {@code DockState}, the {@code DockGeometry} that lays it out
 * and the {@code DockDropResolver} that finds where a dragged window would drop. JDK-only: no Swing or AWT components,
 * and from {@code java.awt} only the {@code Rectangle} and {@code Point} values; {@code verifyApplicationArchitecture}
 * enforces it. Each window owns its state and confines it to the EDT.
 * <p>Allowed outgoing Jasper dependencies: no other Jasper package.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.dock;
