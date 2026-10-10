package dev.jasper.sdk.ui;

import java.util.Map;
import java.util.Objects;

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
}
