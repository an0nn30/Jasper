package dev.jasper.app;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.DesktopTestSupport.*;
import static dev.jasper.app.WindowTabsTest.*;
import static org.assertj.core.api.Assertions.assertThat;

class TabMotionTest {
    @Test void singleTabHasNoStripAndSecondTabRevealsSettledEqualWidths() throws Exception {
        edt(() -> {
            try (var f = new Fixture()) {
                assertThat(f.strip.isVisible()).isFalse();
                f.add("second");
                assertThat(f.strip.isVisible()).isTrue();
                assertThat(entry(f.strip, f.owner.currentTab()).getWidth()).isEqualTo(338);
                assertThat(f.strip.animationTimer.isRunning()).isFalse();
            }
        });
    }

    @Test void newTabExpandsWhileNeighboursContractAndPlusStaysAtTheEdge() throws Exception {
        edt(() -> {
            try (var f = new Fixture()) {
                f.add("second"); f.add("third");
                Container third = entry(f.strip, f.owner.currentTab());
                assertThat(third.getWidth()).isZero();
                assertThat(f.strip.animationTimer.isRunning()).isTrue();
                f.frame(90);
                assertThat(third.getWidth()).isBetween(100, 225);
                assertThat(named(f.strip, "newTab").getBounds()).isEqualTo(new Rectangle(676, 0, 24, 38));
                assertThat(third.getX() + third.getWidth()).isEqualTo(676);
                f.frame(180);
                assertThat(third.getWidth()).isBetween(225, 226);
                assertThat(f.strip.animationTimer.isRunning()).isFalse();
            }
        });
    }

    @Test void metadataAndSelectionDoNotChangeGeometryOrRestartEntryDeadline() throws Exception {
        edt(() -> {
            try (var f = new Fixture()) {
                var first = f.owner.currentTab(); f.add("second"); f.add("third"); f.frame(60);
                var third = f.owner.currentTab();
                Rectangle before = entry(f.strip, third).getBounds();
                third.rename("a much longer program-supplied title"); f.owner.selectTab(first); f.layout();
                assertThat(entry(f.strip, third).getBounds()).isEqualTo(before);
                assertThat(f.owner.currentTab()).isSameAs(first);
                f.frame(180);
                assertThat(f.strip.animationTimer.isRunning()).isFalse();
            }
        });
    }

    @Test void closingDuringArrivalKeepsPaintedWidthThenRemovesTheDisabledEntry() throws Exception {
        edt(() -> {
            try (var f = new Fixture()) {
                f.add("second"); f.add("third"); f.frame(60);
                var third = f.owner.currentTab();
                Container departing = entry(f.strip, third);
                int before = departing.getWidth();
                f.owner.closeTab(third); f.layout();
                assertThat(departing.getWidth()).isEqualTo(before);
                assertThat(named(departing, "close:third").isEnabled()).isFalse();
                f.frame(120);
                assertThat(departing.getWidth()).isBetween(1, before - 1);
                f.frame(240);
                assertThat(departing.getParent()).isNull();
                assertThat(entry(f.strip, f.owner.currentTab()).getWidth()).isEqualTo(338);
                assertThat(f.strip.animationTimer.isRunning()).isFalse();
            }
        });
    }

    @Test void closingDownToOneHidesTheStripAndClearsAllDeparturesImmediately() throws Exception {
        edt(() -> {
            try (var f = new Fixture()) {
                var first = f.owner.currentTab(); f.add("second"); var second = f.owner.currentTab();
                f.add("third"); f.frame(180);
                var third = f.owner.currentTab(); Container one = entry(f.strip, first), two = entry(f.strip, second);
                f.owner.closeTab(first); f.layout(); f.frame(240);
                f.owner.closeTab(second); f.layout();
                assertThat(f.owner.currentTab()).isSameAs(third);
                assertThat(f.strip.isVisible()).isFalse();
                assertThat(one.getParent()).isNull(); assertThat(two.getParent()).isNull();
                assertThat(f.strip.animationTimer.isRunning()).isFalse();
            }
        });
    }

