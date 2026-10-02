package dev.jasper.app.notificationui;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationGroup;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
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

    private static void click(JComponent card) {
        card.dispatchEvent(new MouseEvent(card, MouseEvent.MOUSE_CLICKED, 0, 0, 5, 5, 1, false, MouseEvent.BUTTON1));
    }

    @Test void titleContentAndCountArePlainWrappedText() {
        var card = new NotificationCard(center, posted(titled("<html><b>Disk</b> full").withContent("word ".repeat(300))), false);
        assertThat(card.getPreferredSize().width).isEqualTo(UIScale.scale(NotificationCard.WIDTH));
        assertThat(String.join(" ", card.titleLines())).isEqualTo("<html><b>Disk</b> full");
        assertThat(card.contentLines()).hasSize(NotificationCard.CONTENT_LINES);
        assertThat(card.contentLines().getLast()).endsWith("…");
        assertThat(card.countText()).isEmpty();
        card.update(posted(titled("<html><b>Disk</b> full").withContent("word ".repeat(300))));
        assertThat(card.countText()).isEqualTo("×2");
    }

    @Test void threeActionsShowAndTheRestWaitUnderMore() {
        var ran = new ArrayList<String>();
        var request = titled("Actions");
        for (String label : List.of("One", "Two", "Three", "Four", "Five"))
            request = request.withAction(NotificationAction.of(label, () -> ran.add(label)));
        var card = new NotificationCard(center, posted(request), false);
        assertThat(card.actionButtons()).extracting(JButton::getText).containsExactly("One", "Two", "Three", "More ▾");
        assertThat(card.actionButtons()).allSatisfy(button -> assertThat(button.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE));
        assertThat(Arrays.stream(card.moreMenu().getComponents()).map(item -> ((JMenuItem) item).getText()))
            .containsExactly("Four", "Five");
        ((JMenuItem) card.moreMenu().getComponent(1)).doClick();
        assertThat(ran).containsExactly("Five");
        assertThat(center.find(card.notification().id()).orElseThrow().expired()).isTrue();
    }

    @Test void clickingTheBodyReadsItAndRunsADefaultActionWhenThereIsOne() {
        var ran = new ArrayList<String>();
        var withDefault = new NotificationCard(center, posted(titled("Default").withActivate(() -> ran.add("activated"))), false);
        click(withDefault);
        assertThat(ran).containsExactly("activated");
        assertThat(center.find(withDefault.notification().id()).orElseThrow().read()).isTrue();
        var plain = new NotificationCard(center, posted(titled("Plain")), false);
        click(plain);
        assertThat(center.find(plain.notification().id()).orElseThrow().read()).as("spec section 2: clicking the body marks it read").isTrue();
        assertThat(ran).containsExactly("activated");
    }

    @Test void closeShowsOnHoverForAutoHidingCardsAndAlwaysForStickyOnes() {
        var hovers = new ArrayList<Boolean>();
        var card = new NotificationCard(center, posted(titled("Hover")), false);
        card.onHover = hovers::add;
        assertThat(card.closeButton().isVisible()).isFalse();
        card.setHovered(true);
        assertThat(card.closeButton().isVisible()).isTrue();
        card.setHovered(false);
        assertThat(card.closeButton().isVisible()).isFalse();
        assertThat(hovers).containsExactly(true, false);
        var sticky = new NotificationCard(center, posted(titled("Sticky")), true);
        assertThat(sticky.closeButton().isVisible()).isTrue();
        sticky.closeButton().doClick();
        assertThat(center.find(sticky.notification().id()).orElseThrow().read()).isTrue();
    }

    @Test void theCardRestylesWhenTheThemeChanges() {
        try {
            new ThemeController(Theme.DARK);
            Color dark = UIManager.getColor("Jasper.notification.background");
            var card = new NotificationCard(center, posted(titled("Theme")), false);
            new ThemeController(Theme.INTELLIJ_LIGHT);
            SwingUtilities.updateComponentTreeUI(card);
            Color light = UIManager.getColor("Jasper.notification.background");
            assertThat(light).isNotEqualTo(dark);
            assertThat(card.titleColor()).isEqualTo(new Color(UIManager.getColor("Jasper.notification.foreground").getRGB(), true));
            card.setSize(card.getPreferredSize());
            var image = new BufferedImage(card.getWidth(), card.getHeight(), BufferedImage.TYPE_INT_ARGB);
            var g = image.createGraphics();
            try { card.paint(g); } finally { g.dispose(); }
            assertThat(new Color(image.getRGB(card.getWidth() / 2, card.getHeight() - UIScale.scale(4)), true))
                .isEqualTo(new Color(light.getRGB()));
        } finally { new ThemeController(); }
    }
}
