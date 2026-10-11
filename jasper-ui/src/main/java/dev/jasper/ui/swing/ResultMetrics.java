package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;

/** Shared measured one-line result allocations for lists and their native host's preferred size/scroll policy. */
public final class ResultMetrics {
    private ResultMetrics() { }
    /** Returns the effective Swing pixel height, applying user scaling exactly once. */
    public static int height(KitContext context, boolean heading) {
        return ThemePainting.height(context, heading ? DesignMetrics.Role.RESULTS_HEADING_HEIGHT : DesignMetrics.Role.RESULTS_ROW_HEIGHT,
            heading ? Typography.Role.CAPTION : Typography.Role.BODY);
    }
}
