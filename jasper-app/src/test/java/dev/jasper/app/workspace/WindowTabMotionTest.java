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

    @Test void firstRevealUsesFullHeightAndExpandsBothTitlesFromTheRecordedInset() throws Exception {
        edt(() -> {
            var f = fixture(); f.add("second");
            assertThat(f.strip.isVisible()).isTrue();
            assertThat(f.strip.getPreferredSize().height).isEqualTo(30);
            assertThat(f.entry("first").getX()).isEqualTo(100);
            assertThat(f.entry("first").getWidth()).isZero();
            f.advance(75);
            // Reference at 1.750 s: left inset 122 px, first width 329 px, on a 1698 px strip.
            assertThat(f.entry("first").getX()).isBetween(57, 63);
            assertThat(f.entry("first").getWidth()).isBetween(151, 159);
            f.advance(415);
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
            var closing = f.owner.currentTab(); var pane = closing.focusedPane(); int[] released = {0};
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
            f.owner.closeTab(f.owner.currentTab()); f.strip.refresh();
            assertThat(f.strip.isVisible()).isFalse();
            assertThat(f.strip.getPreferredSize().height).isZero();
            assertThat(f.strip.motionRunning()).isFalse();
        });
    }

    @Test void metadataAndSelectionDoNotRestartOrChangeTheGeometry() throws Exception {
        edt(() -> {
            var f = fixture(); f.add("second"); f.advance(490); f.add("third"); f.advance(100);
            var before = f.entry("third").getBounds();
            f.owner.currentTab().rename("a much longer title"); f.strip.refresh();
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
            f.owner.closeTab(f.owner.currentTab()); f.strip.refresh();
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

    @Test void firstRevealAnimatesAfterRealHiddenParentLayout() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setSize(800, 500); WindowTabsTest.layoutTree(owner);
            assertThat(owner.windowTabs().isVisible()).isFalse();
            owner.newTab(HOME); WindowTabsTest.layoutTree(owner);
            assertThat(owner.windowTabs().motionRunning()).isTrue();
            owner.setAnimations(new AnimationSettings(false, 475, 425, 0));
            assertThat(owner.windowTabs().motionRunning()).isFalse();
        });
    }

    private Fixture fixture() {
        var owner = content(launcher(new ArrayDeque<>())); owner.currentTab().rename("first"); owner.update();
        var clock = new AtomicLong(); var strip = new WindowTabs(owner, clock::get);
        strips.add(strip); strip.refresh(); strip.setSize(800, 30); strip.doLayout();
        return new Fixture(owner, strip, clock);
    }
    private record Fixture(WindowContent owner, WindowTabs strip, AtomicLong clock) {
        Container entry(String title) { return named(strip, "select:" + title).getParent(); }
        void add(String title) { owner.newTab(HOME); owner.currentTab().rename(title); strip.refresh(); }
        void advance(int millis) { clock.addAndGet(millis * 1_000_000L); strip.animate(clock.get()); }
    }
}
