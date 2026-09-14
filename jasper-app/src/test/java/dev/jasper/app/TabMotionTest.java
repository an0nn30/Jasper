package dev.jasper.app;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.ActionEvent;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.DesktopTestSupport.*;
import static dev.jasper.app.WindowTabsTest.*;
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

    @Test void overflowResizeAndReorderSettleWhileCloseAnimatesWithoutStaleBounds() throws Exception {
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
                assertThat(underline(strip)).isEqualTo(new Rectangle(334, 37, 132, 1));
                assertThat(strip.animationTimer.isRunning()).isTrue();
                fixture.frame(280);
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

    @Test void realHeaderTitleAllocationPreservesSelectionSlide() throws Exception {
        edt(() -> {
            try (var fixture = new Fixture()) {
                var owner = fixture.owner; var strip = owner.windowTabs();
                var first = owner.currentTab(); first.rename("short");
                owner.newTab(HOME); owner.currentTab().rename("a much longer title"); owner.update();
                fixture.installHeader();
                int beforeWidth = strip.getWidth();
                Rectangle before = underline(strip);
                owner.selectTab(first); fixture.layoutHeader();
                assertThat(strip.getWidth()).isGreaterThan(beforeWidth);
                assertThat(owner.currentTab()).isSameAs(first);
                assertThat(underline(strip)).isEqualTo(before);
                assertThat(strip.animationTimer.isRunning()).isTrue();
                fixture.frame(60);
                assertThat(underline(strip).x).isBetween(15, 173);
                fixture.frame(180);
                assertThat(underline(strip)).isEqualTo(new Rectangle(14, 37, 132, 1));
                assertThat(strip.animationTimer.isRunning()).isFalse();
            }
        });
    }

    @Test void realHeaderNewTabExpandsDespiteActiveTitleAllocationChange() throws Exception {
        edt(() -> {
            try (var fixture = new Fixture()) {
                var owner = fixture.owner; var strip = owner.windowTabs();
                owner.currentTab().rename("a much longer title"); owner.update(); fixture.installHeader();
                int beforeWidth = strip.getWidth();
                owner.newTab(HOME); var second = owner.currentTab(); second.rename("short"); owner.update();
                fixture.layoutHeader();
                assertThat(strip.getWidth()).isGreaterThan(beforeWidth);
                assertThat(entry(strip, second).getWidth()).isEqualTo(64);
                assertThat(strip.animationTimer.isRunning()).isTrue();
                fixture.frame(90);
                assertThat(entry(strip, second).getWidth()).isBetween(100, 159);
                fixture.frame(180);
                assertThat(entry(strip, second).getWidth()).isEqualTo(160);
                assertThat(underline(strip)).isEqualTo(new Rectangle(174, 37, 132, 1));
                assertThat(strip.animationTimer.isRunning()).isFalse();
            }
        });
    }

    @Test void realHeaderMetadataAllocationPreservesEntryAndUnderlineDeadline() throws Exception {
        edt(() -> {
            try (var fixture = new Fixture()) {
                var owner = fixture.owner; var strip = owner.windowTabs();
                fixture.installHeader();
                owner.newTab(HOME); var second = owner.currentTab(); second.rename("short"); owner.update();
                fixture.layoutHeader(); fixture.frame(60);
                int beforeWidth = strip.getWidth();
                int entryWidth = entry(strip, second).getWidth();
                Rectangle before = underline(strip);
                second.rename("a much longer shell title"); owner.update(); fixture.layoutHeader();
                assertThat(strip.getWidth()).isLessThan(beforeWidth);
                assertThat(entry(strip, second).getWidth()).isEqualTo(entryWidth);
                assertThat(underline(strip)).isEqualTo(before);
                assertThat(strip.animationTimer.isRunning()).isTrue();
                fixture.frame(180);
                assertThat(entry(strip, second).getWidth()).isEqualTo(160);
                assertThat(underline(strip)).isEqualTo(new Rectangle(174, 37, 132, 1));
                assertThat(strip.animationTimer.isRunning()).isFalse();
                owner.newTab(HOME); owner.currentTab().rename("third"); owner.update();
                fixture.layoutHeader(); fixture.frame(210);
                layout(fixture.host, 400, 958);
                assertThat(entry(strip, owner.currentTab()).isVisible()).isTrue();
                assertThat(named(strip, "newTab").getBounds().getMaxX()).isLessThanOrEqualTo(strip.getWidth());
                assertThat(strip.animationTimer.isRunning()).isFalse();
            }
        });
    }

    @Test void realHeaderActiveCloseContractsAndSlidesToPreviousTabImmediatelySelected() throws Exception {
        edt(() -> {
            try (var fixture = new Fixture()) {
                var owner = fixture.owner; var strip = owner.windowTabs();
                var first = owner.currentTab(); first.rename("short");
                owner.newTab(HOME); var second = owner.currentTab(); second.rename("a much longer title"); owner.update();
                fixture.installHeader();
                Container departing = entry(strip, second);
                Rectangle before = underline(strip);
                owner.closeTab(second); fixture.layoutHeader();
                assertThat(owner.currentTab()).isSameAs(first);
                assertThat(owner.tabStrip().getTabCount()).isEqualTo(1);
                assertThat(departing.getParent()).isSameAs(strip);
                assertThat(departing.getWidth()).isEqualTo(160);
                assertThat(named(departing, "close:a much longer title").isEnabled()).isFalse();
                assertThat(underline(strip)).isEqualTo(before);
                fixture.frame(90);
                assertThat(departing.getWidth()).isBetween(1, 100);
                assertThat(underline(strip).x).isBetween(15, 173);
                fixture.frame(180);
                assertThat(departing.getParent()).isNull();
                assertThat(underline(strip)).isEqualTo(new Rectangle(14, 37, 132, 1));
                assertThat(strip.animationTimer.isRunning()).isFalse();
            }
        });
    }

    @Test void inactiveCloseMovesFollowingTabAndUnderlineTogether() throws Exception {
        edt(() -> {
            try (var fixture = new Fixture()) {
                var owner = fixture.owner; var strip = owner.windowTabs();
                var first = owner.currentTab(); first.rename("first");
                owner.newTab(HOME); var second = owner.currentTab(); second.rename("second"); owner.update();
                fixture.installHeader();
                Container departing = entry(strip, first), remaining = entry(strip, second);
                owner.closeTab(first); fixture.layoutHeader();
                assertThat(owner.currentTab()).isSameAs(second);
                assertThat(remaining.getX()).isEqualTo(160);
                assertThat(underline(strip).x).isEqualTo(174);
                fixture.frame(90);
                assertThat(remaining.getX()).isBetween(1, 100);
                assertThat(underline(strip).x).isEqualTo(remaining.getX() + 14);
                fixture.frame(180);
                assertThat(departing.getParent()).isNull();
                assertThat(remaining.getX()).isZero();
                assertThat(underline(strip).x).isEqualTo(14);
                assertThat(strip.animationTimer.isRunning()).isFalse();
            }
        });
    }

    @Test void closeDuringEntryStartsAtCurrentWidthAndCleanupDropsAllDepartures() throws Exception {
        edt(() -> {
            try (var fixture = new Fixture()) {
                var owner = fixture.owner; var strip = owner.windowTabs();
                fixture.installHeader();
                owner.newTab(HOME); var second = owner.currentTab(); second.rename("second"); owner.update();
                fixture.layoutHeader(); fixture.frame(60);
                Container departing = entry(strip, second);
                int before = departing.getWidth();
                owner.closeTab(second); fixture.layoutHeader();
                assertThat(departing.getParent()).isSameAs(strip);
                assertThat(departing.getWidth()).isEqualTo(before);
                fixture.frame(120);
                assertThat(departing.getWidth()).isBetween(1, before - 1);
                owner.newTab(HOME); fixture.layoutHeader();
                owner.closeTab(owner.currentTab()); fixture.layoutHeader();
                fixture.host.setVisible(false);
                assertThat(departing.getParent()).isNull();
                assertThat(strip.animationTimer.isRunning()).isFalse();
                fixture.host.setVisible(true); fixture.layoutHeader();
                assertThat(underline(strip).x).isEqualTo(14);
                assertThat(strip.animationTimer.isRunning()).isFalse();
                owner.close(); fixture.frame(400);
                assertThat(underline(strip)).isEqualTo(new Rectangle());
                assertThat(strip.animationTimer.isRunning()).isFalse();
            }
        });
    }

    @Test void consecutiveClosesFinishIndependentlyAndEmptyOwnerKeepsNoMotion() throws Exception {
        edt(() -> {
            try (var fixture = new Fixture()) {
                var owner = fixture.owner; var strip = owner.windowTabs();
                var first = owner.currentTab(); first.rename("first");
                owner.newTab(HOME); var second = owner.currentTab(); second.rename("second");
                owner.newTab(HOME); var third = owner.currentTab(); third.rename("third"); owner.update();
                fixture.installHeader();
                Container one = entry(strip, first), two = entry(strip, second);
                owner.closeTab(first); fixture.layoutHeader(); fixture.frame(60);
                owner.closeTab(second); fixture.layoutHeader();
                assertThat(owner.currentTab()).isSameAs(third);
                assertThat(owner.tabStrip().getTabCount()).isEqualTo(1);
                fixture.frame(180);
                assertThat(one.getParent()).isNull();
                assertThat(two.getParent()).isSameAs(strip);
                assertThat(two.getWidth()).isPositive();
                fixture.frame(240);
                assertThat(two.getParent()).isNull();
                assertThat(entry(strip, third).getX()).isZero();
                assertThat(underline(strip)).isEqualTo(new Rectangle(14, 37, 132, 1));
                assertThat(strip.animationTimer.isRunning()).isFalse();
                owner.closeTab(third); fixture.layoutHeader();
                assertThat(owner.tabStrip().getTabCount()).isZero();
                assertThat(underline(strip)).isEqualTo(new Rectangle());
                assertThat(strip.animationTimer.isRunning()).isFalse();
            }
        });
    }

    private static final class Fixture implements AutoCloseable {
        final AtomicLong time = new AtomicLong();
        final WindowContent owner = new WindowContent(launcher(new ArrayDeque<>()), HOME,
            path -> {}, () -> {}, () -> {}, new ThemeController(), KeyBindings.defaults(true), time::get);
        JPanel host;
        MacTitleBar header;
        void installHeader() {
            var root = new JRootPane();
            header = MacTitleBar.install(root, owner, true, title -> {});
            host = new JPanel(new BorderLayout()); host.add(root);
            host.addNotify(); layout(host, 958, 958);
        }
        void layoutHeader() { layoutTree(host); }
        void frame(long milliseconds) {
            time.set(milliseconds * 1_000_000);
            Timer timer = owner.windowTabs().animationTimer;
            for (var listener : timer.getActionListeners())
                listener.actionPerformed(new ActionEvent(timer, ActionEvent.ACTION_PERFORMED, "frame"));
        }
        @Override public void close() {
            if (header != null) header.close();
            owner.close();
            if (host != null) host.removeNotify();
            else if (owner.isDisplayable()) owner.removeNotify();
        }
    }

    static Container entry(WindowTabs strip, TerminalTab tab) {
        return named(strip, "select:" + tab.title()).getParent();
    }

    static Rectangle underline(WindowTabs strip) {
        BufferedImage image = new BufferedImage(strip.getWidth(), strip.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try { strip.printAll(graphics); } finally { graphics.dispose(); }
        int color = UIManager.getColor("Jasper.tabUnderline").getRGB();
        int left = -1, right = -1, y = strip.getHeight() - 1;
        for (int x = 0; x < image.getWidth(); x++) if (image.getRGB(x, y) == color) {
            if (left < 0) left = x;
            right = x;
        }
        return left < 0 ? new Rectangle() : new Rectangle(left, y, right - left + 1, 1);
    }
}
