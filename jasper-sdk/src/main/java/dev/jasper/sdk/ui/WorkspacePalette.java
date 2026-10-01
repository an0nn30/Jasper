package dev.jasper.sdk.ui;

import java.awt.Color;
import java.awt.Component;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.UIManager;
import java.util.Objects;

/** Semantic colors for workspace controls.
 * @param background background color
 * @param surfaceRaised raised surface background
 * @param surfaceSunken recessed surface background
 * @param text text color
 * @param textMuted secondary text color
 * @param border border color
 * @param selection selection color
 * @param selectionText selected text foreground
 * @param hover hover color
 * @param accent accent color
 * @param success success color
 * @param warning warning color
 * @param error error color
 * @since 0.12.0
 */
public record WorkspacePalette(Color background, Color surfaceRaised, Color surfaceSunken, Color text,
                               Color textMuted, Color border, Color selection, Color selectionText, Color hover,
                               Color accent, Color success, Color warning, Color error) {
    /** Validates every color. */
    public WorkspacePalette {
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(surfaceRaised, "surfaceRaised");
        Objects.requireNonNull(surfaceSunken, "surfaceSunken");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(textMuted, "textMuted");
        Objects.requireNonNull(border, "border");
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(selectionText, "selectionText");
        Objects.requireNonNull(hover, "hover");
        Objects.requireNonNull(accent, "accent");
        Objects.requireNonNull(success, "success");
        Objects.requireNonNull(warning, "warning");
        Objects.requireNonNull(error, "error");
    }

    /** Reads workspace colors, falling back to Swing for custom hosts. UI thread only.
     * @return the workspace palette
     */
    public static WorkspacePalette current() { return read(true); }

    /** Resolves colors for the component's current zone. UI thread only.
     * @param component the component, or null for chrome
     * @return workspace colors within the workspace, otherwise chrome colors
     */
    public static WorkspacePalette current(Component component) {
        for (Component current = component; current != null; current = current.getParent()) {
            if (current instanceof JPopupMenu || current instanceof javax.swing.JToolTip
                    || current instanceof javax.swing.JRootPane || current instanceof java.awt.Window) break;
            if (current instanceof JComponent jc && jc.getClientProperty("Jasper.zone") instanceof String zone)
                return read(zone.equals("workspace"));
        }
        return read(false);
    }

    private static WorkspacePalette read(boolean workspace) {
        return new WorkspacePalette(
            color(workspace, "background", "Panel.background", "Panel.background"),
            color(workspace, "surfaceRaised", "PopupMenu.background", "Panel.background"),
            color(workspace, "surfaceSunken", "Tree.background", "TextArea.background"),
            color(workspace, "text", "Label.foreground", "Label.foreground"),
            color(workspace, "textMuted", "Jasper.mutedForeground", "Label.disabledForeground"),
            color(workspace, "border", "Component.borderColor", "Separator.foreground"),
            color(workspace, "selection", "List.selectionBackground", "TextArea.selectionBackground"),
            color(workspace, "selectionText", "List.selectionForeground", "TextArea.selectionForeground"),
            color(workspace, "hover", "Button.hoverBackground", "Button.background"),
            color(workspace, "accent", "Component.accentColor", "TextArea.selectionBackground"),
            color(workspace, "success", "Jasper.configSuccessForeground", "Label.foreground"),
            color(workspace, "warning", "Jasper.configWarningForeground", "Label.foreground"),
            color(workspace, "error", "Jasper.configErrorForeground", "Label.foreground"));
    }
    private static Color color(boolean workspace, String name, String chrome, String fallback) {
        Color color = workspace ? UIManager.getColor("Jasper.workspace." + name) : null;
        if (color == null) color = UIManager.getColor(chrome);
        if (color == null) color = Objects.requireNonNull(UIManager.getColor(fallback), fallback);
        return new Color(color.getRGB(), true);
    }
}
