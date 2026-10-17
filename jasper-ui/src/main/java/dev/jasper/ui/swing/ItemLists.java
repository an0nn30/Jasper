package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.SemanticColor;

import java.awt.Container;
import java.util.*;
import java.util.List;

import javax.swing.*;

/** Interactive semantic item rows with a footer outside the selectable data set. */
final class ItemLists {
    private ItemLists() {}

    static final class ListControl extends InteractiveRows.RowsControl {
        final Map<String, Integer> nodeRows = new HashMap<>();
        final EnumMap<Typography.Role, Integer> measuredFonts =
                new EnumMap<>(Typography.Role.class);
        final EnumMap<DesignMetrics.Role, Integer> measuredInputs =
                new EnumMap<>(DesignMetrics.Role.class);

        /** A classic ordered list's side column (plan 4c), or null. */
        SideColumn side;
        /** The entry row a classic side column spans with the rows (a VERTICAL parent's INLINE row after the list), or null. */
        JComponent entry;
        /** Whether a VERTICAL parent decided this list's entry row on this pass; the reconciler clears it before every pass. */
        boolean spanned;

        ListControl(KitView view, Nodes.ItemList node) {
            super(view, node, new ListPane());
            ((ListPane) scroll).owner = this;
            rowKey("D", "details", Nodes.ItemList::detailsAction);
            rowKey(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac") ? "meta C" : "ctrl C",
                "copy", Nodes.ItemList::copyAction);
            rowKey("ESCAPE", "cancel", Nodes.ItemList::cancelAction);
            refresh();
        }

        /** These keys belong to the focused row canvas only; disabled or blank descriptors pass them outward. */
        private void rowKey(String stroke, String key, java.util.function.Function<Nodes.ItemList, String> action) {
            String binding = "item-list." + key;
            canvas.getInputMap().put(KeyStroke.getKeyStroke(stroke), binding);
            canvas.getActionMap().put(binding, new AbstractAction() {
                @Override public boolean isEnabled() {
                    return !closed && focusItem != null && rowInteractive() && !action.apply(value()).isBlank();
                }
                @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                    String row = focusItem;
                    String named = action.apply(value());
                    if (row != null && stillCurrent(row) && selectable(indexOf(row)) && !named.isBlank()
                        && named.equals(action.apply(value()))) emit(new UiEvent.Action(named));
                }
            });
        }

        @Override boolean identityRows() {
            return value().items().stream().anyMatch(item->item.leading().filter(n->n instanceof Nodes.Icon && view.artworkPresentation(n.id())==ArtworkPresentation.IDENTITY_ROW).isPresent());
        }
        Nodes.ItemList value() {
            return (Nodes.ItemList) node;
        }

        @Override
        int size() {
            return value().items().size();
        }

        @Override
        String itemId(int i) {
            return value().items().get(i).id();
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
            var item = item(id);
            return item == null ? List.of() : item.contextActions().orElse(value().contextActions());
        }

        @Override
        boolean ownsContextActions(String id) {
            var item = item(id);
            return item != null && item.contextActions().isPresent();
        }

        ListItem item(String id) {
            for (var item : value().items()) if (item.id().equals(id)) return item;
            return null;
        }

        /**
         * Space on a row whose trailing slot holds exactly one switch operates that switch (it reports {@code Toggled},
         * once, without selecting or activating the row), so a list of switch rows stays one focus stop; a disabled
         * switch makes Space do nothing. Other rows select as usual.
         */
        @Override
        void space(String id) {
            var item = item(id);
            var toggles = item == null ? List.<Nodes.Toggle>of() : item.trailing().stream()
                    .filter(Nodes.Toggle.class::isInstance).map(Nodes.Toggle.class::cast).toList();
            if (toggles.size() != 1) { super.space(id); return; }
            var toggle = toggles.getFirst();
            if (toggle.state().visible() && toggle.state().enabled() && !toggle.state().busy())
                emit(new UiEvent.Toggled(toggle.id(), !toggle.checked()));
        }

