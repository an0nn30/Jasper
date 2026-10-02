package dev.jasper.app.notificationui;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.contributions.PanelSite;
import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationGroup;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.UUID;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class HistoryPanelTest {
    private static final NotificationGroup GENERAL = new NotificationGroup("jasper.general", "Jasper", "jasper", DisplayMode.CARD);
    private final NotificationCenter center = new NotificationCenter(Runnable::run,
        Clock.fixed(Instant.parse("2026-10-02T14:05:00Z"), ZoneOffset.UTC), () -> Delivery.Presence.FOCUSED);
    private final boolean[] visible = {false};
    private PanelSite site;

    private HistoryPanel panel(boolean focused) {
        center.register(GENERAL);
        site = new PanelSite(UUID.randomUUID(), () -> {}, () -> {}, () -> visible[0]);
        return new HistoryPanel(center, site, focused);
    }

    private void show() { visible[0] = true; site.notifyVisibility(true); }
    private void hide() { visible[0] = false; site.notifyVisibility(false); }

    private UUID post(String title) {
        center.register(GENERAL);
        return center.post(NotificationRequest.of(GENERAL.id(), Severity.INFO, title)).orElseThrow();
    }

    @Test void anEmptyHistorySaysSo() {
        var panel = panel(true);
        show();
        assertThat(panel.showingEmpty()).isTrue();
        assertThat(panel.clearAllButton().isEnabled()).isFalse();
    }

    @Test void showingInAFocusedWindowReadsEverythingAndKeepsItUnderNewUntilHidden() {
        UUID a = post("a");
        UUID b = post("b");
        var panel = panel(true);
        show();
        assertThat(panel.newSection()).containsExactly(b, a);
        assertThat(panel.earlierSection()).isEmpty();
        assertThat(center.unread()).isZero();
        assertThat(panel.row(a).titleBold()).isFalse();
        hide();
        show();
        assertThat(panel.newSection()).isEmpty();
        assertThat(panel.earlierSection()).containsExactly(b, a);
    }

    @Test void anUnfocusedWindowReadsNothingUntilFocused() {
        UUID a = post("a");
        var panel = panel(false);
        show();
        assertThat(center.unread()).isEqualTo(1);
        assertThat(panel.newSection()).containsExactly(a);
        assertThat(panel.earlierSection()).isEmpty();
        assertThat(panel.row(a).titleBold()).isTrue();
        panel.windowFocusChanged(true);
        assertThat(center.unread()).isZero();
        assertThat(panel.newSection()).containsExactly(a);
        assertThat(panel.earlierSection()).isEmpty();
        assertThat(panel.row(a).titleBold()).isFalse();
        hide();
        show();
        assertThat(panel.newSection()).isEmpty();
        assertThat(panel.earlierSection()).containsExactly(a);
    }

    @Test void unreadEntriesSitUnderNewAndTheRestUnderEarlierNewestFirst() {
        UUID old1 = post("old1");
        UUID old2 = post("old2");
        center.markAllRead();
        UUID unread = post("unread");
        var panel = panel(false);
        show();
        assertThat(panel.sectionHeadings()).containsExactly("New", "Earlier");
        assertThat(panel.newSection()).containsExactly(unread);
        assertThat(panel.earlierSection()).containsExactly(old2, old1);
        UUID later = post("later");
        assertThat(panel.newSection()).containsExactly(later, unread);
        assertThat(panel.earlierSection()).containsExactly(old2, old1);
    }

    @Test void anEntryReadElsewhereStaysUnderNewUntilTheSiteHides() {
        UUID a = post("a");
        var panel = panel(false);
        show();
        center.markRead(a);
        assertThat(panel.newSection()).containsExactly(a);
        UUID b = post("b");
        assertThat(panel.newSection()).containsExactly(b, a);
        assertThat(panel.earlierSection()).isEmpty();
        hide();
        show();
        assertThat(panel.newSection()).containsExactly(b);
        assertThat(panel.earlierSection()).containsExactly(a);
    }

    @Test void aNotificationArrivingWhileLookingIsReadAndNew() {
        var panel = panel(true);
        show();
        UUID c = post("c");
        assertThat(center.find(c).orElseThrow().read()).isTrue();
        assertThat(panel.newSection()).containsExactly(c);
    }

    @Test void aHiddenPanelReadsNothing() {
        panel(true);
        post("unseen");
        assertThat(center.unread()).isEqualTo(1);
    }

    @Test void clearAllAndRowRemoval() {
        UUID a = post("a");
        UUID b = post("b");
        var panel = panel(true);
        show();
        panel.row(a).removeButton().doClick();
        assertThat(center.history()).extracting(entry -> entry.id()).containsExactly(b);
        assertThat(panel.row(a)).isNull();
        panel.clearAllButton().doClick();
        assertThat(center.history()).isEmpty();
        assertThat(panel.showingEmpty()).isTrue();
    }

    @Test void rowsShowGroupTimeCountAndPlainTitles() {
        UUID id = post("<html><i>x</i>");
        var panel = panel(true);
        show();
        assertThat(panel.row(id).titleText()).isEqualTo("<html><i>x</i>");
        assertThat(panel.row(id).metaText()).isEqualTo("Jasper · 14:05");
        assertThat(panel.row(id).countText()).isEmpty();
        post("<html><i>x</i>");
        assertThat(panel.row(id).countText()).isEqualTo("×2");
    }

    @Test void expiredActionsAreDisabled() {
        center.register(GENERAL);
        var ran = new ArrayList<String>();
        UUID id = center.post(NotificationRequest.of(GENERAL.id(), Severity.INFO, "act")
            .withAction(NotificationAction.of("Fix", () -> ran.add("fix")))).orElseThrow();
        var panel = panel(true);
        show();
        assertThat(panel.row(id).actionButtons()).singleElement().satisfies(button -> assertThat(button.isEnabled()).isTrue());
        panel.row(id).actionButtons().getFirst().doClick();
        assertThat(ran).containsExactly("fix");
        assertThat(panel.row(id).actionButtons().getFirst().isEnabled()).isFalse();
    }

    @Test void longContentWrapsToTheRowsWidthInsteadOfOverflowing() {
        center.register(GENERAL);
        UUID id = center.post(NotificationRequest.of(GENERAL.id(), Severity.INFO, "Wrap").withContent("word ".repeat(80))).orElseThrow();
        var panel = panel(true);
        show();
        int narrow = contentHeightAt(panel, id, 300);
        var content = panel.row(id).contentArea();
        int lineHeight = content.getFontMetrics(content.getFont()).getHeight();
        assertThat(narrow).as("content height at 300 px, line height %d", lineHeight).isGreaterThan(3 * lineHeight);
        Rectangle inRow = SwingUtilities.convertRectangle(content.getParent(), content.getBounds(), panel.row(id));
        assertThat(inRow.x + inRow.width).as("content right edge in the row").isLessThanOrEqualTo(panel.row(id).getWidth());
        assertThat(panel.row(id).getWidth()).as("row width").isLessThanOrEqualTo(300);
        assertThat(contentHeightAt(panel, id, 600)).as("wider rows need fewer lines").isLessThan(narrow);
    }

    @Test void theRowsRestyleAndRebuildWhenTheThemeChanges() {
        try {
            new ThemeController(Theme.DARK);
            UUID id = post("Theme");
            var panel = panel(true);
            show();
            HistoryRow before = panel.row(id);
            Color dark = before.titleColor();
            new ThemeController(Theme.INTELLIJ_LIGHT);
            SwingUtilities.updateComponentTreeUI(panel);
            HistoryRow after = panel.row(id);
            assertThat(after).isNotNull().isNotSameAs(before);
            Color light = UIManager.getColor("Label.foreground");
            assertThat(after.titleColor().getRGB()).isEqualTo(light.getRGB());
            assertThat(after.titleColor().getRGB()).isNotEqualTo(dark.getRGB());
        } finally { new ThemeController(); }
    }

    /** Lays the panel out at a width until the wrapped text has settled (it revalidates once its width is known). */
    private static int contentHeightAt(HistoryPanel panel, UUID id, int width) {
        panel.setSize(width, 800);
        for (int pass = 0; pass < 3; pass++) {
            invalidateTree(panel);
            LayoutTestSupport.layoutTree(panel);
        }
        return panel.row(id).contentArea().getHeight();
    }

    private static void invalidateTree(Component component) {
        component.invalidate();
        if (component instanceof Container container) for (Component child : container.getComponents()) invalidateTree(child);
    }

    @Test void aClosedSiteStopsListening() {
        var panel = panel(true);
        show();
        site.notifyClosed();
        UUID late = post("late");
        assertThat(panel.row(late)).isNull();
        assertThat(center.find(late).orElseThrow().read()).isFalse();
    }
}
