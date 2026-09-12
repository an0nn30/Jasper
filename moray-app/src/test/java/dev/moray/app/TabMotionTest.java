package dev.moray.app;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.ActionEvent;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.moray.app.DesktopTestSupport.*;
import static dev.moray.app.WindowTabsTest.*;
import static org.assertj.core.api.Assertions.assertThat;

class TabMotionTest {
    @Test void newlyAddedTabStartsNarrowWithUsableControlsAfterSettledFirstLayout() throws Exception {
        edt(() -> {
            try (var fixture = new Fixture()) {
                var owner = fixture.owner;
                var strip = owner.windowTabs();
                layout(strip, 700, 38);
                assertThat(entry(strip, owner.currentTab()).getWidth()).isEqualTo(160);
                owner.newTab(HOME); owner.currentTab().rename("new"); owner.update();
                layout(strip, 700, 38);
                assertThat(entry(strip, owner.currentTab()).getWidth()).isBetween(48, 159);
                assertThat(named(strip, "select:new").getWidth()).isPositive();
                assertThat(named(strip, "close:new").getWidth()).isPositive();
            }
        });
    }

    @Test void selectionChangesImmediatelyWhileUnderlineStartsAtOldPaintedPosition() throws Exception {
        edt(() -> {
            try (var fixture = new Fixture()) {
                var owner = fixture.owner;
                var first = owner.currentTab();
                owner.newTab(HOME);
                var strip = owner.windowTabs();
                layout(strip, 700, 38);
                Rectangle old = underline(strip);
                owner.selectTab(first); layout(strip, 700, 38);
                assertThat(owner.currentTab()).isSameAs(first);
                assertThat(underline(strip)).isEqualTo(old);
            }
        });
    }

    @Test void entryAndUnderlineTraversePaintedIntermediateAndSettledBounds() throws Exception {
        edt(() -> {
            try (var fixture = new Fixture()) {
                var owner = fixture.owner; var strip = owner.windowTabs();
                owner.addNotify(); layout(strip, 700, 38);
                assertThat(strip.animationTimer.isRunning()).isFalse();
                owner.newTab(HOME); owner.currentTab().rename("new"); owner.update();
                layout(strip, 700, 38);
                assertThat(strip.animationTimer.isRunning()).isTrue();
                assertThat(entry(strip, owner.currentTab()).getBounds()).isEqualTo(new Rectangle(160, 0, 64, 38));
                assertThat(underline(strip)).isEqualTo(new Rectangle(14, 37, 132, 1));
                fixture.frame(90);
                assertThat(entry(strip, owner.currentTab()).getWidth()).isBetween(100, 159);
                assertThat(underline(strip).x).isBetween(15, 173);
                fixture.frame(148);
                assertThat(entry(strip, owner.currentTab()).getWidth()).isBetween(161, 164);
                assertThat(underline(strip).x).isBetween(175, 180);
                fixture.frame(180);
                assertThat(entry(strip, owner.currentTab()).getBounds()).isEqualTo(new Rectangle(160, 0, 160, 38));
                assertThat(underline(strip)).isEqualTo(new Rectangle(174, 37, 132, 1));
                assertThat(strip.animationTimer.isRunning()).isFalse();
                Rectangle settled = underline(strip);
                fixture.frame(400);
                assertThat(underline(strip)).isEqualTo(settled);
                assertThat(strip.animationTimer.isRunning()).isFalse();
            }
        });
    }

    @Test void rapidSelectionRetargetsFromCurrentPaintAndMetadataDoesNotRestartIt() throws Exception {
        edt(() -> {
            try (var fixture = new Fixture()) {
                var owner = fixture.owner; var strip = owner.windowTabs();
                var first = owner.currentTab(); owner.newTab(HOME); var second = owner.currentTab();
                owner.newTab(HOME); owner.addNotify(); layout(strip, 700, 38);
                owner.selectTab(first); layout(strip, 700, 38);
                fixture.frame(60);
                Rectangle before = underline(strip);
                owner.selectTab(second); layout(strip, 700, 38);
                assertThat(owner.currentTab()).isSameAs(second);
                assertThat(underline(strip)).isEqualTo(before);
                fixture.frame(120);
                Rectangle during = underline(strip);
                assertThat(during).isNotEqualTo(before);
                second.rename("shell title"); owner.update(); layout(strip, 700, 38);
                assertThat(underline(strip)).isEqualTo(during);
                fixture.frame(240);
                assertThat(underline(strip)).isEqualTo(new Rectangle(174, 37, 132, 1));
                assertThat(strip.animationTimer.isRunning()).isFalse();
            }
        });
    }