        /** List rows report a plain left click as {@link UiEvent.Clicked}, as tables and trees do; RESULTS rows do not. */
        @Override
        boolean reportsClicks() {
            return !results() && !identityRows();
        }

        @Override
        String enterKey() {
            return value().enterAction().isBlank() ? activation() : value().enterAction();
        }

        /** A list's own Enter action selects the focused row and reports it; otherwise Enter activates as usual. */
        @Override
        void enter(String id) {
            String action = value().enterAction();
            if (action.isBlank()) { activate(id); return; }
            if (indexOf(id) < 0 || !selectable(indexOf(id))) return;
            select(id, false);
            if (stillCurrent(id) && action.equals(value().enterAction())) emit(new UiEvent.Action(action));
        }

        @Override
        String deletion() {
            return value().deleteAction();
        }

        @Override
        int lines() {
            return value().kind() == Nodes.ListKind.DOUBLE ? 2 : 1;
        }

        @Override
        String emptyText() {
            return value().emptyText();
        }

        boolean results() {
            return value().kind() == Nodes.ListKind.RESULTS;
        }

        /** A list of plain rows: not results, identity rows, or a panel's compact or manager column rows (plan 9b Task 5). */
        boolean plainRows() { return !results() && !identityRows() && !panelCompact && column < 0; }

        /** Plan 9b Rulings 12: a plain list's heading bands from the table face (none by default), under the rows' fills. */
        @Override
        void paintBands(java.awt.Graphics2D g, int width) { if (plainRows()) context().faces().table.paintListHeadings(this, g, width); }

        /** A plain list's heading opens with the row hairline only where the table face rules it (the classic band draws its own rules). */
        @Override
        boolean headingRule() { return !plainRows() || context().faces().table.headingRule(); }

        /** A plain list's heading height with its rules ({@code LIST_HEADING_HEIGHT}), or 0 when headings are as tall as the rows. */
        int headingHeight() { return plainRows() ? ThemePainting.metric(context(), DesignMetrics.Role.LIST_HEADING_HEIGHT) : 0; }

        /** A plain two-line list's {@code LIST_TWO_LINE_ROW_HEIGHT}, or 0 to measure its rows. */
        @Override
        int fixedRowHeight() {
            // A classic ordered list's rows are ORDERED_ROW_HEIGHT tall (the Shell, Font & Colors and Project Index mocks' 20, plan 9b Task 6).
            if (sideColumn()) return ThemePainting.metric(context(), DesignMetrics.Role.ORDERED_ROW_HEIGHT);
            return lines() == 2 && plainRows() ? ThemePainting.metric(context(), DesignMetrics.Role.LIST_TWO_LINE_ROW_HEIGHT) : 0;
        }

        @Override
        boolean dataRow(int index) {
            return index < 0 || index >= size() || !value().items().get(index).heading();
        }

        /** RESULTS rows and headings keep measured minimum heights; headings elsewhere are as tall as the rows. */
        @Override
        boolean uniformRows() {
            return !results() && !identityRows() && headingHeight() == 0;
        }

        @Override
        int rowHeightAt(int index) {
            if (headingHeight() > 0) return value().items().get(index).heading() ? headingHeight() : rowHeight;
            if(identityRows())return value().items().get(index).heading()?ThemePainting.height(context(),DesignMetrics.Role.IDENTITY_HEADING_HEIGHT,Typography.Role.CAPTION):rowHeight;
            if (results() && view.runWindow()) return RunWindowPresentation.rowHeight(context(), value().items().get(index).heading());
            return ResultMetrics.height(context(), value().items().get(index).heading());
        }

        @Override
        OptionalInt visibleRows() {
            return value().visibleRows();
        }

        /**
         * A list with {@code visibleRows} that a field in the same view names as its {@code listId} (a filter-driven list,
         * such as a picker's search results) keeps its visible-rows height while the filter narrows it, as a Swing list's
         * visible row count did; every other list sizes to its rows. RESULTS lists reserve their fixed data-row height.
         */
        @Override
        boolean keepsVisibleHeight() {
            return visibleRows().isPresent() && view.namesList(node.id());
        }

