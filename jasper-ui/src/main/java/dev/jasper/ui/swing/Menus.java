package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.SemanticColor;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
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
                        trace("main-action");
                        String action = ((Nodes.MenuButton) this.node).action();
                        if (action.isBlank()) open();
                        else emit(new UiEvent.Action(action));
                    };
            ActionListener right = e -> { trace("arrow-action"); open(); };
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
                            trace("popup-will-show"); installOutside();
                        }

                        @Override
                        public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                            trace("popup-will-hide"); removeOutside();
                        }

                        @Override
                        public void popupMenuCanceled(PopupMenuEvent e) {
                            trace("popup-canceled"); removeOutside();
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
            trace("dismiss");
            boolean visible = popup.isVisible();
            popup.setVisible(false);
            removeOutside();
            main.setSelected(false);
            return visible;
        }

        void open() {
            trace("open-entry");
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
                trace("before-show anchor=" + anchor + " local=" + local + " host=" + host.getSize());
                popup.show(component, local.x, local.y);
            } else popup.setVisible(true);
            trace("open-exit");
            installOutside();
        }

        // Temporary native QA diagnostics. Removed before the final repair gate.
        void trace(String phase) {
            if (!Boolean.getBoolean("jasper.nativeTitleTrace") || !(node.id().equals("add") || node.id().equals("more"))) return;
            System.getLogger("jasper.native-title-trace").log(System.Logger.Level.INFO,
                "menu=" + node.id() + " phase=" + phase + " interactive=" + interactive()
                    + " enabled=" + main.isEnabled() + " showing=" + component.isShowing()
                    + " bounds=" + component.getBounds() + " main=" + main.getBounds()
                    + " armed=" + main.getModel().isArmed() + " pressed=" + main.getModel().isPressed()
                    + " selected=" + main.isSelected() + " popupVisible=" + popup.isVisible()
                    + " popupBounds=" + popup.getBounds());
        }

        @Override
        JComponent focusTarget() {
            return main;
        }

        @Override
        void refresh() {
            state();
            var menu = (Nodes.MenuButton) node;
            boolean embedded = menu.presentation() != Nodes.ButtonPresentation.STANDARD;
            for (var button : java.util.List.of(main, arrow)) {
                button.kind = menu.kind();
                button.setEnabled(component.isEnabled());
                Composition.style(button, context(), Typography.Role.LABEL, button.textRole(false));
                int ring = ThemePainting.metric(context(), DesignMetrics.Role.FOCUS_RING),
                        pad = ThemePainting.metric(context(), DesignMetrics.Role.MENU_ITEM_PADDING);
                button.embedded = embedded;
                button.contextual = menu.presentation() == Nodes.ButtonPresentation.CONTEXTUAL_EMBEDDED;
                button.setBorder(embedded ? new EmptyBorder(0, 0, 0, 0) : new EmptyBorder(ring, pad, ring, pad));
            }
            ((MenuSurface) component).background.embedded = embedded;
            main.setText(menu.label());
            if (menu.action().isBlank() && !embedded) {
                Insets insets = main.getInsets();
                main.setBorder(
                        new EmptyBorder(
                                insets.top,
                                insets.left,
                                insets.bottom,
                                insets.right
                                        + ThemePainting.metric(
                                                context(), DesignMetrics.Role.MENU_ARROW_WIDTH)));
            }
            main.setIcon(
                    menu.icon().isEmpty()
                            ? null
                            : context()
                                    .icons()
                                    .icon(
                                            menu.icon(),
                                            context().theme().semantic(),
                                            main.textRole(false),
                                            com.formdev.flatlaf.util.UIScale.scale(
                                                    context()
                                                            .icons()
                                                            .size(Nodes.IconSize.CONTROL))));
            main.setDisabledIcon(main.getIcon());
            main.setToolTipText(menu.tooltip().isBlank() ? null : menu.tooltip());
            main.getAccessibleContext()
                    .setAccessibleName(menu.label().isBlank() ? menu.tooltip() : menu.label());
            arrow.setText("");
            arrow.setToolTipText("Open menu");
            arrow.getAccessibleContext().setAccessibleName("Open menu");
            arrow.setVisible(!menu.action().isBlank());
            ((Main) main).menuOnly = menu.action().isBlank() && !embedded;
            ((MenuSurface) component).background.kind = menu.kind();
            int pad = ThemePainting.metric(context(), DesignMetrics.Role.MENU_PADDING);
            popup.setBorder(new EmptyBorder(pad, pad, pad, pad));
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
                    item.setText(descriptor.label());
                    item.putClientProperty("html.disable", true);
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
                    Composition.style(
                            item, context(), Typography.Role.BODY, SemanticColor.TEXT_PRIMARY);
                    item.setEnabled(component.isEnabled() && descriptor.enabled());
                    int inset =
                            ThemePainting.metric(context(), DesignMetrics.Role.MENU_ITEM_PADDING);
                    item.setBorder(new EmptyBorder(0, inset, 0, inset));
                    item.setIcon(
                            descriptor.icon().isBlank()
                                    ? null
                                    : context()
                                            .icons()
                                            .tile(
                                                    descriptor.icon(),
                                                    context().theme().semantic(),
                                                    descriptor.hue(),
                                                    ThemePainting.metric(
                                                            context(),
                                                            DesignMetrics.Role.MENU_TILE),
                                                    false));
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
    }

    static final class Main extends Part {
        final KitContext context;
        boolean menuOnly;

        Main(KitContext context) {
            super(context);
            this.context = context;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (menuOnly)
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
            background = new Buttons.KitButton(context);
            background.setText("");
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (owner == null) return;
            background.setSize(getSize());
            background.setEnabled(isEnabled());
            background.focus = owner.main.focus;
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
                ThemePainting.antialias(g);
                int r = ThemePainting.metric(context, DesignMetrics.Role.CARD_RADIUS);
                g.setColor(ThemePainting.color(context, SemanticColor.SURFACE_MENU));
                g.fillRoundRect(0, 0, component.getWidth(), component.getHeight(), r * 2, r * 2);
                g.setColor(ThemePainting.color(context, SemanticColor.BORDER_MENU));
                g.draw(new RoundRectangle2D.Double(.5, .5, component.getWidth() - 1, component.getHeight() - 1, r * 2, r * 2));
            } finally {
                g.dispose();
            }
    }

    static final class Item extends JMenuItem {
        final KitContext context;

        Item(KitContext context) {
            this.context = context;
            // Before any text: Swing parses markup when the text is set, so the property must already be there.
            putClientProperty("html.disable", true);
            setOpaque(false);
            setBorderPainted(false);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension s = super.getPreferredSize();
            return new Dimension(
                    s.width,
                    Math.max(
                            s.height,
                            ThemePainting.height(
                                    context,
                                    DesignMetrics.Role.MENU_ITEM_HEIGHT,
                                    Typography.Role.BODY)));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                ThemePainting.antialias(g);
                if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
                boolean hover = isEnabled() && (getModel().isArmed() || getModel().isRollover());
                if (hover) {
                    g.setColor(ThemePainting.color(context, SemanticColor.ACCENT));
                    int r = ThemePainting.metric(context, DesignMetrics.Role.MENU_ITEM_RADIUS);
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), r * 2, r * 2);
                }
                g.setColor(
                        ThemePainting.color(
                                context,
                                hover ? SemanticColor.TEXT_ONACCENT : SemanticColor.TEXT_PRIMARY));
                g.setFont(getFont());
                Insets i = getInsets();
                int x = i.left;
                if (getIcon() != null) {
                    getIcon().paintIcon(this, g, x, (getHeight() - getIcon().getIconHeight()) / 2);
                    x +=
                            getIcon().getIconWidth()
                                    + ThemePainting.metric(context, DesignMetrics.Role.ICON_GAP);
                }
                g.drawString(
                        getText(),
                        x,
                        (getHeight() - g.getFontMetrics().getHeight()) / 2
                                + g.getFontMetrics().getAscent());
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
