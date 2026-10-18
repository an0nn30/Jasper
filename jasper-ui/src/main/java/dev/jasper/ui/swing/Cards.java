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
        /** The body of a Section (a titled group), which the reconciler marks. */
        boolean titled;
        /** A manager detail's titled FORM card of label and value rows under a group face that draws it as a grid ({@link GridRows}). */
        boolean grid;
        /** The children of the last update, which {@link #relabel} measures again. */
        private List<ControlRegistry.Control> members = List.of();
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
            ((CardSurface) component).grid = grid;
            component.setLayout(
                    ((Nodes.Card) node).kind() == Nodes.CardKind.MIXED
                            ? new MixedLayout(this)
                            : new Composition.Vertical(
                                    ((Nodes.Card) node).kind() == Nodes.CardKind.SETTINGS
                                            ? 0
                                            : context().faces().group.cardGap(context(), ((Nodes.Card) node).kind(), settings,
                                                    ThemePainting.metric(context(), DesignMetrics.Role.ROW_GAP))));
            if (grid) { component.setBorder(GridRows.border(context())); component.setLayout(new GridRows(context())); }
        }

        @Override
        void children(List<ControlRegistry.Control> controls) {
            controls.stream().filter(c -> c.node instanceof Nodes.Choice choice && choice.kind() == Nodes.ChoiceKind.EXCLUSIVE).forEach(c -> Composition.label(c, true));
            members = List.copyOf(controls);
            boolean rows = detail && titled && ((Nodes.Card) node).kind() == Nodes.CardKind.FORM && !controls.isEmpty()
                && controls.stream().allMatch(c -> c instanceof FormRows.RowControl) && gridFace();
            if (rows != grid) { grid = rows; refresh(); }
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

        /** Whether the kit's group face draws a manager detail's titled card of label and value rows as a grid. */
        boolean gridFace() { return context().faces().group.detailGrid(); }

        /** Measures the label column again after every control has refreshed (a theme or interface font change). */
        void relabel() { labelColumnFor(this, members); }
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
            // A hidden row does not widen the column for the rows shown; a setting row measures its title as it shows it.
            if (!c.node.state().visible()) continue;
            if (c instanceof FormRows.RowControl row) widths.add(row.label.getPreferredSize().width);
            if (c instanceof SettingRows.RowControl row && row.node instanceof Nodes.SettingRow setting && !(setting.control() instanceof Nodes.Toggle))
                widths.add(ThemePainting.measureText(row.title.getFont(),
                    labels.label(setting.title()) + (setting.titleNote().isEmpty() ? "" : " · " + setting.titleNote())));
        }
        // A grid's labels take a column as wide as its widest label (the Credential Vault mock's Fields).
        int column = card.grid ? widths.stream().mapToInt(Integer::intValue).max().orElse(0) : form ? labels.column(card.context(), widths) : -1;
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

    /**
     * A grid card's rows (spec 14.8's Fields): inside a box {@code DETAIL_GRID_INSET} from the card's sides and bottom and
     * {@code DETAIL_GRID_TOP} below its top, each row centred in a slot at least {@code SETTING_HEIGHT} tall, the slots a
     * hairline apart for the rules the group face paints, and the rows {@code DETAIL_GRID_INSET} inside the box's sides.
     */
    static final class GridRows implements Composition.WidthLayout {
        private final KitContext context;
        GridRows(KitContext context) { this.context = context; }

        static EmptyBorder border(KitContext context) {
            int inset = ThemePainting.metric(context, DesignMetrics.Role.DETAIL_GRID_INSET), line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
            return new EmptyBorder(ThemePainting.metric(context, DesignMetrics.Role.DETAIL_GRID_TOP) + line, 2 * inset + line, inset + line,
                inset + line + ThemePainting.metric(context, DesignMetrics.Role.DETAIL_GRID_TRAILING));
        }

        private static int slot(KitContext context, int height) { return Math.max(ThemePainting.metric(context, DesignMetrics.Role.SETTING_HEIGHT), height); }

        /** The y of the rule between each two visible rows of {@code card}, laid out by this layout. */
        static List<Integer> rules(CardSurface card) {
            var rules = new java.util.ArrayList<Integer>();
            int line = ThemePainting.metric(card.context, DesignMetrics.Role.HAIRLINE), y = card.getInsets().top;
            boolean first = true;
            for (Component c : card.getComponents()) if (c.isVisible()) {
                if (!first) { rules.add(y); y += line; }
                y += slot(card.context, c.getHeight());
                first = false;
            }
            return rules;
        }

        @Override public int height(Container p, int width) {
            Insets insets = p.getInsets();
            int line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE), h = insets.top + insets.bottom, count = 0;
            for (Component c : p.getComponents()) if (c.isVisible()) {
                h += slot(context, Composition.height(c, Math.max(0, width - insets.left - insets.right))) + (count++ > 0 ? line : 0);
            }
            return h;
        }

        @Override public Dimension preferredLayoutSize(Container p) {
            int w = 0;
            for (Component c : p.getComponents()) if (c.isVisible()) w = Math.max(w, c.getPreferredSize().width);
            Insets insets = p.getInsets();
            w += insets.left + insets.right;
            return new Dimension(w, height(p, p.getWidth() > 0 ? p.getWidth() : w));
        }

        @Override public void layoutContainer(Container p) {
            Insets insets = p.getInsets();
            int line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE), y = insets.top, w = Math.max(0, p.getWidth() - insets.left - insets.right);
            boolean first = true;
            for (Component c : p.getComponents()) if (c.isVisible()) {
                if (!first) y += line;
                int h = Composition.height(c, w), s = slot(context, h);
                c.setBounds(insets.left, y + (s - h) / 2, w, h);
                y += s;
                first = false;
            }
        }
    }

    /**
     * A grid row's value beside its actions (an INLINE stack the reconciler marks): the value fills up to the grid's action
     * lane, as wide as its widest row's actions, and the actions stand from the lane's start, {@code DETAIL_ACTION_GAP}
     * apart, so each row's first action shares one column with a secret's Show (the Credential Vault mock's Fields).
     */
    static final class GridValueLayout implements Composition.WidthLayout {
        private final CardControl card;
        GridValueLayout(CardControl card) { this.card = card; }

        private int gap() { return ThemePainting.metric(card.context(), DesignMetrics.Role.DETAIL_ACTION_GAP); }

        private static List<Component> visible(Container p) { return java.util.Arrays.stream(p.getComponents()).filter(Component::isVisible).toList(); }

        /** The widest actions among the grid's rows: a value's actions after it, or a secret's Show and Copy. */
        private int lane() {
            int lane = 0;
            for (var member : card.members) {
                if (!(member instanceof FormRows.RowControl row) || row.controls.isEmpty() || !row.component.isVisible()) continue;
                int width = 0;
                if (row.controls.getFirst() instanceof Stacks.StackControl stack && stack.gridCard == card) {
                    var children = visible(stack.component);
                    for (int i = 1; i < children.size(); i++) width += (i > 1 ? gap() : 0) + children.get(i).getPreferredSize().width;
                } else if (row.controls.getFirst() instanceof SecretFields.SecretControl secret && secret.detail) {
                    width = secret.reveal.isVisible() ? secret.reveal.getPreferredSize().width : 0;
                    if (secret.copy.isVisible()) width += (width > 0 ? gap() : 0) + secret.copy.getPreferredSize().width;
                }
                lane = Math.max(lane, width);
            }
            return lane;
        }

        @Override public int height(Container p, int width) {
            Insets insets = p.getInsets();
            int h = 0;
            for (Component c : visible(p)) h = Math.max(h, c.getPreferredSize().height);
            return insets.top + insets.bottom + h;
        }

        @Override public Dimension preferredLayoutSize(Container p) {
            var children = visible(p); Insets insets = p.getInsets();
            int w = insets.left + insets.right + (children.isEmpty() ? 0 : children.getFirst().getPreferredSize().width) + (children.size() > 1 ? gap() + lane() : 0);
            return new Dimension(w, height(p, p.getWidth() > 0 ? p.getWidth() : w));
        }

        @Override public void layoutContainer(Container p) {
            var children = visible(p); if (children.isEmpty()) return;
            Insets insets = p.getInsets();
            int h = Math.max(0, p.getHeight() - insets.top - insets.bottom), right = p.getWidth() - insets.right;
            int start = children.size() > 1 ? Math.max(insets.left, right - lane()) : right;
            var value = children.getFirst();
            int vh = Math.min(h, value.getPreferredSize().height);
            value.setBounds(insets.left, insets.top + (h - vh) / 2, Math.max(0, (children.size() > 1 ? start - gap() : right) - insets.left), vh);
            int x = start;
            for (int i = 1; i < children.size(); i++) {
                var size = children.get(i).getPreferredSize();
                children.get(i).setBounds(x, insets.top + (h - size.height) / 2, size.width, Math.min(h, size.height));
                x += size.width + gap();
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
        boolean page, grid;
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