        @Override
        int reservedRowHeight() {
            return results() && view.runWindow() ? RunWindowPresentation.rowHeight(context(), false) : results() ? ResultMetrics.height(context(), false) : rowHeight;
        }

        @Override
        boolean externallyDriven() {
            return results();
        }

        @Override
        boolean resultsLayout() {
            return results();
        }

        /**
         * A RESULTS list follows its host: whenever the host updates the list, the selected row (with the heading directly
         * above it) is scrolled into view; a theme or font refresh keeps the scroll position.
         */
        @Override
        void update(Nodes.Node next) {
            boolean changed = next != node;
            super.update(next);
            if (changed && results() && !closed) {
                String first = selected.stream().min(Comparator.comparingInt(this::indexOf)).orElse(null);
                if (first != null) { pendingReveal = first; sync(); }
            }
        }

        /** RESULTS scroll as little as possible, first to the heading above the row and then to the row itself. */
        @Override
        void position(int index) {
            if (!results()) { super.position(index); return; }
            var viewport = scroll.getViewport();
            int y = viewport.getViewPosition().y, h = viewport.getExtentSize().height;
            if (index > 0 && !selectable(index - 1)) y = visible(y, h, top(index - 1), heightAt(index - 1));
            y = visible(y, h, top(index), heightAt(index));
            if (y != viewport.getViewPosition().y) viewport.setViewPosition(new java.awt.Point(0, y));
        }

        static int visible(int y, int extent, int top, int height) {
            if (top < y) return top;
            if (top + height > y + extent) return Math.max(0, top + height - extent);
            return y;
        }

        /** Descriptor-only measurement keeps offscreen choices from allocating Swing widgets. */
        @Override
        int minimumRowHeight() {
            measuredFonts.clear();
            measuredInputs.clear();
            int height = identityRows()?ThemePainting.height(context(),DesignMetrics.Role.IDENTITY_ROW_HEIGHT,Typography.Role.BODY):0;
            for (var item : value().items()) {
                int middle = slotHeight(item.title()) + slotHeight(item.subtitle());
                if (middle > 0)
                    middle += ThemePainting.metric(context(), DesignMetrics.Role.ROW_VERTICAL_PADDING);
                int side =
                        Math.max(
                                item.leading().map(this::inlineHeight).orElse(0),
                                slotHeight(item.trailing()));
                height = Math.max(height, Math.max(middle, side));
            }
            return value().kind() == Nodes.ListKind.ORDERED ? Math.max(height, ThemePainting.metric(context(), DesignMetrics.Role.ORDERED_ROW_HEIGHT)) : height;
        }

        @Override
        int compactRowHeight() {
            int vertical = 2 * ThemePainting.metric(context(), DesignMetrics.Role.PANEL_COMPACT_ROW_VERTICAL), height = 0;
            for (var item : value().items()) {
                int middle = slotHeight(item.title()) + slotHeight(item.subtitle());
                int side = Math.max(item.leading().map(this::inlineHeight).orElse(0), slotHeight(item.trailing()));
                height = Math.max(height, Math.max(middle, side) + vertical);
            }
            return height;
        }

        int slotHeight(List<Nodes.Node> nodes) {
            return nodes.stream().mapToInt(this::inlineHeight).max().orElse(0);
        }

        int fontHeight(Typography.Role role) {
            return measuredFonts.computeIfAbsent(
                    role,
                    key -> canvas.getFontMetrics(ThemePainting.font(context(), key)).getHeight());
        }

        int inputHeight(DesignMetrics.Role metric, Typography.Role font) {
            return measuredInputs.computeIfAbsent(
                    metric, key -> ThemePainting.height(context(), key, font));
        }

