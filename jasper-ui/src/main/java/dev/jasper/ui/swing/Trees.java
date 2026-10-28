package dev.jasper.ui.swing;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes;

import java.util.*;
import java.util.List;

/** Flatten only expanded branches; expansion intent carries both owner and local item id. */
final class Trees {
    private Trees() {}

    static void validateDetail(Nodes.Node node, java.util.Set<String> icons) {
        if(node instanceof Nodes.Custom)throw new IllegalArgumentException("Tree detail requires standard descriptors");
        switch(node) {
            case Nodes.Icon icon -> requireIcon(icon.name(),icons);
            case Nodes.Button button -> optionalIcon(button.icon(),icons);
            case Nodes.MenuButton menu -> {optionalIcon(menu.icon(),icons);validateMenus(menu.items(),icons);}
            case Nodes.Choice choice -> {
                optionalIcon(choice.icon(),icons);choice.face().ifPresent(face->optionalIcon(face.icon(),icons));
                choice.options().forEach(option->optionalIcon(option.icon(),icons));
            }
            case Nodes.PickerMenu menu -> menu.items().forEach(item->optionalIcon(item.icon(),icons));
            case Nodes.Header header -> validateMenus(header.overflow(),icons);
            case Nodes.ItemList list -> {
                validateMenus(list.contextActions(),icons);
                for(var item:list.items()) {
                    item.contextActions().ifPresent(menu->validateMenus(menu,icons));
                    item.leading().ifPresent(child->validateDetail(child,icons));
                    item.title().forEach(child->validateDetail(child,icons));
                    item.subtitle().forEach(child->validateDetail(child,icons));
                    item.trailing().forEach(child->validateDetail(child,icons));
                }
            }
            case Nodes.Table table -> {
                validateMenus(table.contextActions(),icons);
                for(var row:table.rows()) {
                    row.icon().ifPresent(name->requireIcon(name,icons));
                    row.contextActions().ifPresent(menu->validateMenus(menu,icons));
                    row.cellNodes().values().forEach(child->validateDetail(child,icons));
                }
                table.empty().ifPresent(child->validateDetail(child,icons));
            }
            case Nodes.Tree tree -> tree.roots().forEach(item->validateTreeItem(item,icons));
            case Nodes.Breadcrumbs crumbs -> crumbs.crumbs().forEach(crumb->validateMenus(crumb.contextActions(),icons));
            default -> {}
        }
        ControlRegistry.children(node).forEach(child->validateDetail(child,icons));
    }
    private static void requireIcon(String name,java.util.Set<String> icons) {
        if(!icons.contains(name))throw new IllegalArgumentException("Unknown icon: "+name);
    }
    private static void optionalIcon(String name,java.util.Set<String> icons) {
        if(!name.isEmpty())requireIcon(name,icons);
    }
    private static void validateMenus(java.util.List<dev.jasper.ui.model.MenuItem> items,java.util.Set<String> icons) {
        items.stream().filter(item->!item.icon().isBlank()).forEach(item->requireIcon(item.icon(),icons));
    }
    private static void validateTreeItem(dev.jasper.ui.model.TreeItem item,java.util.Set<String> icons) {
        item.icon().ifPresent(name->requireIcon(name,icons));
        item.contextActions().ifPresent(menu->validateMenus(menu,icons));
        item.children().forEach(child->validateTreeItem(child,icons));
    }
    static final class TreePane extends OverlayScrollPane {
        final KitContext context; TreeControl owner;
        TreePane(KitContext context){super(context,false);this.context=context;}
        @Override protected void paintComponent(java.awt.Graphics graphics) {
            if(owner==null || owner.presentation.isEmpty()){super.paintComponent(graphics);return;}
            var g=(java.awt.Graphics2D)graphics.create();
            try {
                ThemePainting.antialias(g);int radius=ThemePainting.metric(context,dev.jasper.ui.metrics.DesignMetrics.Role.CARD_RADIUS)*2;
                g.setColor(ThemePainting.color(context,dev.jasper.ui.theme.SemanticColor.SURFACE_CARD));g.fillRoundRect(0,0,getWidth(),getHeight(),radius,radius);
                g.setColor(ThemePainting.color(context,dev.jasper.ui.theme.SemanticColor.BORDER_SUBTLE));g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,radius,radius);
            }finally{g.dispose();}
        }
    }
    record Visible(TreeItem item, int depth) {}

    /**
     * A column tree's header: the item column's title from the rows' content inset, then each later title right-aligned
     * over its trailing column ({@link TreeControl#columnWidths}), packed against the count column at the rows' trailing
     * inset, as the rows pack them.
     */
    static final class ColumnHeaderLayout implements java.awt.LayoutManager {
        final TreeControl owner;
        ColumnHeaderLayout(TreeControl owner) { this.owner = owner; }
        @Override public void addLayoutComponent(String name, java.awt.Component component) {}
        @Override public void removeLayoutComponent(java.awt.Component component) {}
        @Override public java.awt.Dimension preferredLayoutSize(java.awt.Container parent) {
            int height = 0;
            for (var child : parent.getComponents()) height = Math.max(height, child.getPreferredSize().height);
            var insets = parent.getInsets();
            return new java.awt.Dimension(insets.left + insets.right, height + insets.top + insets.bottom);
        }
        @Override public java.awt.Dimension minimumLayoutSize(java.awt.Container parent) { return preferredLayoutSize(parent); }
        @Override public void layoutContainer(java.awt.Container parent) {
            var insets = parent.getInsets();
            var children = parent.getComponents();
            int height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
            if (owner.laned()) {
                // Plan 9b Rulings 7: the titles span the rows' lanes, measured from the header's end as the rows measure theirs;
                // the Action title starts over the sections' disclosure, as the mock sets it.
                int w = parent.getWidth(), scope = owner.scopeLaneStart(w), shortcut = owner.shortcutLaneStart(w);
                int first = ThemePainting.metric(owner.context(), dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_GROUP_SIDE);
                int[][] lanes = {{first, scope - first}, {scope, shortcut - scope}, {shortcut, w - shortcut}};
                for (int i = 0; i < children.length && i < lanes.length; i++) {
                    children[i].setBounds(lanes[i][0], insets.top, Math.max(0, lanes[i][1]), height);
                    if (children[i] instanceof javax.swing.JLabel label) label.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
                }
                return;
            }
            int[] sizes = owner.columnWidths(), starts = owner.trailingStarts(parent.getWidth(), sizes);
            for (int i = 1; i < children.length && i - 1 < sizes.length; i++) children[i].setBounds(starts[i - 1], insets.top, sizes[i - 1], height);
            if (children.length > 0) children[0].setBounds(insets.left, insets.top, Math.max(0, owner.titleEnd(parent.getWidth(), sizes) - insets.left), height);
        }
    }

    static final class TreeControl extends InteractiveRows.RowsControl {
        boolean settingsNavigation;
        final List<Visible> visible = new ArrayList<>();
        final Set<String> itemIds = new HashSet<>();
        final Map<String, TreeItem> items = new HashMap<>();
        /** Each trailing column's width, measured over every item so the columns line up from row to row. */
        int[] trailingWidths = new int[0];
        /** A column tree's header (Tree.columns): the item column's title and one title over each trailing column. */
        final javax.swing.JPanel header = new Tables.HeaderBand(this);
        /** A column tree's count column: as wide as its widest count, so every row's values line up; zero without counts. */
        int countWidth;
        /** A column tree's minimum width for each column (the item column's too): its title's preferred width. */
        int[] minimums = new int[0];
        /** The widths the user dragged this view's columns to, unscaled, by column id; they outrank the columns' own. */
        final Map<String, Integer> kept = new HashMap<>();

        TreeControl(KitView view, Nodes.Tree node) {
            super(view, node, new TreePane(view.context()));
            ((TreePane)scroll).owner=this;
            var box=context().faces().tree.box(this);
            if(box!=null)scroll.setBorder(box);
            refresh();
            ColumnResizer.install(this, header, new Resizing());
            InteractiveRows.bind(canvas, "RIGHT", () -> expandFocused(true));
            InteractiveRows.bind(canvas, "LEFT", () -> expandFocused(false));
        }

        Map<String,TreeRowPresentation> presentation=Map.of();
        TreeDetailHandle detail;
        TreeDetailHandle attach(String itemId,ViewSpec spec,java.util.function.Consumer<UiEvent> events,Runnable detached) {
            if(detail!=null)throw new IllegalStateException("Tree already has a detail");
            var item=items.get(itemId);
            if(item==null || !item.leaf() || indexOf(itemId)<0)throw new IllegalArgumentException("Detail requires a visible leaf");
            validateDetail(spec.root(),context().icons().names());
            var body=new KitView(context(),spec,events);
            if(!presentation.isEmpty())body.markKeycapDetail();
            detail=new TreeDetailHandle(this,itemId,body,detached);
            body.inheritedEnabled(interactive());canvas.add(body);detailChanged();return detail;
        }
        void detailChanged(){measureRows();canvas.revalidate();sync();canvas.repaint();}
        /** Today's keycap detail: a rounded card from the row's top, in the control surface, edged in the focus colour. */
        void paintDetailCard(java.awt.Graphics2D g, int index, int width) {
            ThemePainting.antialias(g);
            int inset=ThemePainting.metric(context(),dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_DETAIL_INSET),radius=ThemePainting.metric(context(),dev.jasper.ui.metrics.DesignMetrics.Role.CONTROL_RADIUS)*2;
            g.setColor(ThemePainting.color(context(),dev.jasper.ui.theme.SemanticColor.SURFACE_CONTROL));
            int card=Math.max(0,heightAt(index)-detailAfter());
            g.fillRoundRect(inset,top(index),Math.max(0,width-2*inset),card,radius,radius);
            g.setColor(ThemePainting.color(context(),dev.jasper.ui.theme.SemanticColor.BORDER_FOCUS));
            g.drawRoundRect(inset,top(index),Math.max(0,width-2*inset-1),Math.max(0,card-1),radius,radius);
        }
        void detach(TreeDetailHandle handle) {
            if(!handle.attached)return;
            handle.attached=false;
            if(detail==handle)detail=null;
            var focused=java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            boolean returnFocus=focused!=null && javax.swing.SwingUtilities.isDescendingFrom(focused,handle.body);
            canvas.remove(handle.body);
            if(returnFocus)canvas.requestFocusInWindow();
            try {handle.body.close();} finally {try {handle.detached.run();}finally{if(!closed)detailChanged();}}
        }
        private int detailInset(){return ThemePainting.metric(context(),dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_DETAIL_INSET);}
        // A keycap tree's editor sits just below its row and keeps more room beneath it, inside the edit card.
        private int detailTop(){return presentation.isEmpty()?detailInset():ThemePainting.metric(context(),dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_DETAIL_TOP);}
        private int detailBottom(){return presentation.isEmpty()?detailInset():ThemePainting.metric(context(),dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_DETAIL_BOTTOM);}
        private int detailTrailing(){return presentation.isEmpty()?detailInset():ThemePainting.metric(context(),dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_CONTENT_TRAILING);}
        /** Room between a keycap tree's edit card and the next row; the card stops short of it. */
        int detailAfter(){return presentation.isEmpty()?0:ThemePainting.metric(context(),dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_DETAIL_AFTER);}
        private int detailLeading(){return presentation.isEmpty()?detailInset():ThemePainting.metric(context(),dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_DETAIL_LEADING);}
        private int detailHeight() {
            if(detail==null)return 0;
            int width=Math.max(1,scroll.getViewport().getExtentSize().width-detailLeading()-detailTrailing());
            return detail.body.getComponentCount()==0?0:Composition.height(detail.body.getComponent(0),width)+detailTop()+detailBottom()+detailAfter();
        }
        /** The Settings navigation's sections collapse behind a disclosure triangle (Enterprise presentation spec 12.1). */
        boolean collapsibleNavigation;
        @Override int minimumRowHeight(){return settingsNavigation?ThemePainting.height(context(),dev.jasper.ui.metrics.DesignMetrics.Role.SETTINGS_NAV_ROW,dev.jasper.ui.metrics.Typography.Role.SETTINGS_BODY):presentation.isEmpty()?0:ThemePainting.height(context(),dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_TREE_ROW_HEIGHT,dev.jasper.ui.metrics.Typography.Role.BODY);}
        @Override boolean uniformRows(){return !settingsNavigation && !rail() && detail==null && presentation.isEmpty();}
        @Override java.awt.Color hoverFill(){return context().faces().tree.hover(context());}
        private int keyWidth(int index,int width) {
            var value=presentation.get(itemId(index));
            if(value==null || value.keycaps().isEmpty())return 0;
            var caps=new Keycaps(context(),value.keycaps().get());
            int available=Math.max(0,width-ThemePainting.metric(context(),dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_TREE_LEAF_INDENT)-2*ThemePainting.metric(context(),dev.jasper.ui.metrics.DesignMetrics.Role.ICON_GAP));
            return Math.min(available,Math.min(caps.getIconWidth(),Math.max(caps.minimumWidth(),available/2)));
        }
        /** A keycap tree with columns (the classic Keymap) lays every row on its header's lanes (plan 9b Rulings 7). */
        boolean laned(){return !presentation.isEmpty() && !value().columns().isEmpty();}
        private int laneMetric(dev.jasper.ui.metrics.DesignMetrics.Role role){return ThemePainting.metric(context(),role);}
        /** The Scope lane's start for a row or header {@code width} wide: the Shortcut and Scope lanes back from its end. */
        int scopeLaneStart(int width){return Math.max(0,width-laneMetric(dev.jasper.ui.metrics.DesignMetrics.Role.KEYMAP_SHORTCUT_COLUMN)-laneMetric(dev.jasper.ui.metrics.DesignMetrics.Role.KEYMAP_SCOPE_COLUMN));}
        /** The Shortcut lane's start for a row or header {@code width} wide. */
        int shortcutLaneStart(int width){return Math.max(0,width-laneMetric(dev.jasper.ui.metrics.DesignMetrics.Role.KEYMAP_SHORTCUT_COLUMN));}
        private static java.awt.Component named(java.awt.Component[] children,String... names){
            for(var name:names)for(var child:children)if(name.equals(child.getName()))return child;
            return null;
        }
        /**
         * Title in the Action lane, scope right-aligned before the Shortcut lane, keys or a section's count in it; the trailing
         * slot spans from the section's modified mark (after its title) or the Scope lane to the row's end.
         */
        private boolean layoutLanes(InteractiveRows.Row row){
            int width=row.getWidth(),height=row.getHeight();
            int pad=laneMetric(dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_GROUP_SIDE),start=Math.min(width,pad+row.indent);
            int lw=row.leading.getComponentCount()==0?0:row.leading.getPreferredSize().width;
            row.leading.setBounds(start,0,lw,height);row.leading.doLayout();
            int titleX=start+lw+(lw>0?laneMetric(dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_GROUP_TEXT_GAP):0);
            int scope=scopeLaneStart(width),shortcut=shortcutLaneStart(width);
            row.title.setBounds(titleX,0,Math.max(0,scope-titleX),height);row.title.doLayout();
            row.subtitle.setBounds(0,0,0,0);
            var children=row.trailing.getComponents();
            for(var child:children)child.setBounds(0,0,0,0);
            String base="@tree/"+node.id()+"/"+itemId(row.index)+"/";
            var keys=named(children,base+"keys",base+"trailing/1");
            var where=named(children,base+"context",base+"trailing/0");
            var mark=named(children,base+"modified");
            var markSize=mark==null?null:mark.getPreferredSize();
            int markX=mark==null?scope:Math.max(0,Math.min(scope-markSize.width,titleX+Math.min(row.title.getWidth(),row.title.getPreferredSize().width)+laneMetric(dev.jasper.ui.metrics.DesignMetrics.Role.ICON_GAP)));
            int left=Math.min(scope,markX);
            row.trailing.setBounds(left,0,width-left,height);
            int keysX=shortcut+laneMetric(dev.jasper.ui.metrics.DesignMetrics.Role.KEYMAP_KEYS_INSET);
            if(keys!=null){keys.setBounds(keysX-left,0,Math.max(0,width-keysX),height);if(keys instanceof javax.swing.JLabel label)label.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);}
            if(where!=null){where.setBounds(scope-left,0,Math.max(0,shortcut-laneMetric(dev.jasper.ui.metrics.DesignMetrics.Role.KEYMAP_SCOPE_GAP)-scope),height);
                if(where instanceof javax.swing.JLabel label)label.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);}
            // The scope and a section's count stand on the title's baseline, as the mock sets them; keycaps stay centred.
            int baseline=titleBaseline(row);
            for(var lane:new java.awt.Component[]{where,presentation.containsKey(itemId(row.index))?null:keys})
                if(baseline>=0 && lane instanceof javax.swing.JLabel label && label.getIcon()==null){
                    int h=Math.min(height,label.getPreferredSize().height),b=label.getBaseline(label.getWidth(),h);
                    if(b>=0)label.setBounds(label.getX(),Math.max(0,Math.min(height-h,baseline-b)),label.getWidth(),h);
                }
            if(mark!=null)mark.setBounds(markX-left,(height-markSize.height)/2,markSize.width,markSize.height);
            return true;
        }
        /** The baseline of {@code row}'s title text in the row, or -1. */
        private static int titleBaseline(InteractiveRows.Row row){
            for(var child:row.title.getComponents())if(child instanceof javax.swing.JLabel label && label.getHeight()>0){
                int b=label.getBaseline(label.getWidth(),label.getHeight());
                if(b>=0)return javax.swing.SwingUtilities.convertPoint(label,0,b,row).y;
            }
            return -1;
        }
        boolean layoutPresented(InteractiveRows.Row row) {
            if(laned())return layoutLanes(row);
            var value=presentation.get(itemId(row.index));if(value==null)return false;
            int pad=ThemePainting.metric(context(),dev.jasper.ui.metrics.DesignMetrics.Role.ICON_GAP);
            int start=Math.min(row.getWidth(),pad+row.indent),end=Math.max(start,row.getWidth()-detailTrailing()),height=row.getHeight();
            row.leading.setBounds(0,0,0,0);
            int keys=keyWidth(row.index,row.getWidth());
            if(value.keycaps().isEmpty())keys=Math.min(row.trailing.getComponent(row.trailing.getComponentCount()-1).getPreferredSize().width,Math.max(0,(end-start)/2));
            // Keys right-align in a shared column, so every row's context ends at the same place.
            int column=Math.max(keys,Math.min(ThemePainting.metric(context(),dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_KEYS_COLUMN),Math.max(0,(end-start)/2)));
            int lane=column+ThemePainting.metric(context(),dev.jasper.ui.metrics.DesignMetrics.Role.COLUMN_GAP);
            var children=row.trailing.getComponents();
            int natural=java.util.Arrays.stream(children).limit(children.length-1).mapToInt(c->c.getPreferredSize().width+pad).sum()-pad;
            int contextWidth=Math.max(0,Math.min(natural,(end-start-lane)/2));
            int trailing=lane+contextWidth;
            row.trailing.setBounds(end-trailing,0,trailing,height);
            int x=Math.max(0,contextWidth-natural);
            for(int i=0;i<children.length;i++) {
                int w=i==children.length-1?keys:Math.min(children[i].getPreferredSize().width,Math.max(0,contextWidth-x));
                children[i].setBounds(i==children.length-1?trailing-keys:x,0,w,height);x+=w+pad;
            }
            row.title.setBounds(start,0,Math.max(0,end-trailing-pad-start),height);row.title.doLayout();
            row.subtitle.setBounds(0,0,0,0);return true;
        }
        /** A laned tree asks for every row; its host bounds it and it scrolls beyond (the classic Keymap stands as tall as its rows). */
        @Override int viewportRowsHeight(){return laned()?rowsHeight():super.viewportRowsHeight();}
        @Override int rowHeightAt(int index){return rowShellHeight(index)+navigationGapAfter(index)+(detail!=null && itemId(index).equals(detail.itemId)?detailHeight():0);}
        /** A labelled group's heading carries its own space; an unlabelled root group is set apart by a wider gap. */
        private int navigationGapAfter(int index) {
            if(!settingsNavigation || index+1>=visible.size())return 0;
            var next=visible.get(index+1);var current=visible.get(index);
            if(next.depth()!=0)return 0;
            if(!next.item().leaf())return ThemePainting.metric(context(),dev.jasper.ui.metrics.DesignMetrics.Role.SETTINGS_NAV_HEADING_GAP);
            return current.depth()>0?ThemePainting.metric(context(),dev.jasper.ui.metrics.DesignMetrics.Role.SETTINGS_NAV_GROUP_GAP):0;
        }
        /** A keycap tree's section heading: one strong title line, a little shorter than its shortcut rows. */
        private int keycapGroupHeight() {
            return ThemePainting.height(context(), dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_TREE_GROUP_HEIGHT, dev.jasper.ui.metrics.Typography.Role.PANEL_TITLE);
        }
        /**
         * A manager rail's tree (column 0) under a face with flat rails, or a host's own rail (the file chooser's places)
         * under any face: its branches are flat section headings, its leaves rail rows.
         */
        boolean rail(){return column==0 && !settingsNavigation && presentation.isEmpty() && (context().faces().tree.flatRail() || view.hostColumn() >= 0);}
        @Override int rowShellHeight(int index){
            if((settingsNavigation || rail()) && !visible.get(index).item().leaf())return ThemePainting.height(context(),dev.jasper.ui.metrics.DesignMetrics.Role.SETTINGS_NAV_HEADING,dev.jasper.ui.metrics.Typography.Role.SECTION_LABEL);
            if(!presentation.isEmpty() && !visible.get(index).item().leaf())return keycapGroupHeight();
            var value=presentation.get(itemId(index));
            return value==null || value.keycaps().isEmpty()?rowHeight:Math.max(rowHeight,new Keycaps(context(),value.keycaps().get()).heightAt(keyWidth(index,scroll.getViewport().getExtentSize().width))+2*detailInset());
        }
        @Override void layoutAccessories(int width) {
            if(detail==null)return;
            int index=indexOf(detail.itemId);if(index<0)return;
            int above=detailTop();
            detail.body.setBounds(detailLeading(),top(index)+rowShellHeight(index)+above,Math.max(0,width-detailLeading()-detailTrailing()),Math.max(0,detailHeight()-above-detailBottom()-detailAfter()));
            detail.body.doLayout();
        }
        @Override void refresh(){
            // Settings navigation and the keycap card use the overlay scroll policy; the card keeps a little space above and below its rows.
            boolean card=!presentation.isEmpty();
            ((TreePane)scroll).overlay(settingsNavigation || card);
            int inset=card?ThemePainting.metric(context(),dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_TREE_INSET):0;
            var border=scroll.getViewportBorder();
            if(!(border==null && inset==0) && !(border instanceof javax.swing.border.EmptyBorder empty && empty.getBorderInsets().top==inset))
                scroll.setViewportBorder(inset==0?null:new javax.swing.border.EmptyBorder(inset,0,inset,0));
            super.refresh();if(detail!=null)detail.body.inheritedEnabled(interactive());
        }
        @Override public void close(){try {if(detail!=null)detach(detail);}finally{super.close();}}

        Nodes.Tree value() {
            return (Nodes.Tree) node;
        }

        @Override
        int size() {
            return visible.size();
        }

        @Override
        String itemId(int i) {
            return visible.get(i).item().id();
        }

        @Override
        boolean reportsClicks() {
            return true;
        }

        @Override
        Set<String> specifiedSelection() {
            return value().selectedIds();
        }

        @Override
        boolean multiple() {
            return false;
        }

        @Override
        String activation() {
            return value().activateAction();
        }

        @Override
        List<MenuItem> contextActions() {
            return List.of();
        }

        @Override
        List<MenuItem> contextActions(String id) {
            var item = items.get(id);
            return item == null ? List.of() : item.contextActions().orElse(List.of());
        }

        /** Tree menus are always the item's own. */
        @Override
        boolean ownsContextActions(String id) {
            return items.containsKey(id);
        }

        @Override
        String itemText(int i) {
            return visible.get(i).item().title();
        }

        @Override
        void data() {
            visible.clear();
            itemIds.clear();
            items.clear();
            value().roots().forEach(root -> flatten(root, 0, true));
            if(detail!=null && indexOf(detail.itemId)<0)detach(detail);
            presentation=presentation.entrySet().stream().filter(e->itemIds.contains(e.getKey())).collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,Map.Entry::getValue));
            int columns = value().columns().isEmpty() ? items.values().stream().mapToInt(item -> item.trailing().size()).max().orElse(0)
                : value().columns().size() - 1;
            trailingWidths = new int[columns];
            // Detail columns are at least TREE_DETAIL_COLUMN wide and the last (the value) at least TREE_VALUE_COLUMN.
            for (int i = 0; i < columns; i++)
                trailingWidths[i] = ThemePainting.metric(context(), i == columns - 1
                        ? dev.jasper.ui.metrics.DesignMetrics.Role.TREE_VALUE_COLUMN
                        : dev.jasper.ui.metrics.DesignMetrics.Role.TREE_DETAIL_COLUMN);
            // Each value is measured in the role it is drawn in, and as wide as the labels paint it: their fractional
            // advance can exceed the integer metrics at a fractional size.
            var fonts = new java.awt.Font[columns];
            var metrics = new java.awt.FontMetrics[columns];
            for (int i = 0; i < columns; i++) {
                fonts[i] = ThemePainting.font(context(), dev.jasper.ui.metrics.Typography.Role.valueOf(valueRole(i).name()));
                metrics[i] = canvas.getFontMetrics(fonts[i]);
            }
            for (var item : items.values())
                for (int i = 0; i < item.trailing().size(); i++) {
                    String text = item.trailing().get(i);
                    trailingWidths[i] = Math.max(trailingWidths[i], Math.max(metrics[i].stringWidth(text), ThemePainting.measureText(fonts[i], text)));
                }
            var ids = value().columns().stream().map(Column::id).collect(java.util.stream.Collectors.toSet());
            kept.keySet().retainAll(ids);
            columnHeader();
        }

        /**
         * Each trailing column's width as the header and the rows lay it out: the width the user dragged it to, else its
         * {@code Column.width} (scaled, at least its title), else its content fit ({@link #trailingWidths}). The item column
         * takes the rest; when it would fall below its title the columns with a width shrink proportionally towards their
         * titles (content-fit columns never do), so the header never scrolls.
         */
        int[] columnWidths() {
            var sizes = trailingWidths.clone();
            var columns = value().columns();
            if (laned() || columns.size() != sizes.length + 1 || minimums.length != columns.size()) return sizes;
            var set = new boolean[sizes.length];
            int slack = 0;
            for (int i = 0; i < sizes.length; i++) {
                var column = columns.get(i + 1);
                Integer kept = this.kept.get(column.id());
                int width = kept != null ? kept : column.width().orElse(0);
                if (width <= 0) continue;
                set[i] = true;
                sizes[i] = Math.max(minimums[i + 1], com.formdev.flatlaf.util.UIScale.scale(width));
                slack += sizes[i] - minimums[i + 1];
            }
            int room = trailingRoom(), excess = java.util.Arrays.stream(sizes).sum() - room;
            if (room >= 0 && excess > 0 && slack > 0) {
                double share = Math.min(1, excess / (double) slack);
                for (int i = 0; i < sizes.length; i++) if (set[i]) sizes[i] -= (int) Math.ceil((sizes[i] - minimums[i + 1]) * share);
            }
            return sizes;
        }

        /** The width the trailing columns may take with the item column at its title's width, or -1 before layout. */
        private int trailingRoom() {
            int width = scroll.getViewport().getExtentSize().width;
            if (width <= 0) return -1;
            var insets = header.getInsets();
            int gap = ThemePainting.metric(context(), dev.jasper.ui.metrics.DesignMetrics.Role.ICON_GAP);
            return Math.max(0, width - insets.left - insets.right - (countWidth > 0 ? countWidth + gap : 0) - trailingWidths.length * gap - minimums[0]);
        }

        /** Where each trailing column {@code sizes} wide starts in a header {@code width} wide, packed against the count column. */
        int[] trailingStarts(int width, int[] sizes) {
            int gap = ThemePainting.metric(context(), dev.jasper.ui.metrics.DesignMetrics.Role.ICON_GAP);
            int end = width - header.getInsets().right - (countWidth > 0 ? countWidth + gap : 0);
            var starts = new int[sizes.length];
            for (int i = sizes.length - 1; i >= 0; i--) {
                starts[i] = end - sizes[i];
                end = starts[i] - gap;
            }
            return starts;
        }

        /** Where the item column ends in a header {@code width} wide, before trailing columns {@code sizes} wide. */
        int titleEnd(int width, int[] sizes) {
            int gap = ThemePainting.metric(context(), dev.jasper.ui.metrics.DesignMetrics.Role.ICON_GAP);
            return sizes.length == 0 ? width - header.getInsets().right - (countWidth > 0 ? countWidth + gap : 0) : trailingStarts(width, sizes)[0] - gap;
        }

        /**
         * A column tree's side of a header drag. Boundary {@code b} is trailing column {@code b}'s start: a drag moves width
         * between it and the column on its left (the item column for the first), and a double-click returns it to its
         * content fit. A laned tree has no boundaries.
         */
        private final class Resizing implements ColumnResizer.Target {
            private boolean resizable() { return !laned() && minimums.length == value().columns().size() && minimums.length == trailingWidths.length + 1 && minimums.length > 1; }

            private int titleWidth(int[] sizes) { return titleEnd(header.getWidth(), sizes) - header.getInsets().left; }

            @Override public int[] edges() {
                return resizable() ? trailingStarts(header.getWidth(), columnWidths()) : new int[0];
            }

            @Override public void drag(int boundary, int dx) {
                if (!resizable()) return;
                int[] sizes = columnWidths();
                if (boundary < 0 || boundary >= sizes.length) return;
                int left = boundary == 0 ? titleWidth(sizes) : sizes[boundary - 1];
                dx = Math.max(dx, Math.min(0, minimums[boundary] - left));
                dx = Math.min(dx, Math.max(0, sizes[boundary] - minimums[boundary + 1]));
                if (dx == 0) return;
                sizes[boundary] -= dx;
                if (boundary > 0) sizes[boundary - 1] += dx;
                keep(sizes);
                relayout();
            }

            @Override public boolean fit(int boundary) {
                if (!resizable() || boundary < 0 || boundary >= trailingWidths.length) return false;
                int[] before = columnWidths();
                String id = value().columns().get(boundary + 1).id();
                Integer old = kept.put(id, Math.max(1, com.formdev.flatlaf.util.UIScale.unscale(trailingWidths[boundary])));
                if (java.util.Arrays.equals(before, columnWidths())) {
                    if (old == null) kept.remove(id); else kept.put(id, old);
                    return false;
                }
                relayout();
                return true;
            }

            @Override public void finish() {
                if (!resizable()) return;
                keep(columnWidths());
                var widths = new java.util.LinkedHashMap<String, Integer>();
                for (var column : value().columns()) widths.put(column.id(), kept.get(column.id()));
                emit(new UiEvent.ColumnsResized(node.id(), widths));
            }

            /** Keeps every column at the width it shows, unscaled: the trailing columns at {@code sizes}, the item column at the rest. */
            private void keep(int[] sizes) {
                var columns = value().columns();
                kept.put(columns.getFirst().id(), Math.max(1, com.formdev.flatlaf.util.UIScale.unscale(titleWidth(sizes))));
                for (int i = 0; i < sizes.length; i++)
                    kept.put(columns.get(i + 1).id(), Math.max(1, com.formdev.flatlaf.util.UIScale.unscale(sizes[i])));
            }

            /** Lays the header and every mounted row out again at the new widths. */
            private void relayout() {
                header.revalidate();
                header.repaint();
                sync();
            }
        }

        /** A plain tree row's side inset, as InteractiveRows.Row.doLayout gives it. */
        int rowPad() {
            return ThemePainting.metric(context(), column >= 0 ? dev.jasper.ui.metrics.DesignMetrics.Role.COLUMN_LIST_SIDE : dev.jasper.ui.metrics.DesignMetrics.Role.ICON_GAP);
        }

        /** The role trailing value {@code i} is drawn in: its column's in a column tree, else CAPTION. */
        Nodes.TextRole valueRole(int i) {
            var columns = value().columns();
            return i + 1 < columns.size() ? columns.get(i + 1).role() : Nodes.TextRole.CAPTION;
        }

        /** How many trailing values a row shows: its own, or in a column tree one per column after the title. */
        int trailingColumns(TreeItem item) {
            return value().columns().isEmpty() ? item.trailing().size() : value().columns().size() - 1;
        }

        /** A column tree's header, or none; widens each trailing column to its title and measures the count column. */
        private void columnHeader() {
            var columns = value().columns();
            countWidth = 0;
            header.removeAll();
            minimums = new int[columns.size()];
            if (columns.isEmpty()) {
                if (scroll.getColumnHeader() != null && scroll.getColumnHeader().getView() == header) scroll.setColumnHeader(null);
                return;
            }
            for (int i = 0; i < columns.size(); i++) {
                var label = Tables.headerLabel(this, "@tree/", columns.get(i));
                context().faces().tree.styleTitle(label, context(), i);
                // The item column's title reads from the rows' start; the others sit over their right-aligned values.
                if (i > 0) {
                    label.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
                    trailingWidths[i - 1] = Math.max(trailingWidths[i - 1], label.getPreferredSize().width);
                }
                minimums[i] = label.getPreferredSize().width;
                header.add(label);
            }
            var counts = canvas.getFontMetrics(ThemePainting.font(context(), dev.jasper.ui.metrics.Typography.Role.COUNT));
            for (var item : items.values())
                if (item.count().isPresent()) countWidth = Math.max(countWidth, counts.stringWidth(Integer.toString(item.count().getAsInt())));
            header.setOpaque(false);
            header.setLayout(new ColumnHeaderLayout(this));
            int pad = ThemePainting.metric(context(), dev.jasper.ui.metrics.DesignMetrics.Role.ICON_GAP),
                    side = rowPad() + com.formdev.flatlaf.util.UIScale.scale(gutter);
            var insets = context().faces().tree.headerInsets(context(), side,
                    header.getComponentCount() == 0 ? 0 : header.getComponent(0).getPreferredSize().height);
            header.setBorder(javax.swing.BorderFactory.createEmptyBorder(insets.top, insets.left, insets.bottom, insets.right));
            if (scroll.getColumnHeader() == null || scroll.getColumnHeader().getView() != header) {
                scroll.setColumnHeaderView(header);
                scroll.getColumnHeader().setOpaque(false);
            }
            header.revalidate();
            header.repaint();
        }

        @Override
        boolean containsItem(String id) {
            return itemIds.contains(id);
        }

        void flatten(TreeItem item, int depth, boolean shown) {
            itemIds.add(item.id());
            items.put(item.id(), item);
            if (shown) visible.add(new Visible(item, depth));
            boolean childrenShown = shown && value().expandedIds().contains(item.id());
            item.children().forEach(child -> flatten(child, depth + 1, childrenShown));
        }

        /** Settings navigation's headings stay open unless the navigation collapses (Enterprise presentation spec 12.1). */
        void expandFocused(boolean expanded) {
            if (settingsNavigation && !collapsibleNavigation) return;
            int i = indexOf(focusItem);
            if (i >= 0 && !visible.get(i).item().leaf())
                emit(new UiEvent.Expanded(node.id(), focusItem, expanded));
        }

        private static Nodes.Hue settingsHue(String icon){return switch(icon){case "appearance","font"->Nodes.Hue.BLUE;case "bell"->Nodes.Hue.AMBER;case "user","plugin"->Nodes.Hue.PURPLE;case "terminal"->Nodes.Hue.GREEN;default->Nodes.Hue.NEUTRAL;};}

        @Override
        void populate(InteractiveRows.Row row, int index) {
            row.begin();
            var entry = visible.get(index);
            var item = entry.item();
            row.indent = settingsNavigation?entry.depth()*ThemePainting.metric(context(),dev.jasper.ui.metrics.DesignMetrics.Role.SETTINGS_NAV_CHILD_INDENT):rail()?0:!presentation.isEmpty() && item.leaf()?ThemePainting.metric(context(),dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_TREE_LEAF_INDENT):context().faces().tree.indent(context(), entry.depth());
            var leading = new ArrayList<Nodes.Node>();
            if (!item.leaf() && (!settingsNavigation || collapsibleNavigation) && !rail())
                leading.add(
                        new Nodes.Button(
                                "@tree/" + node.id() + "/" + item.id() + "/expand",
                                ControlState.READY,
                                "",
                                value().expandedIds().contains(item.id())
                                        ? "chevron.down"
                                        : "chevron.right",
                                Nodes.ButtonKind.GHOST,
                                "expand",
                                "Expand or collapse " + item.title(),presentation.isEmpty() || laned()?context().faces().tree.disclosure():Nodes.ButtonPresentation.EMBEDDED));
            item.icon()
                    .ifPresent(
                            icon ->
                                    leading.add(
                                            new Nodes.Icon(
                                                    "@tree/" + node.id() + "/" + item.id()
                                                            + "/icon",
                                                    ControlState.READY,
                                                    icon,
                                                    !presentation.isEmpty() ? settingsHue(icon) : Nodes.Hue.NONE,
                                                    Nodes.IconSize.CONTROL,
                                                    item.title())));
            item.status().ifPresent(status -> leading.add(new Nodes.StatusDot(
                    "@tree/" + node.id() + "/" + item.id() + "/status", ControlState.READY, status)));
            row.slot(row.leading, leading);
            // A laned keycap tree's sections take the tree face's disclosure, as a column tree's branches do (plan 9b Rulings 7).
            if (!item.leaf() && (presentation.isEmpty() || laned()) && row.controls.get("@tree/" + node.id() + "/" + item.id() + "/expand") instanceof Buttons.ButtonControl expand)
                context().faces().tree.styleDisclosure(expand.button, context(), value().expandedIds().contains(item.id()));
            // The navigation's triangle stands alone; its title follows at the navigation's text gap (plan 9a Rulings 13).
            if (settingsNavigation && row.controls.get("@tree/" + node.id() + "/" + item.id() + "/expand") instanceof Buttons.ButtonControl navigationExpand)
                navigationExpand.button.disclosureWidth = navigationExpand.button.disclosure ? ThemePainting.metric(context(), dev.jasper.ui.metrics.DesignMetrics.Role.DISCLOSURE_GLYPH) : 0;
            for(var control:row.controls.values())if(control instanceof Labels.IconControl icon && control.node.id().endsWith("/icon") && presentation.isEmpty())icon.tile(0,false);
            if(!presentation.isEmpty())for(var control:row.controls.values())if(control instanceof Labels.IconControl icon && control.node.id().endsWith("/icon")){int tile=context().metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.SETTINGS_ICON_TILE);icon.tile(tile,tile,context().icons().size(context().metrics(), Nodes.IconSize.CONTROL),selected.contains(item.id()));}
            String titleId = "@tree/" + node.id() + "/" + item.id() + "/title";
            row.slot(
                    row.title,
                    List.of(
                            new Nodes.Text(
                                    titleId,
                                    ControlState.READY,
                                    item.title(),
                                    (settingsNavigation || rail()) && !item.leaf()?Nodes.TextRole.SECTION_LABEL:!presentation.isEmpty()&&!item.leaf()&&!laned()?Nodes.TextRole.PANEL_TITLE:context().faces().tree.title(item.leaf(), item.emphasized() ? Nodes.TextRole.BODY_STRONG : Nodes.TextRole.BODY))));
            // Settings navigation pages use the Settings body face; recycled rows restore the declared role.
            if(row.controls.get(titleId) instanceof Labels.TextControl titleText) {
                var role=settingsNavigation && item.leaf() ? dev.jasper.ui.metrics.Typography.Role.SETTINGS_BODY : null;
                if(titleText.override!=role){titleText.override=role;titleText.refresh();}
            }
            // A collapsible navigation's sections read as category rows in the strong secondary text (plan 9a Rulings 13).
            if (settingsNavigation && collapsibleNavigation && !item.leaf() && !row.inked)
                row.controls.get(titleId).component.setForeground(ThemePainting.color(context(), dev.jasper.ui.theme.SemanticColor.TEXT_STRONG_SECONDARY));
            // Emphasis is strong text in the link color (a keycap tree marks a section with its modified dot instead); the text
            // control's own refresh picks its role color first.
            if (!row.inked && item.emphasized() && (presentation.isEmpty() || item.leaf()))
                row.controls.get(titleId).component.setForeground(ThemePainting.color(context(), dev.jasper.ui.theme.SemanticColor.TEXT_LINK));
            row.slot(row.subtitle, List.of());
            var trailing = new ArrayList<Nodes.Node>();
            var presented=presentation.get(item.id());
            // A read-only contextual key reads quieter than the rows the user can change.
            if(!row.inked && presented!=null && presented.locked() && !item.emphasized())
                row.controls.get(titleId).component.setForeground(ThemePainting.color(context(), dev.jasper.ui.theme.SemanticColor.TEXT_SECONDARY));
            for (int i = 0; presented==null && i < trailingColumns(item); i++)
                trailing.add(new Nodes.Text("@tree/" + node.id() + "/" + item.id() + "/trailing/" + i,
                        ControlState.READY, i < item.trailing().size() ? item.trailing().get(i) : "", valueRole(i)));
            if(presented!=null) {
                // On lanes a read-only key shows no lock glyph (plan 9b Rulings 8); its row's tooltip still says it.
                if(presented.locked() && value().columns().isEmpty())trailing.add(new Nodes.Icon("@tree/"+node.id()+"/"+item.id()+"/lock",ControlState.READY,"lock",Nodes.Hue.NEUTRAL,Nodes.IconSize.CAPTION,"Read-only contextual key"));
                trailing.add(new Nodes.Text("@tree/"+node.id()+"/"+item.id()+"/context",ControlState.READY,presented.context(),laned()?valueRole(0):Nodes.TextRole.CAPTION));
                trailing.add(new Nodes.Text("@tree/"+node.id()+"/"+item.id()+"/keys",ControlState.READY,presented.keycaps().map(KeycapSequence::accessibleName).orElse(item.trailing().isEmpty()?"":item.trailing().getLast()),Nodes.TextRole.CAPTION));
            }
            String modifiedId="@tree/"+node.id()+"/"+item.id()+"/modified";
            if(!presentation.isEmpty() && !item.leaf() && item.emphasized())trailing.add(new Nodes.StatusDot(modifiedId,ControlState.READY,Nodes.StatusKind.IDLE));
            item.count().ifPresent(count -> trailing.add(new Nodes.Text(
                    "@tree/" + node.id() + "/" + item.id() + "/count", ControlState.READY, Integer.toString(count), Nodes.TextRole.COUNT)));
            // A column tree reserves the count column on every row, so the values before it line up.
            if (presented == null && countWidth > 0 && item.count().isEmpty())
                trailing.add(new Nodes.Text("@tree/" + node.id() + "/" + item.id() + "/count", ControlState.READY, "", Nodes.TextRole.COUNT));
            row.slot(row.trailing, trailing);
            if(presented!=null || laned())row.trailing.setLayout(null);
            if(row.controls.get(modifiedId) instanceof Indicators.DotControl dot){dot.modified=true;dot.refresh();}
            // On lanes the scope and a section's count read in the placeholder ink, as the Keymap mock sets them (plan 9b Rulings 8).
            if(laned() && !row.inked)for(var suffix:List.of("/context","/trailing/0","/trailing/1"))
                if(row.controls.get("@tree/"+node.id()+"/"+item.id()+suffix) instanceof Labels.TextControl lane)
                    lane.component.setForeground(ThemePainting.color(context(),dev.jasper.ui.theme.SemanticColor.TEXT_PLACEHOLDER));
            if(presented!=null) {
                // Context reads right-aligned against the keys column; a recycled row measures its own text.
                var label=(javax.swing.JLabel)row.controls.get("@tree/"+node.id()+"/"+item.id()+"/context").component;
                label.setPreferredSize(null); label.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
            }
            if(presented!=null && presented.keycaps().isPresent()) {
                var label=(javax.swing.JLabel)row.controls.get("@tree/"+node.id()+"/"+item.id()+"/keys").component;
                label.getAccessibleContext().setAccessibleName(presented.keycaps().get().accessibleName());label.setText("");label.setIcon(new Keycaps(context(),presented.keycaps().get(),row.inked && context().faces().tree.boxesSelectedKeycaps()));label.setDisabledIcon(label.getIcon());
            }
            int[] columnWidths = presented == null ? columnWidths() : trailingWidths;
            for (int i = 0; presented==null && i < trailingColumns(item); i++) {
                var label = (javax.swing.JLabel) row.controls.get(trailing.get(i).id()).component;
                label.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
                label.setPreferredSize(null);
                int width = i < columnWidths.length ? columnWidths[i] : label.getPreferredSize().width;
                label.setPreferredSize(new java.awt.Dimension(width + label.getInsets().left + label.getInsets().right, label.getPreferredSize().height));
            }
            if (presented == null && row.controls.get("@tree/" + node.id() + "/" + item.id() + "/count") instanceof Labels.TextControl countText
                    && countText.component instanceof javax.swing.JLabel count) {
                count.setPreferredSize(null);
                if (countWidth > 0) {
                    count.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
                    count.setPreferredSize(new java.awt.Dimension(countWidth + count.getInsets().left + count.getInsets().right, count.getPreferredSize().height));
                }
            }
            // The row, its slots and its passive texts (title, trailing columns, count) show the item's literal tooltip.
            row.tooltip(item.tooltip());
            row.name(List.of(new Nodes.Text("@tree/" + node.id() + "/" + item.id() + "/name", ControlState.READY, item.title(), Nodes.TextRole.BODY)));
            // Rail and Settings navigation headings have no disclosure button.
            if (!item.leaf() && row.controls.get("@tree/"+node.id()+"/"+item.id()+"/expand") != null) {
                var c = (Buttons.ButtonControl) row.controls.get("@tree/"+node.id()+"/"+item.id()+"/expand");
                if (c.button.getClientProperty("Jasper.treeCommand") == null) {
                    for (var listener : c.button.getActionListeners())
                        c.button.removeActionListener(listener);
                    java.awt.event.ActionListener listener =
                            e ->
                                    emit(
                                            new UiEvent.Expanded(
                                                    node.id(),
                                                    row.id,
                                                    !value().expandedIds().contains(row.id)));
                    c.button.addActionListener(listener);
                    c.own(() -> c.button.removeActionListener(listener));
                    c.button.putClientProperty("Jasper.treeCommand", true);
                }
            }
        }
    }
}
