package dev.jasper.sdk.ui;
import java.util.Objects;
import javax.swing.JComponent;
/** Explicit specialized widget registration; appearance subscriptions refresh domain palettes.
 *
 * @param id Custom contentId
 * @param kind specialized category
 * @param component widget
 * @param lifetime owned cleanup */
public record CustomContent(String id, Nodes.CustomKind kind, JComponent component, AutoCloseable lifetime) {
 public CustomContent { ModelChecks.id(id); Objects.requireNonNull(kind); Objects.requireNonNull(component); Objects.requireNonNull(lifetime); }
 @Override public String toString() { return "CustomContent[kind=" + kind + "]"; }
}