        int inlineHeight(Nodes.Node node) {
            if (!node.state().visible()) return 0;
            return switch (node) {
                case Nodes.Choice choice -> {
                    int input =
                            switch (choice.kind()) {
                                case DROPDOWN, SEARCHABLE ->
                                        inputHeight(
                                                DesignMetrics.Role.FIELD_HEIGHT,
                                                Typography.Role.BODY);
                                case PICKER ->
                                        inputHeight(
                                                DesignMetrics.Role.BUTTON_HEIGHT,
                                                Typography.Role.LABEL);
                                case PREVIEWS -> ThemePainting.metric(context(),DesignMetrics.Role.THEME_PREVIEW_HEIGHT)+ThemePainting.metric(context(),DesignMetrics.Role.THEME_PREVIEW_CAPTION_GAP)+fontHeight(Typography.Role.SETTINGS_PREVIEW)
                                    +2*ThemePainting.metric(context(),DesignMetrics.Role.CARD_PADDING)+ThemePainting.metric(context(),DesignMetrics.Role.HAIRLINE);
                                case EXCLUSIVE, TABS ->
                                        inputHeight(
                                                        DesignMetrics.Role.FORM_SEGMENT_HEIGHT,
                                                        Typography.Role.LABEL)
                                                + 2
                                                        * ThemePainting.metric(
                                                                context(),
                                                                DesignMetrics.Role.FORM_SEGMENT_PADDING);
                            };
                    yield input
                            + (choice.state().error().isEmpty()
                                    ? 0
                                    : fontHeight(Typography.Role.CAPTION)
                                            + ThemePainting.metric(
                                                    context(), DesignMetrics.Role.ICON_GAP));
                }
                case Nodes.Stack stack -> slotHeight(stack.children());
                case Nodes.Text text -> fontHeight(Typography.Role.valueOf(text.role().name()));
                case Nodes.Icon icon ->
                        com.formdev.flatlaf.util.UIScale.scale(context().icons().size(context().metrics(), icon.size()));
                case Nodes.Button _, Nodes.MenuButton _ ->
                        inputHeight(DesignMetrics.Role.BUTTON_HEIGHT, Typography.Role.LABEL);
                case Nodes.Toggle toggle -> context().faces().toggle.rowHeight(context(), toggle.compact());
                default -> fontHeight(Typography.Role.BODY);
            };
        }

        @Override
        void data() {
            // The side column's own commands are not the reconciler's; they follow the theme and fonts with the list.
            if (side != null) side.restyle();
            nodeRows.clear();
            for (int i = 0; i < size(); i++) {
                var item = value().items().get(i);
                final int index = i;
                item.leading().ifPresent(n -> index(n, index));
                item.title().forEach(n -> index(n, index));
                item.subtitle().forEach(n -> index(n, index));
                item.trailing().forEach(n -> index(n, index));
            }
            if (focusControl != null
                    && !nodeRows.containsKey(focusControl)
                    && !(value().kind() == Nodes.ListKind.ORDERED
                            && focusItem != null
                            && List.of("up", "down", "remove").stream()
                                    .anyMatch(
                                            suffix ->
                                                    focusControl.equals(
                                                            internalId(focusItem, suffix)))))
                focusControl = null;
        }

