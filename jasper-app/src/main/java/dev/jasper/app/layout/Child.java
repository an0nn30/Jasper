package dev.jasper.app.layout;

import java.util.Objects;

/**
 * One child of a {@link Split} and its share of the split's length.
 *
 * @param node the child
 * @param weight its share: positive and finite; the split normalizes its children's weights to sum to 1
 */
public record Child(Node node, double weight) {
    /** Checks the node is present and the weight positive and finite. */
    public Child {
        Objects.requireNonNull(node, "node");
        if (!(weight > 0 && Double.isFinite(weight))) throw new IllegalArgumentException("A weight must be positive and finite: " + weight);
    }
}
