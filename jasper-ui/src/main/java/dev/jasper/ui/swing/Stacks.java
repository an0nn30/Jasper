package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.theme.SemanticColor;

import java.awt.*;
import java.util.List;

import javax.swing.*;

/**
 * Basic semantic composition pulled forward for stable siblings; larger containers compose this
 * layout.
 */
final class Stacks {
    private Stacks() {}

    static final class StackSurface extends JPanel {
        final KitContext context;boolean joined;StackSurface(KitContext context){this.context=context;}
        @Override protected void paintComponent(Graphics graphics){
            super.paintComponent(graphics);if(!joined)return;var g=(Graphics2D)graphics.create();
            try{ThemePainting.antialias(g);int radius=ThemePainting.metric(context,DesignMetrics.Role.CONTROL_RADIUS);g.setColor(ThemePainting.color(context,dev.jasper.ui.theme.SemanticColor.BORDER_CONTROL));g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,radius*2,radius*2);if(getComponentCount()>1)g.drawLine(getComponent(1).getX(),0,getComponent(1).getX(),getHeight());}finally{g.dispose();}
        }
    }

    static final class StackControl extends ControlRegistry.Control {
        final Tile tile;
        boolean detail, sheetHeading, panelDetail, embedded, settings, identityHeader, identityGroup;
        List<ControlRegistry.Control> children = List.of();
        @Override void embeddedLabel(boolean value) { embedded = value; if (settings) children.forEach(c -> Composition.label(c, value)); }
        java.util.Set<Component> captions = java.util.Set.of();

        StackControl(KitView view, Nodes.Stack node) {
            super(view, node, new StackSurface(view.context()));
            component.setOpaque(false);
            tile = new Tile(view.context());
            KitOwnership.mark(tile, node.id() + ".tile");
            bindCancel();
            refresh();
        }

        /**
         * Escape from any focused control inside the stack runs its cancelAction (an inline find bar closing) unless a
         * nearer owner takes the key first (a field's own cancelAction, an open popup); blank passes Escape on.
         */
        private void bindCancel() {
            var input = component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
            KeyStroke key = KeyStroke.getKeyStroke("ESCAPE"); String name = "stack.cancel";
            input.put(key, name);
            component.getActionMap().put(name, new AbstractAction() {
                // A disabled, busy or hidden stack leaves Escape to its ancestors (a dialog's or form's Cancel).
                @Override public boolean isEnabled() { return !closed && interactive() && !((Nodes.Stack) node).cancelAction().isBlank(); }
                @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                    if (!isEnabled() || view.dismissPopup()) return;
                    emit(new UiEvent.Action(((Nodes.Stack) node).cancelAction()));
                }
            });
            own(() -> { input.remove(key); component.getActionMap().remove(name); });
        }

        @Override
        void refresh() {
            state();
            var stack = (Nodes.Stack) node;
            ((StackSurface)component).joined=stack.role()==Nodes.LayoutRole.JOINED_ACTIONS;
            int detailPadding = stack.role() == Nodes.LayoutRole.DETAIL ? ThemePainting.metric(context(), panelDetail ? DesignMetrics.Role.PANEL_DETAIL_PADDING : DesignMetrics.Role.DETAIL_PADDING) : 0;
            component.setBorder(new javax.swing.border.EmptyBorder(detailPadding, detailPadding, panelDetail && stack.role()==Nodes.LayoutRole.DETAIL ? Math.max(0,detailPadding-ThemePainting.metric(context(),DesignMetrics.Role.FOCUS_RING)) : detailPadding, detailPadding));
            component.setLayout(
                    stack.role() == Nodes.LayoutRole.SETTINGS_NAVIGATION ? new FillLayout() : stack.role() == Nodes.LayoutRole.SETTINGS_TRAILING ? new SettingsTrailingLayout(context()) : stack.role() == Nodes.LayoutRole.SETTINGS_GROUP_HEADER ? new SettingsGroupLayout(context()) : stack.role() == Nodes.LayoutRole.SETTINGS_FOOTER ? new SettingsFooterLayout(context()) : stack.role() == Nodes.LayoutRole.SETTINGS_PAGE ? new SettingsLayout(context()) : sheetHeading ? new Navigation.InlineLayout(context(),DesignMetrics.Role.COLUMN_GAP) : stack.role() == Nodes.LayoutRole.HERO
                            ? new HeroLayout(this)
                            : stack.role() == Nodes.LayoutRole.FOOTER ? new FooterStatusLayout(context()) : stack.role() == Nodes.LayoutRole.SUPPORTING ? new SupportingLayout(context()) : stack.role() == Nodes.LayoutRole.NAVIGATION ? new Navigation.InlineLayout(context(), identityHeader ? DesignMetrics.Role.IDENTITY_HEADER_GAP : panelDetail && stack.children().stream().anyMatch(n -> n instanceof Nodes.Icon icon && icon.size() == Nodes.IconSize.HERO) ? DesignMetrics.Role.PANEL_DETAIL_IDENTITY_GAP : detail && !panelDetail ? DesignMetrics.Role.DETAIL_HEADING_GAP : DesignMetrics.Role.ICON_GAP) : new StackLayout(context(), stack.role(), stack.children().size()==2 && stack.children().getFirst() instanceof Nodes.Text text && text.role()==Nodes.TextRole.DETAIL_TITLE, captions) {
                                @Override int gap() { return identityGroup ? ThemePainting.metric(context(),DesignMetrics.Role.IDENTITY_HEADER_AFTER) : panelDetail ? ThemePainting.metric(context(), DesignMetrics.Role.PANEL_DETAIL_GAP) : super.gap(); }
                                @Override int verticalGap(Component previous, Component child) {
                                    return panelDetail && stack.role() == Nodes.LayoutRole.VERTICAL && stack.children().stream().allMatch(n -> n instanceof Nodes.Text) ? 0 : super.verticalGap(previous, child);
                                }
                            });
            component.revalidate();
            component.repaint();
        }

        @Override
        Container childParent(Nodes.Node child) {
            return ((Nodes.Stack) node).role() == Nodes.LayoutRole.HERO
                            && child instanceof Nodes.Icon
                    ? tile
                    : component;
        }

        @Override
        void children(List<ControlRegistry.Control> controls) {
            children = List.copyOf(controls);
            if(((Nodes.Stack)node).role()==Nodes.LayoutRole.SETTINGS_GROUP_HEADER)
                controls.forEach(c->{if(c instanceof Labels.IconControl icon)icon.tile(context().metrics().value(DesignMetrics.Role.SETTINGS_ICON_TILE),context().metrics().value(DesignMetrics.Role.SETTINGS_ICON_TILE),context().icons().size(Nodes.IconSize.CONTROL),false);});
            if (embedded && settings) controls.forEach(c -> Composition.label(c, true));
            var wanted = new java.util.ArrayList<JComponent>();
            for (var c : controls)
                if (childParent(c.node) == tile) {
                    mount(tile, List.of(c.component));
                    wanted.add(tile);
                } else wanted.add(c.component);
            mount(component, wanted);
            captions = controls.stream().filter(c -> c.node instanceof Nodes.Text text && text.role() == Nodes.TextRole.CAPTION).map(c -> (Component)c.component).collect(java.util.stream.Collectors.toSet());
            if (component.getLayout() instanceof StackLayout layout) layout.captions = captions;
        }
    }

    static void mount(Container component, List<JComponent> wanted) {
        for (Component child : component.getComponents())
            if (!wanted.contains(child)) component.remove(child);
        for (int i = 0; i < wanted.size(); i++) {
            JComponent child = wanted.get(i);
            if (child.getParent() != component) component.add(child, i);
            else if (component.getComponentZOrder(child) != i)
                component.setComponentZOrder(child, i);
        }
    }

    static final class Tile extends JPanel {
        final KitContext context;
        final DesignMetrics.Role sizeRole;

        Tile(KitContext context) {
            this(context, DesignMetrics.Role.HERO_TILE);
        }

        Tile(KitContext context, DesignMetrics.Role sizeRole) {
            super(new GridBagLayout());
            this.context = context;
            this.sizeRole = sizeRole;
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredSize() {
            int size = ThemePainting.metric(context, sizeRole);
            return new Dimension(size, size);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                ThemePainting.antialias(g);
                g.setColor(ThemePainting.color(context, SemanticColor.SURFACE_CONTROL));
                int radius = ThemePainting.metric(context, DesignMetrics.Role.HERO_TILE_RADIUS);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), radius * 2, radius * 2);
                g.setColor(ThemePainting.color(context, SemanticColor.BORDER_CONTROL));
                g.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, radius * 2, radius * 2);
            } finally {
                g.dispose();
            }
        }
    }

    static final class HeroLayout implements Composition.WidthLayout {
        final StackControl owner;

        HeroLayout(StackControl owner) {
            this.owner = owner;
        }

        int gap(Component previous) {
            return ThemePainting.metric(
                    owner.context(),
                    previous == owner.tile
                            ? DesignMetrics.Role.HERO_TILE_GAP
                            : DesignMetrics.Role.DESCRIPTION_GAP);
        }

        @Override
        public int height(Container parent, int width) {
            int h = 0;
            Component previous = null;
            for (Component c : parent.getComponents())
                if (c.isVisible()) {
                    if (previous != null) h += gap(previous);
                    h += Composition.height(c, width);
                    previous = c;
                }
            return h;
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            int width = 0;
            for (Component c : parent.getComponents())
                if (c.isVisible()) width = Math.max(width, c.getPreferredSize().width);
            return new Dimension(
                    width, height(parent, parent.getWidth() > 0 ? parent.getWidth() : width));
        }

        @Override
        public void layoutContainer(Container parent) {
            int y = 0;
            Component previous = null;
            for (Component c : parent.getComponents())
                if (c.isVisible()) {
                    if (previous != null) y += gap(previous);
                    int width = c instanceof Labels.LiteralLabel label && label.wraps() ? parent.getWidth() : Math.min(parent.getWidth(), c.getPreferredSize().width),
                            h = Composition.height(c, width);
                    c.setBounds((parent.getWidth() - width) / 2, y, width, h);
                    y += h;
                    previous = c;
                }
        }
    }

    /** Footer status groups retain leading facts; the final path elides. Narrow footers stack those same groups. */
    static final class FooterStatusLayout implements Composition.WidthLayout {
        final KitContext context;
        final SupportingLayout inline;
        final Composition.Vertical vertical;
        FooterStatusLayout(KitContext context) {this.context=context;inline=new SupportingLayout(context);vertical=new Composition.Vertical(ThemePainting.metric(context,DesignMetrics.Role.ICON_GAP));}
        Composition.WidthLayout layout(Container parent,int width) {
            var children=java.util.Arrays.stream(parent.getComponents()).filter(Component::isVisible).toList();
            int prefix=children.stream().limit(Math.max(0,children.size()-1)).mapToInt(c->c.getPreferredSize().width+ThemePainting.metric(context,DesignMetrics.Role.INLINE_GAP)).sum();
            return width<prefix+ThemePainting.metric(context,DesignMetrics.Role.FORM_LABEL_WIDTH)?vertical:inline;
        }
        @Override public int height(Container parent,int width){return layout(parent,width).height(parent,width);}
        @Override public Dimension preferredLayoutSize(Container parent){return inline.preferredLayoutSize(parent);}
        @Override public void layoutContainer(Container parent){layout(parent,parent.getWidth()).layoutContainer(parent);}
    }

    /** Natural leading adornments beside a flexible final body, aligned to its first line. */
    static final class SupportingLayout implements Composition.WidthLayout {
        private final KitContext context;
        SupportingLayout(KitContext context) { this.context = context; }
        private int gap() { return ThemePainting.metric(context, DesignMetrics.Role.INLINE_GAP); }
        private List<Component> visible(Container parent) {
            return java.util.Arrays.stream(parent.getComponents()).filter(Component::isVisible).toList();
        }
        private int leadingWidth(List<Component> children) {
            return children.stream().limit(Math.max(0, children.size()-1)).mapToInt(c -> c.getPreferredSize().width + gap()).sum();
        }
        @Override public void addLayoutComponent(String name, Component component) {}
        @Override public void removeLayoutComponent(Component component) {}
        @Override public Dimension minimumLayoutSize(Container parent) { return preferredLayoutSize(parent); }
        @Override public Dimension preferredLayoutSize(Container parent) {
            var children = visible(parent); var insets = parent.getInsets();
            int width = insets.left + insets.right + leadingWidth(children)
                + (children.isEmpty() ? 0 : children.getLast().getPreferredSize().width);
            return new Dimension(width, height(parent, parent.getWidth() > 0 ? parent.getWidth() : width));
        }
        @Override public int height(Container parent, int width) {
            var children = visible(parent); var insets = parent.getInsets();
            int result = 0, bodyWidth = Math.max(0, width - insets.left - insets.right - leadingWidth(children));
            for (Component child : children) result = Math.max(result,
                Composition.height(child, child == children.getLast() ? bodyWidth : child.getPreferredSize().width));
            return insets.top + insets.bottom + result;
        }
        @Override public void layoutContainer(Container parent) {
            var children = visible(parent); if (children.isEmpty()) return;
            var insets = parent.getInsets(); var body = children.getLast();
            int x = insets.left, bodyWidth = Math.max(0, parent.getWidth() - insets.left - insets.right - leadingWidth(children));
            int lineHeight = body.getFontMetrics(body.getFont()).getHeight();
            for (Component child : children) {
                int width = child == body ? bodyWidth : child.getPreferredSize().width;
                int height = Composition.height(child, width);
                child.setBounds(x, insets.top + (child == body ? 0 : Math.max(0, (lineHeight-height)/2)), width, height);
                x += width + gap();
            }
        }
    }

    /** Help/status lead; all remaining actions are pinned to the trailing edge. */
    static final class SettingsFooterLayout implements Composition.WidthLayout {
        final KitContext context;
        SettingsFooterLayout(KitContext context) { this.context = context; }
        int gap() { return ThemePainting.metric(context, DesignMetrics.Role.BUTTON_GAP); }
        @Override public int height(Container parent, int width) {
            int height = 0; for (Component child : parent.getComponents()) if (child.isVisible()) height = Math.max(height, child.getPreferredSize().height);
            return height;
        }
        @Override public Dimension preferredLayoutSize(Container parent) {
            int width = 0; for (Component child : parent.getComponents()) if (child.isVisible()) width += child.getPreferredSize().width + gap();
            return new Dimension(width, height(parent, width));
        }
        @Override public void layoutContainer(Container parent) {
            int right = parent.getWidth();
            for (int i = parent.getComponentCount() - 1; i >= 2; i--) {
                Component child = parent.getComponent(i); if (!child.isVisible()) continue;
                Dimension size = child.getPreferredSize(); size.width=Math.max(size.width,ThemePainting.metric(context,DesignMetrics.Role.SETTINGS_ACTION_MIN_WIDTH)); right -= size.width;
                child.setBounds(Math.max(0, right), (parent.getHeight() - size.height) / 2, size.width, size.height); right -= gap();
            }
            int left = 0;
            for (int i = 0; i < Math.min(2, parent.getComponentCount()); i++) {
                Component child = parent.getComponent(i); Dimension size = child.getPreferredSize();
                int width = Math.max(0, Math.min(size.width, right - left));
                child.setBounds(left, (parent.getHeight() - size.height) / 2, width, size.height); left += width + gap();
            }
        }
    }

    /** Bounded reading width and responsive rows for ordinary settings pages. */
    static final class FillLayout implements Composition.WidthLayout {
        @Override public Dimension preferredLayoutSize(Container p){int w=0,h=0;for(var c:p.getComponents()){w=Math.max(w,c.getPreferredSize().width);h=Math.max(h,c.getPreferredSize().height);}return new Dimension(w,h);}
        @Override public int height(Container p,int width){return preferredLayoutSize(p).height;}
        @Override public void layoutContainer(Container p){for(var c:p.getComponents())c.setBounds(0,0,p.getWidth(),p.getHeight());}
        @Override public void addLayoutComponent(String n,Component c){} @Override public void removeLayoutComponent(Component c){}
    }

    /** The final action owns a stable slot even when no override currently makes it visible. */
    static final class SettingsTrailingLayout implements Composition.WidthLayout {
        final KitContext context;SettingsTrailingLayout(KitContext context){this.context=context;}
        int gap(){return ThemePainting.metric(context,DesignMetrics.Role.COLUMN_GAP);}
        int action(Container p){return p.getComponentCount()>1?p.getComponent(1).getPreferredSize().width:0;}
        @Override public Dimension preferredLayoutSize(Container p){if(p.getComponentCount()==0)return new Dimension();var c=p.getComponent(0).getPreferredSize();return new Dimension(c.width+gap()+action(p),c.height);}
        @Override public int height(Container p,int width){return p.getComponentCount()==0?0:Composition.height(p.getComponent(0),Math.max(0,width-gap()-action(p)));}
        @Override public void layoutContainer(Container p){if(p.getComponentCount()==0)return;int a=action(p),w=Math.max(0,p.getWidth()-gap()-a);p.getComponent(0).setBounds(0,0,w,p.getHeight());if(p.getComponentCount()>1)p.getComponent(1).setBounds(w+gap(),0,a,p.getHeight());}
        @Override public void addLayoutComponent(String n,Component c){} @Override public void removeLayoutComponent(Component c){}
    }
    static final class SettingsGroupLayout implements Composition.WidthLayout {
        final KitContext context;SettingsGroupLayout(KitContext context){this.context=context;}
        int pad(){return ThemePainting.metric(context,DesignMetrics.Role.SETTING_SIDE_PADDING);}
        @Override public Dimension preferredLayoutSize(Container p){int w=pad()*2,h=ThemePainting.metric(context,DesignMetrics.Role.SETTINGS_GROUP_HEIGHT);for(var c:p.getComponents()){w+=c.getPreferredSize().width+pad();h=Math.max(h,c.getPreferredSize().height+pad());}return new Dimension(w,h);}
        @Override public int height(Container p,int width){return preferredLayoutSize(p).height;}
        @Override public void layoutContainer(Container p){int x=pad();for(int i=0;i<p.getComponentCount();i++){var c=p.getComponent(i);var d=c.getPreferredSize();int w=Math.min(d.width,Math.max(0,p.getWidth()-x-pad()));c.setBounds(i==p.getComponentCount()-1?Math.max(x,p.getWidth()-pad()-w):x,(p.getHeight()-d.height)/2,w,d.height);x+=w+pad();}}
        @Override public void addLayoutComponent(String n,Component c){} @Override public void removeLayoutComponent(Component c){}
    }

    static final class SettingsLayout implements Composition.WidthLayout {
        private final KitContext context;
        SettingsLayout(KitContext context) { this.context = context; }
        int side(int width) { return Math.min(ThemePainting.metric(context, DesignMetrics.Role.SETTINGS_PAGE_INSET), Math.max(0, width / 12)); }
        int arrange(Container parent, int width, boolean layout) {
            int side = side(width), y = ThemePainting.metric(context, DesignMetrics.Role.SETTINGS_PAGE_TOP);
            int w = Math.max(0, Math.min(Math.max(fixedWidth(parent), ThemePainting.metric(context, DesignMetrics.Role.SETTINGS_CARD_WIDTH)), width - 2 * side));
            boolean before = false;
            for (Component child : parent.getComponents()) if (child.isVisible()) {
                if (before) y += ThemePainting.metric(context, DesignMetrics.Role.SETTINGS_SECTION_GAP);
                int height = Composition.height(child, w);
                if (layout) child.setBounds(side, y, w, height);
                y += height; before = true;
            }
            return y + ThemePainting.metric(context, DesignMetrics.Role.SETTINGS_PAGE_TOP);
        }
        @Override public int height(Container parent, int width) { return arrange(parent, width, false); }
        @Override public Dimension preferredLayoutSize(Container parent) {
            int width = ThemePainting.metric(context, DesignMetrics.Role.SETTINGS_CARD_WIDTH) + 2 * ThemePainting.metric(context, DesignMetrics.Role.SETTINGS_PAGE_INSET);
            return new Dimension(Math.max(width, minimumLayoutSize(parent).width), height(parent, parent.getWidth() > 0 ? parent.getWidth() : width));
        }
        int fixedWidth(Container parent) {
            int width = 0;
            for (Component child : parent.getComponents()) if (child instanceof Container nested) {
                if (nested.getLayout() instanceof SettingRows.RowLayout row && row.fixedControl()) width = Math.max(width, nested.getPreferredSize().width);
                else width = Math.max(width, fixedWidth(nested));
            }
            return width;
        }
        @Override public Dimension minimumLayoutSize(Container parent) { int fixed = fixedWidth(parent); return new Dimension(fixed == 0 ? 0 : fixed + 2 * ThemePainting.metric(context, DesignMetrics.Role.SETTINGS_PAGE_INSET), 0); }
        @Override public void layoutContainer(Container parent) { arrange(parent, parent.getWidth(), true); }
    }

    static class StackLayout implements Composition.WidthLayout {
        final KitContext context;
        final Nodes.LayoutRole role;
        final boolean titlePair;
        java.util.Set<Component> captions = java.util.Set.of();

        StackLayout(KitContext context, Nodes.LayoutRole role) { this(context, role, false); }
        StackLayout(KitContext context, Nodes.LayoutRole role, boolean titlePair) { this(context,role,titlePair,java.util.Set.of()); }
        StackLayout(KitContext context, Nodes.LayoutRole role, boolean titlePair, java.util.Set<Component> captions) {
            this.captions=captions;
            this.titlePair=titlePair;
            this.context = context;
            this.role = role;
        }

        boolean horizontal() {
            return switch (role) {
                case INLINE, ACTIONS, FOOTER, NAVIGATION, SETTINGS_CONTROLS, JOINED_ACTIONS -> true;
                default -> false;
            };
        }

        int gap() {
            if(role==Nodes.LayoutRole.JOINED_ACTIONS)return 0;
            return ThemePainting.metric(
                    context,
                    role == Nodes.LayoutRole.DETAIL ? DesignMetrics.Role.SECTION_GAP : role == Nodes.LayoutRole.INLINE ? DesignMetrics.Role.INLINE_GAP
                            : role == Nodes.LayoutRole.ACTIONS
                            ? DesignMetrics.Role.BUTTON_GAP
                            : horizontal()
                                    ? DesignMetrics.Role.COLUMN_GAP
                                    : DesignMetrics.Role.ROW_GAP);
        }

        @Override
        public void addLayoutComponent(String name, Component component) {}

        @Override
        public void removeLayoutComponent(Component component) {}

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return measure(parent, false);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return measure(parent, true);
        }

        Dimension measure(Container parent, boolean minimum) {
            int width = 0, count = 0;
            for (Component child : parent.getComponents())
                if (child.isVisible()) {
                    Dimension size = minimum ? child.getMinimumSize() : child.getPreferredSize();
                    width = horizontal() ? width + size.width : Math.max(width, size.width);
                    count++;
                }
            Insets insets = parent.getInsets();
            width +=
                    insets.left
                            + insets.right
                            + (horizontal() ? Math.max(0, count - 1) * gap() : 0);
            return new Dimension(
                    width, height(parent, parent.getWidth() > 0 ? parent.getWidth() : width));
        }

        /** ACTIONS too wide for one row wrap onto further rows at their natural widths instead of being cut short. */
        List<List<Component>> rows(List<Component> children, int available) {
            var rows = new java.util.ArrayList<List<Component>>();
            var row = new java.util.ArrayList<Component>();
            int used = 0;
            for (Component child : children) {
                int natural = child.getPreferredSize().width;
                if (!row.isEmpty() && used + gap() + natural > available) { rows.add(row); row = new java.util.ArrayList<>(); used = 0; }
                used += (row.isEmpty() ? 0 : gap()) + natural;
                row.add(child);
            }
            if (!row.isEmpty()) rows.add(row);
            return rows;
        }

        boolean wraps(List<Component> children, int available) {
            if ((role != Nodes.LayoutRole.ACTIONS && role != Nodes.LayoutRole.SETTINGS_CONTROLS) || children.size() < 2) return false;
            int total = children.stream().mapToInt(c -> c.getPreferredSize().width).sum() + (children.size() - 1) * gap();
            return total > available;
        }

        int fittedWidth(Component child, int width, int count, int total) {
            int natural = child.getPreferredSize().width;
            int available = Math.max(0, width - Math.max(0, count - 1) * gap());
            return total > available && total > 0
                    ? (int) ((long) natural * available / total)
                    : natural;
        }

        /** Preserve fixed navigation controls before spending space on detail side padding. */
        Insets insets(Container parent, int width) {
            Insets padding=parent.getInsets();
            if(role!=Nodes.LayoutRole.DETAIL)return padding;
            int minimum=0;
            for(var child:parent.getComponents())
                if(child.isVisible() && child instanceof JPanel panel && (panel.getLayout() instanceof Navigation.InlineLayout || panel instanceof Cards.CardSurface))
                    minimum=Math.max(minimum,child.getMinimumSize().width);
            int side=Math.max(0,(width-minimum)/2);
            return new Insets(padding.top,Math.min(padding.left,side),padding.bottom,Math.min(padding.right,side));
        }

        @Override
        public int height(Container parent, int width) {
            Insets insets = insets(parent,width);
            int available = Math.max(0, width - insets.left - insets.right), height = 0;
            var children =
                    java.util.Arrays.stream(parent.getComponents())
                            .filter(Component::isVisible)
                            .toList();
            if (wraps(children, available)) {
                var rows = rows(children, available);
                for (var row : rows) height += row.stream().mapToInt(c -> Composition.height(c, Math.min(available, c.getPreferredSize().width))).max().orElse(0);
                return insets.top + insets.bottom + height + Math.max(0, rows.size() - 1) * gap();
            }
            int total =
                    horizontal()
                            ? children.stream().mapToInt(c -> c.getPreferredSize().width).sum()
                            : 0;
            Component previous = null;
            for (Component child : children) {
                if (!horizontal() && previous != null) height += verticalGap(previous, child);
                int childWidth =
                        horizontal()
                                ? fittedWidth(child, available, children.size(), total)
                                : available;
                int childHeight = Composition.height(child, childWidth);
                height = horizontal() ? Math.max(height, childHeight) : height + childHeight;
                previous = child;
            }
            return insets.top + insets.bottom + height;
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets insets = insets(parent,parent.getWidth());
            int x = insets.left, y = insets.top;
            int width = Math.max(0, parent.getWidth() - insets.left - insets.right),
                    height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
            var children =
                    java.util.Arrays.stream(parent.getComponents())
                            .filter(Component::isVisible)
                            .toList();
            if (wraps(children, width)) {
                for (var row : rows(children, width)) {
                    int rowHeight = 0, rowX = x;
                    for (Component child : row) {
                        int childWidth = Math.min(width, child.getPreferredSize().width), childHeight = Composition.height(child, childWidth);
                        child.setBounds(rowX, y, childWidth, childHeight);
                        rowX += childWidth + gap();
                        rowHeight = Math.max(rowHeight, childHeight);
                    }
                    y += rowHeight + gap();
                }
                return;
            }
            int total =
                    horizontal()
                            ? children.stream().mapToInt(c -> c.getPreferredSize().width).sum()
                            : 0;
            Component previous = null;
            for (Component child : children) {
                if (!horizontal() && previous != null) y += verticalGap(previous, child);
                int childWidth =
                        horizontal() ? fittedWidth(child, width, children.size(), total) : child instanceof Indicators.StrengthMeter ? Math.min(width, child.getPreferredSize().width) : width;
                int childHeight = Composition.height(child, childWidth);
                if (horizontal()) {
                    child.setBounds(
                            x,
                            y + Math.max(0, (height - childHeight) / 2),
                            childWidth,
                            Math.min(height, childHeight));
                    x += childWidth + gap();
                } else {
                    child.setBounds(x, y, childWidth, childHeight);
                    y += childHeight;
                }
                previous = child;
            }
        }
        int verticalGap(Component previous, Component child) {
            if (role == Nodes.LayoutRole.DETAIL && previous instanceof Cards.CardSurface && captions.contains(child)) return ThemePainting.metric(context, DesignMetrics.Role.DETAIL_METADATA_GAP);
            return titlePair ? ThemePainting.metric(context, DesignMetrics.Role.DETAIL_VALUE_GAP) : child instanceof Indicators.StrengthMeter
                ? ThemePainting.metric(context, DesignMetrics.Role.STRENGTH_TOP_GAP) : gap();
        }
    }
}