        void index(Nodes.Node n, int index) {
            nodeRows.put(n.id(), index);
            ControlRegistry.children(n).forEach(child -> index(child, index));
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

        /** The add action's container: a classic side column, or the canvas that holds it under the rows. */
        @Override
        Container childParent(Nodes.Node child) {
            return side != null && sideColumn() ? side : canvas;
        }

        /** Whether this is an ORDERED list whose face gives its rows no commands, so a side column holds them (plan 4c). */
        boolean sideColumn() {
            return value().kind() == Nodes.ListKind.ORDERED && !rowCommands();
        }

        /**
         * Whether an ORDERED list's rows carry their own commands: under the face that draws them, and always for a list
         * without selection, whose side column could never have a row to act on.
         */
        boolean rowCommands() {
            return context().faces().ordered.rowCommands() || value().selectionMode() == Nodes.SelectionMode.NONE;
        }

        @Override
        void children(List<ControlRegistry.Control> controls) {
            // An entry row stays adopted only while a VERTICAL parent adopts it again on this pass (ClassicOrderedFace.span).
            if (!spanned && entry != null) entry(null);
            // A list that kept its id but no longer takes a side column gives it back, with the box and its layout.
            if (side != null && !sideColumn()) {
                scroll.remove(side);
                side = null;
                scroll.setLayout(new ScrollPaneLayout.UIResource());
                scroll.setBorder(BorderFactory.createEmptyBorder());
            }
            if (sideColumn()) {
                // The side column holds the add action; nothing sits under the rows.
                if (footer != null && footer.getParent() == canvas) canvas.remove(footer);
                footer = null;
                context().faces().ordered.mount(this, controls.isEmpty() ? null : controls.getFirst());
                canvas.revalidate();
                sync();
                return;
            }
            JComponent next = controls.isEmpty() ? null : controls.getFirst().component;
            if (footer != next && footer != null) canvas.remove(footer);
            footer = next;
            if (footer instanceof Buttons.KitButton button && value().kind() == Nodes.ListKind.ORDERED) {button.orderedAction=true;controls.getFirst().refresh();button.setHorizontalAlignment(SwingConstants.LEADING);}
            if (footer != null && footer.getParent() != canvas) canvas.add(footer);
            canvas.revalidate();
            sync();
        }

        @Override
        void populate(InteractiveRows.Row row, int index) {
            var item = value().items().get(index);
            row.begin();
            var leading = new ArrayList<Nodes.Node>(item.leading().stream().toList());
            var trailing = new ArrayList<Nodes.Node>(item.trailing());
            if (value().kind() == Nodes.ListKind.ORDERED) {
                leading.addFirst(
                        new Nodes.Text(
                                internalId(row.id, "index"),
                                ControlState.READY,
                                Integer.toString(index + 1),
                                context().faces().ordered.indexRole()));
                if (rowCommands()) {
                    trailing.add(commandNode(row, "up", "chevron.up", "Move up", index > 0));
                    trailing.add(
                            commandNode(row, "down", "chevron.down", "Move down", index < size() - 1));
                    trailing.add(commandNode(row, "remove", "close", "Remove", true));
                }
            }
            row.slot(row.leading, leading);
            if (value().kind() == Nodes.ListKind.ORDERED && row.controls.get(internalId(row.id, "index")) instanceof Labels.TextControl number) {
                // A fixed index column keeps every key glyph and name on one vertical line.
                ((Labels.LiteralLabel) number.component).columnWidth = ThemePainting.metric(context(), DesignMetrics.Role.ORDERED_INDEX_WIDTH);
                // A selected row's index keeps the ink its refresh gave it.
                if (!row.inked) context().faces().ordered.styleIndex((JLabel) number.component, context(), index);
            }
            if (item.leading().orElse(null) instanceof Nodes.Icon icon && (icon.size() == Nodes.IconSize.RAIL || icon.size() == Nodes.IconSize.PROMPT) && icon.hue() != Nodes.Hue.NONE) {
                ((Labels.IconControl)row.controls.get(icon.id())).tile(context().metrics().value(value().kind() == Nodes.ListKind.DOUBLE ? DesignMetrics.Role.CATEGORY_ROW_TILE : DesignMetrics.Role.CATEGORY_RAIL_TILE), selected.contains(item.id()));
            } else if (item.leading().orElse(null) instanceof Nodes.Icon icon && row.controls.get(icon.id()) instanceof Labels.IconControl glyph)
                // A compact panel row draws its leading glyph at the panel-row size; elsewhere the icon's size role holds.
                glyph.glyph(results() && view.runWindow() && icon.size() == Nodes.IconSize.SWATCH ? context().metrics().value(DesignMetrics.Role.RUN_WINDOW_SWATCH)
                    : panelCompact ? context().metrics().value(DesignMetrics.Role.PANEL_COMPACT_ROW_GLYPH) : 0);
            for(var control:row.controls.values()) if(control instanceof Indicators.DotControl dot) ((Indicators.Dot)dot.component).panelRow=panelSingle||panelCompact;
            for(var control:row.controls.values()) if(control instanceof Indicators.ChipControl chip) chip.panelRow(panelCompact);
            row.slot(row.title, item.title());
            if(panelSingle)for(int i=1;i<item.title().size();i++)if(item.title().get(i) instanceof Nodes.Text text && text.role()==Nodes.TextRole.BODY && row.controls.get(text.id()).component instanceof javax.swing.JLabel label)label.setFont(ThemePainting.font(context(),Typography.Role.PANEL_ROW_SECONDARY));
            // A selected panel row's name takes the selected face; its ink comes from the row (InteractiveRows.Row.inked).
            if (panelSingle && selected.contains(item.id()) && !item.title().isEmpty() && item.title().getFirst() instanceof Nodes.Text text
                    && text.role()==Nodes.TextRole.BODY && row.controls.get(text.id()).component instanceof javax.swing.JLabel label)
                label.setFont(ThemePainting.font(context(),Typography.Role.PANEL_ROW_SELECTED));
            // RESULTS section headings use their own caption size, smaller than the rows they introduce.
            if (results() && item.heading())
                for (var node : item.title()) if (node instanceof Nodes.Text && row.controls.get(node.id()).component instanceof JLabel label)
                    label.setFont(ThemePainting.font(context(), Typography.Role.RESULTS_HEADING));
            row.slot(row.subtitle, item.subtitle());
            row.slot(row.trailing, trailing);
            // On a selected row the subtitle (and a result's tag) reads as supporting text on the fill, whatever its role.
            if (row.inked) {
                for (var node : item.subtitle()) supportingInk(row, node);
                if (results()) for (var node : item.trailing()) supportingInk(row, node);
            }
            if(results() && !item.heading() && !selected.contains(item.id()))
                for(var node:item.subtitle())if(node instanceof Nodes.Text text && text.severity().isEmpty()
                        && row.controls.get(text.id()).component instanceof JLabel label)
                    label.setForeground(ThemePainting.color(context(),SemanticColor.TEXT_TERTIARY));
            if (results() && !item.heading() && selected.contains(item.id())) {
                resultSelection(row, item.title(), true);
                resultSelection(row, item.subtitle(), true);
                resultSelection(row, item.trailing(), false);
            }
            for(var control:row.controls.values())if(control instanceof Buttons.ButtonControl button)button.button.panelRow=panelSingle;
            if (results() && view.runWindow()) RunWindowPresentation.row(context(), row, item, selected.contains(item.id()));
            row.tooltip(item.tooltip());
            row.name(item.title());
            if (value().kind() == Nodes.ListKind.ORDERED && rowCommands()) {
                command(
                        row,
                        "up",
                        index > 0,
                        () -> emit(new UiEvent.Reordered(node.id(), row.id, row.index - 1)));
                command(
                        row,
                        "down",
                        index < size() - 1,
                        () -> emit(new UiEvent.Reordered(node.id(), row.id, row.index + 1)));
                command(row, "remove", true, () -> emit(new UiEvent.Removed(node.id(), row.id)));
            }
        }

        /** A selected row's supporting text, inline stacks included, in its fill's supporting ink; a severity keeps its own. */
        private void supportingInk(InteractiveRows.Row row, Nodes.Node node) {
            if (node instanceof Nodes.Stack stack) for (var child : stack.children()) supportingInk(row, child);
            else if (node instanceof Nodes.Text text && text.severity().isEmpty() && row.controls.get(text.id()) != null
                    && row.controls.get(text.id()).component instanceof JLabel label)
                label.setForeground(ThemePainting.color(context(), row.owner.ink().supporting));
        }

        /** A selected result's strong face; its ink comes from the row (InteractiveRows.Row.inked). */
        private void resultSelection(InteractiveRows.Row row, List<Nodes.Node> nodes, boolean strong) {
            for (var node : nodes) if (node instanceof Nodes.Text text
                    && row.controls.get(text.id()).component instanceof JLabel label) {
                label.setFont(SwingTypography.effective(context().typography().resultFont(
                    Typography.Role.valueOf(text.role().name()), strong)));
            }
        }

        /** Adopts {@code next} as the entry row the side column spans, or gives up the current one for null; a held row stays put. */
        void entry(JComponent next) {
            spanned = true;
            if (entry == next && (next == null || next.getParent() == scroll)) return;
            if (entry != null && entry != next && entry.getParent() == scroll) scroll.remove(entry);
            entry = next;
            if (entry != null && entry.getParent() != scroll) scroll.add(entry);
            scroll.revalidate();
            scroll.repaint();
        }

        /** The side column's commands follow the selection after every pass over the rows. */
        @Override
        void layoutAccessories(int width) {
            if (side != null) side.sync();
        }

        String internalId(String item, String suffix) {
            String id = "@ordered/" + node.id() + "/" + item + "/" + suffix;
            while (nodeRows.containsKey(id)) id = "@" + id;
            return id;
        }

        Nodes.Button commandNode(
                InteractiveRows.Row row,
                String suffix,
                String icon,
                String title,
                boolean possible) {
            return new Nodes.Button(
                    internalId(row.id, suffix),
                    new ControlState(true, possible, false, ""),
                    "",
                    icon,
                    Nodes.ButtonKind.GHOST,
                    suffix,
                    title);
        }

        void command(InteractiveRows.Row row, String suffix, boolean possible, Runnable action) {
            var c = (Buttons.ButtonControl) row.controls.get(internalId(row.id, suffix));
            c.button.disabledOpacity = possible ? .42f : .30f;
            if (!c.button.orderedCommand) { c.button.orderedCommand = true; c.refresh(); }
            if (c.button.getClientProperty("Jasper.rowCommand") == null) {
                for (var listener : c.button.getActionListeners())
                    c.button.removeActionListener(listener);
                java.awt.event.ActionListener listener =
                        e -> {
                            if (interactive() && c.button.isEnabled())
                                ((Runnable) c.button.getClientProperty("Jasper.rowCommand")).run();
                        };
                c.button.addActionListener(listener);
                c.own(() -> c.button.removeActionListener(listener));
            }
            c.button.putClientProperty("Jasper.rowCommand", action);
        }
    }

