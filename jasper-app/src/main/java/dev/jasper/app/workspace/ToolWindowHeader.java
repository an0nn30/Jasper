package dev.jasper.app.workspace;

import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.NativeStyles;
import dev.jasper.ui.theme.SemanticColor;

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

/**
 * The header at the top of a tool window's frame, as in IntelliJ's new UI: the title on the left and, on the right,
 * contributed icon actions, an options menu (Move to, Hide), and a collapse chevron. It takes the
 * tool window background and its non-control area is a drag handle. EDT only.
 */
final class ToolWindowHeader extends JPanel {
    /** Height in logical pixels. */
    static final int HEIGHT = DesignMetrics.DEFAULT.value(DesignMetrics.Role.NATIVE_TOOL_HEADER_HEIGHT);

    private final JLabel title = new JLabel() {
        @Override public void updateUI() {
            super.updateUI();
            Font font = ThemeZone.font(Typography.Role.LABEL);
            if (font != null) setFont(ThemeZone.font(Typography.Role.PANEL_TITLE));
        }
    };
    // Module glyphs: "more" is the horizontal ellipsis and "collapse" the down chevron main's panel redesign chose.
    private final JButton options = button("more", "Options");
    private final JButton hide = button("collapse", "Hide");
    private final Supplier<JPopupMenu> menu;
    /** Count or connection name beside the title: the kit panel header's count typography and colour. */
    private final JLabel detail = new JLabel() {
        @Override public void updateUI() { super.updateUI(); setFont(ThemeZone.font(Typography.Role.COUNT)); }
        @Override public java.awt.Color getForeground() { return ThemeZone.color(this, SemanticColor.TEXT_TERTIARY); }
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
        setBorder(BorderFactory.createCompoundBorder(new FlatEmptyBorder(0, DesignMetrics.DEFAULT.value(DesignMetrics.Role.TOOL_HEADER_LEADING_PADDING), 0, DesignMetrics.DEFAULT.value(DesignMetrics.Role.TOOL_HEADER_TRAILING_PADDING)), null));
        detail.putClientProperty("html.disable", Boolean.TRUE);
        labels = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0)) {
            @Override public void doLayout() {
                int titleWidth = Math.min(title.getPreferredSize().width, getWidth());
                int height = getHeight();
                title.setBounds(0, 0, titleWidth, height);
                int x = titleWidth + NativeStyles.installed().metric(DesignMetrics.Role.TOOL_HEADER_DETAIL_GAP);
                detail.setBounds(x, 0, Math.max(0, getWidth() - x), height);
            }
            @Override public Dimension getMinimumSize() { return new Dimension(0, 0); }
        };
        labels.setOpaque(false);
        labels.add(title); labels.add(detail);
        add(labels, BorderLayout.CENTER);
        add(buttons, BorderLayout.EAST);
    }

    /** This native shell owns its surface; no descendant walker styles it. */
    @Override public java.awt.Color getBackground() { return ThemeZone.color(this, SemanticColor.SURFACE_PANEL); }

    private static JButton button(String icon, String name) {
        var button = new JButton(AppIcons.semantic(icon));
        NativeStyles.installed().button(button, () -> ThemeZone.theme(button));
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
            @Override public int getIconWidth() { return NativeStyles.installed().metric(DesignMetrics.Role.TOOL_HEADER_ACTIVE_DOT); }
            @Override public int getIconHeight() { return getIconWidth(); }
            @Override public void paintIcon(java.awt.Component c, java.awt.Graphics graphics, int x, int y) {
                // The connected dot is the success status, held readable on the tool window surface it sits on.
                dev.jasper.ui.swing.ChromePainter.installed().connectedDot(graphics, x, y, getIconWidth(), ThemeZone.theme(ToolWindowHeader.this));
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
                NativeStyles.installed().button(button, () -> ThemeZone.theme(button));
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
