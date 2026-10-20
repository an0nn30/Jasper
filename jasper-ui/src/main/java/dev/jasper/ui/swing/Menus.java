package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.SemanticColor;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;

/**
 * Menu-only and split action surfaces with owned popup listeners and content-relative placement.
 */
final class Menus {
    private Menus() {}

    static final class MenuControl extends ControlRegistry.Control {
        final Buttons.KitButton main, arrow;
        final Popup popup;
        final ArrayList<AutoCloseable> items = new ArrayList<>();
        java.util.List<MenuItem> prior = java.util.List.of();
        boolean outsideInstalled;
        final AWTEventListener outside =
                event -> {
                    if (event instanceof MouseEvent mouse
                            && mouse.getID() == MouseEvent.MOUSE_PRESSED
                            && mouse.getSource() instanceof Component source
                            && !SwingUtilities.isDescendingFrom(source, component)
                            && !SwingUtilities.isDescendingFrom(
                                    source, component.getComponentPopupMenu())) dismiss();
                };

        MenuControl(KitView view, Nodes.MenuButton node) {
            super(view, node, new MenuSurface(view.context()));
            component.setOpaque(false);
            main = new Main(context());
            arrow = new Arrow(context());
            ((MenuSurface) component).owner = this;
            popup = new Popup(context());
            component.setComponentPopupMenu(popup);
            KitOwnership.mark(main, node.id() + ".main");
            KitOwnership.mark(arrow, node.id() + ".arrow");
            setup(main);
            setup(arrow);
            component.add(main, BorderLayout.CENTER);
            component.add(arrow, BorderLayout.EAST);
            ActionListener left =
                    e -> {
                        String action = ((Nodes.MenuButton) this.node).action();
                        if (action.isBlank()) open();
                        else emit(new UiEvent.Action(action));
                    };
            ActionListener right = e -> open();
            main.addActionListener(left);
            arrow.addActionListener(right);
            own(() -> main.removeActionListener(left));
            own(() -> arrow.removeActionListener(right));
            own(this::closeItems);
            own(
                    () -> {
                        dismiss();
                        popup.removeAll();
                    });
            PopupMenuListener lifetime =
                    new PopupMenuListener() {
                        @Override
                        public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                            installOutside();
                        }

                        @Override
                        public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                            removeOutside();
                        }

