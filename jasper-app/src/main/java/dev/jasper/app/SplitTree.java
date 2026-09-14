package dev.jasper.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** A Swing-independent split layout with stable pane and branch identities. */
final class SplitTree {
    enum Direction {
        LEFT, RIGHT, UP, DOWN
    }

    enum Axis {
        RIGHT, DOWN
    }

    sealed interface Node permits Leaf, Branch {
    }

    record Leaf(UUID paneId) implements Node {
        Leaf {
            if (paneId == null) {
                throw new IllegalArgumentException("pane ID must not be null");
            }
        }
    }

    record Branch(UUID id, Axis axis, double ratio, Node first, Node second) implements Node {
        Branch {
            if (id == null) {
                throw new IllegalArgumentException("branch ID must not be null");
            }
            if (axis == null) {
                throw new IllegalArgumentException("branch axis must not be null");
            }
            if (first == null || second == null) {
                throw new IllegalArgumentException("branch children must not be null");
            }
            ratio = clampRatio(ratio);
        }
    }

    private static final double MIN_RATIO = 0.1;
    private static final double MAX_RATIO = 0.9;
    private static final double EPSILON = 1.0e-12;

    private Node root;
    private UUID focused;
    private boolean zoomed;

    SplitTree(UUID firstPane) {
        requireId(firstPane, "first pane ID");
        root = new Leaf(firstPane);
        focused = firstPane;
    }

    Optional<Node> root() {
        return Optional.ofNullable(root);
    }

    Optional<UUID> focused() {
        return Optional.ofNullable(focused);
    }

    List<UUID> panes() {
        List<UUID> result = new ArrayList<>();
        collectPanes(root, result);
        return List.copyOf(result);
    }

    boolean zoomed() {
        return zoomed;
    }

    void focus(UUID paneId) {
        requireExistingPane(paneId);
        focused = paneId;
    }

    void split(UUID newPane, Axis axis) {
        requireId(newPane, "new pane ID");
        if (axis == null) {
            throw new IllegalArgumentException("split axis must not be null");
        }
        if (containsPane(root, newPane)) {
            throw new IllegalArgumentException("pane ID already exists: " + newPane);
        }
        if (focused == null) {
            throw new IllegalStateException("cannot split an empty tree");
        }
        root = replaceLeaf(root, focused,
            new Branch(UUID.randomUUID(), axis, 0.5, new Leaf(focused), new Leaf(newPane)));
        focused = newPane;
        zoomed = false;
    }

    void close(UUID paneId) {
        requireExistingPane(paneId);
        Removal removal = remove(root, paneId);
        root = removal.node();
        if (paneId.equals(focused)) {
            focused = removal.focusReplacement();
        }
        if (root == null) {
            focused = null;
        }
        zoomed = false;
    }

    void navigate(Direction direction) {
        if (direction == null) {
            throw new IllegalArgumentException("direction must not be null");
        }
        if (root == null || focused == null) {
            return;
        }
        List<LocatedLeaf> leaves = new ArrayList<>();
        collectGeometry(root, new Rectangle(0.0, 0.0, 1.0, 1.0), leaves);
        Rectangle current = leaves.stream()
            .filter(leaf -> leaf.paneId().equals(focused))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("focused pane is not in the split tree"))
            .bounds();

