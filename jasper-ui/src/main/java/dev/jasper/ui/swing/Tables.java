package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.SemanticColor;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.*;

/**
 * Column-aligned virtual rows; numeric cells align to the trailing column edge. Columns share the width by weight,
 * the header shows the host's sort and reports clicks as {@link UiEvent.Sorted}, and a muted row dims every cell.
 * A column holds a plain cell or a cell node (a bare dropdown among them), and a heading row shows its title and its
 * muted count across the row.
 */
final class Tables {
    private Tables() {}

    static JScrollPane pane(KitView view, Nodes.Table node) {
        return node.scroll() == Nodes.ScrollPolicy.OVERLAY ? new OverlayScrollPane(view.context()) : new JScrollPane();
    }

    /**
     * A column title as a table's or a tree's header shows it: caption type, the host's sort indicator, and left clicks
     * reported as {@link UiEvent.Sorted} for {@code owner}'s node.
     */
    static JLabel headerLabel(ControlRegistry.Control owner, String prefix, Column column) {
        var context = owner.context();
        var label = Labels.literal();
        KitOwnership.mark(label, prefix + owner.node.id() + "/header/" + column.id());
        label.setText(column.title());
        label.setFont(ThemePainting.font(context, Typography.Role.CAPTION));
        label.setForeground(ThemePainting.color(context, SemanticColor.TEXT_TERTIARY));
        label.setHorizontalAlignment(
                column.numeric() ? SwingConstants.RIGHT : SwingConstants.LEFT);
        label.setHorizontalTextPosition(SwingConstants.LEADING);
        label.setIcon(column.sorted().map(ascending -> context.icons().icon(ascending ? "chevron.up" : "chevron.down",
                context.theme().semantic(), SemanticColor.TEXT_SECONDARY,
                com.formdev.flatlaf.util.UIScale.scale(context.icons().size(context.metrics(), Nodes.IconSize.CAPTION)))).orElse(null));
        label.getAccessibleContext().setAccessibleName(column.title()
                + column.sorted().map(ascending -> ascending ? ", sorted ascending" : ", sorted descending").orElse(""));
        String columnId = column.id();
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) owner.emit(new UiEvent.Sorted(owner.node.id(), columnId));
            }
        });
        return label;
    }

    /** A heading row's count as it reads: "6 groups", "1 group", the bare number without a noun, or nothing. */
    static String countText(TableRow.Heading heading) {
        if (heading.count().isEmpty()) return "";
        int count = heading.count().getAsInt();
        return heading.countNoun().isBlank() ? Integer.toString(count) : count + " " + heading.countNoun() + (count == 1 ? "" : "s");
    }

    static final class TableControl extends InteractiveRows.RowsControl {
        final JPanel header = new JPanel();
        /** The row of every cell node and its descendants, so focus and reveal reach row-mounted nodes. */
        final Map<String, Integer> nodeRows = new HashMap<>();
        private List<Column> measuredColumns=List.of();
        private List<TableRow> measuredRows=List.of();
        private List<Font> measuredFonts=List.of();
        private int[] minimums=new int[0];
        private float measuredScale=-1;
        private void measureColumns(){
            var columns=value().columns();var fonts=columns.stream().map(c->ThemePainting.font(context(),Typography.Role.valueOf(c.role().name()))).toList();
            float scale=com.formdev.flatlaf.util.UIScale.getUserScaleFactor();
            if(measuredColumns.equals(columns)&&measuredRows.equals(value().rows())&&measuredFonts.equals(fonts)&&measuredScale==scale)return;
            measuredScale=scale;measuredColumns=columns;measuredRows=value().rows();measuredFonts=fonts;minimums=new int[columns.size()];
            for(int i=0;i<columns.size();i++){
                var column=columns.get(i);var probe=Labels.literal();probe.setFont(fonts.get(i));probe.setText("…");minimums[i]=probe.getPreferredSize().width;
                if(column.numeric())for(var row:measuredRows){probe.setText(row.cells().getOrDefault(column.id(),""));minimums[i]=Math.max(minimums[i],probe.getPreferredSize().width);}
            }
        }

        /** Minimum negotiation only: rendering still uses the existing weighted allocation and literal cells. */
        int usableWidth() {
            int padding=2*ThemePainting.metric(context(),DesignMetrics.Role.ICON_GAP);
            if(value().columns().isEmpty())return padding;
            var first=value().columns().getFirst();
            var font=ThemePainting.font(context(),Typography.Role.valueOf(first.role().name()));
            int target=ThemePainting.measureText(font,"M".repeat(DesignMetrics.TABLE_MIN_VISIBLE_GLYPHS));
            var icon=value().rows().stream().flatMap(row->row.icon().stream()).findFirst();
            if(icon.isPresent())target+=context().icons().icon(icon.get(),context().theme().semantic(),Nodes.Hue.NONE,
                com.formdev.flatlaf.util.UIScale.scale(14)).getIconWidth()+new JLabel().getIconTextGap();
            // Allocation is monotonic only after existing column minima fit. Below that
            // boundary proportional fallback can give the first column a misleading usable width.
            var layout=weights();int low=java.util.Arrays.stream(minimums).sum(),high=Math.max(low,Math.max(1,target));
            while(layout.allocation(high)[0]<target && high<Integer.MAX_VALUE/2)high*=2;
            while(low<high){int middle=low+(high-low)/2;if(layout.allocation(middle)[0]<target)low=middle+1;else high=middle;}
            return padding+high;
        }

        TableControl(KitView view, Nodes.Table node) {
            super(view, node, pane(view, node));
            header.setOpaque(false);
            scroll.setColumnHeaderView(header);
            // The titles sit on whatever is behind the table, as a heading does: an opaque header viewport painted its
            // own strip over a rounded pane's top.
            scroll.getColumnHeader().setOpaque(false);
            refresh();
        }

        Nodes.Table value() {
            return (Nodes.Table) node;
        }

        @Override
        int size() {
            return value().rows().size();
        }

        @Override
        String itemId(int i) {
            return value().rows().get(i).id();
        }

        @Override
        String itemText(int i) {
            var columns = value().columns();
            return columns.isEmpty() ? "" : cellText(value().rows().get(i), columns.getFirst().id());
        }

        /** A column's text for type-ahead: its plain cell, or a Text cell node's text. */
        static String cellText(TableRow row, String column) {
            if (row.cellNodes().get(column) instanceof Nodes.Text text) return text.text();
            return row.cells().getOrDefault(column, "");
        }

        /** A heading row groups the rows below it; it is never selected, activated or offered a menu. */
        @Override
        boolean dataRow(int index) {
            return index < 0 || index >= size() || value().rows().get(index).heading().isEmpty();
        }

        /** A table keeps Enter with no focused row, as the JTable it replaced did (its Enter moved the selection). */
        @Override
        boolean keepsEnterWithoutFocus() {
            return true;
        }

        @Override
        boolean reportsClicks() {
            return true;
        }

        /** A context press outside every row opens the table's shared menu, as the JTable's component popup did. */
        @Override
        boolean sharedMenuOutsideRows() {
            return true;
        }

        @Override
        Set<String> specifiedSelection() {
            return value().selectedIds();
        }

        @Override
        boolean multiple() {
            return value().selectionMode() == Nodes.SelectionMode.MULTIPLE;
        }

        @Override
        String activation() {
            return value().activateAction();
        }

        @Override
        List<MenuItem> contextActions() {
            return value().contextActions();
        }

        @Override
        List<MenuItem> contextActions(String id) {
            for (var row : value().rows())
                if (row.id().equals(id)) return row.contextActions().orElse(value().contextActions());
            return List.of();
        }

        @Override
        boolean ownsContextActions(String id) {
            return value().rows().stream().anyMatch(row -> row.id().equals(id) && row.contextActions().isPresent());
        }

        void index(Nodes.Node cell, int index) {
            nodeRows.put(cell.id(), index);
            ControlRegistry.children(cell).forEach(child -> index(child, index));
        }

        /** Reveal can arrive before layout and never changes the row's logical keyboard focus. */
        @Override
        boolean revealInline(String id) {
            Integer index = nodeRows.get(id);
            if (index == null) return false;
            pendingReveal = itemId(index);
            ensure(index);
            return true;
        }

        @Override
        ControlRegistry.Control inline(String id) {
            Integer index = nodeRows.get(id);
            if (index == null) return null;
            focusItem = itemId(index);
            focusControl = id;
            ensure(index);
            var row = mounted.get(focusItem);
            return row == null ? null : row.controls.get(id);
        }

        WeightLayout weights() {
            return new WeightLayout(value().columns().stream().mapToDouble(Column::weight).toArray(),minimums);
        }

        @Override
        void data() {
            nodeRows.clear();
            for (int i = 0; i < size(); i++) for (var cell : value().rows().get(i).cellNodes().values()) index(cell, i);
            if (focusControl != null && !nodeRows.containsKey(focusControl)) focusControl = null;
            measureColumns();
            scroll.setMinimumSize(new Dimension(usableWidth(),0));
            // A manager list column's rows start at the rail's top inset below the header, level with a rail beside it.
            scroll.setViewportBorder(column >= 0 ? BorderFactory.createEmptyBorder(ThemePainting.metric(context(), DesignMetrics.Role.COLUMN_RAIL_TOP), 0, 0, 0) : null);
            header.removeAll();
            header.setLayout(weights());
            for (var column : value().columns()) header.add(headerLabel(this, "@table/", column));
            // The header's titles start where the cells' text does: the row inset (a manager column's, else the icon gap).
            int pad = ThemePainting.metric(context(), DesignMetrics.Role.ICON_GAP),
                    side = (column >= 0 ? ThemePainting.metric(context(), DesignMetrics.Role.COLUMN_LIST_SIDE) : pad) + com.formdev.flatlaf.util.UIScale.scale(gutter);
            header.setBorder(BorderFactory.createEmptyBorder(pad, side, pad, side));
            header.revalidate();
            header.repaint();
        }

        @Override
        void populate(InteractiveRows.Row row, int index) {
            row.begin();
            row.slot(row.leading, List.of());
            row.slot(row.subtitle, List.of());
            var data = value().rows().get(index);
            if (data.heading().isPresent()) {
                heading(row, data, data.heading().get());
                return;
            }
            row.slot(row.trailing, List.of());
            var nodes = new java.util.ArrayList<Nodes.Node>();
            for (var column : value().columns()) {
                var cell = data.cellNodes().get(column.id());
                nodes.add(cell != null ? cell : new Nodes.Text(
                                "@table/" + node.id() + "/" + data.id() + "/" + column.id(),
                                ControlState.READY,
                                data.cells().getOrDefault(column.id(), ""),
                                column.role()));
            }
            row.slot(row.title, nodes);
            row.title.setLayout(weights());
            // A selected row's cells and glyph take its fill's ink (row.inked); a muted row keeps its placeholder ink only unselected.
            Color muted = data.muted() && !row.inked ? ThemePainting.color(context(), SemanticColor.TEXT_PLACEHOLDER) : null;
            for (int i = 0; i < nodes.size(); i++) {
                var cell = (JComponent) row.title.getComponent(i);
                var control = row.controls.get(nodes.get(i).id());
                // A cell dropdown is bare; on a selected row its bare value takes the fill's ink.
                if (control instanceof Choices.ChoiceControl choice) choice.inCell(row.inked ? ink() : null);
                if (control instanceof Labels.TextControl && cell instanceof JLabel label) {
                    label.setHorizontalAlignment(
                            value().columns().get(i).numeric()
                                    ? SwingConstants.RIGHT
                                    : SwingConstants.LEFT);
                    // The row's glyph leads the first column when that column is a plain cell.
                    if (!data.cellNodes().containsKey(value().columns().get(i).id()))
                        label.setIcon(
                                i == 0 && data.icon().isPresent() && row.inked
                                        ? context().icons().icon(data.icon().get(), context().theme().semantic(), row.owner.ink().text,
                                                com.formdev.flatlaf.util.UIScale.scale(14))
                                        : i == 0 && data.icon().isPresent()
                                        ? context()
                                                .icons()
                                                .icon(
                                                        data.icon().get(),
                                                        context().theme().semantic(),
                                                        Nodes.Hue.NONE,
                                                        com.formdev.flatlaf.util.UIScale.scale(14))
                                        : null);
                    if (muted != null) label.setForeground(muted);
                }
                row.cells.add(cell);
            }
            row.tooltip(data.tooltip());
            // A table row is named by its first column, as its identity (a file's name, an entry's title).
            row.name(nodes.isEmpty() ? List.of() : List.of(nodes.getFirst()));
        }

        /** A heading row: its strong title across the row and its count, muted, at the row's end. */
        private void heading(InteractiveRows.Row row, TableRow data, TableRow.Heading heading) {
            String prefix = "@table/" + node.id() + "/" + data.id();
            String count = countText(heading);
            row.slot(row.trailing, count.isEmpty() ? List.of()
                    : List.of(new Nodes.Text(prefix + "/count", ControlState.READY, count, Nodes.TextRole.CAPTION)));
            var title = new Nodes.Text(prefix + "/heading", ControlState.READY, heading.title(), Nodes.TextRole.BODY_STRONG);
            row.slot(row.title, List.of(title));
            row.tooltip("");
            row.name(List.of(title));
        }
    }

    /** Columns side by side, each taking its weight's share of the width; children beyond the weights get none. */
    static final class WeightLayout implements LayoutManager {
        final double[] weights;
        final int[] minimums;

        WeightLayout(double[] weights) {this(weights,new int[weights.length]);}
        WeightLayout(double[] weights,int[] minimums) {this.weights=weights;this.minimums=minimums;}
        /** Preserve ordinary weighted widths; freeze only undersized columns and redistribute their deficit. */
        private int[] allocation(int width){
            int count=weights.length;var sizes=new double[count];var fixed=new boolean[count];
            int required=java.util.Arrays.stream(minimums).sum();boolean feasible=required<=width;
            double remaining=width,total=java.util.Arrays.stream(weights).sum();
            if(feasible)for(int pass=0;pass<count;pass++){
                boolean changed=false;
                for(int i=0;i<count;i++)if(!fixed[i]&&total>0&&remaining*weights[i]/total<minimums[i]){
                    fixed[i]=true;sizes[i]=minimums[i];remaining-=minimums[i];total-=weights[i];changed=true;
                }
                if(!changed)break;
            }
            for(int i=0;i<count;i++)if(!fixed[i])sizes[i]=total>0?Math.max(0,remaining)*weights[i]/total:0;
            var result=new int[count];double end=0;int previous=0;
            for(int i=0;i<count;i++){end+=sizes[i];int edge=Math.min(width,(int)Math.round(end));result[i]=Math.max(0,edge-previous);previous=edge;}
            return result;
        }

        @Override
        public void addLayoutComponent(String name, Component component) {}

        @Override
        public void removeLayoutComponent(Component component) {}

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            int width = 0, height = 0;
            for (var c : parent.getComponents()) {
                width += c.getPreferredSize().width;
                height = Math.max(height, c.getPreferredSize().height);
            }
            Insets insets = parent.getInsets();
            return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            Insets insets = parent.getInsets();
            return new Dimension(insets.left + insets.right, preferredLayoutSize(parent).height);
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int width = Math.max(0, parent.getWidth() - insets.left - insets.right),
                    height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
            int[] sizes=allocation(width);int x=insets.left;var children=parent.getComponents();
            for(int i=0;i<children.length;i++){
                int w=i<sizes.length?sizes[i]:0;
                // A cell dropdown reaches back into the column gap by its text inset, so its value starts under the column's title.
                int lead=w>0?Math.min(x-insets.left,Choices.cellLead(children[i])):0;
                children[i].setBounds(x-lead,insets.top,w+lead,height);x+=w;
            }
        }
    }
}
