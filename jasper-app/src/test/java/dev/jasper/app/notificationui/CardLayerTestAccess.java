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

    public static CardLayer layer(dev.jasper.ui.swing.KitContext kit, NotificationCenter center, boolean focused, Runnable openHistory,
                                  Consumer<UUID> timedOut, BiFunction<Duration, Runnable, Runnable> schedule, LongSupplier nanoTime) {
        return new CardLayer(kit, center, focused, openHistory, timedOut, schedule, nanoTime);
    }
}
