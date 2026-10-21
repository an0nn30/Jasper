package dev.jasper.sdk.ui;
import java.util.Objects;
import javax.swing.JComponent;
/** Explicit specialized widget registration; appearance subscriptions refresh domain palettes. Jasper sets the
 * component's foreground ({@code text.primary}) and background ({@code surface.input}, at the workspace opacity) on
 * every theme change and move, and its font to the interface's body font when it is registered, at every update of the
 * view and at every theme or font change, replacing a font set on the component itself; give a font of your own to a
 * component inside it. The lifetime closes when the Custom node leaves the view or the view closes.
 *
 * @param id Custom contentId
 * @param kind specialized category
 * @param component widget
 * @param lifetime owned cleanup */
public record CustomContent(String id, Nodes.CustomKind kind, JComponent component, AutoCloseable lifetime) {
 public CustomContent { ModelChecks.id(id); Objects.requireNonNull(kind); Objects.requireNonNull(component); Objects.requireNonNull(lifetime); }
 @Override public String toString() { return "CustomContent[kind=" + kind + "]"; }
}
