package dev.jasper.app.notificationui;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationGroup;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.ThemeRoute;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class NotificationCardTest {
    private static final NotificationGroup CARDS = new NotificationGroup("test.cards", "Cards", "jasper", DisplayMode.CARD);
    private final NotificationCenter center = new NotificationCenter(Runnable::run,
        Clock.fixed(Instant.parse("2026-10-02T14:05:00Z"), ZoneOffset.UTC), () -> Delivery.Presence.FOCUSED);

    private Notification posted(NotificationRequest request) {
        center.register(CARDS);
        return center.find(center.post(request).orElseThrow()).orElseThrow();
    }

    private static NotificationRequest titled(String title) { return NotificationRequest.of(CARDS.id(), Severity.INFO, title); }

    private NotificationCard card(Notification notification, boolean sticky) { return card(KitTestSupport.context(), notification, sticky); }

    private NotificationCard card(KitContext kit, Notification notification, boolean sticky) {
        return new NotificationCard(kit, center, notification, sticky);
    }

    private static void click(JComponent card) {
        card.dispatchEvent(new MouseEvent(card, MouseEvent.MOUSE_CLICKED, 0, 0, 5, 5, 1, false, MouseEvent.BUTTON1));
    }

    static <T extends Component> T find(Component root, String name, Class<T> type) { return KitTestSupport.require(root, name, type); }

    /** The More menu's mounted items: the kit builds them headless without showing the popup. */
    static List<AbstractButton> moreItems(NotificationCard card) {
        var more = KitTestSupport.find(card.actionsView(), NotificationCard.MORE, JComponent.class);
        if (more == null) return List.of();
        return Arrays.stream(more.getComponentPopupMenu().getComponents()).filter(AbstractButton.class::isInstance)
            .map(AbstractButton.class::cast).toList();
    }

    @Test void titleContentAndCountArePlainWrappedText() {
        var card = card(posted(titled("<html><b>Disk</b> full").withContent("word ".repeat(300))), false);
        assertThat(DesignMetrics.DEFAULT.value(DesignMetrics.Role.NOTIFICATION_WIDTH)).as("the former width, named").isEqualTo(360);
        assertThat(card.getPreferredSize().width).isEqualTo(UIScale.scale(360));
        assertThat(String.join(" ", card.titleLines())).isEqualTo("<html><b>Disk</b> full");
        assertThat(card.contentLines()).hasSize(NotificationCard.CONTENT_LINES);
        assertThat(card.contentLines().getLast()).endsWith("…");
        assertThat(card.countText()).isEmpty();
        card.update(posted(titled("<html><b>Disk</b> full").withContent("word ".repeat(300))));
        assertThat(card.countText()).as("a repeat refreshes the same card").isEqualTo("×2");
        card.close();
    }

    @Test void theSeverityGlyphKeepsItsColour() {
        center.register(CARDS);
        for (var severity : Severity.values()) {
            var card = card(posted(NotificationRequest.of(CARDS.id(), severity, severity.name())), false);
            var icon = (Nodes.Icon) card.iconView().spec().root();
            assertThat(icon.name()).isEqualTo(NotificationStyle.glyph(severity));
            assertThat(icon.severity()).contains(NotificationStyle.tone(severity));
            card.close();
        }
        assertThat(NotificationStyle.tone(Severity.ERROR)).isEqualTo(Nodes.Severity.DANGER);
    }

    @Test void threeActionsShowAndTheRestWaitUnderMoreAndEachRunsOnce() {
        var ran = new ArrayList<String>();
        var request = titled("Actions");
        for (String label : List.of("One", "Two", "Three", "Four", "Five"))
            request = request.withAction(NotificationAction.of(label, () -> ran.add(label)));
        var card = card(posted(request), false);
        assertThat(card.actionLabels()).containsExactly("One", "Two", "Three", NotificationCard.MORE_LABEL);
        assertThat(moreItems(card)).extracting(AbstractButton::getText).containsExactly("Four", "Five");
        moreItems(card).get(1).doClick(0);
        assertThat(ran).containsExactly("Five");
        assertThat(center.find(card.notification().id()).orElseThrow().expired()).isTrue();
        card.close();
        var fresh = card(posted(titled("Once").withAction(NotificationAction.of("Go", () -> ran.add("go")))), false);
        find(fresh.actionsView(), NotificationCard.ACTION + 0, AbstractButton.class).doClick(0);
        assertThat(ran).as("a link runs its action exactly once").containsExactly("Five", "go");
        fresh.close();
    }

    @Test void clickingTheBodyReadsItAndRunsADefaultActionWhenThereIsOne() {
        var ran = new ArrayList<String>();
        var withDefault = card(posted(titled("Default").withActivate(() -> ran.add("activated"))), false);
        click(withDefault);
        assertThat(ran).containsExactly("activated");
        assertThat(center.find(withDefault.notification().id()).orElseThrow().read()).isTrue();
        var plain = card(posted(titled("Plain")), false);
        click(plain);
        assertThat(center.find(plain.notification().id()).orElseThrow().read()).as("spec section 2: clicking the body marks it read").isTrue();
        assertThat(ran).containsExactly("activated");
        withDefault.close(); plain.close();
    }

    @Test void closeShowsOnHoverForAutoHidingCardsAndAlwaysForStickyOnes() {
        var hovers = new ArrayList<Boolean>();
        var card = card(posted(titled("Hover")), false);
        card.onHover = hovers::add;
        assertThat(card.closeShown()).isFalse();
        card.setHovered(true);
        assertThat(card.closeShown()).isTrue();
        assertThat(find(card.closeView(), NotificationCard.CLOSE, AbstractButton.class).isVisible()).isTrue();
        card.setHovered(false);
        assertThat(card.closeShown()).isFalse();
        assertThat(hovers).containsExactly(true, false);
        var sticky = card(posted(titled("Sticky")), true);
        assertThat(sticky.closeShown()).isTrue();
        var close = find(sticky.closeView(), NotificationCard.CLOSE, AbstractButton.class);
        assertThat(close.getAccessibleContext().getAccessibleName()).isEqualTo("Close notification");
        close.doClick(0);
        assertThat(center.find(sticky.notification().id()).orElseThrow().read()).isTrue();
        card.close(); sticky.close();
    }

    /** Ruling G5: nothing on a card takes the keyboard from the terminal; its controls stay named and operable. */
    @Test void nothingOnACardIsFocusableButItsActionsAreNamed() {
        var request = titled("Focus");
        for (String label : List.of("One", "Two", "Three", "Four")) request = request.withAction(NotificationAction.of(label, () -> {}));
        var card = card(posted(request), true);
        assertThat(focusable(card)).isEmpty();
        assertThat(card.actionsView().pointerOnly() && card.closeView().pointerOnly() && card.iconView().pointerOnly()).isTrue();
        assertThat(find(card.actionsView(), NotificationCard.ACTION + 0, AbstractButton.class).getText()).isEqualTo("One");
        assertThat(card.getAccessibleContext().getAccessibleName()).isEqualTo("Information: Focus");
        card.close();
    }

    static List<Component> focusable(Component component) {
        var found = new ArrayList<Component>();
        if (component.isFocusable()) found.add(component);
        if (component instanceof Container container) for (var child : container.getComponents()) found.addAll(focusable(child));
        return found;
    }

    /** An open card follows a theme switch from module roles and keeps its hover, text and actions. */
    @Test void theCardRestylesFromModuleRolesWhenTheThemeChangesAndKeepsItsState() {
        var bindings = KitTestSupport.bindings(true);
        try {
            var kit = bindings.context(ThemeRoute.CHROME);
            var request = titled("Theme").withContent("word ".repeat(300));
            for (String label : List.of("One", "Two", "Three", "Four")) request = request.withAction(NotificationAction.of(label, () -> {}));
            var card = card(kit, posted(request), false);
            card.setHovered(true);
            int lines = card.contentLines().size();
            assertThat(card.titleColor()).isEqualTo(kit.theme().semantic().color(SemanticColor.TEXT_PRIMARY));
            bindings.binding().publish(ThemeTestSupport.pair(false));
            var light = kit.theme().semantic();
            assertThat(card.titleColor()).isEqualTo(light.color(SemanticColor.TEXT_PRIMARY));
            assertThat(card.closeShown()).as("hover survives the switch").isTrue();
            assertThat(card.contentLines()).hasSize(lines);
            assertThat(card.actionLabels()).containsExactly("One", "Two", "Three", NotificationCard.MORE_LABEL);
            card.setSize(card.getPreferredSize());
            var image = new BufferedImage(card.getWidth(), card.getHeight(), BufferedImage.TYPE_INT_ARGB);
            var g = image.createGraphics();
            try { card.paint(g); } finally { g.dispose(); }
            assertThat(new Color(image.getRGB(card.getWidth() / 2, card.getHeight() - UIScale.scale(4)), true))
                .isEqualTo(new Color(light.color(SemanticColor.SURFACE_CARD).getRGB()));
            card.close();
        } finally { bindings.close(); }
    }

    private static NotificationRequest withActions(NotificationRequest request, List<String> labels) {
        for (String label : labels) request = request.withAction(NotificationAction.of(label, () -> {}));
        return request;
    }

    /** What a user can reach: the visible links in order, then the More menu's items. */
    private static List<String> reachableLabels(NotificationCard card) {
        var labels = new ArrayList<String>();
        for (String label : card.actionLabels()) if (!label.equals(NotificationCard.MORE_LABEL)) labels.add(label);
        labels.addAll(moreItems(card).stream().map(AbstractButton::getText).toList());
        return labels;
    }

    private static void layOut(NotificationCard card) {
        card.setSize(card.getPreferredSize());
        KitTestSupport.layout(card);
    }

    /** Every link and More sit on the one row, at their natural widths, inside the action column. */
    private static void assertSingleRowInsideThePanel(NotificationCard card) {
        var parts = ((Container) card.actionsView().getComponent(0)).getComponents();
        assertThat(parts).isNotEmpty();
        for (var part : parts) {
            if (!part.isVisible()) continue;
            assertThat(part.getY()).as(part.getName() + " is on the first and only row").isZero();
            assertThat(part.getX() + part.getWidth()).as(part.getName() + " fits the panel").isLessThanOrEqualTo(card.actionsView().getWidth());
            assertThat(part.getWidth()).as(part.getName() + " keeps its natural width").isEqualTo(part.getPreferredSize().width);
        }
    }

    @Test void actionLabelsNeverRenderMarkup() {
        var labels = List.of("<html>One", "<html>Two", "<html>Three", "<html>Four", "<html>Five");
        var card = card(posted(withActions(titled("Markup"), labels)), false);
        // Kit links carry the kit's button padding, so fewer of these wide labels fit beside More than the former
        // unpadded links did (a visual-only change until Plan 3); every one stays reachable, in order, never as markup.
        var shown = card.actionLabels();
        assertThat(shown.getLast()).isEqualTo(NotificationCard.MORE_LABEL);
        assertThat(shown.size() - 1).isBetween(1, NotificationCard.VISIBLE_ACTIONS);
        assertThat(shown.subList(0, shown.size() - 1)).isEqualTo(labels.subList(0, shown.size() - 1));
        for (int i = 0; i < shown.size() - 1; i++) {
            var link = find(card.actionsView(), NotificationCard.ACTION + i, AbstractButton.class);
            assertThat(link.getText()).isEqualTo(labels.get(i));
            assertThat(link.getClientProperty("html")).as(link.getText()).isNull();
        }
        assertThat(reachableLabels(card)).containsExactlyElementsOf(labels);
        assertThat(moreItems(card)).allSatisfy(item -> assertThat(item.getClientProperty("html")).as(item.getText()).isNull());
        card.close();
    }

    @Test void wideActionLabelsLeaveMoreInsideTheRowAndEveryActionReachable() {
        var labels = List.of("Open in Editor window", "Show in Finder window", "Copy path to clipboard", "Four", "Five", "Six", "Seven");
        var card = card(posted(withActions(titled("Wide"), labels)), false);
        layOut(card);
        assertSingleRowInsideThePanel(card);
        assertThat(card.actionLabels().getLast()).isEqualTo(NotificationCard.MORE_LABEL);
        assertThat(reachableLabels(card)).containsExactlyElementsOf(labels);
        card.close();
    }

    @Test void threeLongLabelsStillGetAMoreLink() {
        var labels = List.of("a".repeat(40), "b".repeat(40), "c".repeat(40));
        var card = card(posted(withActions(titled("Long"), labels)), false);
        layOut(card);
        assertSingleRowInsideThePanel(card);
        assertThat(card.actionLabels().getLast()).isEqualTo(NotificationCard.MORE_LABEL);
        assertThat(reachableLabels(card)).containsExactlyElementsOf(labels);
        card.close();
    }
}
