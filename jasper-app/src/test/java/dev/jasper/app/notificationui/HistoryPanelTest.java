package dev.jasper.app.notificationui;

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

    private HistoryPanel panel(boolean focused) { return panel(dev.jasper.app.testsupport.KitTestSupport.context(), focused); }

    private HistoryPanel panel(dev.jasper.ui.swing.KitContext kit, boolean focused) {
        center.register(GENERAL);
        site = new PanelSite(UUID.randomUUID(), () -> {}, () -> {}, () -> visible[0]);
        return new HistoryPanel(kit, center, site, focused);
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

    /** Review M9: "No notifications" is centred across the panel, as before. */
    @Test void theEmptyTextIsCentred() {
        var panel = panel(true);
        show();
        panel.setSize(400, 300);
        for (int pass = 0; pass < 3; pass++) LayoutTestSupport.layoutTree(panel);
        var empty = panel.emptyView();
        var bounds = SwingUtilities.convertRectangle(empty.getParent(), empty.getBounds(), panel);
        assertThat(bounds.width).as("as wide as its text").isLessThan(panel.getWidth() / 2);
        assertThat(Math.abs(bounds.x + bounds.width / 2 - panel.getWidth() / 2)).as("centred").isLessThanOrEqualTo(1);
    }

    /** The title wraps to the row (as the former wrapping title did), strong while unread, with its count beside it. */
    @Test void aLongTitleWrapsWithItsCountBesideIt() {
        String title = "The background job that refreshes every remote listing finished with warnings on several hosts";
        UUID id = post(title);
        post(title);
        var panel = panel(false);
        show();
        panel.setSize(300, 800);
        for (int pass = 0; pass < 3; pass++) LayoutTestSupport.layoutTree(panel);
        var row = panel.row(id);
        assertThat(row.titleBold()).isTrue();
        assertThat(row.titleLines()).as("wrapped").hasSizeGreaterThan(1);
        assertThat(String.join(" ", row.titleLines())).isEqualTo(title);
        assertThat(row.countText()).isEqualTo("×2");
        var block = row.titleBlock();
        assertThat(block.getWidth() + block.getX()).as("inside the row").isLessThanOrEqualTo(row.getWidth());
        assertThat(block.getHeight()).isEqualTo(row.titleLines().size() * block.lineHeight());
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

    /** A theme switch restyles the rows from module roles and keeps them (and their state) rather than rebuilding them. */
    @Test void theRowsRestyleAndStayWhenTheThemeChanges() {
        var bindings = dev.jasper.app.testsupport.KitTestSupport.bindings(true);
        try {
            var kit = bindings.context(dev.jasper.ui.theme.ThemeRoute.CHROME);
            UUID id = post("Theme");
            var panel = panel(kit, false);
            show();
            HistoryRow before = panel.row(id);
            Color dark = before.titleColor();
            assertThat(dark).isEqualTo(kit.theme().semantic().color(dev.jasper.ui.theme.SemanticColor.TEXT_PRIMARY));
            assertThat(before.titleBold()).isTrue();
            bindings.binding().publish(dev.jasper.app.appearance.ThemeTestSupport.pair(false));
            HistoryRow after = panel.row(id);
            assertThat(after).isSameAs(before);
            assertThat(after.titleColor()).isEqualTo(kit.theme().semantic().color(dev.jasper.ui.theme.SemanticColor.TEXT_PRIMARY));
            assertThat(after.titleColor().getRGB()).isNotEqualTo(dark.getRGB());
            assertThat(after.titleBold()).as("still unread").isTrue();
            assertThat(panel.newSection()).containsExactly(id);
        } finally {
            // Close the panel's kit views before their bindings: a live view would refresh on a later font change.
            if (site != null) site.notifyClosed();
            bindings.close();
        }
    }

    /** Ruling G5: the history's action links and remove stay pointer-only, as before; Clear All stays focusable. */
    @Test void rowControlsArePointerOnlyAndClearAllTakesFocus() {
        center.register(GENERAL);
        UUID id = center.post(NotificationRequest.of(GENERAL.id(), Severity.WARNING, "act")
            .withAction(NotificationAction.of("Fix", () -> {}))).orElseThrow();
        var panel = panel(false);
        show();
        assertThat(panel.row(id).actionButtons().getFirst().isFocusable()).isFalse();
        assertThat(panel.row(id).removeButton().isFocusable()).isFalse();
        assertThat(panel.row(id).removeButton().getAccessibleContext().getAccessibleName()).isEqualTo("Remove notification");
        assertThat(panel.clearAllButton().isFocusable()).isTrue();
        assertThat(panel.row(id).getAccessibleContext().getAccessibleName()).isEqualTo("Warning: act");
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

    @Test void theToolWindowHeaderCarriesTheTitle() {
        var panel = panel(true);
        show();
        assertThat(texts(panel)).as("the dock's header shows Notifications").doesNotContain("Notifications");
        assertThat(SwingUtilities.isDescendingFrom(panel.clearAllButton(), panel)).as("Clear All stays").isTrue();
    }

    private static java.util.List<String> texts(Container root) {
        var found = new ArrayList<String>();
        for (Component child : root.getComponents()) {
            if (child instanceof javax.swing.JLabel label) found.add(label.getText());
            if (child instanceof Container nested) found.addAll(texts(nested));
        }
        return found;
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