    @Test void entryMetadataKeepsItsOriginalDeadlineAndControlsSelectDuringExpansion() throws Exception {
        edt(() -> {
            try (var fixture = new Fixture()) {
                var owner = fixture.owner; var strip = owner.windowTabs();
                var first = owner.currentTab(); owner.addNotify(); layout(strip, 700, 38);
                owner.newTab(HOME); var second = owner.currentTab(); second.rename("new"); owner.update();
                layout(strip, 700, 38); fixture.frame(60);
                int midway = entry(strip, second).getWidth();
                second.rename("metadata"); owner.update(); layout(strip, 700, 38);
                assertThat(entry(strip, second).getWidth()).isEqualTo(midway);
                ((AbstractButton) named(strip, "select:" + first.title())).doClick(0);
                assertThat(owner.currentTab()).isSameAs(first);
                ((AbstractButton) named(strip, "select:metadata")).doClick(0);
                assertThat(owner.currentTab()).isSameAs(second);
                fixture.frame(180);
                assertThat(entry(strip, second).getWidth()).isEqualTo(160);
                assertThat(strip.animationTimer.isRunning()).isFalse();
            }
        });
    }

    @Test void overflowResizeReorderAndRemovalSettleWithoutStaleBounds() throws Exception {
        edt(() -> {
            try (var fixture = new Fixture()) {
                var owner = fixture.owner; var strip = owner.windowTabs();
                var first = owner.currentTab(); owner.addNotify(); layout(strip, 700, 38);
                owner.newTab(HOME); owner.currentTab().rename("second"); owner.update();
                layout(strip, 700, 38); fixture.frame(60);
                layout(strip, 250, 38);
                assertThat(entry(strip, owner.currentTab()).isVisible()).isTrue();
                assertThat(entry(strip, owner.currentTab()).getBounds()).isEqualTo(new Rectangle(24, 0, 160, 38));
                assertThat(underline(strip)).isEqualTo(new Rectangle(38, 37, 132, 1));
                assertThat(named(strip, "newTab").getBounds()).isEqualTo(new Rectangle(184, 0, 32, 38));
                assertThat(strip.animationTimer.isRunning()).isFalse();
                owner.newTab(HOME); owner.currentTab().rename("third"); owner.update(); layout(strip, 250, 38);
                assertThat(entry(strip, owner.currentTab()).isVisible()).isTrue();
                assertThat(strip.animationTimer.isRunning()).isFalse();
                layout(strip, 700, 38);
                owner.selectTab(first); layout(strip, 700, 38); fixture.frame(100);
                owner.reorderTab(0, 2); layout(strip, 700, 38);
                assertThat(underline(strip)).isEqualTo(new Rectangle(334, 37, 132, 1));
                assertThat(strip.animationTimer.isRunning()).isFalse();
                owner.closeTab(first); layout(strip, 700, 38);
                assertThat(underline(strip)).isEqualTo(new Rectangle(174, 37, 132, 1));
                assertThat(strip.animationTimer.isRunning()).isFalse();
                layout(strip, 80, 28);
                assertThat(entry(strip, owner.currentTab()).isVisible()).isTrue();
                assertThat(named(strip, "newTab").getBounds().getMaxX()).isLessThanOrEqualTo(80);
                assertThat(underline(strip).getMaxX()).isLessThanOrEqualTo(80);
            }
        });
    }

