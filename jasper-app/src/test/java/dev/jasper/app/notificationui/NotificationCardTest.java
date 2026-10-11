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
        var more = KitTestSupport.find(card.moreView(), NotificationCard.MORE, JComponent.class);
        if (more == null) return List.of();
        return Arrays.stream(more.getComponentPopupMenu().getComponents()).filter(AbstractButton.class::isInstance)
            .map(AbstractButton.class::cast).toList();
    }

    @Test void repeatCountKeepsItsCardFloorThroughShippedThemeChanges() {
        var initial = ThemeTestSupport.pair(true);
        try (var binding = new dev.jasper.ui.theme.ThemeBinding(initial, failure -> { throw new AssertionError(failure); });
             var icons = new dev.jasper.ui.icons.IconRegistry()) {
            var kit = new KitContext(binding, ThemeRoute.CHROME, DesignMetrics.DEFAULT, icons, () -> false);
            var notification = posted(titled("Repeated"));
            notification = posted(titled("Repeated"));
            try (var card = card(kit, notification, false)) {
                var count = Arrays.stream(card.getComponents()).filter(TextBlock.class::isInstance).map(TextBlock.class::cast)
                    .filter(text -> text.text().equals("×2")).findFirst().orElseThrow();
                for (String name : List.of("Clear Light", "Jasper Light", "Nord", "Clear Dark", "Jasper Dark")) {
                    var theme = dev.jasper.ui.theme.ThemeGenerator.generate(ThemeTestSupport.named(name).source().document());
                    var raw = theme.semantic().color(SemanticColor.TEXT_SECONDARY);
                    binding.publish(new dev.jasper.ui.theme.ThemePair(theme, theme));
                    assertThat(dev.jasper.ui.theme.ColorMath.contrast(count.getForeground(), theme.semantic().color(SemanticColor.SURFACE_CARD)))
                        .as(name + " count/card").isGreaterThanOrEqualTo(4.5);
                    assertThat(theme.semantic().color(SemanticColor.TEXT_SECONDARY)).isEqualTo(raw);
                    count.setSize(count.getPreferredSize());
                    var image = new BufferedImage(count.getWidth(), count.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    var graphics = image.createGraphics(); try { count.paint(graphics); } finally { graphics.dispose(); }
                    boolean painted = false;
                    for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                        painted |= image.getRGB(x, y) == count.getForeground().getRGB();
                    assertThat(painted).as(name + " actual count text").isTrue();
                }
            }
        }
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
        link(fresh, 0).doClick(0);
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
        assertThat(card.linkViews()).allSatisfy(view -> assertThat(view.pointerOnly()).isTrue());
        assertThat(card.moreView().pointerOnly() && card.closeView().pointerOnly() && card.iconView().pointerOnly()).isTrue();
        assertThat(link(card, 0).getText()).isEqualTo("One");
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

    /**
     * The link row: each link fits its fractionally painted label (no padding), as tall as the text line plus the former
     * 2 px above and below, 12 px before every link but the first, starting at the text column. Close keeps its 16 px
     * glyph in the 16 px slot at the top right.
     */
    @Test void linksAndCloseKeepTheFormerGeometry() {
        var card = card(posted(withActions(titled("Geometry"), List.of("Open", "Show in Finder", "Copy", "Four"))), true);
        layOut(card);
        assertThat(DesignMetrics.DEFAULT.value(DesignMetrics.Role.NOTIFICATION_LINK_GAP)).isEqualTo(12);
        assertThat(DesignMetrics.DEFAULT.value(DesignMetrics.Role.NOTIFICATION_LINK_VERTICAL_PADDING)).isEqualTo(2);
        int textX = UIScale.scale(12 + 16 + 8), gap = UIScale.scale(12), padding = UIScale.scale(2);
        var views = new ArrayList<JComponent>();
        for (var view : card.linkViews()) if (view.isVisible()) views.add(view);
        if (card.moreView().isVisible()) views.add(card.moreView());
        assertThat(views).hasSizeGreaterThanOrEqualTo(2);
        int x = textX;
        for (int i = 0; i < views.size(); i++) {
            var view = views.get(i);
            var button = firstButton(view);
            var metrics = button.getFontMetrics(button.getFont());
            if (i > 0) x += gap;
            assertThat(view.getX()).as("link %d starts after the former gap", i).isEqualTo(x);
            var image = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            try {
                graphics.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                graphics.setRenderingHint(java.awt.RenderingHints.KEY_FRACTIONALMETRICS, java.awt.RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                graphics.setFont(button.getFont());
                var insets = button.getInsets();
                String painted = javax.swing.SwingUtilities.layoutCompoundLabel(button, graphics.getFontMetrics(), button.getText(), button.getIcon(),
                    javax.swing.SwingConstants.CENTER, button.getHorizontalAlignment(), javax.swing.SwingConstants.CENTER, javax.swing.SwingConstants.RIGHT,
                    new java.awt.Rectangle(insets.left, insets.top, button.getWidth()-insets.left-insets.right, button.getHeight()-insets.top-insets.bottom),
                    new java.awt.Rectangle(), new java.awt.Rectangle(), button.getIconTextGap());
                assertThat(painted).as("link %d paints its complete actionable label", i).isEqualTo(button.getText());
                assertThat(view.getWidth()).as("link %d has no extra layout padding", i).isEqualTo(button.getWidth());
                assertThat(view.getWidth()).isGreaterThanOrEqualTo(graphics.getFontMetrics().stringWidth(button.getText()));
            } finally { graphics.dispose(); }
            assertThat(view.getHeight()).as("link %d is a text line plus the padding", i).isEqualTo(metrics.getHeight() + 2 * padding);
            assertThat(button.getInsets().left + button.getInsets().right).isZero();
            x += view.getWidth();
        }
        var close = card.closeView();
        assertThat(close.getBounds()).isEqualTo(new java.awt.Rectangle(UIScale.scale(360 - 12 - 16), UIScale.scale(12), UIScale.scale(16), UIScale.scale(16)));
        var closeButton = find(close, NotificationCard.CLOSE, AbstractButton.class);
        assertThat(closeButton.getIcon().getIconWidth()).as("the whole 16 px glyph").isEqualTo(UIScale.scale(16));
        assertThat(closeButton.getInsets()).isEqualTo(new java.awt.Insets(0, 0, 0, 0));
        assertThat(closeButton.getSize()).isEqualTo(new java.awt.Dimension(UIScale.scale(16), UIScale.scale(16)));
        card.close();
    }

    /** Links that a theme or font change shows anew still count the pointer over them as hovering the card. */
    @Test void linksShownAfterARestyleKeepTheCardHovered() throws Exception {
        var bindings = KitTestSupport.bindings(true);
        var defaults=new java.util.HashMap<Object,Object>();javax.swing.UIManager.getDefaults().forEach(defaults::put);
        NotificationCard shown=null;
        float priorScale=UIScale.getUserScaleFactor();
        var scaleOverride=UIScale.class.getDeclaredMethod("setUserScaleFactor",float.class,boolean.class);
        scaleOverride.setAccessible(true);
        try {
            // Keep geometry at 13pt while the explicit label override changes the amount of text that fits.
            javax.swing.UIManager.put("defaultFont", new java.awt.Font(java.awt.Font.DIALOG,java.awt.Font.PLAIN,13));
            javax.swing.UIManager.put("Label.font", new java.awt.Font(java.awt.Font.DIALOG,java.awt.Font.PLAIN,40));
            // FlatLaf derives geometry from Label.font in an isolated JVM. Pin it for this
            // font-only fitting transition, independently of the test suite's initialization order.
            scaleOverride.invoke(null,1f,false);
            var card = card(bindings.context(ThemeRoute.CHROME), posted(withActions(titled("Restyle"), List.of("One", "Two", "Three", "Four"))), false);shown=card;
            int before = card.actionLabels().size();
            javax.swing.UIManager.put("Label.font",new java.awt.Font(java.awt.Font.DIALOG,java.awt.Font.PLAIN,13));
            bindings.binding().publish(ThemeTestSupport.pair(false));
            assertThat(card.actionLabels()).as("more links fit after the change").hasSizeGreaterThan(before);
            layOut(card);
            for (int i = 0; i < card.actionLabels().size() - 1; i++) {
                var button = link(card, i);
                for (var listener : button.getMouseListeners())
                    listener.mouseEntered(new MouseEvent(button, MouseEvent.MOUSE_ENTERED, 0, 0, 1, 1, 0, false));
                assertThat(card.hovered()).as("hover from link %d", i).isTrue();card.setHovered(false);
            }
        } finally {
            if(shown!=null)shown.close();
            javax.swing.UIManager.put("Label.font",defaults.get("Label.font"));
            javax.swing.UIManager.put("defaultFont",defaults.get("defaultFont"));scaleOverride.invoke(null,priorScale,false);bindings.close();
        }
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

    static AbstractButton firstButton(Component root) {
        if (root instanceof AbstractButton button) return button;
        if (root instanceof Container container) for (var child : container.getComponents()) {
            var found = firstButton(child);
            if (found != null) return found;
        }
        return null;
    }

    /** The mounted link button at {@code index}. */
    static AbstractButton link(NotificationCard card, int index) {
        return find(card.linkViews().get(index), NotificationCard.ACTION + index, AbstractButton.class);
    }

    /** Every visible link and More: on one row, at their natural widths, inside the card's text column. */
    private static void assertSingleRowInsideThePanel(NotificationCard card) {
        var row = new ArrayList<JComponent>();
        for (var view : card.linkViews()) if (view.isVisible()) row.add(view);
        if (card.moreView().isVisible()) row.add(card.moreView());
        assertThat(row).isNotEmpty();
        int right = card.getWidth() - UIScale.scale(12);
        for (var part : row) {
            assertThat(part.getY()).as("one row").isEqualTo(row.getFirst().getY());
            assertThat(part.getX() + part.getWidth()).as("fits the text column").isLessThanOrEqualTo(right);
            assertThat(part.getWidth()).as("natural width").isEqualTo(part.getPreferredSize().width);
        }
    }

    @Test void actionLabelsNeverRenderMarkup() {
        var labels = List.of("<html>One", "<html>Two", "<html>Three", "<html>Four", "<html>Five");
        var card = card(posted(withActions(titled("Markup"), labels)), false);
        assertThat(card.actionLabels()).containsExactly("<html>One", "<html>Two", "<html>Three", NotificationCard.MORE_LABEL);
        for (int i = 0; i < 3; i++) {
            var link = link(card, i);
            assertThat(link.getText()).isEqualTo(labels.get(i));
            assertThat(link.getClientProperty("html")).as(link.getText()).isNull();
        }
        assertThat(moreItems(card)).extracting(AbstractButton::getText).containsExactly("<html>Four", "<html>Five");
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