        LocatedLeaf best = null;
        double bestForward = Double.POSITIVE_INFINITY;
        double bestPerpendicular = Double.POSITIVE_INFINITY;
        for (LocatedLeaf candidate : leaves) {
            if (candidate.paneId().equals(focused)
                || !isInDirection(current, candidate.bounds(), direction)
                || !perpendicularOverlap(current, candidate.bounds(), direction)) {
                continue;
            }
            double forward = forwardDistance(current, candidate.bounds(), direction);
            double perpendicular = perpendicularCenterDistance(current, candidate.bounds(), direction);
            if (forward < bestForward - EPSILON
                || (Math.abs(forward - bestForward) <= EPSILON
                    && perpendicular < bestPerpendicular - EPSILON)) {
                best = candidate;
                bestForward = forward;
                bestPerpendicular = perpendicular;
            }
        }
        if (best != null) {
            focused = best.paneId();
        }
    }

    void toggleZoom() {
        if (focused != null) {
            zoomed = !zoomed;
        }
    }

    void setRatio(UUID branchId, double ratio) {
        requireId(branchId, "branch ID");
        double adjusted = clampRatio(ratio);
        RatioUpdate update = replaceRatio(root, branchId, adjusted);
        if (!update.found()) {
            throw new IllegalArgumentException("unknown branch ID: " + branchId);
        }
        root = update.node();
    }

    private void requireExistingPane(UUID paneId) {
        requireId(paneId, "pane ID");
        if (!containsPane(root, paneId)) {
            throw new IllegalArgumentException("unknown pane ID: " + paneId);
        }
    }

    private static void requireId(UUID id, String description) {
        if (id == null) {
            throw new IllegalArgumentException(description + " must not be null");
        }
    }

    private static double clampRatio(double ratio) {
        if (!Double.isFinite(ratio)) {
            throw new IllegalArgumentException("ratio must be finite: " + ratio);
        }
        return Math.max(MIN_RATIO, Math.min(MAX_RATIO, ratio));
    }

    private static boolean containsPane(Node node, UUID paneId) {
        if (node == null) {
            return false;
        }
        if (node instanceof Leaf leaf) {
            return leaf.paneId().equals(paneId);
        }
        Branch branch = (Branch) node;
        return containsPane(branch.first(), paneId) || containsPane(branch.second(), paneId);
    }

    private static void collectPanes(Node node, List<UUID> panes) {
        if (node == null) {
            return;
        }
        if (node instanceof Leaf leaf) {
            panes.add(leaf.paneId());
            return;
        }
        Branch branch = (Branch) node;
        collectPanes(branch.first(), panes);
        collectPanes(branch.second(), panes);
    }

    private static Node replaceLeaf(Node node, UUID paneId, Node replacement) {
        if (node instanceof Leaf leaf) {
            return leaf.paneId().equals(paneId) ? replacement : leaf;
        }
        Branch branch = (Branch) node;
        Node first = replaceLeaf(branch.first(), paneId, replacement);
        Node second = replaceLeaf(branch.second(), paneId, replacement);
        if (first == branch.first() && second == branch.second()) {
            return branch;
        }
        return new Branch(branch.id(), branch.axis(), branch.ratio(), first, second);
    }

    private static Removal remove(Node node, UUID paneId) {
        if (node instanceof Leaf leaf) {
            return leaf.paneId().equals(paneId)
                ? new Removal(null, null, true)
                : new Removal(leaf, null, false);
        }
        Branch branch = (Branch) node;
        Removal first = remove(branch.first(), paneId);
        if (first.removed()) {
            if (first.node() == null) {
                return new Removal(branch.second(), firstLeaf(branch.second()), true);
            }
            return new Removal(
                new Branch(branch.id(), branch.axis(), branch.ratio(), first.node(), branch.second()),
                first.focusReplacement(), true);
        }
        Removal second = remove(branch.second(), paneId);
        if (second.removed()) {
            if (second.node() == null) {
                return new Removal(branch.first(), firstLeaf(branch.first()), true);
            }
            return new Removal(
                new Branch(branch.id(), branch.axis(), branch.ratio(), branch.first(), second.node()),
                second.focusReplacement(), true);
        }
        return new Removal(branch, null, false);
    }

    private static UUID firstLeaf(Node node) {
        Node current = node;
        while (current instanceof Branch branch) {
            current = branch.first();
        }
        return ((Leaf) current).paneId();
    }

    private static RatioUpdate replaceRatio(Node node, UUID branchId, double ratio) {
        if (node == null || node instanceof Leaf) {
            return new RatioUpdate(node, false);
        }
        Branch branch = (Branch) node;
        if (branch.id().equals(branchId)) {
            return new RatioUpdate(
                new Branch(branch.id(), branch.axis(), ratio, branch.first(), branch.second()), true);
        }
        RatioUpdate first = replaceRatio(branch.first(), branchId, ratio);
        if (first.found()) {
            return new RatioUpdate(
                new Branch(branch.id(), branch.axis(), branch.ratio(), first.node(), branch.second()), true);
        }
        RatioUpdate second = replaceRatio(branch.second(), branchId, ratio);
        if (second.found()) {
            return new RatioUpdate(
                new Branch(branch.id(), branch.axis(), branch.ratio(), branch.first(), second.node()), true);
        }
        return new RatioUpdate(branch, false);
    }

    private static void collectGeometry(Node node, Rectangle bounds, List<LocatedLeaf> leaves) {
        if (node instanceof Leaf leaf) {
            leaves.add(new LocatedLeaf(leaf.paneId(), bounds));
            return;
        }
        Branch branch = (Branch) node;
        if (branch.axis() == Axis.RIGHT) {
            double firstWidth = bounds.width() * branch.ratio();
            collectGeometry(branch.first(),
                new Rectangle(bounds.x(), bounds.y(), firstWidth, bounds.height()), leaves);
            collectGeometry(branch.second(),
                new Rectangle(bounds.x() + firstWidth, bounds.y(), bounds.width() - firstWidth, bounds.height()),
                leaves);
        } else {
            double firstHeight = bounds.height() * branch.ratio();
            collectGeometry(branch.first(),
                new Rectangle(bounds.x(), bounds.y(), bounds.width(), firstHeight), leaves);
            collectGeometry(branch.second(),
                new Rectangle(bounds.x(), bounds.y() + firstHeight, bounds.width(), bounds.height() - firstHeight),
                leaves);
        }
    }

    private static boolean isInDirection(Rectangle current, Rectangle candidate, Direction direction) {
        return switch (direction) {
            case LEFT -> candidate.right() <= current.x() + EPSILON;
            case RIGHT -> candidate.x() >= current.right() - EPSILON;
            case UP -> candidate.bottom() <= current.y() + EPSILON;
            case DOWN -> candidate.y() >= current.bottom() - EPSILON;
        };
    }

    private static boolean perpendicularOverlap(Rectangle current, Rectangle candidate, Direction direction) {
        if (direction == Direction.LEFT || direction == Direction.RIGHT) {
            return Math.min(current.bottom(), candidate.bottom())
                - Math.max(current.y(), candidate.y()) > EPSILON;
        }
        return Math.min(current.right(), candidate.right())
            - Math.max(current.x(), candidate.x()) > EPSILON;
    }

    private static double forwardDistance(Rectangle current, Rectangle candidate, Direction direction) {
        return switch (direction) {
            case LEFT -> Math.max(0.0, current.x() - candidate.right());
            case RIGHT -> Math.max(0.0, candidate.x() - current.right());
            case UP -> Math.max(0.0, current.y() - candidate.bottom());
            case DOWN -> Math.max(0.0, candidate.y() - current.bottom());
        };
    }

    private static double perpendicularCenterDistance(
        Rectangle current, Rectangle candidate, Direction direction
    ) {
        if (direction == Direction.LEFT || direction == Direction.RIGHT) {
            return Math.abs(current.centerY() - candidate.centerY());
        }
        return Math.abs(current.centerX() - candidate.centerX());
    }

    private record Removal(Node node, UUID focusReplacement, boolean removed) {
    }

    private record RatioUpdate(Node node, boolean found) {
    }

    private record LocatedLeaf(UUID paneId, Rectangle bounds) {
    }

    private record Rectangle(double x, double y, double width, double height) {
        double right() {
            return x + width;
        }

        double bottom() {
            return y + height;
        }

        double centerX() {
            return x + width / 2.0;
        }

        double centerY() {
            return y + height / 2.0;
        }
    }
}