    /** An item list's scroll pane: the classic ordered face paints its box here and lays out its side column. */
    static final class ListPane extends JScrollPane {
        ListControl owner;

        @Override protected void paintComponent(java.awt.Graphics graphics) {
            super.paintComponent(graphics);
            // Only a list with a side column has the classic box (a passive ORDERED list keeps its row commands instead).
            if (owner == null || !owner.sideColumn()) return;
            var g = (java.awt.Graphics2D) graphics.create();
            try { owner.context().faces().ordered.paintPane(owner, g); } finally { g.dispose(); }
        }
    }

    /**
     * A classic ordered list's side column (spec 6.4): the list's add action, then Remove, Move Up and Move Down, equal
     * buttons acting on the selected row and emitting the row commands' events (plan 4c Rulings 14).
     */
    static final class SideColumn extends JPanel {
        final ListControl owner;
        final Buttons.ButtonControl removeCommand, upCommand, downCommand;
        /** The list's add action, or null. */
        ControlRegistry.Control addControl;

        SideColumn(ListControl owner) {
            super(null);
            this.owner = owner;
            setOpaque(false);
            KitOwnership.mark(this, "@ordered/" + owner.node.id() + "/side");
            removeCommand = command("remove", "Remove");
            upCommand = command("up", "Move Up");
            downCommand = command("down", "Move Down");
            owner.own(() -> { removeCommand.close(); upCommand.close(); downCommand.close(); });
        }

