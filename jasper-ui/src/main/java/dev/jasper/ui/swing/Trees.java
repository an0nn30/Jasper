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
                }
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

    static final class TreeControl extends InteractiveRows.RowsControl {
        boolean settingsNavigation;
        final List<Visible> visible = new ArrayList<>();
        final Set<String> itemIds = new HashSet<>();
        final Map<String, TreeItem> items = new HashMap<>();
        /** Each trailing column's width, measured over every item so the columns line up from row to row. */
        int[] trailingWidths = new int[0];

        TreeControl(KitView view, Nodes.Tree node) {
            super(view, node, new TreePane(view.context()));
            ((TreePane)scroll).owner=this;
            refresh();
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
        @Override int minimumRowHeight(){return settingsNavigation?ThemePainting.height(context(),dev.jasper.ui.metrics.DesignMetrics.Role.SETTINGS_NAV_ROW,dev.jasper.ui.metrics.Typography.Role.SETTINGS_BODY):presentation.isEmpty()?0:ThemePainting.height(context(),dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_TREE_ROW_HEIGHT,dev.jasper.ui.metrics.Typography.Role.BODY);}
        @Override boolean uniformRows(){return !settingsNavigation && !rail() && detail==null && presentation.isEmpty();}
        private int keyWidth(int index,int width) {
            var value=presentation.get(itemId(index));
            if(value==null || value.keycaps().isEmpty())return 0;
            var caps=new Keycaps(context(),value.keycaps().get());
            int available=Math.max(0,width-ThemePainting.metric(context(),dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_TREE_LEAF_INDENT)-2*ThemePainting.metric(context(),dev.jasper.ui.metrics.DesignMetrics.Role.ICON_GAP));
            return Math.min(available,Math.min(caps.getIconWidth(),Math.max(caps.minimumWidth(),available/2)));
        }
        boolean layoutPresented(InteractiveRows.Row row) {
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
        /** A manager rail's tree (column 0): its branches are flat section headings, its leaves rail rows. */
        boolean rail(){return column==0 && !settingsNavigation && presentation.isEmpty();}
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
            var metrics = canvas.getFontMetrics(ThemePainting.font(context(), dev.jasper.ui.metrics.Typography.Role.CAPTION));
            int columns = items.values().stream().mapToInt(item -> item.trailing().size()).max().orElse(0);
            trailingWidths = new int[columns];
            // Detail columns are at least TREE_DETAIL_COLUMN wide and the last (the value) at least TREE_VALUE_COLUMN.
            for (int i = 0; i < columns; i++)
                trailingWidths[i] = ThemePainting.metric(context(), i == columns - 1
                        ? dev.jasper.ui.metrics.DesignMetrics.Role.TREE_VALUE_COLUMN
                        : dev.jasper.ui.metrics.DesignMetrics.Role.TREE_DETAIL_COLUMN);
            for (var item : items.values())
                for (int i = 0; i < item.trailing().size(); i++)
                    trailingWidths[i] = Math.max(trailingWidths[i], metrics.stringWidth(item.trailing().get(i)));
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

        /** Settings navigation's headings stay open. */
        void expandFocused(boolean expanded) {
            if (settingsNavigation) return;
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
            row.indent = settingsNavigation || rail()?0:!presentation.isEmpty() && item.leaf()?ThemePainting.metric(context(),dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_TREE_LEAF_INDENT):entry.depth() * com.formdev.flatlaf.util.UIScale.scale(16);
            var leading = new ArrayList<Nodes.Node>();
            if (!item.leaf() && !settingsNavigation && !rail())
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
                                "Expand or collapse " + item.title(),presentation.isEmpty()?Nodes.ButtonPresentation.STANDARD:Nodes.ButtonPresentation.EMBEDDED));
            item.icon()
                    .ifPresent(
                            icon ->
                                    leading.add(
                                            new Nodes.Icon(
                                                    "@tree/" + node.id() + "/" + item.id()
                                                            + "/icon",
                                                    ControlState.READY,
                                                    icon,
                                                    settingsNavigation || !presentation.isEmpty() ? settingsHue(icon) : Nodes.Hue.NONE,
                                                    Nodes.IconSize.CONTROL,
                                                    item.title())));
            item.status().ifPresent(status -> leading.add(new Nodes.StatusDot(
                    "@tree/" + node.id() + "/" + item.id() + "/status", ControlState.READY, status)));
            row.slot(row.leading, leading);
            for(var control:row.controls.values())if(control instanceof Labels.IconControl icon && control.node.id().endsWith("/icon") && !settingsNavigation && presentation.isEmpty())icon.tile(0,false);
            if(settingsNavigation || !presentation.isEmpty())for(var control:row.controls.values())if(control instanceof Labels.IconControl icon && control.node.id().endsWith("/icon")){int tile=context().metrics().value(settingsNavigation?dev.jasper.ui.metrics.DesignMetrics.Role.SETTINGS_NAV_TILE:dev.jasper.ui.metrics.DesignMetrics.Role.SETTINGS_ICON_TILE);icon.tile(tile,tile,settingsNavigation?context().metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.SETTINGS_NAV_GLYPH):context().icons().size(Nodes.IconSize.CONTROL),selected.contains(item.id()));}
            String titleId = "@tree/" + node.id() + "/" + item.id() + "/title";
            row.slot(
                    row.title,
                    List.of(
                            new Nodes.Text(
                                    titleId,
                                    ControlState.READY,
                                    item.title(),
                                    (settingsNavigation || rail()) && !item.leaf()?Nodes.TextRole.SECTION_LABEL:!presentation.isEmpty()&&!item.leaf()?Nodes.TextRole.PANEL_TITLE:item.emphasized() ? Nodes.TextRole.BODY_STRONG : Nodes.TextRole.BODY)));
            // Settings navigation pages use the Settings body face; recycled rows restore the declared role.
            if(row.controls.get(titleId) instanceof Labels.TextControl titleText) {
                var role=settingsNavigation && item.leaf() ? dev.jasper.ui.metrics.Typography.Role.SETTINGS_BODY : null;
                if(titleText.override!=role){titleText.override=role;titleText.refresh();}
            }
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
            for (int i = 0; presented==null && i < item.trailing().size(); i++)
                trailing.add(new Nodes.Text("@tree/" + node.id() + "/" + item.id() + "/trailing/" + i,
                        ControlState.READY, item.trailing().get(i), Nodes.TextRole.CAPTION));
            if(presented!=null) {
                if(presented.locked())trailing.add(new Nodes.Icon("@tree/"+node.id()+"/"+item.id()+"/lock",ControlState.READY,"lock",Nodes.Hue.NEUTRAL,Nodes.IconSize.CAPTION,"Read-only contextual key"));
                trailing.add(new Nodes.Text("@tree/"+node.id()+"/"+item.id()+"/context",ControlState.READY,presented.context(),Nodes.TextRole.CAPTION));
                trailing.add(new Nodes.Text("@tree/"+node.id()+"/"+item.id()+"/keys",ControlState.READY,presented.keycaps().map(KeycapSequence::accessibleName).orElse(item.trailing().isEmpty()?"":item.trailing().getLast()),Nodes.TextRole.CAPTION));
            }
            String modifiedId="@tree/"+node.id()+"/"+item.id()+"/modified";
            if(!presentation.isEmpty() && !item.leaf() && item.emphasized())trailing.add(new Nodes.StatusDot(modifiedId,ControlState.READY,Nodes.StatusKind.IDLE));
            item.count().ifPresent(count -> trailing.add(new Nodes.Text(
                    "@tree/" + node.id() + "/" + item.id() + "/count", ControlState.READY, Integer.toString(count), Nodes.TextRole.COUNT)));
            row.slot(row.trailing, trailing);
            if(presented!=null)row.trailing.setLayout(null);
            if(row.controls.get(modifiedId) instanceof Indicators.DotControl dot){dot.modified=true;dot.refresh();}
            if(presented!=null) {
                // Context reads right-aligned against the keys column; a recycled row measures its own text.
                var label=(javax.swing.JLabel)row.controls.get("@tree/"+node.id()+"/"+item.id()+"/context").component;
                label.setPreferredSize(null); label.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
            }
            if(presented!=null && presented.keycaps().isPresent()) {
                var label=(javax.swing.JLabel)row.controls.get("@tree/"+node.id()+"/"+item.id()+"/keys").component;
                label.getAccessibleContext().setAccessibleName(presented.keycaps().get().accessibleName());label.setText("");label.setIcon(new Keycaps(context(),presented.keycaps().get()));label.setDisabledIcon(label.getIcon());
            }
            for (int i = 0; presented==null && i < item.trailing().size(); i++) {
                var label = (javax.swing.JLabel) row.controls.get(trailing.get(i).id()).component;
                label.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
                label.setPreferredSize(null);
                int width = i < trailingWidths.length ? trailingWidths[i] : label.getPreferredSize().width;
                label.setPreferredSize(new java.awt.Dimension(width + label.getInsets().left + label.getInsets().right, label.getPreferredSize().height));
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
