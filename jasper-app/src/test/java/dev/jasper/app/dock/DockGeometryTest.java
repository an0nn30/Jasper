package dev.jasper.app.dock;

import dev.jasper.app.dock.DockGeometry.Handle;
import dev.jasper.app.dock.DockGeometry.Layout;
import dev.jasper.app.dock.DockGeometry.Metrics;
import java.awt.Rectangle;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DockGeometryTest {
    private static final Rectangle BOUNDS = new Rectangle(0, 0, 1000, 600);

    /** A state showing one window in each of the given slots. */
    private static DockState showing(Slot... slots) {
        var state = new DockState();
        for (Slot slot : slots) {
            String id = slot.name().toLowerCase(java.util.Locale.ROOT);
            state.add(id, slot);
            state.show(id);
        }
        return state;
    }

    private static Layout layout(DockState state) { return DockGeometry.compute(BOUNDS, state, Metrics.DEFAULT); }

    private static Layout layout(int width, int height, DockState state) {
        return DockGeometry.compute(new Rectangle(0, 0, width, height), state, Metrics.DEFAULT);
    }

    private static Rectangle rect(int x, int y, int width, int height) { return new Rectangle(x, y, width, height); }

    @Test void withNothingShowingTheTerminalTakesEverything() {
        Layout layout = layout(new DockState());
        assertThat(layout.terminal()).isEqualTo(BOUNDS);
        assertThat(layout.slots()).isEmpty();
        assertThat(layout.dividers()).isEmpty();
    }

    @Test void aWindowAloneOnItsSideTakesAllOfIt() {
        Layout left = layout(showing(Slot.LEFT_TOP));
        assertThat(left.slots()).containsOnlyKeys(Slot.LEFT_TOP);
        assertThat(left.slot(Slot.LEFT_TOP)).hasValue(rect(0, 0, 260, 600));
        assertThat(left.divider(Handle.LEFT_WIDTH).orElseThrow().bounds()).isEqualTo(rect(260, 0, 6, 600));
        assertThat(left.terminal()).isEqualTo(rect(266, 0, 734, 600));
        assertThat(layout(showing(Slot.LEFT_BOTTOM)).slot(Slot.LEFT_BOTTOM)).hasValue(rect(0, 0, 260, 600));

        Layout right = layout(showing(Slot.RIGHT_TOP));
        assertThat(right.slot(Slot.RIGHT_TOP)).hasValue(rect(740, 0, 260, 600));
        assertThat(right.divider(Handle.RIGHT_WIDTH).orElseThrow().bounds()).isEqualTo(rect(734, 0, 6, 600));
        assertThat(right.terminal()).isEqualTo(rect(0, 0, 734, 600));
        assertThat(layout(showing(Slot.RIGHT_BOTTOM)).slot(Slot.RIGHT_BOTTOM)).hasValue(rect(740, 0, 260, 600));

        Layout bottom = layout(showing(Slot.BOTTOM_LEFT));
        assertThat(bottom.slot(Slot.BOTTOM_LEFT)).hasValue(rect(0, 400, 1000, 200));
        assertThat(bottom.divider(Handle.BOTTOM_HEIGHT).orElseThrow().bounds()).isEqualTo(rect(0, 394, 1000, 6));
        assertThat(bottom.terminal()).isEqualTo(rect(0, 0, 1000, 394));
        assertThat(layout(showing(Slot.BOTTOM_RIGHT)).slot(Slot.BOTTOM_RIGHT)).hasValue(rect(0, 400, 1000, 200));
    }

    @Test void bothSlotsOfASideSplitItAtItsRatio() {
        Layout left = layout(showing(Slot.LEFT_TOP, Slot.LEFT_BOTTOM));
        assertThat(left.slot(Slot.LEFT_TOP)).hasValue(rect(0, 0, 260, 297));
        assertThat(left.slot(Slot.LEFT_BOTTOM)).hasValue(rect(0, 303, 260, 297));
        assertThat(left.divider(Handle.LEFT_RATIO).orElseThrow().bounds()).isEqualTo(rect(0, 297, 260, 6));

        Layout right = layout(showing(Slot.RIGHT_TOP, Slot.RIGHT_BOTTOM));
        assertThat(right.slot(Slot.RIGHT_TOP)).hasValue(rect(740, 0, 260, 297));
        assertThat(right.slot(Slot.RIGHT_BOTTOM)).hasValue(rect(740, 303, 260, 297));
        assertThat(right.divider(Handle.RIGHT_RATIO).orElseThrow().bounds()).isEqualTo(rect(740, 297, 260, 6));

        Layout bottom = layout(showing(Slot.BOTTOM_LEFT, Slot.BOTTOM_RIGHT));
        assertThat(bottom.slot(Slot.BOTTOM_LEFT)).hasValue(rect(0, 400, 497, 200));
        assertThat(bottom.slot(Slot.BOTTOM_RIGHT)).hasValue(rect(503, 400, 497, 200));
        assertThat(bottom.divider(Handle.BOTTOM_RATIO).orElseThrow().bounds()).isEqualTo(rect(497, 400, 6, 200));
    }

    @Test void theBottomAreaSpansTheFullWidthUnderTheSidesAndTheTerminal() {
        Layout layout = layout(showing(Slot.values()));
        assertThat(layout.slot(Slot.LEFT_TOP)).hasValue(rect(0, 0, 260, 194));
        assertThat(layout.slot(Slot.LEFT_BOTTOM)).hasValue(rect(0, 200, 260, 194));
        assertThat(layout.slot(Slot.RIGHT_TOP)).hasValue(rect(740, 0, 260, 194));
        assertThat(layout.slot(Slot.RIGHT_BOTTOM)).hasValue(rect(740, 200, 260, 194));
        assertThat(layout.terminal()).isEqualTo(rect(266, 0, 468, 394));
        assertThat(layout.divider(Handle.BOTTOM_HEIGHT).orElseThrow().bounds()).isEqualTo(rect(0, 394, 1000, 6));
        assertThat(layout.slot(Slot.BOTTOM_LEFT)).hasValue(rect(0, 400, 497, 200));
        assertThat(layout.slot(Slot.BOTTOM_RIGHT)).hasValue(rect(503, 400, 497, 200));
        assertThat(layout.dividers()).extracting(DockGeometry.Divider::handle).containsExactlyInAnyOrder(Handle.values());
    }

    @Test void ratiosMoveTheSplitButKeepBothSlotsAtTheirMinimum() {
        DockState state = showing(Slot.LEFT_TOP, Slot.LEFT_BOTTOM, Slot.BOTTOM_LEFT, Slot.BOTTOM_RIGHT);
        state.setRatio(Side.BOTTOM, 0.7);
        Layout layout = layout(state);
        assertThat(layout.slot(Slot.BOTTOM_LEFT)).hasValue(rect(0, 400, 696, 200));
        assertThat(layout.slot(Slot.BOTTOM_RIGHT)).hasValue(rect(702, 400, 298, 200));

        DockState side = showing(Slot.LEFT_TOP, Slot.LEFT_BOTTOM);
        side.setRatio(Side.LEFT, 0.25);
        assertThat(layout(side).slot(Slot.LEFT_TOP)).hasValue(rect(0, 0, 260, 149));
        assertThat(layout(side).slot(Slot.LEFT_BOTTOM)).hasValue(rect(0, 155, 260, 445));
        side.setRatio(Side.LEFT, 0.1);
        assertThat(layout(side).slot(Slot.LEFT_TOP)).as("the upper slot keeps its minimum").hasValue(rect(0, 0, 260, 120));
        side.setRatio(Side.LEFT, 0.9);
        assertThat(layout(side).slot(Slot.LEFT_BOTTOM)).as("the lower slot keeps its minimum").hasValue(rect(0, 480, 260, 120));
    }

    @Test void aNarrowWindowSqueezesTheRightSideThenTheLeftBeforeTheTerminal() {
        DockState state = showing(Slot.LEFT_TOP, Slot.RIGHT_TOP);
        Layout seven = layout(700, 600, state);
        assertThat(seven.slot(Slot.LEFT_TOP)).hasValue(rect(0, 0, 260, 600));
        assertThat(seven.slot(Slot.RIGHT_TOP)).hasValue(rect(472, 0, 228, 600));
        assertThat(seven.terminal()).isEqualTo(rect(266, 0, 200, 600));

        Layout five = layout(500, 600, state);
        assertThat(five.slot(Slot.RIGHT_TOP).orElseThrow().width).isEqualTo(120);
        assertThat(five.slot(Slot.LEFT_TOP).orElseThrow().width).isEqualTo(168);
        assertThat(five.terminal().width).isEqualTo(200);

        Layout four = layout(400, 600, state);
        assertThat(four.slot(Slot.RIGHT_TOP).orElseThrow().width).isEqualTo(120);
        assertThat(four.slot(Slot.LEFT_TOP).orElseThrow().width).isEqualTo(120);
        assertThat(four.terminal().width).as("only now does the terminal go under its minimum").isEqualTo(148);

        Layout two = layout(200, 600, state);
        assertThat(two.terminal().width).isZero();
        assertThat(two.slot(Slot.RIGHT_TOP)).hasValue(rect(132, 0, 68, 600));
        assertThat(two.slot(Slot.LEFT_TOP)).hasValue(rect(0, 0, 120, 600));

        assertThat(state.size(Side.LEFT)).as("the squeeze never changes the saved sizes").isEqualTo(260);
        assertThat(state.size(Side.RIGHT)).isEqualTo(260);
        assertThat(layout(state).slot(Slot.RIGHT_TOP).orElseThrow().width).as("a larger window restores them").isEqualTo(260);
    }

    @Test void aShortWindowSqueezesTheBottomAreaFirst() {
        DockState state = showing(Slot.BOTTOM_LEFT);
        Layout three = layout(1000, 300, state);
        assertThat(three.slot(Slot.BOTTOM_LEFT)).hasValue(rect(0, 126, 1000, 174));
        assertThat(three.terminal()).isEqualTo(rect(0, 0, 1000, 120));
        Layout two = layout(1000, 200, state);
        assertThat(two.slot(Slot.BOTTOM_LEFT)).hasValue(rect(0, 80, 1000, 120));
        assertThat(two.terminal().height).isEqualTo(74);
        assertThat(state.size(Side.BOTTOM)).isEqualTo(200);
    }

    @Test void tinyBoundsNeverProduceNegativeSizes() {
        for (int[] size : new int[][]{{0, 0}, {5, 5}, {130, 90}}) {
            Layout layout = layout(size[0], size[1], showing(Slot.values()));
            assertThat(layout.terminal().width).isNotNegative();
            assertThat(layout.terminal().height).isNotNegative();
            layout.slots().values().forEach(bounds -> {
                assertThat(bounds.width).isNotNegative();
                assertThat(bounds.height).isNotNegative();
            });
            layout.dividers().forEach(divider -> assertThat(divider.minimum()).isLessThanOrEqualTo(divider.maximum()));
        }
    }

    @Test void boundsWithAnOriginOffsetEveryRectangle() {
        Layout layout = DockGeometry.compute(new Rectangle(10, 20, 1000, 600), showing(Slot.LEFT_TOP, Slot.BOTTOM_LEFT), Metrics.DEFAULT);
        assertThat(layout.slot(Slot.LEFT_TOP)).hasValue(rect(10, 20, 260, 394));
        assertThat(layout.terminal()).isEqualTo(rect(276, 20, 734, 394));
        assertThat(layout.divider(Handle.BOTTOM_HEIGHT).orElseThrow().bounds()).isEqualTo(rect(10, 414, 1000, 6));
        assertThat(layout.slot(Slot.BOTTOM_LEFT)).hasValue(rect(10, 420, 1000, 200));
    }

    @Test void theUiScaleScalesSizesGapsAndMinimums() {
        Metrics doubled = Metrics.at(2);
        Layout layout = DockGeometry.compute(new Rectangle(0, 0, 2000, 1200), showing(Slot.LEFT_TOP), doubled);
        assertThat(layout.slot(Slot.LEFT_TOP)).hasValue(rect(0, 0, 520, 1200));
        assertThat(layout.divider(Handle.LEFT_WIDTH).orElseThrow().bounds()).isEqualTo(rect(520, 0, 12, 1200));
        assertThat(layout.terminal()).isEqualTo(rect(532, 0, 1468, 1200));
        assertThat(doubled.scaled(120)).isEqualTo(240);
        assertThat(doubled.unscaled(701)).isEqualTo(351);
    }

    @Test void draggingASideDividerResizesItsSideWithinItsRange() {
        DockState left = showing(Slot.LEFT_TOP);
        var bar = layout(left).divider(Handle.LEFT_WIDTH).orElseThrow();
        assertThat(List.of(bar.position(), bar.minimum(), bar.maximum())).containsExactly(260, 120, 794);
        DockGeometry.drag(left, bar, 300, Metrics.DEFAULT);
        assertThat(left.size(Side.LEFT)).isEqualTo(300);
        DockGeometry.drag(left, bar, 50, Metrics.DEFAULT);
        assertThat(left.size(Side.LEFT)).as("not below a tool window's minimum").isEqualTo(120);
        DockGeometry.drag(left, bar, 900, Metrics.DEFAULT);
        assertThat(left.size(Side.LEFT)).as("not into the terminal's minimum").isEqualTo(794);

        DockState right = showing(Slot.RIGHT_TOP);
        var rightBar = layout(right).divider(Handle.RIGHT_WIDTH).orElseThrow();
        assertThat(List.of(rightBar.minimum(), rightBar.maximum())).containsExactly(200, 874);
        DockGeometry.drag(right, rightBar, 700, Metrics.DEFAULT);
        assertThat(right.size(Side.RIGHT)).isEqualTo(294);
        DockGeometry.drag(right, rightBar, 950, Metrics.DEFAULT);
        assertThat(right.size(Side.RIGHT)).isEqualTo(120);

        DockState bottom = showing(Slot.BOTTOM_RIGHT);
        var bottomBar = layout(bottom).divider(Handle.BOTTOM_HEIGHT).orElseThrow();
        assertThat(List.of(bottomBar.minimum(), bottomBar.maximum())).containsExactly(120, 474);
        DockGeometry.drag(bottom, bottomBar, 350, Metrics.DEFAULT);
        assertThat(bottom.size(Side.BOTTOM)).isEqualTo(244);
        DockGeometry.drag(bottom, bottomBar, 0, Metrics.DEFAULT);
        assertThat(bottom.size(Side.BOTTOM)).as("the terminal keeps its minimum height").isEqualTo(474);
    }

    @Test void draggingASplitDividerSetsTheRatio() {
        DockState side = showing(Slot.LEFT_TOP, Slot.LEFT_BOTTOM);
        var split = layout(side).divider(Handle.LEFT_RATIO).orElseThrow();
        assertThat(List.of(split.minimum(), split.maximum())).containsExactly(120, 474);
        DockGeometry.drag(side, split, 198, Metrics.DEFAULT);
        assertThat(side.ratio(Side.LEFT)).isCloseTo(198 / 594.0, within(1e-9));
        DockGeometry.drag(side, split, 10, Metrics.DEFAULT);
        assertThat(side.ratio(Side.LEFT)).isCloseTo(120 / 594.0, within(1e-9));

        DockState row = showing(Slot.BOTTOM_LEFT, Slot.BOTTOM_RIGHT);
        DockGeometry.drag(row, layout(row).divider(Handle.BOTTOM_RATIO).orElseThrow(), 300, Metrics.DEFAULT);
        assertThat(row.ratio(Side.BOTTOM)).isCloseTo(300 / 994.0, within(1e-9));
    }

    @Test void draggingStoresUnscaledPixels() {
        DockState state = showing(Slot.LEFT_TOP);
        Metrics doubled = Metrics.at(2);
        var bar = DockGeometry.compute(new Rectangle(0, 0, 2000, 1200), state, doubled).divider(Handle.LEFT_WIDTH).orElseThrow();
        DockGeometry.drag(state, bar, 700, doubled);
        assertThat(state.size(Side.LEFT)).isEqualTo(350);
    }

    @Test void aLayoutHandsOutCopies() {
        Layout layout = layout(showing(Slot.LEFT_TOP));
        layout.terminal().width = 1;
        layout.slot(Slot.LEFT_TOP).orElseThrow().width = 1;
        layout.slots().get(Slot.LEFT_TOP).width = 1;
        layout.divider(Handle.LEFT_WIDTH).orElseThrow().bounds().width = 1;
        assertThat(layout.terminal().width).isEqualTo(734);
        assertThat(layout.slot(Slot.LEFT_TOP).orElseThrow().width).isEqualTo(260);
        assertThat(layout.divider(Handle.LEFT_WIDTH).orElseThrow().bounds().width).isEqualTo(6);
    }

    @Test void theBottomAreaGrowsOnlyUntilASplitSidesHalvesReachTheirMinimum() {
        DockState state = showing(Slot.LEFT_TOP, Slot.LEFT_BOTTOM, Slot.BOTTOM_LEFT);
        var bar = layout(1144, 746, state).divider(Handle.BOTTOM_HEIGHT).orElseThrow();
        // Above the bottom area the halves need 120 + 6 + 120 = 246, more than the terminal's 120.
        assertThat(List.of(bar.position(), bar.minimum(), bar.maximum())).containsExactly(540, 246, 620);
        DockGeometry.drag(state, bar, 0, Metrics.DEFAULT);
        assertThat(state.size(Side.BOTTOM)).as("the limit leaves the halves their minimum").isEqualTo(746 - 6 - 246);

        Layout limit = layout(1144, 746, state);
        assertThat(limit.slot(Slot.LEFT_TOP)).hasValue(rect(0, 0, 260, 120));
        assertThat(limit.slot(Slot.LEFT_BOTTOM)).hasValue(rect(0, 126, 260, 120));
        assertThat(limit.slot(Slot.BOTTOM_LEFT)).hasValue(rect(0, 252, 1144, 494));
        var split = limit.divider(Handle.LEFT_RATIO).orElseThrow();
        assertThat(List.of(split.position(), split.minimum(), split.maximum()))
            .as("the split keeps the range that holds both halves at their minimum").containsExactly(120, 120, 120);

        DockGeometry.drag(state, limit.divider(Handle.BOTTOM_HEIGHT).orElseThrow(), 286, Metrics.DEFAULT);
        var roomier = layout(1144, 746, state).divider(Handle.LEFT_RATIO).orElseThrow();
        assertThat(List.of(roomier.minimum(), roomier.maximum())).as("and it moves again once there is room").containsExactly(120, 160);
    }

    @Test void aShortWindowSqueezesTheBottomAreaBeforeASplitSidesHalves() {
        DockState state = showing(Slot.LEFT_TOP, Slot.LEFT_BOTTOM, Slot.BOTTOM_LEFT);
        Layout squeezed = layout(640, 420, state);
        assertThat(squeezed.slot(Slot.LEFT_TOP)).hasValue(rect(0, 0, 260, 120));
        assertThat(squeezed.slot(Slot.LEFT_BOTTOM)).hasValue(rect(0, 126, 260, 120));
        assertThat(squeezed.slot(Slot.BOTTOM_LEFT)).as("the bottom area gives way first").hasValue(rect(0, 252, 640, 168));
        assertThat(squeezed.terminal()).isEqualTo(rect(266, 0, 374, 246));

        Layout tight = layout(640, 372, state);
        assertThat(tight.slot(Slot.LEFT_TOP)).hasValue(rect(0, 0, 260, 120));
        assertThat(tight.slot(Slot.LEFT_BOTTOM)).hasValue(rect(0, 126, 260, 120));
        assertThat(tight.slot(Slot.BOTTOM_LEFT)).as("down to its own minimum").hasValue(rect(0, 252, 640, 120));

        Layout tooShort = layout(640, 360, state);
        assertThat(tooShort.slot(Slot.BOTTOM_LEFT).orElseThrow().height).isEqualTo(120);
        assertThat(tooShort.slot(Slot.LEFT_TOP).orElseThrow().height).as("only now do the halves give way").isEqualTo(114);

        Layout right = layout(640, 420, showing(Slot.RIGHT_TOP, Slot.RIGHT_BOTTOM, Slot.BOTTOM_RIGHT));
        assertThat(right.slot(Slot.RIGHT_TOP)).hasValue(rect(380, 0, 260, 120));
        assertThat(right.slot(Slot.RIGHT_BOTTOM)).hasValue(rect(380, 126, 260, 120));
        assertThat(right.slot(Slot.BOTTOM_RIGHT)).hasValue(rect(0, 252, 640, 168));
        assertThat(state.size(Side.BOTTOM)).as("the squeeze never changes the saved sizes").isEqualTo(200);
    }

    @Test void theSplitSidesMinimumScalesWithTheUi() {
        Metrics scaled = Metrics.at(1.5);
        var bounds = new Rectangle(0, 0, 1500, 1000);
        DockState state = showing(Slot.LEFT_TOP, Slot.LEFT_BOTTOM, Slot.BOTTOM_LEFT);
        var bar = DockGeometry.compute(bounds, state, scaled).divider(Handle.BOTTOM_HEIGHT).orElseThrow();
        // 180 + 9 + 180 = 369 scaled pixels above the bottom area.
        assertThat(List.of(bar.position(), bar.minimum(), bar.maximum())).containsExactly(691, 369, 811);
        DockGeometry.drag(state, bar, 0, scaled);
        assertThat(state.size(Side.BOTTOM)).as("1000 - 369 - 9 = 622 pixels, unscaled").isEqualTo(415);
        Layout limit = DockGeometry.compute(bounds, state, scaled);
        assertThat(limit.slot(Slot.LEFT_TOP)).hasValue(rect(0, 0, 390, 180));
        assertThat(limit.slot(Slot.LEFT_BOTTOM)).hasValue(rect(0, 189, 390, 180));
        assertThat(limit.slot(Slot.BOTTOM_LEFT)).as("rounding back up to 623 pixels is squeezed away").hasValue(rect(0, 378, 1500, 622));
    }

    @Test void theUpperMinimumIsTheTerminalsWhenNoSideIsSplit() {
        assertThat(DockGeometry.upperMinimum(new DockState(), Metrics.DEFAULT)).isEqualTo(120);
        assertThat(DockGeometry.upperMinimum(showing(Slot.LEFT_TOP, Slot.RIGHT_BOTTOM, Slot.BOTTOM_LEFT, Slot.BOTTOM_RIGHT),
            Metrics.DEFAULT)).as("one window per side, and a split bottom row, do not split a side").isEqualTo(120);
        assertThat(DockGeometry.upperMinimum(new DockState(), Metrics.at(1.5))).as("scaled").isEqualTo(180);
    }

    @Test void theUpperMinimumCountsASplitSidesTwoHalvesAtTheUiScale() {
        assertThat(DockGeometry.upperMinimum(showing(Slot.LEFT_TOP, Slot.LEFT_BOTTOM), Metrics.DEFAULT)).isEqualTo(2 * 120 + 6);
        assertThat(DockGeometry.upperMinimum(showing(Slot.RIGHT_TOP, Slot.RIGHT_BOTTOM, Slot.BOTTOM_LEFT), Metrics.DEFAULT))
            .isEqualTo(2 * 120 + 6);
        assertThat(DockGeometry.upperMinimum(showing(Slot.LEFT_TOP, Slot.LEFT_BOTTOM), Metrics.at(1.5)))
            .as("180 + 9 + 180 scaled pixels").isEqualTo(2 * 180 + 9);
    }

    @Test void onlyASplitSideHasASplitMinimum() {
        assertThat(DockGeometry.splitMinimum(new DockState(), Metrics.DEFAULT)).isZero();
        assertThat(DockGeometry.splitMinimum(showing(Slot.LEFT_TOP, Slot.RIGHT_BOTTOM, Slot.BOTTOM_LEFT, Slot.BOTTOM_RIGHT),
            Metrics.at(1.5))).as("one window per side, and a split bottom row, do not split a side").isZero();
        assertThat(DockGeometry.splitMinimum(showing(Slot.LEFT_TOP, Slot.LEFT_BOTTOM), Metrics.DEFAULT)).isEqualTo(2 * 120 + 6);
        assertThat(DockGeometry.splitMinimum(showing(Slot.RIGHT_TOP, Slot.RIGHT_BOTTOM), Metrics.at(1.5)))
            .as("180 + 9 + 180 scaled pixels").isEqualTo(2 * 180 + 9);
    }

    @Test void aWideSplitsDividerRangeStaysWithinTheRatioLimits() {
        DockState state = showing(Slot.BOTTOM_LEFT, Slot.BOTTOM_RIGHT);
        Layout layout = DockGeometry.compute(new Rectangle(0, 0, 1512, 982), state, Metrics.DEFAULT);
        var split = layout.divider(Handle.BOTTOM_RATIO).orElseThrow();
        // Row width is 1512 - 6 (gap) = 1506; 0.1 * 1506 = 150.6 → 151; 0.9 * 1506 = 1354.4 → 1355
        assertThat(split.minimum()).isEqualTo(151);
        assertThat(split.maximum()).isEqualTo(1355);

        DockGeometry.drag(state, split, split.minimum(), Metrics.DEFAULT);
        Layout atMin = DockGeometry.compute(new Rectangle(0, 0, 1512, 982), state, Metrics.DEFAULT);
        var splitAtMin = atMin.divider(Handle.BOTTOM_RATIO).orElseThrow();
        assertThat(splitAtMin.position()).isEqualTo(splitAtMin.minimum());

        DockGeometry.drag(state, split, split.maximum(), Metrics.DEFAULT);
        Layout atMax = DockGeometry.compute(new Rectangle(0, 0, 1512, 982), state, Metrics.DEFAULT);
        var splitAtMax = atMax.divider(Handle.BOTTOM_RATIO).orElseThrow();
        assertThat(splitAtMax.position()).isEqualTo(splitAtMax.maximum());
    }
}
