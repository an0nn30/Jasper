package dev.jasper.app.workspace;

import dev.jasper.app.config.AnimationSettings;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class WindowTabMotionTest {
    private final java.util.List<WindowTabs> strips = new java.util.ArrayList<>();
    @AfterEach void cleanup() throws Exception { edt(() -> strips.forEach(WindowTabs::close)); closeOwners(); }
    private static JComponent named(Container root, String name) {
        for (var child : root.getComponents()) {
            if (name.equals(child.getName())) return (JComponent) child;
            if (child instanceof Container container) { var found = named(container, name); if (found != null) return found; }
        }
        return null;
    }

    @Test void openingExpandsAndFadesWithoutDelayingSelection() throws Exception {
        edt(() -> {
            var fixture = fixture();
            fixture.owner.newTab(HOME); fixture.owner.currentTab().rename("second"); fixture.strip.refresh();
            Container second = named(fixture.strip, "select:second").getParent();
            assertThat(fixture.owner.currentTab().title()).isEqualTo("second");
            assertThat(second.getWidth()).isZero();
            fixture.advance(75);
            assertThat(second.getWidth()).isBetween(35, 120);
            assertThat(maximumAlpha(second)).as("the entering title fades at midpoint").isBetween(1, 200);
            fixture.advance(75);
            assertThat(second.getWidth()).isBetween(80, 240);
            assertThat(maximumAlpha(second)).isEqualTo(255);
            assertThat(fixture.strip.motionRunning()).isFalse();
        });
    }

    @Test void closingReleasesTheSessionImmediatelyAndClosesTheGapFromItsCurrentWidth() throws Exception {
        edt(() -> {
            var fixture = fixture();
            fixture.owner.newTab(HOME); fixture.owner.currentTab().rename("second"); fixture.strip.refresh(); fixture.advance(150);
            var closing = fixture.owner.terminalTabs().get(0);
            var pane = closing.focusedPane();
            int[] released = {0}; pane.onClosed = () -> released[0]++;
            Container second = named(fixture.strip, "select:second").getParent();
            int before = second.getX();
            fixture.owner.closeTab(closing); fixture.strip.refresh();
            assertThat(fixture.owner.tabStrip().indexOfComponent(closing)).isEqualTo(-1);
            assertThat(released[0]).as("resource teardown precedes the exit visual").isEqualTo(1);
            assertThat(pane.getComponentCount()).isZero();
            assertThat(named(fixture.strip, "select:first")).isNull();
            assertThat(second.getX()).isEqualTo(before);
            fixture.advance(60);
            assertThat(second.getX()).isBetween(1, before - 1);
            fixture.advance(60);
            assertThat(second.getX()).isZero();
            assertThat(fixture.strip.motionRunning()).isFalse();
        });
    }

    @Test void switchingGlidesTheFolderAndRepeatedSwitchesRetargetWithoutAJump() throws Exception {
        edt(() -> {
            var fixture = fixture();
            fixture.owner.newTab(HOME); fixture.owner.currentTab().rename("second"); fixture.strip.refresh(); fixture.advance(150);
            var second = fixture.owner.currentTab(); var first = fixture.owner.tabStrip().getComponentAt(0);
            double start = fixture.strip.folderBounds().getX();
            fixture.owner.selectTab(first); fixture.strip.refresh();
            assertThat(fixture.owner.selectedComponent()).isSameAs(first);
            assertThat(fixture.strip.folderBounds().getX()).isEqualTo(start);
            fixture.advance(65);
            double middle = fixture.strip.folderBounds().getX();
            assertThat(middle).isBetween(1.0, start - 1);
            fixture.owner.selectTab(second); fixture.strip.refresh();
            assertThat(fixture.strip.folderBounds().getX()).isEqualTo(middle);
            fixture.advance(130);
            assertThat(fixture.strip.folderBounds().getX()).isEqualTo(start);
        });
    }

    @Test void rapidMouseClicksRetargetTheMovingFolderWithoutSnapping() throws Exception {
        edt(() -> {
            var fixture = fixture(); fixture.owner.newTab(HOME); fixture.owner.currentTab().rename("second");
            fixture.strip.refresh(); fixture.advance(150);
            click(named(fixture.strip, "select:first")); fixture.strip.refresh(); fixture.advance(65);
            var before = fixture.strip.folderBounds();
            click(named(fixture.strip, "select:second")); fixture.strip.refresh();
            assertThat(fixture.owner.currentTab().title()).isEqualTo("second");
            assertThat(fixture.strip.folderBounds()).isEqualTo(before);
            fixture.advance(130);
            assertThat(fixture.strip.folderBounds().getX()).isGreaterThan(before.getX());
        });
    }

    private static void click(JComponent button) {
        for (int id : new int[]{java.awt.event.MouseEvent.MOUSE_PRESSED, java.awt.event.MouseEvent.MOUSE_RELEASED})
            button.dispatchEvent(new java.awt.event.MouseEvent(button, id, 1, 0, 10, 10, 1, false, java.awt.event.MouseEvent.BUTTON1));
    }

    @Test void rapidOpenCloseRetargetsTheCurrentGapAndLiveDisableSettlesEverything() throws Exception {
        edt(() -> {
            var fixture = fixture();
            fixture.owner.newTab(HOME); fixture.owner.currentTab().rename("second"); fixture.strip.refresh(); fixture.advance(50);
            int width = named(fixture.strip, "select:second").getParent().getWidth();
            fixture.owner.closeTab(fixture.owner.currentTab()); fixture.strip.refresh();
            fixture.owner.newTab(HOME); fixture.owner.currentTab().rename("third"); fixture.strip.refresh();
            assertThat(named(fixture.strip, "select:third").getParent().getX()).isGreaterThanOrEqualTo(80 + width - 1);
            fixture.strip.setAnimations(new AnimationSettings(false, 150, 120, 130));
            assertThat(named(fixture.strip, "select:third").getParent().getWidth()).isBetween(80, 240);
            assertThat(fixture.strip.motionRunning()).isFalse();
        });
    }

    @Test void zeroDurationsAndDisposalLeaveNoRunningTimer() throws Exception {
        edt(() -> {
            var fixture = fixture(); fixture.strip.setAnimations(new AnimationSettings(true, 0, 0, 0));
            fixture.owner.newTab(HOME); fixture.strip.refresh();
            assertThat(fixture.strip.motionRunning()).isFalse();
            fixture.strip.setAnimations(AnimationSettings.defaults());
            fixture.owner.newTab(HOME); fixture.strip.refresh();
            assertThat(fixture.strip.motionRunning()).isTrue();
            fixture.strip.close(); fixture.advance(200);
            assertThat(fixture.strip.motionRunning()).isFalse();
        });
    }

    @Test void overflowAndRepeatedGlassPaintStayBoundedDuringMotion() throws Exception {
        edt(() -> {
            var fixture = fixture();
            fixture.owner.applyBackgroundOpacity(.55);
            for (int i = 0; i < 9; i++) { fixture.owner.newTab(HOME); fixture.strip.refresh(); }
            fixture.advance(75);
            fixture.strip.setSize(220, 30); fixture.strip.doLayout();
            assertThat(named(fixture.strip, "newTab").getBounds()).isEqualTo(new Rectangle(196, 0, 24, 30));
            fixture.owner.selectTab(fixture.owner.tabStrip().getComponentAt(0)); fixture.strip.refresh(); fixture.advance(65);
            assertThat(fixture.strip.motionRunning()).isTrue();
            var image = new BufferedImage(220, 30, BufferedImage.TYPE_INT_ARGB);
            var g = image.createGraphics(); fixture.strip.paint(g); int before = image.getRGB(100, 29);
            g.setClip(90, 20, 30, 10);
            fixture.strip.paint(g); assertThat(image.getRGB(100, 29)).isEqualTo(before); g.dispose();
            assertThat(fixture.strip.folderBounds().getWidth()).isBetween(0.0, 240.0);
        });
    }

    @Test void operationDurationsKeepTheFolderWithOpeningAndClosingMotion() throws Exception {
        edt(() -> {
            var fixture = fixture(); fixture.strip.setAnimations(new AnimationSettings(true, 1000, 800, 0));
            double initial = fixture.strip.folderBounds().getX();
            fixture.owner.newTab(HOME); fixture.strip.refresh();
            assertThat(fixture.strip.folderBounds().getX()).isEqualTo(initial);
            fixture.advance(500);
            double middle = fixture.strip.folderBounds().getX();
            assertThat(middle).isBetween(1.0, 79.0);
            fixture.advance(500);
            double beforeClose = fixture.strip.folderBounds().getX();
            fixture.owner.closeTab(fixture.owner.currentTab()); fixture.strip.refresh();
            assertThat(fixture.strip.folderBounds().getX()).isEqualTo(beforeClose);
            fixture.advance(400);
            assertThat(fixture.strip.folderBounds().getX()).isBetween(1.0, beforeClose - 1);
            fixture.advance(400);
            assertThat(fixture.strip.folderBounds().getX()).isZero();
        });
    }

    @Test void aTitleArrivingMidOpenRetargetsWidthWithoutJumpingTheFolder() throws Exception {
        edt(() -> {
            var fixture = fixture(); fixture.strip.setAnimations(new AnimationSettings(true, 1000, 800, 0));
            fixture.owner.newTab(HOME); fixture.strip.refresh(); fixture.advance(500);
            var before = fixture.strip.folderBounds();
            fixture.owner.currentTab().rename("a shell title with enough characters to use the maximum tab width"); fixture.strip.refresh();
            assertThat(fixture.strip.folderBounds()).isEqualTo(before);
            fixture.advance(500);
            assertThat(fixture.strip.folderBounds().getWidth()).isEqualTo(240);
            assertThat(fixture.strip.motionRunning()).isFalse();
        });
    }

    @Test void savedConfigurationDisablesRunningAndFutureMotionAcrossWindows() throws Exception {
        edt(() -> {
            var first = content(launcher(new ArrayDeque<>())); var second = content(launcher(new ArrayDeque<>()));
            var enabled = dev.jasper.app.config.ConfigSnapshot.defaults().toBuilder()
                .animations(new AnimationSettings(true, 1000, 1000, 1000)).build();
            var disabled = enabled.toBuilder().animations(new AnimationSettings(false, 1000, 1000, 1000)).build();
            for (var owner : java.util.List.of(first, second)) {
                owner.applyConfiguration(enabled, false);
                owner.windowTabs().setSize(800, 30); owner.windowTabs().doLayout();
                owner.newTab(HOME); assertThat(owner.windowTabs().motionRunning()).isTrue();
            }
            for (var owner : java.util.List.of(first, second)) {
                owner.applyConfiguration(disabled, false);
                assertThat(owner.windowTabs().motionRunning()).isFalse();
                owner.newTab(HOME);
                assertThat(owner.windowTabs().motionRunning()).isFalse();
                assertThat(named(owner.windowTabs(), "select:" + owner.currentTab().title()).getParent().getWidth()).isBetween(80, 240);
            }
        });
    }

    private Fixture fixture() {
        var owner = content(launcher(new ArrayDeque<>())); owner.currentTab().rename("first"); owner.update();
        var clock = new AtomicLong(); var strip = new WindowTabs(owner, clock::get);
        strips.add(strip); strip.refresh(); strip.setSize(800, 30); strip.doLayout();
        return new Fixture(owner, strip, clock);
    }
    private record Fixture(WindowContent owner, WindowTabs strip, AtomicLong clock) {
        void advance(int millis) { clock.addAndGet(millis * 1_000_000L); strip.animate(clock.get()); }
    }
    private static int maximumAlpha(Container entry) {
        var image = new BufferedImage(entry.getWidth(), entry.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); entry.paint(g); g.dispose();
        int maximum = 0;
        for (int x = 0; x < image.getWidth(); x++) for (int y = 0; y < image.getHeight(); y++)
            maximum = Math.max(maximum, image.getRGB(x, y) >>> 24);
        return maximum;
    }
}