    @Test void detachmentReattachmentHiddenOwnerAndCloseLeaveNoRunningTimer() throws Exception {
        edt(() -> {
            try (var fixture = new Fixture()) {
                var owner = fixture.owner; var strip = owner.windowTabs();
                owner.addNotify(); layout(strip, 700, 38);
                owner.newTab(HOME); layout(strip, 700, 38);
                assertThat(strip.animationTimer.isRunning()).isTrue();
                owner.removeNotify();
                assertThat(strip.animationTimer.isRunning()).isFalse();
                owner.addNotify(); layout(strip, 700, 38);
                assertThat(entry(strip, owner.currentTab()).getWidth()).isEqualTo(160);
                assertThat(strip.animationTimer.isRunning()).isFalse();
                owner.newTab(HOME); layout(strip, 700, 38);
                assertThat(strip.animationTimer.isRunning()).isTrue();
                owner.setVisible(false);
                assertThat(strip.animationTimer.isRunning()).isFalse();
                owner.setVisible(true); layout(strip, 700, 38);
                assertThat(strip.animationTimer.isRunning()).isFalse();
                owner.newTab(HOME); layout(strip, 700, 38);
                assertThat(strip.animationTimer.isRunning()).isTrue();
                owner.close();
                assertThat(strip.animationTimer.isRunning()).isFalse();
                fixture.frame(90);
                assertThat(underline(strip)).isEqualTo(new Rectangle());
                assertThat(strip.animationTimer.isRunning()).isFalse();
            }
        });
    }

    @Test void hiddenNewEntriesSettleSoVisibleOverflowSelectionCanStillSlide() throws Exception {
        edt(() -> {
            try (var fixture = new Fixture()) {
                var owner = fixture.owner; var strip = owner.windowTabs();
                owner.addNotify(); layout(strip, 550, 38);
                owner.newTab(HOME); owner.newTab(HOME); var third = owner.currentTab(); owner.newTab(HOME);
                layout(strip, 550, 38);
                Rectangle old = underline(strip);
                owner.selectTab(third); layout(strip, 550, 38);
                assertThat(underline(strip)).isEqualTo(old);
                assertThat(strip.animationTimer.isRunning()).isTrue();
                fixture.frame(90);
                assertThat(underline(strip).x).isBetween(39, 197);
                fixture.frame(180);
                assertThat(underline(strip)).isEqualTo(new Rectangle(38, 37, 132, 1));
                assertThat(strip.animationTimer.isRunning()).isFalse();
            }
        });
    }

    @Test void animationFramesDoNotRelayoutTheTerminalDeck() throws Exception {
        edt(() -> {
            try (var fixture = new Fixture()) {
                var owner = fixture.owner; var strip = owner.windowTabs();
                owner.addNotify(); layout(owner, 700, 400);
                owner.newTab(HOME); layout(owner, 700, 400);
                // A pending deck layout must remain pending while strip frames are advanced.
                owner.tabStrip().setSize(200, 100);
                Rectangle retained = owner.currentTab().getBounds();
                fixture.frame(90); fixture.frame(180);
                assertThat(owner.currentTab().getBounds()).isEqualTo(retained);
            }
        });
    }

    private static final class Fixture implements AutoCloseable {
        final AtomicLong time = new AtomicLong();
        final WindowContent owner = new WindowContent(launcher(new ArrayDeque<>()), HOME,
            path -> {}, () -> {}, () -> {}, new ThemeController(), KeyBindings.defaults(true), time::get);
        void frame(long milliseconds) {
            time.set(milliseconds * 1_000_000);
            Timer timer = owner.windowTabs().animationTimer;
            for (var listener : timer.getActionListeners())
                listener.actionPerformed(new ActionEvent(timer, ActionEvent.ACTION_PERFORMED, "frame"));
        }
        @Override public void close() { owner.close(); if (owner.isDisplayable()) owner.removeNotify(); }
    }

    static Container entry(WindowTabs strip, TerminalTab tab) {
        return named(strip, "select:" + tab.title()).getParent();
    }

    static Rectangle underline(WindowTabs strip) {
        BufferedImage image = new BufferedImage(strip.getWidth(), strip.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try { strip.printAll(graphics); } finally { graphics.dispose(); }
        int color = UIManager.getColor("Moray.tabUnderline").getRGB();
        int left = -1, right = -1, y = strip.getHeight() - 1;
        for (int x = 0; x < image.getWidth(); x++) if (image.getRGB(x, y) == color) {
            if (left < 0) left = x;
            right = x;
        }
        return left < 0 ? new Rectangle() : new Rectangle(left, y, right - left + 1, 1);
    }
}
