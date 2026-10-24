package dev.jasper.app.workspace;

import dev.jasper.app.config.AnimationSettings;
import java.awt.*;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static dev.jasper.app.workspace.WindowTabsTest.named;
import static org.assertj.core.api.Assertions.assertThat;

class WindowTabMotionTest {
    private final java.util.List<WindowTabs> strips = new java.util.ArrayList<>();
    @AfterEach void cleanup() throws Exception { edt(() -> strips.forEach(WindowTabs::close)); closeOwners(); }

    @Test void aSecondTabOpensAtTheRecordedSpeed() throws Exception {
        edt(() -> {
            var f = fixture();
            assertThat(f.entry("first").getBounds()).as("one tab fills its strip").isEqualTo(new Rectangle(0, 0, 800, 30));
            f.add("second");
            assertThat(f.entry("first").getX()).isZero();
            f.advance(75);
            // The open curve at 75 ms of 475: the first tab from 800 px towards 400, the second from 0.
            assertThat(f.entry("first").getWidth()).isBetween(618, 626);
            assertThat(f.entry("second").getX()).isEqualTo(f.entry("first").getWidth());
            f.advance(400);
            assertThat(f.entry("first").getBounds()).isEqualTo(new Rectangle(0, 0, 400, 30));
            assertThat(f.entry("second").getBounds()).isEqualTo(new Rectangle(400, 0, 400, 30));
            assertThat(f.strip.motionRunning()).isFalse();
        });
    }

    @Test void thirdTabRedistributesTheFullWidthAtRecordedFrameTimes() throws Exception {
        edt(() -> {
            var f = fixture(); f.add("second"); f.advance(490); f.add("third");
            assertThat(f.entry("third").getWidth()).isZero();
            f.advance(75);
            // 2.908333 s: first boundary 1649 versus final 1491 and initial 1775.
            assertThat(f.entry("first").getWidth()).isBetween(338, 344);
            int edge = 0;
            for (String title : new String[]{"first", "second", "third"}) {
                assertThat(f.entry(title).getX()).isEqualTo(edge); edge += f.entry(title).getWidth();
            }
            assertThat(edge).isEqualTo(800);
            f.advance(400);
            assertThat(f.entry("third").getBounds()).isEqualTo(new Rectangle(533, 0, 267, 30));
            assertThat(f.strip.motionRunning()).isFalse();
        });
    }

    @Test void closingReleasesResourcesImmediatelyAndSurvivorsExpandAtRecordedSpeed() throws Exception {
        edt(() -> {
            var f = fixture(); f.add("second"); f.advance(490); f.add("third"); f.advance(475);
            var closing = f.owner.currentTerminal(); var pane = closing.pane(); int[] released = {0};
            pane.onClosed = () -> released[0]++;
            f.owner.closeTab(closing); f.strip.refresh();
            assertThat(released[0]).isEqualTo(1);
            assertThat(named(f.strip, "select:third")).isNull();
            assertThat(pane.getComponentCount()).isZero();
            f.advance(75);
            assertThat(f.entry("first").getWidth()).isBetween(329, 335);
            f.advance(350);
            assertThat(f.entry("second").getBounds()).isEqualTo(new Rectangle(400, 0, 400, 30));
            assertThat(f.strip.motionRunning()).isFalse();
            f.owner.closeTab(f.owner.currentTerminal()); f.strip.refresh();
            assertThat(f.strip.getPreferredSize().height).isEqualTo(30);
            assertThat(f.strip.motionRunning()).isFalse();
        });
    }

    @Test void metadataAndSelectionDoNotRestartOrChangeTheGeometry() throws Exception {
        edt(() -> {
            var f = fixture(); f.add("second"); f.advance(490); f.add("third"); f.advance(100);
            var before = f.entry("third").getBounds();
            f.owner.currentTerminal().rename("a much longer title"); f.strip.refresh();
            f.owner.selectTab(f.owner.terminalTabs().get(0)); f.strip.refresh();
            assertThat(f.entry("a much longer title").getBounds()).isEqualTo(before);
            f.advance(375);
            assertThat(f.strip.motionRunning()).isFalse();
            assertThat(f.entry("a much longer title").getWidth()).isEqualTo(267);
        });
    }

    @Test void interruptedMotionStartsFromCurrentBoundsAndResizeOrDisableSettles() throws Exception {
        edt(() -> {
            var f = fixture(); f.add("second"); f.advance(490); f.add("third"); f.advance(75);
            var before = f.entry("first").getBounds();
            f.owner.closeTab(f.owner.currentTerminal()); f.strip.refresh();
            assertThat(f.entry("first").getBounds()).isEqualTo(before);
            f.add("fourth");
            f.strip.setSize(601, 30); f.strip.doLayout();
            assertThat(f.strip.motionRunning()).isFalse();
            assertThat(f.entry("fourth").getBounds()).isEqualTo(new Rectangle(401, 0, 200, 30));
            f.add("fifth"); assertThat(f.strip.motionRunning()).isTrue();
            f.strip.setAnimations(new AnimationSettings(false, 475, 425, 0));
            assertThat(f.strip.motionRunning()).isFalse();
            assertThat(f.entry("fifth").getWidth()).isEqualTo(150);
        });
    }

