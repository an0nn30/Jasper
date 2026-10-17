package dev.jasper.ui.metrics;

import java.util.Arrays;
import java.util.Objects;

/**
 * A kit's type: a size and a weight for every {@link Typography.Role}, the body size those sizes are written for (the
 * base) and the body size the kit draws at when the user sets none. A role draws at its size times the body size over
 * the base.
 */
public final class TypeScale {
    /** Today's 13-point table at a 13-point body, frozen by default-type.tsv. */
    public static final TypeScale DEFAULT = new TypeScale(declaredSizes(), declaredWeights(), 13f, 13f, true);
    /** The classic look's 12-point table at a 12-point body (Enterprise presentation spec 5.6), frozen by classic-type.tsv. */
    public static final TypeScale CLASSIC = classic();

    private final float[] sizes;
    private final int[] weights;
    private final float base;
    private final float body;
    private final boolean labelTracking;

    private TypeScale(float[] sizes, int[] weights, float base, float body, boolean labelTracking) {
        this.sizes = sizes; this.weights = weights; this.base = base; this.body = body; this.labelTracking = labelTracking;
    }

    /** {@code role}'s size at the base body size. */
    public float size(Typography.Role role) { return sizes[Objects.requireNonNull(role).ordinal()]; }
    /** {@code role}'s weight: 400, 500 or 600. */
    public int weight(Typography.Role role) { return weights[Objects.requireNonNull(role).ordinal()]; }
    /** The body size the role sizes are written for. */
    public float base() { return base; }
    /** The body size this scale draws at when the user sets none. */
    public float body() { return body; }
    /** Whether section labels take the platform face's extra letter spacing (the default look's; the classic has none). */
    public boolean labelTracking() { return labelTracking; }

    /** This table at another standard body size, from 6 to 96 points. */
    public TypeScale withBody(float body) {
        if (!Float.isFinite(body) || body < 6 || body > 96) throw new IllegalArgumentException("Invalid body size: " + body);
        return new TypeScale(sizes, weights, base, body, labelTracking);
    }

    private static TypeScale classic() {
        var roles = Typography.Role.values();
        var sizes = new float[roles.length];
        var weights = new int[roles.length];
        for (var role : roles) {
            float[] row = classicRow(role);
            sizes[role.ordinal()] = row[0];
            weights[role.ordinal()] = (int) row[1];
        }
        return new TypeScale(sizes, weights, 12f, 12f, false);
    }

    /** {@code role}'s classic size and weight. */
    private static float[] classicRow(Typography.Role role) {
        return switch (role) {
            case PROMPT_TITLE -> new float[] {15, 600};
            case DETAIL_TITLE -> new float[] {15, 600};
            case WINDOW_TITLE -> new float[] {12, 600};
            case PANEL_TITLE -> new float[] {12, 600};
            case PANEL_DETAIL_TITLE -> new float[] {12, 600};
            case PANEL_DETAIL_LABEL -> new float[] {12, 400};
            case PANEL_HEADER_TITLE -> new float[] {12, 600};
            case PANEL_HEADER_COUNT -> new float[] {11, 400};
            case PANEL_SEARCH -> new float[] {12, 400};
            case PANEL_ROW_SELECTED -> new float[] {12, 500};
            case PANEL_ROW_SECONDARY -> new float[] {12, 400};
            case PANEL_DETAIL_MONO -> new float[] {12, 400};
            case PANEL_EMPTY_DESCRIPTION -> new float[] {12, 400};
            case PANEL_ROW_CHIP -> new float[] {10, 500};
            case PICKER_COMPACT -> new float[] {11, 400};
            case PICKER_CAPTION -> new float[] {11, 400};
            case TITLE_CONTEXT -> new float[] {12, 400};
            case TOOLBAR_LABEL -> new float[] {12, 400};
            case STATUS_TEXT -> new float[] {12, 400};
            case STATUS_PATH -> new float[] {12, 400};
            case RESULTS_HEADING -> new float[] {11, 400};
            case PALETTE_HINT -> new float[] {11, 400};
            case BODY -> new float[] {12, 400};
            case BODY_STRONG -> new float[] {12, 500};
            case LABEL -> new float[] {12, 400};
            case DESCRIPTION -> new float[] {11, 400};
            case SETTINGS_BODY -> new float[] {12, 400};
            case SETTINGS_PREVIEW -> new float[] {12, 400};
            case SUPPORTING -> new float[] {11, 400};
            case CAPTION -> new float[] {11, 400};
            case SETTING_DESCRIPTION -> new float[] {11, 400};
            case SECTION_LABEL -> new float[] {12, 600};
            case COUNT -> new float[] {11, 400};
            case CHIP -> new float[] {10, 500};
            case MONO -> new float[] {12, 400};
            case MONO_SMALL -> new float[] {11, 400};
            case MONO_CAPTION -> new float[] {11, 400};
            case ORDERED_ACTION -> new float[] {12, 400};
            case RUN_HEADING -> new float[] {11, 600};
            case RUN_DETAIL -> new float[] {10.5f, 400};
        };
    }

    private static float[] declaredSizes() {
        var roles = Typography.Role.values();
        var sizes = new float[roles.length];
        for (var role : roles) sizes[role.ordinal()] = role.declaredSize;
        return sizes;
    }

    private static int[] declaredWeights() {
        var roles = Typography.Role.values();
        var weights = new int[roles.length];
        for (var role : roles) weights[role.ordinal()] = role.declaredWeight;
        return weights;
    }

    @Override public boolean equals(Object other) {
        return other instanceof TypeScale scale && Arrays.equals(sizes, scale.sizes) && Arrays.equals(weights, scale.weights)
            && Float.compare(base, scale.base) == 0 && Float.compare(body, scale.body) == 0 && labelTracking == scale.labelTracking;
    }
    @Override public int hashCode() { return Objects.hash(Arrays.hashCode(sizes), Arrays.hashCode(weights), base, body, labelTracking); }
}
