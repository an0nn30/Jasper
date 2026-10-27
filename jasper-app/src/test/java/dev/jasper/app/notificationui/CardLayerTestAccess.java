package dev.jasper.app.notificationui;

import dev.jasper.app.notifications.NotificationCenter;
import java.time.Duration;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Test bridge to {@link CardLayer}'s controlled-schedule constructor, kept out of the production jar. */
public final class CardLayerTestAccess {
    private CardLayerTestAccess() {}

    /** Clicks the body of the entry's card in the layer, as the pointer does. */
    public static void clickBody(CardLayer layer, UUID id) {
        var card = layer.card(id);
        card.dispatchEvent(new java.awt.event.MouseEvent(card, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 5, 5, 1, false,
            java.awt.event.MouseEvent.BUTTON1));
    }

    public static CardLayer layer(dev.jasper.ui.swing.KitContext kit, NotificationCenter center, boolean focused, Runnable openHistory,
                                  Consumer<UUID> raise, Consumer<UUID> timedOut, BiFunction<Duration, Runnable, Runnable> schedule, LongSupplier nanoTime) {
        return new CardLayer(kit, center, focused, openHistory, raise, timedOut, schedule, nanoTime);
    }
}