    @Test void overflowResizeAndReorderSettleMotionAndKeepSelectionReachable() throws Exception {
        edt(() -> {
            try (var f = new Fixture()) {
                f.add("second"); f.add("third"); f.frame(60);
                layout(f.strip, 250, 38);
                assertThat(entry(f.strip, f.owner.currentTab()).isVisible()).isTrue();
                assertThat(named(f.strip, "nextTabs").isVisible()).isTrue();
                assertThat(named(f.strip, "newTab").getBounds().getMaxX()).isEqualTo(250);
                assertThat(f.strip.animationTimer.isRunning()).isFalse();
                layout(f.strip, 700, 38);
                f.owner.reorderTab(2, 0); f.layout();
                assertThat(entry(f.strip, f.owner.currentTab()).getX()).isZero();
                assertThat(f.strip.animationTimer.isRunning()).isFalse();
                layout(f.strip, 30, 38);
                assertThat(named(f.strip, "newTab").getBounds().getMaxX()).isEqualTo(30);
            }
        });
    }

    @Test void hidingDetachingAndDisposalStopTheTimerAndClearDepartures() throws Exception {
        edt(() -> {
            try (var f = new Fixture()) {
                f.add("second"); f.add("third");
                assertThat(f.strip.animationTimer.isRunning()).isTrue();
                f.owner.setVisible(false);
                assertThat(f.strip.animationTimer.isRunning()).isFalse();
                f.owner.setVisible(true); f.layout();
                assertThat(f.strip.animationTimer.isRunning()).isFalse();
                f.add("fourth"); f.owner.removeNotify();
                assertThat(f.strip.animationTimer.isRunning()).isFalse();
                f.owner.addNotify(); f.layout();
                f.owner.closeTab(f.owner.currentTab()); f.layout();
                assertThat(f.strip.animationTimer.isRunning()).isTrue();
                f.owner.close();
                assertThat(f.strip.animationTimer.isRunning()).isFalse();
            }
        });
    }

    @Test void framesDoNotRelayoutTerminalContent() throws Exception {
        edt(() -> {
            try (var f = new Fixture()) {
                f.add("second"); layout(f.owner, 700, 400);
                f.add("third"); layout(f.owner, 700, 400);
                f.owner.tabStrip().setSize(200, 100);
                Rectangle retained = f.owner.currentTab().getBounds();
                f.frame(90); f.frame(180);
                assertThat(f.owner.currentTab().getBounds()).isEqualTo(retained);
            }
        });
    }

    @Test void realHeaderDoesNotAllocateSpaceForADuplicateTitle() throws Exception {
        edt(() -> {
            try (var f = new Fixture()) {
                f.add("second");
                var root = new JRootPane();
                try (var header = WindowContent.installTitleBar(root, f.owner, true, title -> {})) {
                    layout(header, 958, 38);
                    Rectangle allocation = f.strip.getBounds();
                    f.owner.currentTab().rename("long title ".repeat(100)); layout(header, 958, 38);
                    assertThat(f.strip.getBounds()).isEqualTo(allocation);
                    assertThat(allocation.x + allocation.width).isEqualTo(958);
                    assertThat(header.getComponent(0).isVisible()).isFalse();
                }
            }
        });
    }

    private static final class Fixture implements AutoCloseable {
        final AtomicLong time = new AtomicLong();
        final WindowContent owner = new WindowContent(launcher(new ArrayDeque<>()), HOME,
            path -> {}, () -> {}, () -> {}, new ThemeController(), KeyBindings.defaults(true), time::get);
        final WindowTabs strip = owner.windowTabs();
        Fixture() { owner.addNotify(); layout(); }
        void add(String title) { owner.newTab(HOME); owner.currentTab().rename(title); layout(); }
        void layout() { WindowTabsTest.layout(strip, 700, 38); }
        void frame(long milliseconds) {
            time.set(milliseconds * 1_000_000);
            Timer timer = strip.animationTimer;
            for (var listener : timer.getActionListeners())
                listener.actionPerformed(new ActionEvent(timer, ActionEvent.ACTION_PERFORMED, "frame"));
        }
        @Override public void close() { owner.close(); if (owner.isDisplayable()) owner.removeNotify(); }
    }

    static Container entry(WindowTabs strip, TerminalTab tab) {
        return named(strip, "select:" + tab.title()).getParent();
    }
}
