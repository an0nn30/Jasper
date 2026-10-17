package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.SemanticColor;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Grouped card surfaces and section labels. */
final class Cards {
    private Cards() {}

    static final class CardControl extends ControlRegistry.Control {
        /** Inside a FULL form or a manager detail; its settings rows each own a top stroke, so the card adds its closing one. */
        boolean fullForm, detail;
        /** On a Settings page: rows share the card's top stroke, and the bottom stroke has its own line below the last row. */
        boolean settings;
        CardControl(KitView view, Nodes.Card node) {
            super(view, node, new CardSurface(view.context()));
            refresh();
        }

        @Override
        void refresh() {
            state();
            int pad =
                    ((Nodes.Card) node).kind() != Nodes.CardKind.FORM
                            ? 0
                            : ThemePainting.metric(context(), DesignMetrics.Role.CARD_PADDING) + ThemePainting.metric(context(), DesignMetrics.Role.HAIRLINE);
            int closing = (fullForm || detail) && ((Nodes.Card) node).kind() == Nodes.CardKind.SETTINGS ? ThemePainting.metric(context(), DesignMetrics.Role.HAIRLINE) : 0;
            int bottom = settings && ((Nodes.Card) node).kind() == Nodes.CardKind.SETTINGS ? ThemePainting.metric(context(), DesignMetrics.Role.HAIRLINE) : pad + closing;
            component.setBorder(context().faces().group.cardBorder(context(), ((Nodes.Card) node).kind(), settings, new EmptyBorder(pad, pad, bottom, pad)));
            ((CardSurface) component).kind = ((Nodes.Card) node).kind();
            ((CardSurface) component).page = settings;
            component.setLayout(
                    ((Nodes.Card) node).kind() == Nodes.CardKind.MIXED
                            ? new MixedLayout(this)
                            : new Composition.Vertical(
                                    ((Nodes.Card) node).kind() == Nodes.CardKind.SETTINGS
                                            ? 0
                                            : ThemePainting.metric(
                                                    context(), DesignMetrics.Role.ROW_GAP)));
        }

        @Override
        void children(List<ControlRegistry.Control> controls) {
            controls.stream().filter(c -> c.node instanceof Nodes.Choice choice && choice.kind() == Nodes.ChoiceKind.EXCLUSIVE).forEach(c -> Composition.label(c, true));
            labelColumnFor(this, controls);
            ((CardSurface) component).settings =
                    controls.stream()
                            .filter(c -> c.node instanceof Nodes.SettingRow)
                            .map(c -> c.component)
                            .toList();
            // A Settings group card (a heading row over its members) lists its members as compact rows.
            boolean grouped = settings && controls.stream().anyMatch(c -> c.node instanceof Nodes.Stack stack && stack.role() == Nodes.LayoutRole.SETTINGS_GROUP_HEADER);
            controls.forEach(c -> { if (c instanceof SettingRows.RowControl row && row.grouped != grouped) { row.grouped = grouped; row.refresh(); } });
            Stacks.mount(component, controls.stream().map(c -> c.component).toList());
        }
    }

    /**
     * The label policy's shared column for a form card (spec 6.4): its FormRows and non-Toggle SettingRows take it, and a
     * bare EXCLUSIVE choice is inset to the field column; the default policy keeps each row's fixed column. A setting row
     * that takes the column renders like a FormRow: a FORM card pads it as it pads its FormRows, and a MIXED or SETTINGS
     * card, which lays setting rows edge to edge, gives it the card padding its FormRows take, so the row's title starts
     * on the card's labels and its control on the field column.
     */
    private static void labelColumnFor(CardControl card, List<ControlRegistry.Control> controls) {
        var labels = card.context().faces().labels;
        boolean form = card.fullForm || controls.stream().anyMatch(c -> c instanceof FormRows.RowControl);
        var widths = new java.util.ArrayList<Integer>();
        for (var c : controls) {
            if (c instanceof FormRows.RowControl row) widths.add(row.label.getPreferredSize().width);
            if (c instanceof SettingRows.RowControl row && !(((Nodes.SettingRow) row.node).control() instanceof Nodes.Toggle))
                widths.add(ThemePainting.measureText(row.title.getFont(), labels.label(((Nodes.SettingRow) row.node).title())));
        }
        int column = form ? labels.column(card.context(), widths) : -1;
        int rowInset = ((Nodes.Card) card.node).kind() == Nodes.CardKind.FORM ? 0
            : ThemePainting.metric(card.context(), DesignMetrics.Role.CARD_PADDING) + ThemePainting.metric(card.context(), DesignMetrics.Role.HAIRLINE);
        for (var c : controls) {
            if (c instanceof FormRows.RowControl row && row.column != column) { row.column = column; row.component.revalidate(); }
            if (c instanceof SettingRows.RowControl row && !(((Nodes.SettingRow) row.node).control() instanceof Nodes.Toggle)) {
                boolean asRow = column >= 0;
                int side = asRow ? rowInset : 0;
                if (row.formLabel != asRow || row.column != column || row.formInset != side) {
                    row.formLabel = asRow; row.column = column; row.formInset = side; row.refresh();
                }
            }
            if (c.node instanceof Nodes.Choice choice && choice.kind() == Nodes.ChoiceKind.EXCLUSIVE) {
                var inset = labels.choiceInset(card.context(), column);
                if (inset != null || c.component.getBorder() != null) c.component.setBorder(inset);
            }
        }
    }