    @Test void zeroDurationsAndDisposalLeaveNoRunningTimer() throws Exception {
        edt(() -> {
            var f = fixture(); f.strip.setAnimations(new AnimationSettings(true, 0, 0, 0));
            f.add("second"); assertThat(f.strip.motionRunning()).isFalse();
            f.strip.setAnimations(AnimationSettings.defaults()); f.add("third");
            assertThat(f.strip.motionRunning()).isTrue();
            f.strip.close(); f.advance(1000);
            assertThat(f.strip.motionRunning()).isFalse();
            assertThat(named(f.strip, "select:third")).isNull();
        });
    }

    @Test void aSecondTabShowsTheMountedStripSettledAndAThirdAnimates() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setSize(800, 500); WindowTabsTest.layoutTree(owner);
            assertThat(owner.windowTabs().isVisible()).as("a window's only tab shows no strip").isFalse();
            owner.newTab(HOME); WindowTabsTest.layoutTree(owner);
            assertThat(owner.windowTabs().isVisible()).isTrue();
            assertThat(owner.windowTabs().motionRunning()).as("the strip shows with its tabs in place").isFalse();
            owner.newTab(HOME); WindowTabsTest.layoutTree(owner);
            assertThat(owner.windowTabs().motionRunning()).isTrue();
            owner.setAnimations(new AnimationSettings(false, 475, 425, 0));
            assertThat(owner.windowTabs().motionRunning()).isFalse();
        });
    }

    /** Spec 10.9: a still look keeps the strip still while the window's own animation settings stay on. */
    @Test void aStillLookSettlesTheStripAtOnceWhateverTheSettings() throws Exception {
        var themes = new java.util.concurrent.atomic.AtomicReference<dev.jasper.app.appearance.ThemeController>();
        try {
            edt(() -> {
                themes.set(dev.jasper.app.appearance.ThemeTestSupport.controller(true));
                themes.get().configureAnimations(false);
                var owner = content(launcher(new ArrayDeque<>()), themes.get());
                owner.setAnimations(AnimationSettings.defaults());
                owner.currentTerminal().rename("first"); owner.update();
                var clock = new AtomicLong(); var strip = new WindowTabs(owner.parentTabs().bar(), clock::get);
                strips.add(strip); strip.refresh(); strip.setSize(800, 30); strip.doLayout();
                var f = new Fixture(owner, strip, clock);
                f.add("second");
                assertThat(f.strip.motionRunning()).isFalse();
                assertThat(f.entry("first").getBounds()).isEqualTo(new Rectangle(0, 0, 400, 30));
                assertThat(f.entry("second").getBounds()).isEqualTo(new Rectangle(400, 0, 400, 30));
            });
        } finally {
            edt(() -> { strips.forEach(WindowTabs::close); strips.clear(); closeOwnersNow(); if (themes.get() != null) themes.get().close(); });
        }
    }

    /**
     * Tabs and panes spec 5 and workspace plan 8a Rulings 20: a reorder takes its place at once, and a tab's entry
     * keeps its place while the tab splits, shows another pane or closes one.
     */
    @Test void reordersAndPanesTakeTheirPlaceAtOnce() throws Exception {
        edt(() -> {
            var f = fixture(); f.add("second"); f.add("third"); f.advance(1000);
            f.owner.reorderParentTab(f.owner.activeParentTab(), 0); f.strip.refresh();
            assertThat(f.strip.motionRunning()).as("a reorder").isFalse();
            assertThat(f.entry("third").getBounds()).isEqualTo(new Rectangle(0, 0, 267, 30));
            f.owner.split(dev.jasper.app.layout.Side.RIGHT); f.owner.currentTerminal().rename("beside"); f.strip.refresh();
            assertThat(f.strip.motionRunning()).as("a split shows its new pane in the tab's entry").isFalse();
            assertThat(f.entry("beside").getBounds()).isEqualTo(new Rectangle(0, 0, 267, 30));
            f.owner.focusGroup(dev.jasper.app.layout.Side.LEFT); f.strip.refresh();
            assertThat(f.strip.motionRunning()).as("another pane focused").isFalse();
            assertThat(f.entry("third").getBounds()).isEqualTo(new Rectangle(0, 0, 267, 30));
            f.owner.closeTab(f.owner.currentTerminal()); f.strip.refresh();
            assertThat(f.strip.motionRunning()).as("a pane of a split tab closed").isFalse();
            assertThat(f.entry("beside").getBounds()).isEqualTo(new Rectangle(0, 0, 267, 30));
            f.add("fourth");
            assertThat(f.strip.motionRunning()).as("an open still moves").isTrue();
        });
    }

    /** Workspace plan 8a Rulings 20: a tab under a new id keeps its entry, settled as it shows another pane. */
    @Test void aTabUnderANewIdKeepsItsEntrySettled() throws Exception {
        edt(() -> {
            var f = fixture(); f.add("second"); f.add("third"); f.advance(1000);
            var entry = f.entry("third");
            f.owner.parentTabs().reid(f.owner.activeParentTab(), dev.jasper.app.layout.ParentTabId.random()); f.strip.refresh();
            f.owner.split(dev.jasper.app.layout.Side.RIGHT); f.owner.currentTerminal().rename("beside"); f.strip.refresh();
            assertThat(f.strip.motionRunning()).as("the new pane shows in the tab's entry, settled").isFalse();
            assertThat(f.entry("beside")).as("the same entry").isSameAs(entry);
            assertThat(f.entry("beside").getBounds()).isEqualTo(new Rectangle(533, 0, 267, 30));
        });
    }

    /** Tabs and panes spec 5: closing down to one tab hides the strip at once, its motion settled. */
    @Test void theStripHidesSettledWithOneTabLeft() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.newTab(HOME); owner.newTab(HOME);
            owner.setSize(800, 500); WindowTabsTest.layoutTree(owner);
            var strip = owner.windowTabs();
            owner.closeTab(owner.currentTerminal());
            assertThat(strip.motionRunning()).as("two tabs left: the close moves").isTrue();
            owner.closeTab(owner.currentTerminal());
            assertThat(strip.isVisible()).as("one tab: no strip").isFalse();
            assertThat(strip.motionRunning()).as("it hides settled").isFalse();
        });
    }

    /** Workspace plan 8c Rulings 9: a pane dropped on a new-tab place makes a tab that takes its place at once; an opened one still moves. */
    @Test void aPaneDroppedAsANewTabTakesItsPlaceAtOnce() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.newTab(HOME);
            owner.split(dev.jasper.app.layout.Side.RIGHT);
            owner.setSize(800, 500); WindowTabsTest.layoutTree(owner);
            var strip = owner.windowTabs();
            assertThat(strip.motionRunning()).as("settled").isFalse();
            owner.movePaneToNewTab(owner.selectedTab(), owner.parentTabCount());
            assertThat(owner.parentTabCount()).isEqualTo(3);
            assertThat(strip.motionRunning()).as("the new tab lands at once").isFalse();
            owner.newTab(HOME);
            assertThat(strip.motionRunning()).as("an opened tab still moves").isTrue();
        });
    }

    /** Workspace plan 8c Rulings 9: Move Pane to New Tab, a command, still opens its tab with the motion. */
    @Test void movePaneToNewTabStillMoves() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.newTab(HOME);
            owner.split(dev.jasper.app.layout.Side.RIGHT);
            owner.setSize(800, 500); WindowTabsTest.layoutTree(owner);
            owner.moveToNewTab(owner.selectedTab());
            assertThat(owner.parentTabCount()).isEqualTo(3);
            assertThat(owner.windowTabs().motionRunning()).isTrue();
        });
    }

    /**
     * Pane flexibility spec 5 and workspace plan 9a Verified facts 7: with motion on, a tab merged into a tab that is
     * not its neighbour leaves the strip at once, the entries left taking their places with no motion, while an open
     * and a close still move.
     */
    @Test void aMergeBetweenTabsThatAreNotNeighboursLandsAtOnceWhileOpenAndCloseKeepTheirMotion() throws Exception {
        edt(() -> {
            var f = fixture(); f.add("second"); f.add("third"); f.advance(1000);
            var ids = f.owner.windowLayout().parentTabs().stream().map(dev.jasper.app.layout.ParentTab::id).toList();
            f.owner.moveTabInto(ids.get(2), ids.get(0)); f.strip.refresh();
            assertThat(f.owner.parentTabCount()).isEqualTo(2);
            assertThat(f.strip.motionRunning()).as("the merge lands at once").isFalse();
            assertThat(f.entry("third").getBounds()).as("the tab it joined, named after the moved tab's active pane")
                .isEqualTo(new Rectangle(0, 0, 400, 30));
            assertThat(f.entry("second").getBounds()).isEqualTo(new Rectangle(400, 0, 400, 30));
            f.add("fourth");
            assertThat(f.strip.motionRunning()).as("an open still moves").isTrue();
            f.advance(1000);
            assertThat(f.strip.motionRunning()).isFalse();
            f.owner.closeTab(f.owner.currentTerminal()); f.strip.refresh();
            assertThat(f.strip.motionRunning()).as("a close still moves").isTrue();
        });
    }

    private Fixture fixture() {
        var owner = content(launcher(new ArrayDeque<>())); owner.currentTerminal().rename("first"); owner.update();
        var clock = new AtomicLong(); var strip = new WindowTabs(owner.parentTabs().bar(), clock::get);
        strips.add(strip); strip.refresh(); strip.setSize(800, 30); strip.doLayout();
        return new Fixture(owner, strip, clock);
    }
    private record Fixture(WindowContent owner, WindowTabs strip, AtomicLong clock) {
        Container entry(String title) { return named(strip, "select:" + title).getParent(); }
        void add(String title) { owner.newTab(HOME); owner.currentTerminal().rename(title); strip.refresh(); }
        void advance(int millis) { clock.addAndGet(millis * 1_000_000L); strip.animate(clock.get()); }
    }
}