                        @Override
                        public void popupMenuCanceled(PopupMenuEvent e) {
                            removeOutside();
                        }
                    };
            popup.addPopupMenuListener(lifetime);
            own(() -> popup.removePopupMenuListener(lifetime));
            component
                    .getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .put(KeyStroke.getKeyStroke("ESCAPE"), "dismiss");
            component
                    .getActionMap()
                    .put(
                            "dismiss",
                            new AbstractAction() {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    Forms.cancelFrom(view, component);
                                }
                            });
            own(
                    () -> {
                        component
                                .getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                                .remove(KeyStroke.getKeyStroke("ESCAPE"));
                        component.getActionMap().remove("dismiss");
                    });
            refresh();
        }

        void setup(Buttons.KitButton button) {
            button.setOpaque(false);
            button.setContentAreaFilled(false);
            button.setBorderPainted(false);
            button.setFocusPainted(false);
            button.setRolloverEnabled(true);
            button.putClientProperty("html.disable", true);
            button.focus = new ThemePainting.Focus(button, button, view);
            own(button.focus);
        }

        void closeItems() {
            for (var item : items)
                try {
                    item.close();
                } catch (Exception failure) {
                    view.report(failure);
                }
            items.clear();
        }

        void installOutside() {
            if (!outsideInstalled) {
                Toolkit.getDefaultToolkit().addAWTEventListener(outside, AWTEvent.MOUSE_EVENT_MASK);
                outsideInstalled = true;
            }
        }

        void removeOutside() {
            if (outsideInstalled) {
                Toolkit.getDefaultToolkit().removeAWTEventListener(outside);
                outsideInstalled = false;
            }
        }

        boolean dismiss() {
            boolean visible = popup.isVisible();
            popup.setVisible(false);
            removeOutside();
            main.setSelected(false);
            return visible;
        }

        void open() {
            if (!interactive()) return;
            if (popup.isVisible()) {
                dismiss();
                return;
            }
            popup.setInvoker(component);
            main.setSelected(true);
            if (component.isShowing()) {
                Container host = SwingUtilities.getRootPane(component);
                if (host == null) host = view;
                Rectangle anchor =
                        SwingUtilities.convertRectangle(
                                component.getParent(), component.getBounds(), host);
                Point point =
                        anchor(
                                anchor,
                                popup.getPreferredSize(),
                                new Rectangle(0, 0, host.getWidth(), host.getHeight()));
                Point local = SwingUtilities.convertPoint(host, point, component);
                popup.show(component, local.x, local.y);
            } else popup.setVisible(true);
            installOutside();
        }

        @Override
        JComponent focusTarget() {
            return main;
        }

        @Override
        void refresh() {
            state();
            var menu = (Nodes.MenuButton) node;
            boolean embedded = menu.presentation() == Nodes.ButtonPresentation.EMBEDDED || menu.presentation() == Nodes.ButtonPresentation.CONTEXTUAL_EMBEDDED;
            for (var button : java.util.List.of(main, arrow)) {
                button.kind = menu.kind();
                button.setEnabled(component.isEnabled());
                Composition.style(button, context(), context().faces().buttons.label(menu.kind(), Typography.Role.LABEL), button.textRole(false));
                int ring = ThemePainting.metric(context(), DesignMetrics.Role.FOCUS_RING),
                        pad = ThemePainting.metric(context(), DesignMetrics.Role.MENU_ITEM_PADDING);
                button.embedded = embedded;
                button.contextual = menu.presentation() == Nodes.ButtonPresentation.CONTEXTUAL_EMBEDDED;
                button.setBorder(embedded ? new EmptyBorder(0, 0, 0, 0) : new EmptyBorder(ring, menu.label().isBlank()?ring:pad, ring, menu.label().isBlank()?ring:pad));
            }
            ((MenuSurface) component).background.embedded = embedded;
            main.setText(menu.label());
            // A document bar's menu takes the face's compact arrow lane where it has one (plan 10b, the Vault's + New).
            int barLane = main.documentBar ? context().faces().buttons.barMenuLane(context()) : -1;
            ((Main) main).barArrow = barLane >= 0;
            if (menu.action().isBlank() && !embedded && !menu.label().isBlank()) {
                Insets insets = main.getInsets();
                main.setBorder(
                        new EmptyBorder(
                                insets.top,
                                insets.left,
                                insets.bottom,
                                insets.right
                                        + (barLane >= 0 ? barLane : ThemePainting.metric(
                                                context(), DesignMetrics.Role.MENU_ARROW_WIDTH))));
            }
            int extent = com.formdev.flatlaf.util.UIScale.scale(context().icons().size(context().metrics(), Nodes.IconSize.CONTROL));
            var classic = menu.icon().isEmpty() ? null
                    : Buttons.classicChrome(view, view.toolbarStrip() || main.panel && !main.documentBar, menu.icon(), extent);
            main.setIcon(
                    menu.icon().isEmpty()
                            ? null
                            : classic != null
                            ? classic
                            : context()
                                    .icons()
                                    .icon(
                                            menu.icon(),
                                            context().theme().semantic(),
                                            main.documentBar ? context().faces().buttons.iconRole(main, false) : main.textRole(false),
                                            extent));
            main.setDisabledIcon(main.getIcon());
            main.setToolTipText(menu.tooltip().isBlank() ? null : menu.tooltip());
            main.getAccessibleContext()
                    .setAccessibleName(menu.label().isBlank() ? menu.tooltip() : menu.label());
            arrow.setText("");
            arrow.setToolTipText("Open menu");
            arrow.getAccessibleContext().setAccessibleName("Open menu");
            arrow.setVisible(!menu.action().isBlank());
            // A title or toolbar strip's icon-only menu shows its indicator on the borderless control surface.
            boolean strip = (view.titleStrip() || view.toolbarStrip()) && menu.action().isBlank() && !embedded && menu.label().isBlank() && !menu.icon().isEmpty();
            ((Main) main).menuOnly = menu.action().isBlank() && !embedded && (!menu.label().isBlank() || strip);
            if (strip) {
                int ring = ThemePainting.metric(context(), DesignMetrics.Role.FOCUS_RING);
                main.setBorder(new EmptyBorder(ring, ThemePainting.metric(context(), DesignMetrics.Role.MENU_ITEM_PADDING), ring,
                    ThemePainting.metric(context(), DesignMetrics.Role.MENU_ICON_ARROW_WIDTH)));
            }
            ((Main) main).iconMenu = strip;
            ((MenuSurface) component).background.kind = strip ? Nodes.ButtonKind.SECONDARY : menu.kind();
            ((MenuSurface) component).background.borderless = strip;
            popup.restyle();
            if (!prior.equals(menu.items())) {
                closeItems();
                popup.removeAll();
                prior = menu.items();
                for (var descriptor : prior) {
                    if (descriptor.separator()) {
                        popup.add(new Separator(context()));
                        continue;
                    }
                    var item = new Item(context());
                    item.describe(descriptor);
                    ActionListener listener =
                            e -> {
                                if (descriptor.enabled() && interactive()) {
                                    dismiss();
                                    emit(new UiEvent.Action(descriptor.action()));
                                }
                            };
                    item.addActionListener(listener);
                    items.add(() -> item.removeActionListener(listener));
                    popup.add(item);
                }
            }
            int i = 0;
            for (var descriptor : prior) {
                Component raw = popup.getComponent(i++);
                if (raw instanceof Item item) {
                    item.describe(descriptor);
                    item.setEnabled(component.isEnabled() && descriptor.enabled());
                }
            }
            if (!interactive()) dismiss();
            popup.revalidate();
            popup.repaint();
            component.revalidate();
            component.repaint();
        }
    }

    static Point anchor(Rectangle anchor, Dimension menu, Rectangle available) {
        boolean right = anchor.getCenterX() > available.getCenterX();
        int x = right ? anchor.x + anchor.width - menu.width : anchor.x,
                y = anchor.y + anchor.height;
        if (y + menu.height > available.y + available.height) y = anchor.y - menu.height;
        x = Math.max(available.x, Math.min(x, available.x + available.width - menu.width));
        y = Math.max(available.y, Math.min(y, available.y + available.height - menu.height));
        return new Point(x, y);
    }

    static class Part extends Buttons.KitButton {
        Part(KitContext context) {
            super(context);
        }

        @Override
        Color fill(boolean hover) {
            return null;
        }

        @Override
        Color border() {
            return null;
        }

        /** A part paints no face of its own under either buttons face, only its focus ring: the surface paints the face. */
        @Override
        void paintKitFace(Graphics2D g, boolean hover, boolean focused) {
            paintRaised(g, hover, focused);
        }

        /** A part stands where its menu button's surface does. */
        @Override
        Component placement() {
            return getParent() instanceof MenuSurface surface ? surface : this;
        }
    }

    static final class Main extends Part {
        final KitContext context;
        boolean menuOnly, iconMenu;
        /** A document bar's menu under a face with a bar arrow ({@link ButtonFace#barMenuLane}), set on every refresh. */
        boolean barArrow;

        @Override
        public Dimension getPreferredSize() {
            var size = super.getPreferredSize();
            if (!iconMenu || getIcon() == null) return size;
            Insets insets = getInsets();
            return new Dimension(insets.left + getIcon().getIconWidth() + insets.right, size.height);
        }

        Main(KitContext context) {
            super(context);
            this.context = context;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (menuOnly && barArrow) context.faces().buttons.paintBarMenuArrow(this, (Graphics2D) graphics);
            else if (menuOnly)
                chevron(
                        graphics,
                        this,
                        context,
                        getWidth()
                                - ThemePainting.metric(context, DesignMetrics.Role.MENU_ARROW_WIDTH)
                                        / 2);
        }
    }

    static final class MenuSurface extends JPanel {
        final Buttons.KitButton background;
        MenuControl owner;

        MenuSurface(KitContext context) {
            super(new BorderLayout());
            // The face is never added to a parent: it stands where this surface does.
            background = new Buttons.KitButton(context) {
                @Override
                Component placement() {
                    return MenuSurface.this;
                }
            };
            background.setText("");
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (owner == null) return;
            background.setSize(getSize());
            background.setEnabled(isEnabled());
            background.focus = owner.main.focus;
            background.panel = owner.main.panel;
            background
                    .getModel()
                    .setRollover(
                            owner.main.getModel().isRollover()
                                    || owner.arrow.getModel().isRollover());
            background
                    .getModel()
                    .setPressed(
                            owner.main.getModel().isPressed()
                                    || owner.arrow.getModel().isPressed());
            background.paintComponent(graphics);
        }
    }

    static void chevron(Graphics graphics, Buttons.KitButton button, KitContext context, int x) {
        var g = (Graphics2D) graphics.create();
        try {
            ThemePainting.antialias(g);
            g.setColor(
                    ThemePainting.color(context, button.textRole(button.getModel().isRollover())));
            if (!button.isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
            int y = button.getHeight() / 2, d = com.formdev.flatlaf.util.UIScale.scale(3);
            g.drawLine(x - d, y - 1, x, y + d - 1);
            g.drawLine(x, y + d - 1, x + d, y - 1);
        } finally {
            g.dispose();
        }
    }

    static final class Arrow extends Part {
        final KitContext context;

        Arrow(KitContext context) {
            super(context);
            this.context = context;
        }

        @Override
        public Dimension getPreferredSize() {
            var size = super.getPreferredSize();
            return new Dimension(
                    ThemePainting.metric(context, DesignMetrics.Role.MENU_ARROW_WIDTH),
                    size.height);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (!contextualVisible()) return;
            super.paintComponent(graphics);
            chevron(graphics, this, context, getWidth() / 2);
        }
    }

    static final class Popup extends JPopupMenu {
        final KitContext context;

        Popup(KitContext context) {
            this.context = context;
            setOpaque(false);
            restyle();
        }

        /** A menu's card: its padding above and below its rows, its 1 px edge beside them. */
        void restyle() {
            int padding = ThemePainting.metric(context, DesignMetrics.Role.MENU_PADDING), line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
            setBorder(new EmptyBorder(padding, line, padding, line));
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            return new Dimension(
                    Math.max(
                            size.width,
                            ThemePainting.metric(context, DesignMetrics.Role.MENU_MIN_WIDTH)),
                    size.height);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            paintSurface(this, graphics, context);
        }
    }

    static void paintSurface(JComponent component, Graphics graphics, KitContext context) {
            var g = (Graphics2D) graphics.create();
            try {
                // One crisp hairline at the surface edge, as every popup reference paints it.
                ThemePainting.rounded(g, component, context, ThemePainting.color(context, SemanticColor.SURFACE_MENU),
                    ThemePainting.color(context, SemanticColor.BORDER_MENU), false, false, DesignMetrics.Role.CARD_RADIUS);
            } finally {
                g.dispose();
            }
    }

    static final class Item extends JMenuItem {
        final KitContext context;
        /** What this row shows; a highlighted row repaints its category tile as a selected tile. */
        MenuItem descriptor;
        /** The bound shortcut of the row's action, as the menu shows it ("" for none). */
        String shortcutText = "";

        Item(KitContext context) {
            this.context = context;
            // Before any text: Swing parses markup when the text is set, so the property must already be there.
            putClientProperty("html.disable", true);
            setOpaque(false);
            setBorderPainted(false);
        }

        /** Shows {@code item}: its label, its category tile and its enablement, in the menu's type and insets. */
        void describe(MenuItem item) {
            descriptor = item;
            putClientProperty("Jasper.menu.item", item);
            setText(item.label());
            setEnabled(item.enabled());
            Composition.style(this, context, Typography.Role.BODY, SemanticColor.TEXT_PRIMARY);
            int inset = ThemePainting.metric(context, DesignMetrics.Role.MENU_ITEM_PADDING);
            setBorder(new EmptyBorder(0, inset, 0, inset));
            setIcon(item.icon().isBlank() ? null : tile(false));
            shortcutText = context.shortcut(item.shortcut());
            revalidate(); repaint();
        }

        /** A menu resolves its rows' bindings each time it opens, so a rebinding shows at the next open. */
        @Override public void addNotify() {
            super.addNotify();
            if (descriptor != null) {
                String next = context.shortcut(descriptor.shortcut());
                if (!next.equals(shortcutText)) { shortcutText = next; revalidate(); }
            }
        }

        /** The shortcut's font: the label's role, in the platform face when the kit's face lacks one of its symbols (the macOS modifier keys). */
        Font shortcutFont() { return ThemePainting.font(context, Typography.Role.BODY, shortcutText); }

        javax.swing.Icon tile(boolean highlighted) {
            return context.icons().tile(context.metrics(), descriptor.icon(), context.theme().semantic(), descriptor.hue(),
                ThemePainting.metric(context, DesignMetrics.Role.MENU_TILE), highlighted);
        }

        /** The icon column is there when any row of this menu has an icon; then every label starts after it. */
        boolean iconColumn() {
            if (getParent() == null) return getIcon() != null;
            for (var sibling : getParent().getComponents()) if (sibling instanceof Item item && item.getIcon() != null) return true;
            return false;
        }

        int labelX() {
            int x = getInsets().left;
            return iconColumn() ? x + ThemePainting.metric(context, DesignMetrics.Role.MENU_TILE) + ThemePainting.metric(context, DesignMetrics.Role.MENU_TILE_GAP) : x;
        }

        @Override public Dimension getPreferredSize() {
            var fm = getFontMetrics(getFont());
            int width = labelX() + fm.stringWidth(getText()) + getInsets().right;
            if (!shortcutText.isEmpty())
                width += ThemePainting.metric(context, DesignMetrics.Role.MENU_SHORTCUT_GAP) + getFontMetrics(shortcutFont()).stringWidth(shortcutText);
            return new Dimension(width, ThemePainting.height(context, DesignMetrics.Role.MENU_ITEM_HEIGHT, Typography.Role.BODY));
        }

        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                ThemePainting.antialias(g);
                boolean highlighted = isEnabled() && (getModel().isArmed() || getModel().isRollover());
                if (highlighted) {
                    g.setColor(ThemePainting.color(context, SemanticColor.ACCENT));
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
                if (getIcon() != null) {
                    var icon = highlighted && descriptor != null ? tile(true) : getIcon();
                    icon.paintIcon(this, g, getInsets().left, (getHeight() - icon.getIconHeight()) / 2);
                }
                g.setFont(getFont());
                var fm = g.getFontMetrics();
                int baseline = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g.setColor(ThemePainting.color(context, !isEnabled() ? SemanticColor.TEXT_PLACEHOLDER : highlighted ? SemanticColor.TEXT_ONACCENT : SemanticColor.TEXT_PRIMARY));
                g.drawString(getText(), labelX(), baseline);
                if (!shortcutText.isEmpty()) {
                    g.setColor(ThemePainting.color(context, !isEnabled() ? SemanticColor.TEXT_PLACEHOLDER : highlighted ? SemanticColor.TEXT_ONACCENT : SemanticColor.TEXT_TERTIARY));
                    g.setFont(shortcutFont());
                    g.drawString(shortcutText, getWidth() - getInsets().right - g.getFontMetrics().stringWidth(shortcutText), baseline);
                }
            } finally {
                g.dispose();
            }
        }
    }

    static final class Separator extends JSeparator {
        final KitContext context;

        Separator(KitContext context) {
            this.context = context;
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(
                    1,
                    2 * ThemePainting.metric(context, DesignMetrics.Role.MENU_SEPARATOR_MARGIN)
                            + com.formdev.flatlaf.util.UIScale.scale(1));
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(ThemePainting.color(context, SemanticColor.BORDER_MENU));
            int i = ThemePainting.metric(context, DesignMetrics.Role.MENU_SEPARATOR_MARGIN);
            g.drawLine(i, getHeight() / 2, getWidth() - i, getHeight() / 2);
        }
    }
}
