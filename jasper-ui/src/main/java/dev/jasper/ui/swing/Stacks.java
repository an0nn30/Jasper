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
        final KitContext context;boolean joined,route;
        /** The stack's layout role, which a classic ghost button reads to know a toolbar row from an actions row. */
        Nodes.LayoutRole role;
        /** A classic route strip's cell starts and the hue of each visible step's edge (ClassicRouteFace, plan 4c). */
        int[] cells=new int[0];
        List<Nodes.Hue> hues=List.of();
        /** Each route step's hue by its component, so a hidden step takes its hue with it. */
        java.util.Map<Component,Nodes.Hue> stepHues=java.util.Map.of();
        StackSurface(KitContext context){this.context=context;}
        /** A ROUTE strip's arrow positions, left to right, as its layout placed them. */
        int[] arrows=new int[0];
        @Override protected void paintComponent(Graphics graphics){
            super.paintComponent(graphics);
            if(route){context.faces().route.paintStrip(this,graphics);return;}
            if(!joined)return;var g=(Graphics2D)graphics.create();
            try{ThemePainting.antialias(g);int radius=ThemePainting.metric(context,DesignMetrics.Role.CONTROL_RADIUS);g.setColor(ThemePainting.color(context,dev.jasper.ui.theme.SemanticColor.BORDER_CONTROL));g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,radius*2,radius*2);if(getComponentCount()>1)g.drawLine(getComponent(1).getX(),0,getComponent(1).getX(),getHeight());}finally{g.dispose();}
        }
        /** Overlapping halves of a joined pair (a face's {@code joinedOverlap}) repaint through this surface, the first on top. */
        @Override public boolean isOptimizedDrawingEnabled(){return !(joined&&context.faces().buttons.joinedOverlap()>0)&&super.isOptimizedDrawingEnabled();}
        /** The route strip: the input surface inside the input outline, and the connecting arrow before each later step. */
        void paintRoute(Graphics graphics){
            var g=(Graphics2D)graphics.create();
            try{
                ThemePainting.antialias(g);if(!isEnabled())g.setComposite(AlphaComposite.SrcOver.derive(.42f));
                int radius=ThemePainting.metric(context,DesignMetrics.Role.CONTROL_RADIUS),top=ThemePainting.metric(context,DesignMetrics.Role.ROUTE_TOP),h=getHeight()-top;
                g.setColor(ThemePainting.color(context,SemanticColor.SURFACE_INPUT));g.fillRoundRect(0,top,getWidth(),h,radius*2,radius*2);
                g.setColor(ThemePainting.color(context,SemanticColor.BORDER_INPUT));g.drawRoundRect(0,top,getWidth()-1,h-1,radius*2,radius*2);
                int size=ThemePainting.metric(context,DesignMetrics.Role.ROUTE_ARROW);
                var arrow=context.icons().icon("arrow.right",context.theme().semantic(),SemanticColor.TEXT_PLACEHOLDER,size);
                for(int x:arrows)arrow.paintIcon(this,g,x,top+(h-arrow.getIconHeight())/2);
            }finally{g.dispose();}
        }
    }

    static final class StackControl extends ControlRegistry.Control {
        final Tile tile;
        boolean detail, sheetHeading, panelDetail, embedded, settings, identityHeader, identityGroup, hint, settingsHeader, keycapDetail;
        /** An INLINE row among a DOCUMENT split's bars, assigned by the reconciler; see {@link DocumentBarLayout}. */
        boolean documentBar;
        /** The Run window's help row inked this PROMPT_HEADER's glyph (plan 8a); turning the flag off returns it. */
        boolean helpInked;
        /** A VERTICAL group of a DOCUMENT split's bars: its rows carry their own padding, so they stack without gaps. */
        boolean documentBarGroup;
        /** Components of Field and Text children, in order: the bar's fill candidates; Text ones also hold status. */
        java.util.Set<Component> barFields = java.util.Set.of(), barTexts = java.util.Set.of();
        /** The panel footer this vertical stack is (set by its panel, cleared on every update); null otherwise. */
        PanelFooters.Footer panelFooter;
        /** A ROUTE strip's step: a tile beside its lines (set by the reconciler, cleared on every update). */
        boolean routeStep;
        /**
         * A classic dock panel's one-line navigation row (an INLINE stack in its search slot under {@code PANEL_SEARCH_RULE},
         * set by the reconciler, cleared on every update): its dropdowns are named by their labels without a caption, and its
         * last control fills the width the others leave ({@link PanelRowLayout}; the SFTP Disconnected mock's Choose
         * Session… beside the root path, plan 10a).
         */
        boolean panelRow;
        /**
         * Children a classic face placed in another container on the last pass, by node id, with that container (an
         * ordered list's entry row in the list's pane, plan 4c). The face's span sets it before the stack mounts the rest;
         * the reconciler does not clear it, so its next detach pass leaves an adopted row where it is.
         */
        java.util.Map<String, Container> adopted = java.util.Map.of();
        /** The classic ordered list that last adopted this INLINE row as its entry row ({@link ClassicOrderedFace#span}), or null. */
        ItemLists.ListControl entryOf;
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
            // The Run window's help tile (Enterprise presentation plan 8a) is its own size.
            tile.sizeRole = view.runWindow() ? DesignMetrics.Role.RUN_WINDOW_TILE : DesignMetrics.Role.PROMPT_TILE;
            tile.run = view.runWindow();
            ((StackSurface)component).joined=stack.role()==Nodes.LayoutRole.JOINED_ACTIONS;
            ((StackSurface)component).route=stack.role()==Nodes.LayoutRole.ROUTE;
            ((StackSurface)component).role=stack.role();
            int detailPadding = stack.role() == Nodes.LayoutRole.DETAIL ? ThemePainting.metric(context(), panelDetail ? DesignMetrics.Role.PANEL_DETAIL_PADDING : DesignMetrics.Role.DETAIL_PADDING) : 0;
            component.setBorder(new javax.swing.border.EmptyBorder(detailPadding, detailPadding, panelDetail && stack.role()==Nodes.LayoutRole.DETAIL ? Math.max(0,detailPadding-ThemePainting.metric(context(),DesignMetrics.Role.FOCUS_RING)) : detailPadding, detailPadding));
            component.setLayout(
                    settingsHeader ? new SettingsHeaderLayout(context()) : stack.role() == Nodes.LayoutRole.ROUTE ? context().faces().route.layout(this)
                    : routeStep ? context().faces().route.stepLayout(this)
                    : panelFooter != null && stack.role() == Nodes.LayoutRole.VERTICAL ? new PanelFooters.StackLayout(this, panelFooter)
                    : stack.role() == Nodes.LayoutRole.SETTINGS_NAVIGATION ? new FillLayout() : stack.role() == Nodes.LayoutRole.SETTINGS_TRAILING ? new SettingsTrailingLayout(context()) : stack.role() == Nodes.LayoutRole.SETTINGS_GROUP_HEADER ? new SettingsGroupLayout(context()) : stack.role() == Nodes.LayoutRole.SETTINGS_FOOTER ? new SettingsFooterLayout(context()) : stack.role() == Nodes.LayoutRole.SETTINGS_PAGE ? new SettingsLayout(context()) : sheetHeading ? new Navigation.InlineLayout(context(),DesignMetrics.Role.COLUMN_GAP) : stack.role() == Nodes.LayoutRole.PROMPT_HEADER
                            ? view.runWindow() ? RunWindowPresentation.helpLayout(this) : context().faces().prompt.headerLayout(this)
                            : stack.role() == Nodes.LayoutRole.FOOTER ? new FooterStatusLayout(context()) : stack.role() == Nodes.LayoutRole.SUPPORTING ? new SupportingLayout(context()) : stack.role() == Nodes.LayoutRole.NAVIGATION ? new Navigation.InlineLayout(context(), hint ? DesignMetrics.Role.PALETTE_HINT_ACTION_GAP : identityHeader ? DesignMetrics.Role.IDENTITY_HEADER_GAP : panelDetail && stack.children().stream().anyMatch(n -> n instanceof Nodes.Icon icon && icon.size() == Nodes.IconSize.PROMPT) ? DesignMetrics.Role.PANEL_DETAIL_IDENTITY_GAP : detail && !panelDetail ? DesignMetrics.Role.DETAIL_HEADING_GAP : DesignMetrics.Role.ICON_GAP) : new StackLayout(context(), stack.role(), stack.children().size()==2 && stack.children().getFirst() instanceof Nodes.Text text && text.role()==Nodes.TextRole.DETAIL_TITLE, captions) {
                                @Override int gap() { return identityGroup ? ThemePainting.metric(context(),DesignMetrics.Role.IDENTITY_HEADER_AFTER) : keycapDetail && stack.role() == Nodes.LayoutRole.VERTICAL ? ThemePainting.metric(context(),DesignMetrics.Role.KEYCAP_DETAIL_GAP) : panelDetail ? ThemePainting.metric(context(), DesignMetrics.Role.PANEL_DETAIL_GAP) : super.gap(); }
                                @Override int verticalGap(Component previous, Component child) {
                                    if (panelDetail && stack.role() == Nodes.LayoutRole.VERTICAL && stack.children().stream().allMatch(n -> n instanceof Nodes.Text)) return 0;
                                    // A manager detail heading's name and type sit as one tight block centred on the tile.
                                    if (detail && titlePair) return ThemePainting.metric(context(), DesignMetrics.Role.DETAIL_TITLE_GAP);
                                    // A manager detail's captioned value (a caption over its value) keeps the detail rows' value gap.
                                    if (detail && stack.role() == Nodes.LayoutRole.VERTICAL && stack.children().size() == 2
                                        && stack.children().getFirst() instanceof Nodes.Text caption && caption.role() == Nodes.TextRole.CAPTION)
                                        return ThemePainting.metric(context(), DesignMetrics.Role.DETAIL_VALUE_GAP);
                                    return super.verticalGap(previous, child);
                                }
                            });
            if (component.getLayout() instanceof Navigation.InlineLayout inline) inline.titleLine = titleLine(stack);
            if (documentBar && stack.role() == Nodes.LayoutRole.INLINE) component.setLayout(new DocumentBarLayout(this));
            if (panelRow && stack.role() == Nodes.LayoutRole.INLINE) component.setLayout(new PanelRowLayout(context()));
            if (documentBarGroup && stack.role() == Nodes.LayoutRole.VERTICAL) component.setLayout(new Composition.Vertical(0));
            // An entry row a classic ordered list holds keeps the face's layout across refreshes (plan 9b Task 6).
            if (entryOf != null && entryOf.entry == component) component.setLayout(new ClassicOrderedFace.EntryLayout(entryOf));
            component.revalidate();
            component.repaint();
        }

        /** Whether {@code stack} is a line led by a title over a further line, without a tile, whose buttons the face stands on the title's line. */
        private boolean titleLine(Nodes.Stack stack) {
            return context().faces().buttons.titleLine() && stack.role() == Nodes.LayoutRole.NAVIGATION
                && !stack.children().isEmpty() && stack.children().stream().noneMatch(Nodes.Icon.class::isInstance)
                && stack.children().getFirst() instanceof Nodes.Stack first && first.role() == Nodes.LayoutRole.VERTICAL && first.children().size() > 1
                && first.children().getFirst() instanceof Nodes.Text title && title.role() == Nodes.TextRole.DETAIL_TITLE;
        }

        @Override
        Container childParent(Nodes.Node child) {
            var adopter = adopted.get(child.id());
            if (adopter != null) return adopter;
            return ((Nodes.Stack) node).role() == Nodes.LayoutRole.PROMPT_HEADER
                            && child instanceof Nodes.Icon
                    ? tile
                    : component;
        }

        @Override
        void children(List<ControlRegistry.Control> controls) {
            children = List.copyOf(controls);
            boolean prompt = ((Nodes.Stack) node).role() == Nodes.LayoutRole.PROMPT_HEADER;
            // The Run window's help row is the Run window's, whatever the kit's prompt face (plan 8a, R2).
            if (view.runWindow() && prompt) { RunWindowPresentation.helpChildren(controls); helpInked = true; }
            else {
                if (helpInked) { RunWindowPresentation.releaseHelpChildren(controls); helpInked = false; }
                if (prompt) context().faces().prompt.children(this, controls);
            }
            if(((Nodes.Stack)node).role()==Nodes.LayoutRole.SETTINGS_GROUP_HEADER)
                controls.forEach(c->{if(c instanceof Labels.IconControl icon)icon.tile(context().metrics().value(DesignMetrics.Role.SETTINGS_ICON_TILE),context().metrics().value(DesignMetrics.Role.SETTINGS_ICON_TILE),context().icons().size(context().metrics(), Nodes.IconSize.CONTROL),false);});
            if (embedded && settings) controls.forEach(c -> Composition.label(c, true));
            // A dropdown among actions (a file type filter beside Cancel and Open) is one action-height control: its
            // label is its accessible name, not a caption stacked above the row.
            if (((Nodes.Stack) node).role() == Nodes.LayoutRole.ACTIONS || panelRow)
                controls.forEach(c -> { if (c.node instanceof Nodes.Choice choice && choice.kind() == Nodes.ChoiceKind.DROPDOWN) Composition.label(c, true); });
            // The classic ordered face adopts an entry row into its list's pane (plan 4c) before the stack mounts the rest.
            adopted = java.util.Map.of();
            context().faces().ordered.span(this, controls);
            var wanted = new java.util.ArrayList<JComponent>();
            for (var c : controls) {
                var parent = childParent(c.node);
                if (parent == tile) {
                    mount(tile, List.of(c.component));
                    wanted.add(tile);
                } else if (parent == component) wanted.add(c.component);
            }
            mount(component, wanted);
            Separators.settle(controls);
            captions = controls.stream().filter(c -> c.node instanceof Nodes.Text text && text.role() == Nodes.TextRole.CAPTION).map(c -> (Component)c.component).collect(java.util.stream.Collectors.toSet());
            if (component.getLayout() instanceof StackLayout layout) layout.captions = captions;
            barFields = controls.stream().filter(c -> c.node instanceof Nodes.Field || c.node instanceof Nodes.Text).map(c -> (Component)c.component)
                .collect(java.util.stream.Collectors.toCollection(() -> java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>())));
            barTexts = controls.stream().filter(c -> c.node instanceof Nodes.Text).map(c -> (Component)c.component)
                .collect(java.util.stream.Collectors.toCollection(() -> java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>())));
            // The classic faces adopt what they draw (plan 4c): a route strip's hues, a route step's captions.
            if (((Nodes.Stack) node).role() == Nodes.LayoutRole.ROUTE) context().faces().route.strip(this, controls);
            if (routeStep) context().faces().route.step(this, controls);
        }

        /** Whether this ROUTE strip's steps lead with hue tiles (the default) rather than the classic hue edges. */
        boolean routeTiles() { return context().faces().route.tiles(); }
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
        /** The tile's size: the prompt tile, or the Run window's (set by its stack on every refresh). */
        DesignMetrics.Role sizeRole;
        /** The Run window's help tile (plan 8a): the accent tile with its edge (set by its stack on every refresh). */
        boolean run;

        Tile(KitContext context) {
            this(context, DesignMetrics.Role.PROMPT_TILE);
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
                if (run) RunWindowPresentation.paintTile(this, g);
                else context.faces().prompt.paintTile(this, g);
            } finally { g.dispose(); }
        }

        /** The control-tone tile and its edge (the default face). */
        void paintDefault(Graphics2D g) {
            ThemePainting.antialias(g);
            g.setColor(ThemePainting.color(context, SemanticColor.SURFACE_CONTROL));
            int radius = ThemePainting.metric(context, DesignMetrics.Role.PROMPT_TILE_RADIUS);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), radius * 2, radius * 2);
            g.setColor(ThemePainting.color(context, SemanticColor.BORDER_CONTROL));
            g.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, radius * 2, radius * 2);
        }
    }

    static final class PromptLayout implements Composition.WidthLayout {
        final StackControl owner;

        PromptLayout(StackControl owner) {
            this.owner = owner;
        }

        int gap(Component previous) {
            return ThemePainting.metric(
                    owner.context(),
                    previous == owner.tile
                            ? DesignMetrics.Role.PROMPT_TILE_GAP
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
        FooterStatusLayout(KitContext context) {this.context=context;inline=new SupportingLayout(context,DesignMetrics.Role.FOOTER_STATUS_GAP);vertical=new Composition.Vertical(ThemePainting.metric(context,DesignMetrics.Role.ICON_GAP));}
        Composition.WidthLayout layout(Container parent,int width) {
            var children=java.util.Arrays.stream(parent.getComponents()).filter(Component::isVisible).toList();
            int prefix=children.stream().limit(Math.max(0,children.size()-1)).mapToInt(c->c.getPreferredSize().width+ThemePainting.metric(context,DesignMetrics.Role.FOOTER_STATUS_GAP)).sum();
            return width<prefix+ThemePainting.metric(context,DesignMetrics.Role.FORM_LABEL_WIDTH)?vertical:inline;
        }
        @Override public int height(Container parent,int width){return layout(parent,width).height(parent,width);}
        @Override public Dimension preferredLayoutSize(Container parent){return inline.preferredLayoutSize(parent);}
        @Override public void layoutContainer(Container parent){layout(parent,parent.getWidth()).layoutContainer(parent);}
    }

    /** Natural leading adornments beside a flexible final body, aligned to its first line. */
    static final class SupportingLayout implements Composition.WidthLayout {
        private final KitContext context;
        private final DesignMetrics.Role gapRole;
        SupportingLayout(KitContext context) { this(context, DesignMetrics.Role.INLINE_GAP); }
        SupportingLayout(KitContext context, DesignMetrics.Role gapRole) { this.context = context; this.gapRole = gapRole; }
        private int gap() { return ThemePainting.metric(context, gapRole); }
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

    /**
     * A ROUTE strip: equal columns, one per step, left to right; every later column opens with the kit's arrow and its
     * step follows the arrow. A step's lines truncate within its column; the last step may run on into the strip's end
     * padding before it truncates.
     */
    static final class RouteLayout implements Composition.WidthLayout {
        final StackControl owner;
        RouteLayout(StackControl owner) { this.owner = owner; }
        int metric(DesignMetrics.Role role) { return ThemePainting.metric(owner.context(), role); }
        int edge() { return metric(DesignMetrics.Role.HAIRLINE) + metric(DesignMetrics.Role.ROUTE_PADDING); }
        /** What opens every column after the first: the arrow's inset, the arrow and its gap to the step. */
        int lead() { return metric(DesignMetrics.Role.ROUTE_ARROW_INSET) + metric(DesignMetrics.Role.ROUTE_ARROW) + metric(DesignMetrics.Role.ROUTE_ARROW_GAP); }
        List<Component> steps(Container parent) { return java.util.Arrays.stream(parent.getComponents()).filter(Component::isVisible).toList(); }
        @Override public void addLayoutComponent(String name, Component component) {}
        @Override public void removeLayoutComponent(Component component) {}
        @Override public int height(Container parent, int width) {
            // The strip stands a little apart from the rows above it.
            int tallest = steps(parent).stream().mapToInt(c -> c.getPreferredSize().height).max().orElse(0);
            return metric(DesignMetrics.Role.ROUTE_TOP) + Math.max(metric(DesignMetrics.Role.ROUTE_HEIGHT), tallest + 2 * (metric(DesignMetrics.Role.HAIRLINE) + metric(DesignMetrics.Role.ROUTE_VERTICAL_PADDING)));
        }
        int width(List<Component> steps, boolean minimum) {
            int column = steps.stream().mapToInt(c -> (minimum ? c.getMinimumSize() : c.getPreferredSize()).width).max().orElse(0) + (steps.size() > 1 ? lead() : 0);
            return 2 * edge() + column * steps.size();
        }
        @Override public Dimension preferredLayoutSize(Container parent) { var steps = steps(parent); int w = width(steps, false); return new Dimension(w, height(parent, w)); }
        @Override public Dimension minimumLayoutSize(Container parent) { var steps = steps(parent); int w = width(steps, true); return new Dimension(w, height(parent, w)); }
        @Override public void layoutContainer(Container parent) {
            var steps = steps(parent); var surface = (StackSurface) parent;
            int count = steps.size(), top = metric(DesignMetrics.Role.ROUTE_TOP), h = parent.getHeight() - top;
            int inner = Math.max(0, parent.getWidth() - 2 * edge()), end = parent.getWidth() - metric(DesignMetrics.Role.HAIRLINE);
            surface.arrows = new int[Math.max(0, count - 1)];
            for (int i = 0; i < count; i++) {
                int column = edge() + (int) ((long) inner * i / Math.max(1, count)), next = edge() + (int) ((long) inner * (i + 1) / Math.max(1, count));
                int x = column;
                if (i > 0) { surface.arrows[i - 1] = column + metric(DesignMetrics.Role.ROUTE_ARROW_INSET); x = column + lead(); }
                var step = steps.get(i);
                int natural = step.getPreferredSize().width, room = Math.max(0, (i == count - 1 ? end : next) - x);
                int sh = Math.min(h, step.getPreferredSize().height);
                step.setBounds(x, top + (h - sh) / 2, Math.min(natural, room), sh);
            }
        }
    }

    /** A route step: its leading tile beside its lines, stacked with no gap and centered on the tile. */
    static final class RouteStepLayout implements Composition.WidthLayout {
        final KitContext context;
        RouteStepLayout(KitContext context) { this.context = context; }
        int gap() { return ThemePainting.metric(context, DesignMetrics.Role.ROUTE_STEP_GAP); }
        List<Component> visible(Container parent) { return java.util.Arrays.stream(parent.getComponents()).filter(Component::isVisible).toList(); }
        boolean leading(List<Component> children) { return !children.isEmpty() && children.getFirst() instanceof JLabel label && label.getIcon() != null && (label.getText() == null || label.getText().isEmpty()); }
        @Override public void addLayoutComponent(String name, Component component) {}
        @Override public void removeLayoutComponent(Component component) {}
        int lead(List<Component> children) { return leading(children) ? children.getFirst().getPreferredSize().width + (children.size() > 1 ? gap() : 0) : 0; }
        List<Component> lines(List<Component> children) { return leading(children) ? children.subList(1, children.size()) : children; }
        @Override public int height(Container parent, int width) {
            var children = visible(parent);
            int text = lines(children).stream().mapToInt(c -> c.getPreferredSize().height).sum();
            return Math.max(leading(children) ? children.getFirst().getPreferredSize().height : 0, text);
        }
        @Override public Dimension preferredLayoutSize(Container parent) {
            var children = visible(parent);
            int text = lines(children).stream().mapToInt(c -> c.getPreferredSize().width).max().orElse(0);
            return new Dimension(lead(children) + text, height(parent, 0));
        }
        @Override public Dimension minimumLayoutSize(Container parent) {
            var children = visible(parent);
            return new Dimension(lead(children), height(parent, 0));
        }
        @Override public void layoutContainer(Container parent) {
            var children = visible(parent); if (children.isEmpty()) return;
            int h = parent.getHeight(), x = 0;
            if (leading(children)) {
                var tile = children.getFirst(); var size = tile.getPreferredSize();
                tile.setBounds(0, (h - size.height) / 2, size.width, size.height);
                x = lead(children);
            }
            var lines = lines(children);
            int y = (h - lines.stream().mapToInt(c -> c.getPreferredSize().height).sum()) / 2, width = Math.max(0, parent.getWidth() - x);
            for (var line : lines) { int lh = line.getPreferredSize().height; line.setBounds(x, y, width, lh); y += lh; }
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

    /**
     * The Settings header: history buttons, then the breadcrumb (ancestors, separators and the current page) at natural
     * widths, vertically centred. When the row is too narrow the ancestors elide first, then the current page.
     */
    static final class SettingsHeaderLayout implements Composition.WidthLayout {
        final KitContext context;
        SettingsHeaderLayout(KitContext context) { this.context = context; }
        /** A page without ancestors sets its title further from the history buttons than a breadcrumb starts. */
        int gap(int index, int count) { return ThemePainting.metric(context, index != 1 ? DesignMetrics.Role.SETTINGS_CRUMB_GAP : count == 2 ? DesignMetrics.Role.SETTINGS_HEADER_TITLE_GAP : DesignMetrics.Role.SETTINGS_HEADER_GAP); }
        List<Component> visible(Container parent) { return java.util.Arrays.stream(parent.getComponents()).filter(Component::isVisible).toList(); }
        @Override public int height(Container parent, int width) { return visible(parent).stream().mapToInt(c -> c.getPreferredSize().height).max().orElse(0); }
        @Override public Dimension preferredLayoutSize(Container parent) {
            var children = visible(parent); int width = 0;
            for (int i = 0; i < children.size(); i++) width += (i > 0 ? gap(i, children.size()) : 0) + children.get(i).getPreferredSize().width;
            return new Dimension(width, height(parent, width));
        }
        @Override public Dimension minimumLayoutSize(Container parent) { return new Dimension(0, height(parent, 0)); }
        @Override public void layoutContainer(Container parent) {
            var children = visible(parent); int[] widths = new int[children.size()]; int total = 0;
            for (int i = 0; i < children.size(); i++) { widths[i] = children.get(i).getPreferredSize().width; total += (i > 0 ? gap(i, children.size()) : 0) + widths[i]; }
            int excess = total - parent.getWidth(), last = children.size() - 1;
            // Ancestors give way first; the current page (the last text) only when they are gone.
            for (int pass = 0; pass < 2; pass++)
                for (int i = 1; i < children.size() && excess > 0; i++)
                    if ((pass == 0) == (i != last) && children.get(i) instanceof JLabel label && label.getIcon() == null) {
                        int cut = Math.min(excess, widths[i]); widths[i] -= cut; excess -= cut;
                    }
            int x = 0;
            for (int i = 0; i < children.size(); i++) {
                if (i > 0) x += gap(i, children.size());
                var child = children.get(i); int h = Math.min(parent.getHeight(), child.getPreferredSize().height);
                child.setBounds(x, (parent.getHeight() - h) / 2, Math.max(0, Math.min(widths[i], parent.getWidth() - x)), h);
                x += widths[i];
            }
        }
    }

    /**
     * A classic panel's one-line navigation row ({@link StackControl#panelRow}): as tall as the panel's search field, its
     * controls at their natural widths the inline gap apart and stretched to the row's height, the last one to the row's
     * end too.
     */
    static final class PanelRowLayout implements Composition.WidthLayout {
        final KitContext context;
        PanelRowLayout(KitContext context) { this.context = context; }
        int gap() { return ThemePainting.metric(context, DesignMetrics.Role.INLINE_GAP); }
        static List<Component> shown(Container parent) { return java.util.Arrays.stream(parent.getComponents()).filter(Component::isVisible).toList(); }
        @Override public Dimension preferredLayoutSize(Container parent) {
            var children = shown(parent); Insets insets = parent.getInsets();
            int width = children.stream().mapToInt(c -> c.getPreferredSize().width).sum() + Math.max(0, children.size() - 1) * gap();
            return new Dimension(width + insets.left + insets.right, height(parent, width));
        }
        @Override public int height(Container parent, int width) {
            Insets insets = parent.getInsets();
            int search = ThemePainting.height(context, DesignMetrics.Role.PANEL_SEARCH_HEIGHT, dev.jasper.ui.metrics.Typography.Role.BODY);
            return insets.top + insets.bottom + Math.max(search, shown(parent).stream().mapToInt(c -> c.getPreferredSize().height).max().orElse(0));
        }
        @Override public void layoutContainer(Container parent) {
            var children = shown(parent); Insets insets = parent.getInsets();
            int x = insets.left, end = parent.getWidth() - insets.right, height = parent.getHeight() - insets.top - insets.bottom;
            for (var child : children) {
                int natural = child.getPreferredSize().width;
                int w = child == children.getLast() ? Math.max(0, end - x) : Math.min(natural, Math.max(0, end - x));
                child.setBounds(x, insets.top, w, height);
                x += w + gap();
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
        /** The gap before the row's action; a control without one takes the catalog's empty gap (none where Reset is in the row's menu, spec 12.1). */
        int gap(Container p){return ThemePainting.metric(context,p.getComponentCount()>1?DesignMetrics.Role.COLUMN_GAP:DesignMetrics.Role.SETTINGS_TRAILING_EMPTY_GAP);}
        int action(Container p){return p.getComponentCount()>1?p.getComponent(1).getPreferredSize().width:0;}
        @Override public Dimension preferredLayoutSize(Container p){if(p.getComponentCount()==0)return new Dimension();var c=p.getComponent(0).getPreferredSize();return new Dimension(c.width+gap(p)+action(p),c.height);}
        @Override public int height(Container p,int width){return p.getComponentCount()==0?0:Composition.height(p.getComponent(0),Math.max(0,width-gap(p)-action(p)));}
        /** A switch's track aligns with the column's other controls; its focus halo lies in the gap before the action. */
        int halo(Container p){return p.getComponent(0) instanceof Switches.SwitchButton button && !button.face.leads()?ThemePainting.metric(context,DesignMetrics.Role.FOCUS_RING):0;}
        @Override public void layoutContainer(Container p){if(p.getComponentCount()==0)return;int a=action(p),w=Math.max(0,p.getWidth()-gap(p)-a);p.getComponent(0).setBounds(halo(p),0,w,p.getHeight());if(p.getComponentCount()>1)p.getComponent(1).setBounds(w+gap(p),0,a,p.getHeight());}
        @Override public void addLayoutComponent(String n,Component c){} @Override public void removeLayoutComponent(Component c){}
    }
    /** A Settings group card's heading row: owner tile and name at the leading edge, its count at the trailing edge. */
    static final class SettingsGroupLayout implements Composition.WidthLayout {
        final KitContext context;SettingsGroupLayout(KitContext context){this.context=context;}
        int pad(){return ThemePainting.metric(context,DesignMetrics.Role.SETTINGS_ROW_SIDE);}
        int gap(){return ThemePainting.metric(context,DesignMetrics.Role.SETTINGS_ICON_TEXT_GAP);}
        // The row starts on the card's top stroke (or the line above it), which it counts in its height.
        int top(){return ThemePainting.metric(context,DesignMetrics.Role.HAIRLINE);}
        @Override public Dimension preferredLayoutSize(Container p){int w=pad()*2,h=ThemePainting.metric(context,DesignMetrics.Role.SETTINGS_GROUP_HEIGHT);for(var c:p.getComponents()){w+=c.getPreferredSize().width+gap();h=Math.max(h,c.getPreferredSize().height+2*gap()+top());}return new Dimension(w,h);}
        @Override public int height(Container p,int width){return preferredLayoutSize(p).height;}
        @Override public void layoutContainer(Container p){int x=pad(),top=top();for(int i=0;i<p.getComponentCount();i++){var c=p.getComponent(i);var d=c.getPreferredSize();int w=Math.min(d.width,Math.max(0,p.getWidth()-x-pad()));c.setBounds(i==p.getComponentCount()-1?Math.max(x,p.getWidth()-pad()-w):x,top+(p.getHeight()-top-d.height)/2,w,d.height);x+=w+gap();}}
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
                if (nested.getLayout() instanceof SettingRows.RowLayout row && row.fixedControl()) width = Math.max(width, row.fixedWidth(nested));
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
            if(role==Nodes.LayoutRole.JOINED_ACTIONS)return -context.faces().buttons.joinedOverlap();
            return ThemePainting.metric(
                    context,
                    role == Nodes.LayoutRole.DETAIL ? DesignMetrics.Role.DETAIL_SECTION_GAP : role == Nodes.LayoutRole.INLINE ? DesignMetrics.Role.INLINE_GAP
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

        /**
         * ACTIONS too wide for one row wrap onto further rows at their natural widths instead of being cut short. A
         * separator's rule never starts or ends a row: a rule at a break is left out (it gets no bounds).
         */
        List<List<Component>> rows(List<Component> children, int available) {
            var rows = new java.util.ArrayList<List<Component>>();
            var row = new java.util.ArrayList<Component>();
            int used = 0;
            for (Component child : children) {
                int natural = child.getPreferredSize().width;
                boolean rule = child instanceof Separators.Rule;
                if (rule && row.isEmpty()) continue;
                if (!row.isEmpty() && used + gap() + natural > available) {
                    if (rule) continue;
                    if (row.getLast() instanceof Separators.Rule) row.removeLast();
                    rows.add(row); row = new java.util.ArrayList<>(); used = 0;
                }
                used += (row.isEmpty() ? 0 : gap()) + natural;
                row.add(child);
            }
            if (!row.isEmpty() && row.getLast() instanceof Separators.Rule) row.removeLast();
            if (!row.isEmpty()) rows.add(row);
            return rows;
        }

        boolean wraps(List<Component> children, int available) {
            if ((role != Nodes.LayoutRole.ACTIONS && role != Nodes.LayoutRole.SETTINGS_CONTROLS) || children.size() < 2) return false;
            int total = children.stream().mapToInt(c -> c.getPreferredSize().width).sum() + (children.size() - 1) * gap();
            return total > available;
        }

        /** The natural widths of the separators' rules among {@code children}: a squeezed row keeps them whole. */
        static int rules(List<Component> children) {
            return children.stream().filter(c -> c instanceof Separators.Rule).mapToInt(c -> c.getPreferredSize().width).sum();
        }

        /** {@code child}'s width in a row of {@code count} items whose other items total {@code total} beside {@code rules}. */
        int fittedWidth(Component child, int width, int count, int total, int rules) {
            int natural = child.getPreferredSize().width;
            if (child instanceof Separators.Rule) return natural;
            int available = Math.max(0, width - Math.max(0, count - 1) * gap() - rules);
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
            int rules = horizontal() ? rules(children) : 0;
            int total =
                    horizontal()
                            ? children.stream().mapToInt(c -> c.getPreferredSize().width).sum() - rules
                            : 0;
            Component previous = null;
            for (Component child : children) {
                if (!horizontal() && previous != null) height += verticalGap(previous, child);
                int childWidth =
                        horizontal()
                                ? fittedWidth(child, available, children.size(), total, rules)
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
                for (Component child : children) if (child instanceof Separators.Rule) child.setBounds(0, 0, 0, 0);
                for (var row : rows(children, width)) {
                    int rowHeight = 0, rowX = x;
                    for (Component child : row) rowHeight = Math.max(rowHeight, Composition.height(child, Math.min(width, child.getPreferredSize().width)));
                    for (Component child : row) {
                        int childWidth = Math.min(width, child.getPreferredSize().width), childHeight = Composition.height(child, childWidth);
                        // A rule is centred in its row; the other items keep the row's top.
                        child.setBounds(rowX, child instanceof Separators.Rule ? y + (rowHeight - childHeight) / 2 : y, childWidth, childHeight);
                        rowX += childWidth + gap();
                    }
                    y += rowHeight + gap();
                }
                return;
            }
            int rules = horizontal() ? rules(children) : 0;
            int total =
                    horizontal()
                            ? children.stream().mapToInt(c -> c.getPreferredSize().width).sum() - rules
                            : 0;
            Component previous = null;
            for (Component child : children) {
                if (!horizontal() && previous != null) y += verticalGap(previous, child);
                int childWidth =
                        horizontal() ? fittedWidth(child, width, children.size(), total, rules) : child instanceof Indicators.StrengthMeter ? Math.min(width, child.getPreferredSize().width) : width;
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
            return titlePair ? ThemePainting.metric(context, DesignMetrics.Role.TITLE_PAIR_GAP) : child instanceof Indicators.StrengthMeter
                ? ThemePainting.metric(context, DesignMetrics.Role.STRENGTH_TOP_GAP) : gap();
        }
    }

    /**
     * One compact row of a document's bars (a find or replace row, a notice, a command strip): inset by the bar padding,
     * the first visible field or text fills the width the controls after it leave, other text keeps a stable status
     * width so its neighbours do not move as it changes, and buttons keep their natural widths, closer together when
     * adjacent. Rows of one vertical group align their fills' trailing edges, so a replace field lines up under its find
     * field. A row too narrow for its fill's minimum keeps the fill and clips what follows at the trailing edge.
     */
    static final class DocumentBarLayout implements Composition.WidthLayout {
        final StackControl owner;
        DocumentBarLayout(StackControl owner) { this.owner = owner; }
        int metric(DesignMetrics.Role role) { return ThemePainting.metric(owner.context(), role); }
        List<Component> visible(Container parent) { return java.util.Arrays.stream(parent.getComponents()).filter(Component::isVisible).toList(); }
        Component fill(List<Component> children) {
            for (Component child : children) if (owner.barFields.contains(child)) return child;
            return null;
        }
        int natural(Component child, Component fill) {
            int width = child.getPreferredSize().width;
            // A field that does not fill takes the catalog's search width where it has one (plan 10b Rulings 4).
            int search = metric(DesignMetrics.Role.DOCUMENT_BAR_SEARCH_WIDTH);
            if (search > 0 && child != fill && owner.barFields.contains(child) && !owner.barTexts.contains(child)) return search;
            return owner.barTexts.contains(child) && child != fill ? Math.max(width, metric(DesignMetrics.Role.DOCUMENT_BAR_STATUS_WIDTH)) : width;
        }
        int gap(Component before, Component after) {
            return metric(before instanceof AbstractButton && after instanceof AbstractButton ? DesignMetrics.Role.DOCUMENT_BAR_ACTION_GAP : DesignMetrics.Role.DOCUMENT_BAR_GAP);
        }
        /** The width the children after the fill take, with their gaps; -1 without a fill. */
        int trailing(Container parent) {
            var children = visible(parent); Component fill = fill(children);
            if (fill == null) return -1;
            int width = 0;
            for (int i = children.indexOf(fill) + 1; i < children.size(); i++) width += gap(children.get(i - 1), children.get(i)) + natural(children.get(i), fill);
            return width;
        }
        /** The trailing width this row's fill leaves: the widest among the visible rows of its vertical group. */
        int aligned(Container parent) {
            int own = trailing(parent);
            if (own < 0 || !(parent.getParent() instanceof StackSurface group)) return own;
            int widest = own;
            for (Component sibling : group.getComponents())
                if (sibling != parent && sibling.isVisible() && sibling instanceof Container row && row.getLayout() instanceof DocumentBarLayout bar)
                    widest = Math.max(widest, bar.trailing(row));
            return widest;
        }
        @Override public int height(Container parent, int width) {
            int height = 0;
            for (Component child : visible(parent)) height = Math.max(height, child.getPreferredSize().height);
            Insets insets = parent.getInsets();
            return insets.top + insets.bottom + height + 2 * metric(DesignMetrics.Role.DOCUMENT_BAR_VERTICAL_PADDING);
        }
        @Override public Dimension preferredLayoutSize(Container parent) {
            var children = visible(parent); Component fill = fill(children); Insets insets = parent.getInsets();
            int width = insets.left + insets.right + 2 * metric(DesignMetrics.Role.DOCUMENT_BAR_SIDE_PADDING);
            for (int i = 0; i < children.size(); i++) width += (i > 0 ? gap(children.get(i - 1), children.get(i)) : 0) + natural(children.get(i), fill);
            return new Dimension(width, height(parent, parent.getWidth() > 0 ? parent.getWidth() : width));
        }
        @Override public void layoutContainer(Container parent) {
            var children = visible(parent); if (children.isEmpty()) return;
            Component fill = fill(children); Insets insets = parent.getInsets();
            int side = metric(DesignMetrics.Role.DOCUMENT_BAR_SIDE_PADDING), top = insets.top + metric(DesignMetrics.Role.DOCUMENT_BAR_VERTICAL_PADDING);
            int left = insets.left + side, right = parent.getWidth() - insets.right - side, inner = Math.max(0, parent.getHeight() - top - insets.bottom - metric(DesignMetrics.Role.DOCUMENT_BAR_VERTICAL_PADDING));
            int x = left;
            for (int i = 0; i < children.size(); i++) {
                Component child = children.get(i);
                if (i > 0) x += gap(children.get(i - 1), child);
                int width = natural(child, fill);
                if (child == fill) {
                    int minimum = Math.min(metric(DesignMetrics.Role.DOCUMENT_BAR_FILL_MIN_WIDTH), Math.max(0, right - x));
                    width = Math.max(minimum, right - aligned(parent) - x);
                }
                width = Math.max(0, Math.min(width, right - x));
                int height = Math.min(inner, child.getPreferredSize().height);
                child.setBounds(x, top + (inner - height) / 2, width, height);
                x += width;
            }
        }
    }
}
