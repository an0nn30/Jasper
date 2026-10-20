package dev.jasper.app.workspace;

import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.dock.Side;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.icons.ChromeArtwork;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.ChromePainter;
import dev.jasper.ui.swing.NativeStyles;
import dev.jasper.ui.theme.GeneratedTheme;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.DefaultButtonModel;
import javax.swing.Icon;
import javax.swing.JToggleButton;

/**
 * What a labelled side stripe and the bottom strip share (spec 10.7): a label's text ("N: " and the title when the
 * window's toggle ends in a digit), its stripe icon in the look's artwork, its length along its stripe, every stripe's
 * thickness, where its icon stands, and the painting of a label in text space: its padding, its icon's room, its text,
 * then a count pill for a badge. EDT only.
 */
final class StripeLabels {
    private final ChromePainter painter;
    private final NativeStyles styles;
    private final ChromeArtwork artwork;
    /** The stripe icon's logical size; {@link AppIcons#stripeIcon} scales it (Execution notes R2). */
    private final int iconSize;

    /** The labels over {@code kit}, their icons in the look's {@code artwork}. */
    StripeLabels(Kit kit, ChromeArtwork artwork) {
        Objects.requireNonNull(kit, "kit");
        this.painter = new ChromePainter(kit);
        this.styles = new NativeStyles(kit);
        this.artwork = Objects.requireNonNull(artwork, "artwork");
        this.iconSize = kit.metrics().value(DesignMetrics.Role.STRIPE_ICON);
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
     * The stripe icon for a window's or action's {@code source} icon, {@code STRIPE_ICON} square in the look's artwork
     * and, where it is a glyph, in its label's ink; null for no icon (a null or empty one), so the label reads text only.
     */
    Icon icon(Icon source) { return AppIcons.stripeIcon(source, artwork, iconSize, this::ink).orElse(null); }

    /**
     * The ink a label paints its text in, read from {@code c} at paint time: its open and hovered state when it is a
     * button, else closed and unhovered (the drag overlay).
     */
    Color ink(Component c) {
        GeneratedTheme theme = ThemeZone.theme(c);
        if (c instanceof AbstractButton button) return painter.stripeLabel(theme, button.isSelected(), button.getModel().isRollover());
        return painter.stripeLabel(theme, false, false);
    }

    /**
     * A label's length along its stripe: the padding at both ends, an icon and its gap when it has {@code icon}, and the
     * text's set advance (its kerned fractional advance, rounded, as the mocks set the labels), then a badge's gap and pill.
     */
    int length(String text, Optional<PanelBadge> badge, Icon icon) {
        int length = 2 * styles.metric(DesignMetrics.Role.STRIPE_LABEL_PADDING) + painter.setAdvance(font(), text);
        if (icon != null) length += styles.metric(DesignMetrics.Role.STRIPE_ICON) + styles.metric(DesignMetrics.Role.STRIPE_ICON_GAP);
        if (badge.isPresent())
            length += styles.metric(DesignMetrics.Role.STRIPE_PILL_GAP) + painter.countPill(Integer.toString(badge.get().count())).width;
        return length;
    }

    /** A side label's line across its stripe: the face's ascent and descent at fractional metrics, rounded. */
    int line() {
        LineMetrics line = font().getLineMetrics("Mg", TEXT_CONTEXT);
        return Math.round(line.getAscent() + line.getDescent());
    }

    /**
     * How deep a side stripe stands, its 1 px rule included (the user's direction of 2026-10-18): the label's line, or the
     * stripe icon where that is larger, and the rail's two edges, so the stripe fits its labels, a larger interface font
     * thickens it and a tiny one never makes it thinner than its icons; {@code window.rail_width} does not apply.
     */
    int thickness() {
        return Math.max(line(), styles.metric(DesignMetrics.Role.STRIPE_ICON)) + 2 * styles.metric(DesignMetrics.Role.RAIL_EDGE)
            + styles.metric(DesignMetrics.Role.DOCK_RULE);
    }

    /**
     * Where a label's icon stands in the label's own unrotated coordinates, {@code width} by {@code height}: upright, past
     * the padding at its reading start (the bottom end on the left stripe, the top end on the right, the left end on the
     * bottom strip) and centred across.
     */
    Point iconAt(Side side, int width, int height) {
        int size = styles.metric(DesignMetrics.Role.STRIPE_ICON), padding = styles.metric(DesignMetrics.Role.STRIPE_LABEL_PADDING);
        return switch (side) {
            case LEFT -> new Point((width - size) / 2, height - padding - size);
            case RIGHT -> new Point((width - size) / 2, padding);
            case BOTTOM -> new Point(padding, (height - size) / 2);
        };
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
     * Paints a label in text space, from its start, on a whole-pixel baseline: its text, after its icon and the icon's gap
     * when it has an {@code icon} (which its stripe paints upright), held on the stripe, a hovered tile or an open tile,
     * then its badge's pill, both centred across {@code across}. A side label centres its face's capitals
     * ({@code sideLabel}, the user's direction of 2026-10-18); the bottom strip centres its face's whole-pixel ascent and
     * descent (the mocks' bottom strip sets its 24 px line's baseline at 17).
     */
    void paint(Graphics2D g, String text, Optional<PanelBadge> badge, boolean icon, int across, boolean sideLabel,
               GeneratedTheme theme, boolean open, boolean hover) {
        painter.prepareText(g);
        Font font = font();
        g.setFont(font);
        int baseline;
        if (sideLabel) {
            float capHeight = (float) -font.createGlyphVector(TEXT_CONTEXT, "H").getVisualBounds().getY();
            baseline = Math.round((across + capHeight) / 2f);
        } else {
            FontMetrics metrics = g.getFontMetrics(font);
            baseline = Math.round((across - metrics.getAscent() - metrics.getDescent()) / 2f + metrics.getAscent());
        }
        int x = styles.metric(DesignMetrics.Role.STRIPE_LABEL_PADDING)
            + (icon ? styles.metric(DesignMetrics.Role.STRIPE_ICON) + styles.metric(DesignMetrics.Role.STRIPE_ICON_GAP) : 0);
        g.setColor(painter.stripeLabel(theme, open, hover));
        g.drawString(text, (float) x, (float) baseline);
        if (badge.isEmpty()) return;
        String count = Integer.toString(badge.get().count());
        Dimension pill = painter.countPill(count);
        painter.countPill(g, x + painter.measureText(font, text) + styles.metric(DesignMetrics.Role.STRIPE_PILL_GAP),
            (across - pill.height) / 2, count, theme, badge.get().tone() == PanelBadge.Tone.ERROR);
    }

    /** The fractional antialiased context a side label's line is measured in. */
    private static final FontRenderContext TEXT_CONTEXT = new FontRenderContext(null, true, true);

    /**
     * One label: a window's toggle or a rail action's button, which its stripe lays out and paints. An action's label has a
     * plain button model, so it never stays selected.
     */
    static final class Label extends JToggleButton {
        /** The window it shows, or null for an action; replaced when the window's badge changes. */
        ToolStripe.Button window;
        /** The action it runs, or null for a window. */
        final Action action;
        /** Its stripe icon, or null: the label reads text only. */
        final Icon icon;
        private final BiConsumer<Label, Graphics> painter;

        Label(ToolStripe.Button window, Icon icon, BiConsumer<Label, Graphics> painter) {
            this.window = Objects.requireNonNull(window, "window");
            this.action = null;
            this.icon = icon;
            this.painter = Objects.requireNonNull(painter, "painter");
            setUp();
        }

        Label(Action action, Icon icon, BiConsumer<Label, Graphics> painter) {
            this.window = null;
            this.action = Objects.requireNonNull(action, "action");
            this.icon = icon;
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
