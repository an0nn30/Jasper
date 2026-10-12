package dev.jasper.ui.swing;

import com.formdev.flatlaf.FlatClientProperties;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Crumb;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * A field-shaped path of crumbs that turns into a typed path. Crumbs are literal, unfocusable and as wide as their
 * labels; when they do not fit, the leading ones slide out on the left so the last stays visible. EDT only.
 */
final class BreadcrumbControls {
    private BreadcrumbControls() {}

    static final String SEPARATOR = "›";

    static final class BreadcrumbsControl extends ControlRegistry.Control {
        final JPanel cards = new JPanel(new CardLayout());
        final JPanel crumbs = new JPanel(new TailLayout());
        final JTextField field = new JTextField();
        final TextFields.FieldSurface surface;
        final Menus.Popup popup;
        final List<Runnable> popupCleanup = new ArrayList<>();
        final List<Runnable> crumbCleanup = new ArrayList<>();
        boolean applying, notifying, shownEditing;
        long valueRevision;

        BreadcrumbsControl(KitView view, Nodes.Breadcrumbs node) {
            super(view, node, new JPanel(new BorderLayout()));
            component.setOpaque(false);
            surface = new TextFields.FieldSurface(context(), field, false);
            var focus = new ThemePainting.Focus(field, surface, view);
            surface.focus = focus;
            own(focus);
            KitOwnership.mark(surface, "@crumbs/" + node.id() + "/box");
            KitOwnership.mark(crumbs, "@crumbs/" + node.id() + "/crumbs");
            KitOwnership.mark(field, "@crumbs/" + node.id() + "/field");
            cards.setOpaque(false);
            crumbs.setOpaque(false);
            field.setOpaque(false);
            field.setBorder(new EmptyBorder(0, 0, 0, 0));
            cards.add(crumbs, "crumbs");
            cards.add(field, "field");
            surface.add(cards, BorderLayout.CENTER);
            component.add(surface, BorderLayout.CENTER);
            popup = new Menus.Popup(context());
            component.putClientProperty(KitOwnership.CONTEXT_MENU, popup);
            own(() -> { popup.setVisible(false); clearPopup(); });
            own(() -> { crumbCleanup.forEach(Runnable::run); crumbCleanup.clear(); });

            MouseAdapter area = new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent event) {
                    String action = value().editAction();
                    if (SwingUtilities.isLeftMouseButton(event) && !action.isBlank()) emit(new UiEvent.Action(action));
                }
            };
            crumbs.addMouseListener(area);
            own(() -> crumbs.removeMouseListener(area));
            DocumentListener typing = new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { changed(); }
                @Override public void removeUpdate(DocumentEvent e) { changed(); }
                @Override public void changedUpdate(DocumentEvent e) { changed(); }
                private void changed() {
                    if (applying || !value().editing()) return;
                    notifying = true;
                    try { emit(new UiEvent.Edited(node.id(), field.getText())); } finally { notifying = false; }
                }
            };
            field.getDocument().addDocumentListener(typing);
            own(() -> field.getDocument().removeDocumentListener(typing));
            bind("ENTER", "crumbs.submit", () -> value().submitAction());
            bind("ESCAPE", "crumbs.cancel", () -> value().cancelAction());
            FocusListener leaving = new FocusAdapter() {
                @Override public void focusLost(FocusEvent event) {
                    if (!event.isTemporary() && value().editing() && !value().cancelAction().isBlank())
                        emit(new UiEvent.Action(value().cancelAction()));
                }
            };
            field.addFocusListener(leaving);
            own(() -> field.removeFocusListener(leaving));
            refresh();
        }

        Nodes.Breadcrumbs value() {
            return (Nodes.Breadcrumbs) node;
        }

        private void bind(String stroke, String name, java.util.function.Supplier<String> action) {
            KeyStroke key = KeyStroke.getKeyStroke(stroke);
            field.getInputMap(JComponent.WHEN_FOCUSED).put(key, name);
            field.getActionMap().put(name, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) {
                    String id = action.get();
                    if (value().editing() && !id.isBlank()) emit(new UiEvent.Action(id));
                }
            });
            own(() -> { field.getInputMap(JComponent.WHEN_FOCUSED).remove(key); field.getActionMap().remove(name); });
        }

        @Override
        void update(Nodes.Node next) {
            var previous = value();
            var crumbsNode = (Nodes.Breadcrumbs) next;
            if (!previous.value().equals(crumbsNode.value()) || !shownEditing && crumbsNode.editing()) {
                long revision = ++valueRevision;
                if (!field.getText().equals(crumbsNode.value())) {
                    if (notifying) EventQueue.invokeLater(() -> { if (!closed && valueRevision == revision) setValue(crumbsNode.value()); });
                    else setValue(crumbsNode.value());
                }
            }
            super.update(next);
        }

        private void setValue(String text) {
            applying = true;
            try { field.setText(text); } finally { applying = false; }
        }

        @Override
        JComponent focusTarget() {
            return value().editing() ? field : crumbs;
        }

        @Override
        void refresh() {
            var node = value();
            state();
            field.setFont(ThemePainting.font(context(), Typography.Role.MONO));
            field.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_PRIMARY));
            field.setCaretColor(field.getForeground());
            field.setSelectionColor(ThemePainting.color(context(), SemanticColor.SELECTION_BG));
            field.setSelectedTextColor(ThemePainting.color(context(), SemanticColor.TEXT_PRIMARY));
            field.setEditable(component.isEnabled());
            field.getAccessibleContext().setAccessibleName(node.label());
            crumbs.getAccessibleContext().setAccessibleName(node.label());
            surface.role = Typography.Role.MONO;
            int ring = ThemePainting.metric(context(), DesignMetrics.Role.FOCUS_RING);
            int padding = ThemePainting.metric(context(), DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
            surface.setBorder(new EmptyBorder(ring, padding, ring, padding));
            rebuild();
            ((CardLayout) cards.getLayout()).show(cards, node.editing() ? "field" : "crumbs");
            if (node.editing() && !shownEditing) {
                field.requestFocusInWindow();
                field.selectAll();
            }
            shownEditing = node.editing();
            component.revalidate();
            component.repaint();
        }

        private void rebuild() {
            crumbCleanup.forEach(Runnable::run);
            crumbCleanup.clear();
            crumbs.removeAll();
            int pad = ThemePainting.metric(context(), DesignMetrics.Role.BREADCRUMB_CRUMB_PADDING);
            int separatorPad = ThemePainting.metric(context(), DesignMetrics.Role.BREADCRUMB_SEPARATOR_PADDING);
            boolean first = true;
            String currentId = value().crumbs().isEmpty() ? "" : value().crumbs().getLast().id();
            for (Crumb crumb : value().crumbs()) {
                if (!first) {
                    var separator = Labels.literal();
                    KitOwnership.mark(separator, "@crumbs/" + node.id() + "/" + crumb.id() + "/separator");
                    separator.setText(SEPARATOR);
                    separator.setFont(ThemePainting.font(context(), Typography.Role.BODY));
                    separator.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_TERTIARY));
                    separator.setBorder(new EmptyBorder(0, separatorPad, 0, separatorPad));
                    crumbs.add(separator);
                }
                first = false;
                boolean current = crumb.id().equals(currentId);
                var button = new Buttons.KitButton(context()) {
                    // The current crumb reads strong secondary; the others are ghost buttons.
                    @Override SemanticColor textRole(boolean hover) { return current && !hover ? SemanticColor.TEXT_STRONG_SECONDARY : super.textRole(hover); }
                };
                KitOwnership.mark(button, "@crumbs/" + node.id() + "/" + crumb.id());
                button.kind = Nodes.ButtonKind.GHOST;
                button.putClientProperty("html.disable", true);
                // As wide as the label: FlatLaf buttons otherwise pad short names out to a minimum width.
                button.putClientProperty(FlatClientProperties.MINIMUM_WIDTH, 0);
                button.setOpaque(false); button.setContentAreaFilled(false); button.setBorderPainted(false); button.setFocusPainted(false);
                button.setRolloverEnabled(true);
                button.setFocusable(false);
                button.setText(crumb.label());
                button.setFont(ThemePainting.font(context(), Typography.Role.BODY));
                button.setMargin(new Insets(0, pad, 0, pad));
                button.setBorder(new EmptyBorder(0, pad, 0, pad));
                button.setEnabled(component.isEnabled());
                button.getAccessibleContext().setAccessibleName(crumb.label());
                String id = crumb.id();
                ActionListener pressed = event -> emit(new UiEvent.Selected(node.id(), Set.of(id)));
                MouseAdapter menu = new MouseAdapter() {
                    @Override public void mousePressed(MouseEvent event) { if (event.isPopupTrigger()) context(id, button, event.getX(), event.getY()); }
                    @Override public void mouseReleased(MouseEvent event) { if (event.isPopupTrigger()) context(id, button, event.getX(), event.getY()); }
                };
                button.addActionListener(pressed);
                button.addMouseListener(menu);
                crumbCleanup.add(() -> { button.removeActionListener(pressed); button.removeMouseListener(menu); });
                crumbs.add(button);
            }
            crumbs.revalidate();
            crumbs.repaint();
        }

        /** A crumb's own menu; choosing an item reports only the item's action (the host names the crumb in it). */
        void context(String crumbId, JComponent invoker, int x, int y) {
            if (!interactive()) return;
            clearPopup();
            var crumb = value().crumbs().stream().filter(candidate -> candidate.id().equals(crumbId)).findFirst();
            if (crumb.isEmpty()) return;
            for (MenuItem item : crumb.get().contextActions()) {
                if (item.separator()) { popup.add(new Menus.Separator(context())); continue; }
                var entry = new Menus.Item(context());
                entry.setText(item.label());
                entry.putClientProperty("Jasper.menu.item", item);
                entry.putClientProperty("html.disable", true);
                entry.setEnabled(item.enabled());
                Composition.style(entry, context(), Typography.Role.BODY, SemanticColor.TEXT_PRIMARY);
                int inset = ThemePainting.metric(context(), DesignMetrics.Role.MENU_ITEM_PADDING);
                entry.setBorder(BorderFactory.createEmptyBorder(0, inset, 0, inset));
                entry.setIcon(item.icon().isBlank() ? null : context().icons().tile(item.icon(), context().theme().semantic(), item.hue(),
                    ThemePainting.metric(context(), DesignMetrics.Role.MENU_TILE), false));
                // A crumb's menu acts on that crumb without navigating to it, so it reports only the item's action.
                ActionListener chosen = event -> {
                    if (value().crumbs().stream().anyMatch(candidate -> candidate.id().equals(crumbId) && candidate.contextActions().contains(item)))
                        emit(new UiEvent.Action(item.action()));
                };
                entry.addActionListener(chosen);
                popupCleanup.add(() -> entry.removeActionListener(chosen));
                popup.add(entry);
            }
            if (popup.getComponentCount() > 0 && invoker.isShowing()) popup.show(invoker, x, y);
        }

        void clearPopup() {
            popupCleanup.forEach(Runnable::run);
            popupCleanup.clear();
            popup.removeAll();
        }

        boolean dismiss() {
            boolean visible = popup.isVisible();
            popup.setVisible(false);
            return visible;
        }
    }

    /** Left to right; when the crumbs do not fit, the leftmost slide out of view so the last stays visible. */
    static final class TailLayout implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }

        @Override public Dimension preferredLayoutSize(Container parent) {
            int width = 0, height = 0;
            for (Component component : parent.getComponents()) {
                Dimension size = component.getPreferredSize();
                width += size.width;
                height = Math.max(height, size.height);
            }
            Insets insets = parent.getInsets();
            return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
        }

        @Override public Dimension minimumLayoutSize(Container parent) {
            Insets insets = parent.getInsets();
            return new Dimension(insets.left + insets.right, preferredLayoutSize(parent).height);
        }

        @Override public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int available = parent.getWidth() - insets.left - insets.right;
            int needed = preferredLayoutSize(parent).width - insets.left - insets.right;
            int x = insets.left + Math.min(0, available - needed);
            int height = parent.getHeight() - insets.top - insets.bottom;
            for (Component component : parent.getComponents()) {
                int width = component.getPreferredSize().width;
                component.setBounds(x, insets.top, width, height);
                x += width;
            }
        }
    }
}
