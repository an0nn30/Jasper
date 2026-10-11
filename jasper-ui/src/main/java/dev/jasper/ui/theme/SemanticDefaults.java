package dev.jasper.ui.theme;

import java.awt.Color;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import static dev.jasper.ui.theme.SemanticColor.*;

/**
 * Canonical fallback graph: legacy bases, authored canonical bases, then dependent defaults.
 * Dependencies always reference the final canonical parent. Stored colors contain no scrim,
 * tile, ring or hover paint treatment; those opacities belong to the kit.
 */
public final class SemanticDefaults {
    private static final List<SemanticColor> DEPENDENT = List.of(TEXT_ONACCENT_SECONDARY, BORDER_IDENTITY, BORDER_FOCUS,
        BORDER_ERROR, ACCENT_HOVER, TEXT_LINK_HOVER, HOVER_OVERLAY, HUE_BLUE_SURFACE, HUE_GREEN_SURFACE, HUE_AMBER_SURFACE, HUE_PURPLE_SURFACE, HUE_NEUTRAL_SURFACE);

    private SemanticDefaults() {}

    /** Resolves complete legacy values and authored overrides without touching UI state. */
    public static SemanticPalette resolve(Map<SemanticColor, Color> overrides, Map<Token, Color> legacy,
                                           TerminalColors terminal) {
        var colors = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) {
            if (!DEPENDENT.contains(role)) colors.put(role, overrides.containsKey(role) ? overrides.get(role) : base(role, legacy, terminal));
        }
        // Every dependent parent is a base role, so these edges are acyclic and in topological order.
        for (var role : DEPENDENT) {
            Color derived = switch (role) {
                case TEXT_ONACCENT_SECONDARY -> ColorMath.opacity(colors.get(TEXT_ONACCENT), .60);
                case BORDER_FOCUS -> colors.get(ACCENT);
                case BORDER_IDENTITY -> colors.get(BORDER_CONTROL);
                case BORDER_ERROR -> colors.get(STATUS_DANGER);
                case ACCENT_HOVER -> ColorMath.mix(colors.get(TEXT_PRIMARY), colors.get(ACCENT), .08);
                case TEXT_LINK_HOVER -> ColorMath.mix(colors.get(TEXT_PRIMARY), colors.get(TEXT_LINK), .08);
                case HOVER_OVERLAY -> colors.get(TEXT_PRIMARY);
                case HUE_BLUE_SURFACE -> colors.get(HUE_BLUE);
                case HUE_GREEN_SURFACE -> colors.get(HUE_GREEN);
                case HUE_AMBER_SURFACE -> colors.get(HUE_AMBER);
                case HUE_PURPLE_SURFACE -> colors.get(HUE_PURPLE);
                case HUE_NEUTRAL_SURFACE -> colors.get(HUE_NEUTRAL);

                default -> throw new IllegalStateException("Unexpected dependent role: " + role);
            };
            colors.put(role, overrides.getOrDefault(role, derived));
        }
        // An unauthored off track must separate from its knob on light as well as dark surfaces.
        // Explicit canonical switch colors retain precedence, including deliberate low contrast.
        if (!overrides.containsKey(TOGGLE_TRACK_OFF)
                && ColorMath.contrast(colors.get(TOGGLE_TRACK_OFF), colors.get(TOGGLE_KNOB)) < SurfaceContrast.INDICATOR) {
            colors.put(TOGGLE_TRACK_OFF, SurfaceContrast.readable(colors.get(TOGGLE_TRACK_OFF),
                colors.get(TOGGLE_KNOB), SurfaceContrast.INDICATOR, colors.get(TEXT_PRIMARY)));
        }
        return new SemanticPalette(colors);
    }

    private static Color base(SemanticColor role, Map<Token, Color> legacy, TerminalColors terminal) {
        return switch (role) {
            case SURFACE_APP -> legacy.get(Token.TITLE_BAR);
            case SURFACE_STATUS -> legacy.get(Token.STATUS_BAR);
            case SURFACE_PANEL, SURFACE_DIALOG -> legacy.get(Token.SURFACE);
            case SURFACE_PANEL_RAISED, SURFACE_MENU -> legacy.get(Token.SURFACE_RAISED);
            case SURFACE_CARD -> legacy.get(Token.SURFACE_SUNKEN);
            case SURFACE_SIDEBAR -> legacy.get(Token.SIDEBAR);
            case SURFACE_INPUT -> legacy.get(Token.INPUT_BACKGROUND);
            case SURFACE_CONTROL, SEGMENT_SELECTED_BG, TOGGLE_TRACK_OFF, BUTTON_SECONDARY_BG -> legacy.get(Token.BUTTON_BACKGROUND);
            case SURFACE_CONTROL_HOVER, BUTTON_SECONDARY_BG_HOVER -> legacy.get(Token.HOVER);
            case SURFACE_TERMINAL -> terminal.background();
            case SURFACE_SCRIM -> terminal.ansi().stream().min(Comparator.comparingDouble(ColorMath::luminance)).orElseThrow();
            case BORDER_WINDOW, BORDER_INPUT, BORDER_MENU -> legacy.get(Token.BORDER);
            case BORDER_SUBTLE -> legacy.get(Token.SEPARATOR);
            case BORDER_CONTROL -> legacy.get(Token.BUTTON_BORDER);
            case TEXT_PRIMARY -> legacy.get(Token.TEXT);
            case TEXT_SECONDARY, TEXT_TERTIARY, HUE_NEUTRAL -> legacy.get(Token.TEXT_MUTED);
            case TEXT_PLACEHOLDER, STATUS_IDLE -> legacy.get(Token.TEXT_DISABLED);
            case TEXT_ONACCENT, TOGGLE_KNOB -> legacy.get(Token.TEXT_ON_ACCENT);
            case TEXT_LINK -> legacy.get(Token.LINK);
            case TEXT_ERROR, STATUS_DANGER -> legacy.get(Token.ERROR);
            case TEXT_SUCCESS, STATUS_SUCCESS -> legacy.get(Token.SUCCESS);
            case STATUS_WARNING -> legacy.get(Token.WARNING);
            case ACCENT -> legacy.get(Token.ACCENT);
            case SELECTION_BG -> legacy.get(Token.SELECTION);
            case HUE_BLUE -> terminal.ansi().get(4);
            case HUE_GREEN -> terminal.ansi().get(2);
            case HUE_AMBER -> terminal.ansi().get(3);
            case HUE_PURPLE -> terminal.ansi().get(5);
            case BORDER_IDENTITY, HUE_BLUE_SURFACE, HUE_GREEN_SURFACE, HUE_AMBER_SURFACE, HUE_PURPLE_SURFACE, HUE_NEUTRAL_SURFACE, TEXT_ONACCENT_SECONDARY, BORDER_FOCUS, BORDER_ERROR, ACCENT_HOVER, TEXT_LINK_HOVER, HOVER_OVERLAY ->
                throw new IllegalStateException("Dependent role cannot resolve before parents: " + role);
        };
    }
}
