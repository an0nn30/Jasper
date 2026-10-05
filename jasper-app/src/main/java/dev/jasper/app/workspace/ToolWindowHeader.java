package dev.jasper.app.workspace;

import com.formdev.flatlaf.ui.FlatEmptyBorder;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.AppIcons;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.UIManager;

/**
 * The header at the top of a tool window's frame, as in IntelliJ's new UI: the title on the left and, on the right,
 * contributed icon actions, an options menu (Move to, Hide), and a collapse chevron. It takes the
 * tool window background and its non-control area is a drag handle. EDT only.
 */
final class ToolWindowHeader extends JPanel {
    /** Height in logical pixels. */
    static final int HEIGHT = 32;

    private final JLabel title = new JLabel() {
        @Override public void updateUI() {
            super.updateUI();
            Font font = UIManager.getFont("Label.font");
            if (font != null) setFont(font.deriveFont(Font.BOLD));
        }
    };
    private final JButton options = button("general/moreHorizontal", "Options");
    private final JButton hide = button("general/chevronDown", "Hide");
    private final Supplier<JPopupMenu> menu;
    private final JLabel detail = new JLabel() {
        @Override public java.awt.Color getForeground() { return UIManager.getColor("Label.disabledForeground"); }
    };
    private final JPanel labels;
    private final JPanel contributed = new JPanel();
    private final java.util.List<JButton> actionButtons = new java.util.ArrayList<>();
    private dev.jasper.app.contributions.PanelHeaderState presentation = dev.jasper.app.contributions.PanelHeaderState.EMPTY;

    /**
     * @param text the window's title, shown as plain text
     * @param menu builds the options menu each time it opens, so it names the window's current slot
     * @param onHide hides the window
     */
    ToolWindowHeader(String text, Supplier<JPopupMenu> menu, Runnable onHide) {
        super(new BorderLayout());
        this.menu = menu;
        title.putClientProperty("html.disable", Boolean.TRUE);
        title.setText(text);
        options.addActionListener(event -> {
            if (options.isShowing()) menu().show(options, 0, options.getHeight());
        });
        hide.addActionListener(event -> onHide.run());
        var buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        contributed.setOpaque(false);
        contributed.setLayout(new BoxLayout(contributed, BoxLayout.X_AXIS));
        buttons.add(contributed);
        buttons.add(options);
        buttons.add(Box.createHorizontalStrut(2));
        buttons.add(hide);
        // An app-owned compound border keeps the padding through look-and-feel switches: the same pattern ToolWindowArea.Host.gutters uses.
        setBorder(BorderFactory.createCompoundBorder(new FlatEmptyBorder(0, 10, 0, 6), null));
        detail.putClientProperty("html.disable", Boolean.TRUE);
        labels = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0)) {
            @Override public void doLayout() {
                int titleWidth = Math.min(title.getPreferredSize().width, getWidth());
                int height = getHeight();
                title.setBounds(0, 0, titleWidth, height);
                int x = titleWidth + UIScale.scale(6);
                detail.setBounds(x, 0, Math.max(0, getWidth() - x), height);
            }
            @Override public Dimension getMinimumSize() { return new Dimension(0, 0); }
        };
        labels.setOpaque(false);
        labels.add(title); labels.add(detail);
        add(labels, BorderLayout.CENTER);
        add(buttons, BorderLayout.EAST);
    }

    private static JButton button(String icon, String name) {
        var button = new JButton(AppIcons.bundled(icon));
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setFocusable(false);
        button.setToolTipText(name);
        button.getAccessibleContext().setAccessibleName(name);
        return button;
    }

    void presentation(dev.jasper.app.contributions.PanelHeaderState state) {
        presentation = java.util.Objects.requireNonNull(state);
        detail.setText(state.detail());
        detail.setToolTipText(state.detail().isEmpty() ? null : state.detail());
        detail.setIcon(state.active() ? new javax.swing.Icon() {
            @Override public int getIconWidth() { return UIScale.scale(6); }
            @Override public int getIconHeight() { return UIScale.scale(6); }
            @Override public void paintIcon(java.awt.Component c, java.awt.Graphics graphics, int x, int y) {
                var g = (java.awt.Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    var background = ToolWindowHeader.this.getBackground();
                    boolean dark = background.getRed() * .299 + background.getGreen() * .587 + background.getBlue() * .114 < 128;
                    var success = UIManager.getColor("Jasper.configSuccessForeground");
                    g.setColor(dark ? new java.awt.Color(0x38ba54) : success == null ? new java.awt.Color(0x248943) : success);
                    g.fillOval(x, y, getIconWidth(), getIconHeight());
                } finally { g.dispose(); }
            }
        } : null);
        while (actionButtons.size() > state.actions().size()) {
            contributed.remove(actionButtons.removeLast());
        }
        for (int i = 0; i < state.actions().size(); i++) {
            var action = state.actions().get(i);
            if (i == actionButtons.size()) {
                int index = i;
                var button = new JButton();
                button.putClientProperty("JButton.buttonType", "toolBarButton");
                button.addActionListener(event -> {
                    if (index < presentation.actions().size()) presentation.actions().get(index).invoke().run();
                });
                actionButtons.add(button); contributed.add(button);
            }
            var button = actionButtons.get(i);
            button.setIcon(action.icon());
            button.setToolTipText(action.label());
            button.getAccessibleContext().setAccessibleName(action.label());
        }
        revalidate(); repaint();
    }

    /** Every non-control header surface receives its own mouse listener because Swing events do not bubble. */
    java.util.List<javax.swing.JComponent> dragSurfaces() { return java.util.List.of(this, title, detail, labels); }

    JLabel titleLabel() { return title; }

    JButton optionsButton() { return options; }

    JButton hideButton() { return hide; }

    /** The options menu as it would open now. */
    JPopupMenu menu() { return menu.get(); }

    @Override public Dimension getPreferredSize() { return new Dimension(super.getPreferredSize().width, UIScale.scale(HEIGHT)); }

    @Override public Dimension getMinimumSize() { return new Dimension(super.getMinimumSize().width, UIScale.scale(HEIGHT)); }
}
