package dev.jasper.app.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Children laid out along one axis, each taking its weight's share of the length (spec 7.1). Construction flattens a
 * child split of the same orientation into this one, scaling its children's weights by its own; needs at least two
 * children after that; and normalizes the weights to sum to 1, keeping weights that already do (within
 * {@code 1e-9}) bit for bit.
 *
 * @param orientation HORIZONTAL lays the children out from left to right, VERTICAL from top to bottom
 * @param children the children in order: at least two, none a split of this orientation, weights summing to 1
 */
public record Split(Orientation orientation, List<Child> children) implements Node {
    /** Weights this close to summing to 1 are kept as they are, so normalizing twice changes nothing. */
    static final double TOLERANCE = 1e-9;

    /** Flattens, checks and normalizes the children. */
    public Split {
        Objects.requireNonNull(orientation, "orientation");
        var flat = new ArrayList<Child>();
        for (Child child : children) {
            Objects.requireNonNull(child, "child");
            if (child.node() instanceof Split nested && nested.orientation() == orientation) {
                for (Child inner : nested.children()) flat.add(new Child(inner.node(), inner.weight() * child.weight()));
            } else {
                flat.add(child);
            }
        }
        if (flat.size() < 2) throw new IllegalArgumentException("A split needs at least two children: " + flat.size());
        double sum = 0;
        for (Child child : flat) sum += child.weight();
        if (Math.abs(sum - 1) > TOLERANCE) {
            double total = sum;
            flat.replaceAll(child -> new Child(child.node(), child.weight() / total));
        }
        children = List.copyOf(flat);
    }
}
