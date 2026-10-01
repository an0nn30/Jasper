package dev.jasper.sdk.ui;

import java.util.Map;
import java.util.EnumMap;
import java.util.Objects;
import java.util.Locale;
import javax.swing.UIManager;

/** Immutable styles for every semantic syntax role.
 * @param styles complete role mapping
 * @since 0.12.0
 */
public record SyntaxPalette(Map<SyntaxRole, SyntaxStyle> styles) {
    /** Copies the mapping and validates its completeness. */
    public SyntaxPalette {
        styles = Map.copyOf(styles);
        for (SyntaxRole role : SyntaxRole.values()) Objects.requireNonNull(styles.get(role), role.name());
    }
    /** Finds a role's style.
     * @param role semantic role
     * @return its style
     */
    public SyntaxStyle style(SyntaxRole role) { return styles.get(Objects.requireNonNull(role)); }
    /** Reads workspace syntax defaults with plain-text fallbacks for custom hosts. UI thread only.
     * @return complete syntax colors and emphasis
     */
    public static SyntaxPalette current() {
        var editor = EditorPalette.current();
        var styles = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
        for (var role : SyntaxRole.values()) {
            String key = "Jasper.workspace.syntax." + role.name().toLowerCase(Locale.ROOT);
            var color = UIManager.getColor(key + ".color");
            Object italic = UIManager.get(key + ".italic");
            styles.put(role, new SyntaxStyle(color == null ? editor.foreground() : new java.awt.Color(color.getRGB(), true),
                Boolean.TRUE.equals(UIManager.get(key + ".bold")), italic instanceof Boolean value ? value
                    : role == SyntaxRole.COMMENT || role == SyntaxRole.DOC_COMMENT));
        }
        return new SyntaxPalette(styles);
    }
}
