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

        ListControl(KitView view, Nodes.ItemList node) {
            super(view, node);
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

        @Override
        boolean dataRow(int index) {
            return index < 0 || index >= size() || !value().items().get(index).heading();
        }

        /** RESULTS rows and headings keep measured minimum heights; headings elsewhere are as tall as the rows. */
        @Override
        boolean uniformRows() {
            return !results() && !identityRows();
        }

        @Override
        int rowHeightAt(int index) {
            if(identityRows())return value().items().get(index).heading()?ThemePainting.height(context(),DesignMetrics.Role.IDENTITY_HEADING_HEIGHT,Typography.Role.CAPTION):rowHeight;
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
            return results() ? ResultMetrics.height(context(), false) : rowHeight;
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
                case Nodes.Toggle toggle ->
                        ThemePainting.metric(
                                context(),
                                toggle.compact()
                                        ? DesignMetrics.Role.COMPACT_SWITCH_HEIGHT
                                        : DesignMetrics.Role.SWITCH_HEIGHT);
                default -> fontHeight(Typography.Role.BODY);
            };
        }

        @Override
        void data() {
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

        @Override
        Container childParent(Nodes.Node child) {
            return canvas;
        }

        @Override
        void children(List<ControlRegistry.Control> controls) {
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
                                Nodes.TextRole.CAPTION));
                trailing.add(commandNode(row, "up", "chevron.up", "Move up", index > 0));
                trailing.add(
                        commandNode(row, "down", "chevron.down", "Move down", index < size() - 1));
                trailing.add(commandNode(row, "remove", "close", "Remove", true));
            }
            row.slot(row.leading, leading);
            if (value().kind() == Nodes.ListKind.ORDERED && row.controls.get(internalId(row.id, "index")) instanceof Labels.TextControl number) {
                // A fixed index column keeps every key glyph and name on one vertical line.
                ((Labels.LiteralLabel) number.component).columnWidth = ThemePainting.metric(context(), DesignMetrics.Role.ORDERED_INDEX_WIDTH);
            }
            if (item.leading().orElse(null) instanceof Nodes.Icon icon && (icon.size() == Nodes.IconSize.RAIL || icon.size() == Nodes.IconSize.PROMPT) && icon.hue() != Nodes.Hue.NONE) {
                ((Labels.IconControl)row.controls.get(icon.id())).tile(context().metrics().value(value().kind() == Nodes.ListKind.DOUBLE ? DesignMetrics.Role.CATEGORY_ROW_TILE : DesignMetrics.Role.CATEGORY_RAIL_TILE), selected.contains(item.id()));
            } else if (item.leading().orElse(null) instanceof Nodes.Icon icon && row.controls.get(icon.id()) instanceof Labels.IconControl glyph)
                // A compact panel row draws its leading glyph at the panel-row size; elsewhere the icon's size role holds.
                glyph.glyph(panelCompact ? context().metrics().value(DesignMetrics.Role.PANEL_COMPACT_ROW_GLYPH) : 0);
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
            row.tooltip(item.tooltip());
            row.name(item.title());
            if (value().kind() == Nodes.ListKind.ORDERED) {
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
}
