package dev.moray.app;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SplitTreeTest {
    @Test
    void splitNavigateCloseAndZoomPreserveAValidFocus() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        SplitTree tree = new SplitTree(a);

        tree.split(b, SplitTree.Axis.RIGHT);
        tree.split(c, SplitTree.Axis.DOWN);
        tree.navigate(SplitTree.Direction.UP);

        assertThat(tree.focused()).contains(b);
        tree.close(b);
        assertThat(tree.panes()).containsExactly(a, c);
        assertThat(tree.focused()).contains(c);

        tree.toggleZoom();
        assertThat(tree.zoomed()).isTrue();
        tree.close(c);
        assertThat(tree.panes()).containsExactly(a);
        assertThat(tree.focused()).contains(a);
        assertThat(tree.zoomed()).isFalse();
    }

    @Test
    void splitRebuildsOnlyTheFocusedLeafAndRetainsBranchRatiosThroughZoom() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        SplitTree tree = new SplitTree(a);
        tree.split(b, SplitTree.Axis.RIGHT);
        SplitTree.Branch outer = (SplitTree.Branch) tree.root().orElseThrow();
        tree.setRatio(outer.id(), 0.73);

        tree.toggleZoom();
        tree.split(c, SplitTree.Axis.DOWN);

        SplitTree.Branch rebuiltOuter = (SplitTree.Branch) tree.root().orElseThrow();
        assertThat(rebuiltOuter.id()).isEqualTo(outer.id());
        assertThat(rebuiltOuter.ratio()).isEqualTo(0.73);
        assertThat(rebuiltOuter.first()).isEqualTo(new SplitTree.Leaf(a));
        assertThat(rebuiltOuter.second()).isInstanceOf(SplitTree.Branch.class);
        assertThat(tree.focused()).contains(c);
        assertThat(tree.zoomed()).isFalse();
    }

    @Test
    void ratiosAreClampedAndNonFiniteRatiosAreRejected() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        SplitTree tree = new SplitTree(a);
        tree.split(b, SplitTree.Axis.RIGHT);
        UUID branchId = ((SplitTree.Branch) tree.root().orElseThrow()).id();

        tree.setRatio(branchId, -4.0);
        assertThat(((SplitTree.Branch) tree.root().orElseThrow()).ratio()).isEqualTo(0.1);
        tree.setRatio(branchId, 4.0);
        assertThat(((SplitTree.Branch) tree.root().orElseThrow()).ratio()).isEqualTo(0.9);

        assertThatIllegalArgumentException().isThrownBy(() -> tree.setRatio(branchId, Double.NaN))
            .withMessageContaining("ratio");
        assertThatIllegalArgumentException().isThrownBy(() -> tree.setRatio(branchId, Double.POSITIVE_INFINITY))
            .withMessageContaining("ratio");
    }

    @Test
    void directionalNavigationRequiresOverlapAndUsesGeometryInsteadOfTraversalOrder() {
        UUID left = UUID.randomUUID();
        UUID upperRight = UUID.randomUUID();
        UUID lowerRight = UUID.randomUUID();
        SplitTree tree = new SplitTree(left);
        tree.split(upperRight, SplitTree.Axis.RIGHT);
        tree.split(lowerRight, SplitTree.Axis.DOWN);
        tree.focus(left);

        tree.navigate(SplitTree.Direction.RIGHT);
        assertThat(tree.focused()).contains(upperRight);
        tree.navigate(SplitTree.Direction.DOWN);
        assertThat(tree.focused()).contains(lowerRight);
        tree.navigate(SplitTree.Direction.LEFT);
        assertThat(tree.focused()).contains(left);
        tree.navigate(SplitTree.Direction.DOWN);
        assertThat(tree.focused()).contains(left);
    }

    @Test
    void asymmetricGeometryChoosesTheNearestPerpendicularCenter() {
        UUID leftTop = UUID.randomUUID();
        UUID leftBottom = UUID.randomUUID();
        UUID rightTop = UUID.randomUUID();
        UUID rightBottom = UUID.randomUUID();
        SplitTree tree = new SplitTree(leftTop);
        tree.split(rightTop, SplitTree.Axis.RIGHT);
        SplitTree.Branch root = (SplitTree.Branch) tree.root().orElseThrow();
        tree.setRatio(root.id(), 0.65);
        tree.focus(leftTop);
        tree.split(leftBottom, SplitTree.Axis.DOWN);
        SplitTree.Branch leftBranch = (SplitTree.Branch) ((SplitTree.Branch) tree.root().orElseThrow()).first();
        tree.setRatio(leftBranch.id(), 0.8);
        tree.focus(rightTop);
        tree.split(rightBottom, SplitTree.Axis.DOWN);
        SplitTree.Branch rightBranch = (SplitTree.Branch) ((SplitTree.Branch) tree.root().orElseThrow()).second();
        tree.setRatio(rightBranch.id(), 0.3);

        tree.focus(leftBottom);
        tree.navigate(SplitTree.Direction.RIGHT);

        assertThat(tree.focused()).contains(rightBottom);
    }

    @Test
    void navigationDoesNotWrapAndStillChangesTheVisiblePaneWhileZoomed() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        SplitTree tree = new SplitTree(a);
        tree.split(b, SplitTree.Axis.RIGHT);
        tree.toggleZoom();

        tree.navigate(SplitTree.Direction.RIGHT);
        assertThat(tree.focused()).contains(b);
        tree.navigate(SplitTree.Direction.LEFT);
        assertThat(tree.focused()).contains(a);
        assertThat(tree.zoomed()).isTrue();
    }

    @Test
    void closingTheLastPaneLeavesAnEmptyUnzoomedTree() {
        UUID a = UUID.randomUUID();
        SplitTree tree = new SplitTree(a);
        tree.toggleZoom();

        tree.close(a);

        assertThat(tree.root()).isEmpty();
        assertThat(tree.focused()).isEmpty();
        assertThat(tree.panes()).isEmpty();
        assertThat(tree.zoomed()).isFalse();
        tree.toggleZoom();
        assertThat(tree.zoomed()).isFalse();
    }

    @Test
    void invalidPaneAndBranchIdsAreRejectedWithoutChangingTheTree() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        SplitTree tree = new SplitTree(a);

        assertThatIllegalArgumentException().isThrownBy(() -> tree.split(a, SplitTree.Axis.RIGHT))
            .withMessageContaining(a.toString());
        assertThatIllegalArgumentException().isThrownBy(() -> tree.focus(b))
            .withMessageContaining(b.toString());
        assertThatIllegalArgumentException().isThrownBy(() -> tree.close(b))
            .withMessageContaining(b.toString());
        assertThatIllegalArgumentException().isThrownBy(() -> tree.setRatio(b, 0.5))
            .withMessageContaining(b.toString());
        assertThat(tree.panes()).containsExactly(a);
        assertThat(tree.focused()).contains(a);
    }
}
