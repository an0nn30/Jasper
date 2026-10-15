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
    public static final TypeScale DEFAULT = new TypeScale(declaredSizes(), declaredWeights(), 13f, 13f);

    private final float[] sizes;
    private final int[] weights;
    private final float base;
    private final float body;

    private TypeScale(float[] sizes, int[] weights, float base, float body) {
        this.sizes = sizes; this.weights = weights; this.base = base; this.body = body;
    }

    /** {@code role}'s size at the base body size. */
    public float size(Typography.Role role) { return sizes[Objects.requireNonNull(role).ordinal()]; }
    /** {@code role}'s weight: 400, 500 or 600. */
    public int weight(Typography.Role role) { return weights[Objects.requireNonNull(role).ordinal()]; }
    /** The body size the role sizes are written for. */
    public float base() { return base; }
    /** The body size this scale draws at when the user sets none. */
    public float body() { return body; }

    /** This table at another standard body size, from 6 to 96 points. */
    public TypeScale withBody(float body) {
        if (!Float.isFinite(body) || body < 6 || body > 96) throw new IllegalArgumentException("Invalid body size: " + body);
        return new TypeScale(sizes, weights, base, body);
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
            && Float.compare(base, scale.base) == 0 && Float.compare(body, scale.body) == 0;
    }
    @Override public int hashCode() { return Objects.hash(Arrays.hashCode(sizes), Arrays.hashCode(weights), base, body); }
}