        private Nodes.Button node(String suffix, String label, boolean possible) {
            return new Nodes.Button("@ordered/" + owner.node.id() + "/side/" + suffix, new ControlState(true, possible, false, ""), label, "",
                Nodes.ButtonKind.SECONDARY, suffix, "");
        }

        private Buttons.ButtonControl command(String suffix, String label) {
            var control = new Buttons.ButtonControl(owner.view, node(suffix, label, false), event -> run(suffix));
            control.button.sideColumn = true;
            add(control.component);
            return control;
        }

        /** The add action first, as a standard button without its glyph (spec 6.4); null removes it. */
        void addAction(ControlRegistry.Control action) {
            var next = action == null ? null : action.component;
            if (addControl != null && addControl.component != next && addControl.component.getParent() == this) remove(addControl.component);
            addControl = action;
            if (action instanceof Buttons.ButtonControl button) {
                button.button.sideColumn = true;
                button.button.orderedAction = false;
                // The face paints the glyph its client property names, so both go.
                button.button.setIcon(null);
                button.button.setDisabledIcon(null);
                button.button.putClientProperty("Jasper.button.icon", null);
                button.button.setHorizontalAlignment(SwingConstants.CENTER);
            }
            if (next != null && next.getParent() != this) add(next, 0);
            revalidate();
            repaint();
        }

