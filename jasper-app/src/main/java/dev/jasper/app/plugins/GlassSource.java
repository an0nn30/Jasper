package dev.jasper.app.plugins;

import dev.jasper.app.lifecycle.Subscription;
import java.awt.Component;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * How translucent the window behind a plugin component is, for {@code Appearance.workspaceOpacity}. EDT only.
 *
 * @param opacity the opacity in effect behind a component; 1 when it is in no translucent window
 * @param subscribe registers a callback for any change of it: a window's opacity, or hosted content entering or
 *                  leaving a translucent window
 */
public record GlassSource(ToDoubleFunction<Component> opacity, Function<Runnable, Subscription> subscribe) {
    /** Every component opaque, nothing ever changes. */
    public static final GlassSource NONE = new GlassSource(component -> 1.0, listener -> new Subscription(() -> {}));

    public GlassSource { Objects.requireNonNull(opacity); Objects.requireNonNull(subscribe); }
}