    static final class MixedLayout implements Composition.WidthLayout {
        final CardControl owner;

        MixedLayout(CardControl owner) {
            this.owner = owner;
        }

        int pad() {
            return ThemePainting.metric(owner.context(), DesignMetrics.Role.CARD_PADDING) + ThemePainting.metric(owner.context(), DesignMetrics.Role.HAIRLINE);
        }

        boolean setting(Component c) {
            return ((CardSurface) owner.component).settings.contains(c);
        }

        int arrange(Container p, int width, boolean layout) {
            int y = 0;
            Component previous = null;
            for (Component c : p.getComponents())
                if (c.isVisible()) {
                    boolean setting = setting(c), before = previous != null && setting(previous);
                    if (previous == null && !setting) y += pad();
                    else if (previous != null && setting != before)
                        // The outer card owns a stroke; an internal content/setting transition
                        // owns only its content padding. The setting row accounts for its divider.
                        y += ThemePainting.metric(owner.context(), DesignMetrics.Role.CARD_PADDING);
                    else if (previous != null && !setting)
                        y += ThemePainting.metric(owner.context(), DesignMetrics.Role.MIXED_ROW_GAP);
                    int inset = setting ? 0 : pad(),
                            w = Math.max(0, width - 2 * inset),
                            h = Composition.height(c, w);
                    if (layout) c.setBounds(inset, y, w, h);
                    y += h;
                    previous = c;
                }
            if (previous != null && !setting(previous)) y += pad();
            return y;
        }

        @Override
        public int height(Container p, int width) {
            return arrange(p, width, false);
        }

        @Override
        public Dimension preferredLayoutSize(Container p) {
            int w = 0;
            for (Component c : p.getComponents())
                if (c.isVisible())
                    w = Math.max(w, c.getPreferredSize().width + (setting(c) ? 0 : 2 * pad()));
            return new Dimension(w, height(p, p.getWidth() > 0 ? p.getWidth() : w));
        }

        @Override
        public void layoutContainer(Container p) {
            arrange(p, p.getWidth(), true);
        }
    }

    static final class CardSurface extends JPanel {
        List<JComponent> settings = List.of();
        /** The card's kind and whether it is on a Settings page, which the group face reads to place its dividers. */
        Nodes.CardKind kind = Nodes.CardKind.FORM;
        boolean page;
        final KitContext context;

        CardSurface(KitContext context) {
            this.context = context;
            setOpaque(false);
        }

        @Override public Dimension getMinimumSize() {
            var minimum=super.getMinimumSize();
            int fixed=settings.stream().filter(Component::isVisible).mapToInt(c->c.getMinimumSize().width).max().orElse(0);
            return new Dimension(Math.max(minimum.width,fixed+getInsets().left+getInsets().right),minimum.height);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try { context.faces().group.paintCard(this, g); } finally { g.dispose(); }
        }

        /** The rounded card, its edge and its setting rows' dividers (the default face). */
        void paintDefault(Graphics2D g) {
            ThemePainting.antialias(g);
            int r = ThemePainting.metric(context, DesignMetrics.Role.CARD_RADIUS);
            g.setColor(ThemePainting.color(context, SemanticColor.SURFACE_CARD));
            g.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), r * 2, r * 2));
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
            g.setColor(ThemePainting.color(context, SemanticColor.BORDER_SUBTLE));
            g.draw(new RoundRectangle2D.Double(.5, .5, getWidth() - 1, getHeight() - 1, r * 2, r * 2));
            Component previous = null;
            for (Component child : getComponents()) if (child.isVisible()) {
                if (previous != null && (settings.contains(child) || settings.contains(previous)))
                    g.fillRect(1,child.getY(),getWidth()-2,ThemePainting.metric(context,DesignMetrics.Role.HAIRLINE));
                previous = child;
            }
        }
    }

    /** A section's surface: the classic group face paints its titled box here; the default face paints nothing. */
    static final class SectionSurface extends JPanel {
        final KitContext context;

        SectionSurface(KitContext context) {
            this.context = context;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try { context.faces().group.paintSection(this, g); } finally { g.dispose(); }
        }
    }

    static final class SectionControl extends ControlRegistry.Control {
        final JLabel title = Labels.literal();

        SectionControl(KitView view, Nodes.Section node) {
            super(view, node, new SectionSurface(view.context()));
            component.setOpaque(false);
            KitOwnership.mark(title, node.id() + ".title");
            component.add(title);
            refresh();
        }

        @Override
        void refresh() {
            state();
            title.setText(((Nodes.Section) node).title());
            title.setEnabled(component.isEnabled());
            context().faces().group.styleTitle(title, context());
            component.setLayout(context().faces().group.sectionLayout(this));
        }

        @Override
        void children(List<ControlRegistry.Control> controls) {
            Stacks.mount(component, List.of(title, controls.getFirst().component));
        }
    }
}