        /** The row the commands act on: the focused row while it is selected, else the first selected row; null for none. */
        String target() {
            if (owner.focusItem != null && owner.selected.contains(owner.focusItem) && owner.indexOf(owner.focusItem) >= 0) return owner.focusItem;
            for (int i = 0; i < owner.size(); i++) if (owner.selected.contains(owner.itemId(i))) return owner.itemId(i);
            return null;
        }

        /** A command's action: acts on {@link #target()}, and on nothing when no row is the target. */
        void run(String suffix) {
            String row = target();
            if (row == null || !owner.interactive()) return;
            int index = owner.indexOf(row);
            switch (suffix) {
                case "remove" -> owner.emit(new UiEvent.Removed(owner.node.id(), row));
                case "up" -> { if (index > 0) owner.emit(new UiEvent.Reordered(owner.node.id(), row, index - 1)); }
                default -> { if (index < owner.size() - 1) owner.emit(new UiEvent.Reordered(owner.node.id(), row, index + 1)); }
            }
        }

        /** Refreshes the column's own commands (their fonts and colours) with the list's refresh. */
        void restyle() {
            removeCommand.refresh();
            upCommand.refresh();
            downCommand.refresh();
        }

        /** Enables each command for the current selection. */
        void sync() {
            String row = target();
            int index = row == null ? -1 : owner.indexOf(row);
            boolean live = owner.interactive();
            update(removeCommand, "remove", "Remove", live && index >= 0);
            update(upCommand, "up", "Move Up", live && index > 0);
            update(downCommand, "down", "Move Down", live && index >= 0 && index < owner.size() - 1);
        }

        private void update(Buttons.ButtonControl command, String suffix, String label, boolean possible) {
            if (((Nodes.Button) command.node).state().enabled() != possible) command.update(node(suffix, label, possible));
        }

        int gap() { return ThemePainting.metric(owner.context(), DesignMetrics.Role.SIDE_BUTTON_GAP); }

        @Override public java.awt.Dimension getPreferredSize() {
            int width = ThemePainting.metric(owner.context(), DesignMetrics.Role.SIDE_COLUMN_WIDTH), height = 0, shown = 0;
            for (var child : getComponents()) if (child.isVisible()) {
                var size = child.getPreferredSize();
                width = Math.max(width, size.width);
                height += size.height;
                shown++;
            }
            return new java.awt.Dimension(width, height + Math.max(0, shown - 1) * gap());
        }

        @Override public void doLayout() {
            int y = 0;
            for (var child : getComponents()) if (child.isVisible()) {
                int height = child.getPreferredSize().height;
                child.setBounds(0, y, getWidth(), height);
                y += height + gap();
            }
        }
    }
}
