package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.ChromePainter;
import dev.jasper.ui.swing.NativeStyles;
import dev.jasper.ui.theme.GeneratedTheme;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import javax.swing.Action;
import javax.swing.DefaultButtonModel;
import javax.swing.JToggleButton;

/**
 * What a labelled side stripe and the bottom strip share (spec 10.7): a label's text ("N: " and the title when the
 * window's toggle ends in a digit), its length along its stripe, every stripe's thickness, and the painting of a label
 * in text space: its padding, its text, then a count pill for a badge. EDT only.
 */
final class StripeLabels {
    private final ChromePainter painter;
    private final NativeStyles styles;

    StripeLabels(Kit kit) {
        Objects.requireNonNull(kit, "kit");
        this.painter = new ChromePainter(kit);
        this.styles = new NativeStyles(kit);
    }

    ChromePainter painter() { return painter; }

    NativeStyles styles() { return styles; }

    /** A window's label: "N: " and its title when its toggle ends in a digit, else its title. */
    static String text(ToolStripe.Button window) {
        return window.number().isPresent() ? window.number().getAsInt() + ": " + window.title() : window.title();
    }

    /** The labels' face: the kit's label role. */
    Font font() { return styles.font(Typography.Role.LABEL); }

    /**
     * A label's length along its stripe: the padding at both ends and the text's set advance (its kerned fractional
     * advance, rounded, as the mocks set the labels), then a badge's gap and pill.
     */
    int length(String text, Optional<PanelBadge> badge) {
        int length = 2 * styles.metric(DesignMetrics.Role.STRIPE_LABEL_PADDING) + painter.setAdvance(font(), text);
        if (badge.isPresent())
            length += styles.metric(DesignMetrics.Role.STRIPE_PILL_GAP) + painter.countPill(Integer.toString(badge.get().count())).width;
        return length;
    }

    /**
     * How deep a side stripe stands, its 1 px rule included (Rulings 4): {@code window.rail_width}, or the label's line and
     * the rail's two edges when a large interface font needs more.
     */
    int thickness(int railWidth, Component reference) {
        int line = reference.getFontMetrics(font()).getHeight();
        return Math.max(UIScale.scale(railWidth),
            line + 2 * styles.metric(DesignMetrics.Role.RAIL_EDGE) + styles.metric(DesignMetrics.Role.DOCK_RULE));
    }

    /**
     * How deep the bottom strip stands, its 1 px rule included (Rulings 4 as ruled on 2026-10-17): the mocks'
     * {@code BOTTOM_STRIP_HEIGHT} (24) above its rule, or the label's line and the rail's two edges when a large interface
     * font needs more; {@code window.rail_width} does not move it.
     */
    int stripThickness(Component reference) {
        int line = reference.getFontMetrics(font()).getHeight();
        return Math.max(styles.metric(DesignMetrics.Role.BOTTOM_STRIP_HEIGHT), line + 2 * styles.metric(DesignMetrics.Role.RAIL_EDGE))
            + styles.metric(DesignMetrics.Role.DOCK_RULE);
    }

    /**
     * Paints a label in text space, from its start, centred across {@code across} on a whole-pixel baseline (the mocks'
     * bottom strip sets its 24 px line's at 17): its text, held on the stripe, a hovered tile or an open tile, then its
     * badge's pill.
     */
    void paint(Graphics2D g, String text, Optional<PanelBadge> badge, int across, GeneratedTheme theme, boolean open, boolean hover) {
        painter.prepareText(g);
        Font font = font();
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics(font);
        int x = styles.metric(DesignMetrics.Role.STRIPE_LABEL_PADDING);
        g.setColor(painter.stripeLabel(theme, open, hover));
        g.drawString(text, (float) x, (float) Math.round((across - metrics.getAscent() - metrics.getDescent()) / 2f + metrics.getAscent()));
        if (badge.isEmpty()) return;
        String count = Integer.toString(badge.get().count());
        Dimension pill = painter.countPill(count);
        painter.countPill(g, x + painter.measureText(font, text) + styles.metric(DesignMetrics.Role.STRIPE_PILL_GAP),
            (across - pill.height) / 2, count, theme, badge.get().tone() == PanelBadge.Tone.ERROR);
    }

    /**
     * One label: a window's toggle or a rail action's button, which its stripe lays out and paints. An action's label has a
     * plain button model, so it never stays selected.
     */
    static final class Label extends JToggleButton {
        /** The window it shows, or null for an action; replaced when the window's badge changes. */
        ToolStripe.Button window;
        /** The action it runs, or null for a window. */
        final Action action;
        private final BiConsumer<Label, Graphics> painter;

        Label(ToolStripe.Button window, BiConsumer<Label, Graphics> painter) {
            this.window = Objects.requireNonNull(window, "window");
            this.action = null;
            this.painter = Objects.requireNonNull(painter, "painter");
            setUp();
        }

        Label(Action action, BiConsumer<Label, Graphics> painter) {
            this.window = null;
            this.action = Objects.requireNonNull(action, "action");
            this.painter = Objects.requireNonNull(painter, "painter");
            setModel(new DefaultButtonModel());
            setHideActionText(true);
            setAction(action);
            setUp();
        }

        private void setUp() {
            setOpaque(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setFocusable(false);
            setRolloverEnabled(true);
        }

        /** The window's id, or null for an action. */
        String id() { return window == null ? null : window.id(); }

        /** What it reads. */
        String text() { return window != null ? StripeLabels.text(window) : String.valueOf(action.getValue(Action.NAME)); }

        /** The window's badge; an action has none. */
        Optional<PanelBadge> badge() { return window == null ? Optional.empty() : window.badge(); }

        @Override protected void paintComponent(Graphics graphics) { painter.accept(this, graphics); }
    }
}
