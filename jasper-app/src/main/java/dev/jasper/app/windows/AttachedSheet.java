package dev.jasper.app.windows;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.WindowBody;
import dev.jasper.ui.swing.KitView;
import java.awt.*;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Scoped attachment/fitting rule. Ordinary DialogFit never participates. EDT only. */
final class AttachedSheet implements AutoCloseable {
    /** Native facts are supplied at use time, in Java user coordinates (never Retina pixels). */
    record Target(BooleanSupplier live, Supplier<Point> rootOrigin, Supplier<Rectangle> usable, Consumer<Rectangle> bounds) { }
    private final AuxiliarySurface surface;
    private final WindowBody owner;
    private final Target target;
    private final BodyScrim scrim;
    private final Runnable unsubscribe;
    private boolean queued, closed, fitting, fallback;
    private int availableHeight = -1;
    private KitView constrainedView;
    AttachedSheet(AuxiliarySurface surface, WindowBody owner, Target target) {
        this.surface = Objects.requireNonNull(surface); this.owner = Objects.requireNonNull(owner); this.target = Objects.requireNonNull(target);
        if (!live() || owner.boundsInRoot().isEmpty()) throw new IllegalArgumentException("Attached sheet needs a live measured owner body");
        scrim = BodyScrim.acquire(owner); unsubscribe = owner.onChanged(this::changed);
    }
    private boolean live() {
        return !closed && !surface.closed() && target.live().getAsBoolean() && WindowBody.find(owner.root()).orElse(null) == owner;
    }
    void changed() {
        if (closed || fitting || queued) return;
        queued = true;
        EventQueue.invokeLater(() -> { queued = false; fitNow(); });
    }
    boolean fallback() { return fallback; }
    void fitNow() {
        if (closed) return;
        if (!live() || owner.boundsInRoot().isEmpty()) { surface.close(); close(); return; }
        fitting = true;
        try {
            Rectangle body = owner.boundsInRoot().orElseThrow(); Point origin = target.rootOrigin().get(); body.translate(origin.x, origin.y);
            Rectangle usable = target.usable().get();
            if (usable.isEmpty()) { surface.close(); return; }
            Rectangle intersection = body.intersection(usable);
            var holder = surface.holder();
            Dimension preferred = holder.getPreferredSize();
            int width = Math.min(preferred.width, Math.max(1, Math.min(body.width, usable.width)));
            int available = Math.max(1, intersection.height);
            measure(width, available);
            Dimension minimum = holder.getMinimumSize();
            fallback = intersection.isEmpty() || available < minimum.height || width < minimum.width;
            if (fallback) {
                width = Math.min(Math.max(width, minimum.width), usable.width);
                available = usable.height;
                measure(width, available);
            }
            preferred = holder.getPreferredSize(); minimum = holder.getMinimumSize();
            int height = Math.min(available, Math.max(preferred.height, minimum.height));
            int x = Math.max(usable.x, Math.min(body.x + (body.width - width) / 2, usable.x + usable.width - width));
            int y = fallback ? Math.max(usable.y, Math.min(body.y, usable.y + usable.height - height)) : Math.max(body.y, usable.y);
            target.bounds().accept(new Rectangle(x, y, width, height));
            layout(holder); scrim.layout();
        } finally { fitting = false; }
    }
    private void measure(int width, int height) {
        int logical = Math.max(1, UIScale.unscale(height));
        for (var child : surface.holder().getComponents()) {
            if (child instanceof KitView view && !view.isClosed() && (logical != availableHeight || view != constrainedView)) {
                availableHeight = logical; constrainedView = view;
                view.setAvailableHeight(logical);
            }
        }
        surface.holder().setSize(width, height);
        layout(surface.holder());
        // A real Form reads its allocated scroll width (including scrollbar allowance) in preferred-size measurement.
        surface.holder().getPreferredSize(); layout(surface.holder());
    }
    private static void layout(Container component) {
        component.doLayout();
        for (var child : component.getComponents()) if (child instanceof Container container) layout(container);
    }
    @Override public void close() {
        if (closed) return; closed = true; unsubscribe.run(); scrim.close();
    }
}
