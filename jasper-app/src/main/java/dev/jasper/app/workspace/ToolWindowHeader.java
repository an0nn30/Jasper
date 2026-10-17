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
    /** The window's kit's header height, in logical pixels, without a flush header's rule. */
    int logicalHeight() { return kit.metrics().value(DesignMetrics.Role.NATIVE_TOOL_HEADER_HEIGHT); }

    /** The window's kit. */
    private final dev.jasper.ui.kit.Kit kit;
    /** The window's kit's native styles and chrome painter. */
    private final NativeStyles styles;
    private final dev.jasper.ui.swing.ChromePainter painter;
    /** Whether this is a flush frame's header strip (spec 10.7). */
    private final boolean flush;

    private final JLabel title = new JLabel() {
        @Override public void updateUI() {
            super.updateUI();
            Font font = ThemeZone.font(Typography.Role.LABEL);
            if (font != null) setFont(ThemeZone.font(Typography.Role.PANEL_TITLE));
        }

        /** A header strip's title is held readable on the strip, focused or not. */
        @Override public java.awt.Color getForeground() {
            return flush && painter != null ? painter.headerTitle(ThemeZone.theme(this), focused) : super.getForeground();
        }
    };
    // Module glyphs: "more" is the horizontal ellipsis and "collapse" the down chevron main's panel redesign chose.
    private final JButton options = button("more", "Options");
    private final JButton hide = button("collapse", "Hide");
    private final Supplier<JPopupMenu> menu;
    /** Count or connection name beside the title: the kit panel header's count typography and colour. */
    private final JLabel detail = new JLabel() {
        @Override public void updateUI() {
            super.updateUI();
            // A header strip's word detail ("Mac") is in the label face; a count keeps the count face (the mock).
            setFont(ThemeZone.font(flush && presentation != null && !presentation.detail().matches("\\d+") ? Typography.Role.LABEL : Typography.Role.COUNT));
        }
        @Override public java.awt.Color getForeground() {
            // A header strip's detail is muted, and a count (a parenthesised number) more so (spec 10.7).
            if (flush && painter != null) return painter.headerDetail(ThemeZone.theme(this), focused, presentation.detail().matches("\\d+"));
            return ThemeZone.color(this, SemanticColor.TEXT_TERTIARY);
        }
    };
    private final JPanel labels;
    private final JPanel contributed = new JPanel();
    private final java.util.List<JButton> actionButtons = new java.util.ArrayList<>();
    private dev.jasper.app.contributions.PanelHeaderState presentation = dev.jasper.app.contributions.PanelHeaderState.EMPTY;

    /**
     * A standard header over the installed kit, as the default look's tests build it.
     *
     * @param text the window's title, shown as plain text
     * @param menu builds the options menu each time it opens, so it names the window's current slot
     * @param onHide hides the window
     */
    ToolWindowHeader(String text, Supplier<JPopupMenu> menu, Runnable onHide) {
        this(text, menu, onHide, dev.jasper.ui.swing.FlatLafInstaller.installedKit(), false);
    }

    /**
     * @param text the window's title, shown as plain text
     * @param menu builds the options menu each time it opens, so it names the window's current slot
     * @param onHide hides the window
     * @param kit the window's kit
     * @param flush whether this is a flush frame's header strip
     */
    ToolWindowHeader(String text, Supplier<JPopupMenu> menu, Runnable onHide, dev.jasper.ui.kit.Kit kit, boolean flush) {
        super(new BorderLayout());
        this.kit = java.util.Objects.requireNonNull(kit, "kit");
        this.styles = new NativeStyles(kit);
        this.painter = new dev.jasper.ui.swing.ChromePainter(kit);
        this.flush = flush;
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
        var metrics = kit.metrics();
        setBorder(BorderFactory.createCompoundBorder(new FlatEmptyBorder(0, metrics.value(DesignMetrics.Role.TOOL_HEADER_LEADING_PADDING),
            flush ? metrics.value(DesignMetrics.Role.DOCK_RULE) : 0, metrics.value(DesignMetrics.Role.TOOL_HEADER_TRAILING_PADDING)), null));
        detail.putClientProperty("html.disable", Boolean.TRUE);
        labels = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0)) {
            @Override public void doLayout() {
                int titleWidth = Math.min(title.getPreferredSize().width, getWidth());
                int height = getHeight();
                title.setBounds(0, 0, titleWidth, height);
                int x = titleWidth + styles.metric(flush ? DesignMetrics.Role.HEADER_STRIP_TITLE_GAP : DesignMetrics.Role.TOOL_HEADER_DETAIL_GAP);
                detail.setBounds(x, 0, Math.max(0, getWidth() - x), height);
            }
            @Override public Dimension getMinimumSize() { return new Dimension(0, 0); }
        };
        labels.setOpaque(false);
        labels.add(title); labels.add(detail);
        add(labels, BorderLayout.CENTER);
        add(buttons, BorderLayout.EAST);
        if (flush) {
            // A header strip's hide glyph is the minus, and its actions are 16 px squares 2 apart (Rulings 14 and 15).
            hide.setIcon(AppIcons.semantic("remove"));
            for (JButton button : java.util.List.of(options, hide)) actionSize(button);
            contributed.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEADING, 2, 0));
            // A box row stretches the actions' row to the strip's height and a flow lays it at the top; a grid bag
            // centres every action on the strip, as the mocks do.
            buttons.setLayout(new java.awt.GridBagLayout());
            detail.setIconTextGap(styles.metric(DesignMetrics.Role.HEADER_STRIP_DOT_GAP));
        }
    }

    /** A header strip action's square. */
    private void actionSize(JButton button) {
        int side = styles.metric(DesignMetrics.Role.TOOL_HEADER_ACTION);
        var size = new Dimension(side, side);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
    }

    /** This native shell owns its surface; no descendant walker styles it. */
    @Override public java.awt.Color getBackground() {
        return flush && painter != null ? painter.headerSurface(ThemeZone.theme(this), focused) : ThemeZone.color(this, SemanticColor.SURFACE_PANEL);
    }

    /** A header strip paints its surface and the 1 px rule along its bottom (spec 10.7). */
    @Override protected void paintComponent(java.awt.Graphics graphics) {
        if (!flush) { super.paintComponent(graphics); return; }
        painter.headerStrip(graphics, getWidth(), getHeight(), ThemeZone.theme(this), focused);
    }

    private static JButton button(String icon, String name) {
        var button = new JButton(AppIcons.semantic(icon));
        NativeStyles.installed().button(button, () -> ThemeZone.theme(button));
        button.setFocusable(false);
        button.setToolTipText(name);
        button.getAccessibleContext().setAccessibleName(name);
        return button;
    }

    /** Whether this header strip shows its frame as focused (flush frames, spec 10.7). */
    private boolean focused;

    /** Shows the frame as focused or not; only a flush header strip paints it. */
    void setFocused(boolean value) {
        if (focused == value) return;
        focused = value;
        repaint();
    }

    boolean focused() { return focused; }

    void presentation(dev.jasper.app.contributions.PanelHeaderState state) {
        presentation = java.util.Objects.requireNonNull(state);
        detail.setText(flush && state.detail().matches("\\d+") ? "(" + state.detail() + ")" : state.detail());
        detail.setToolTipText(state.detail().isEmpty() ? null : state.detail());
        if (flush) detail.setFont(ThemeZone.font(state.detail().matches("\\d+") ? Typography.Role.COUNT : Typography.Role.LABEL));
        detail.setIcon(state.active() ? new javax.swing.Icon() {
            @Override public int getIconWidth() { return styles.metric(DesignMetrics.Role.TOOL_HEADER_ACTIVE_DOT); }
            @Override public int getIconHeight() { return getIconWidth(); }
            @Override public void paintIcon(java.awt.Component c, java.awt.Graphics graphics, int x, int y) {
                // The connected dot is the success status, held readable on the tool window surface it sits on.
                if (flush) painter.headerDot(graphics, x, y, getIconWidth(), ThemeZone.theme(ToolWindowHeader.this), focused);
                else painter.connectedDot(graphics, x, y, getIconWidth(), ThemeZone.theme(ToolWindowHeader.this));
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
                if (flush) actionSize(button);
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

    @Override public Dimension getPreferredSize() { return new Dimension(super.getPreferredSize().width, UIScale.scale(stripHeight())); }

    @Override public Dimension getMinimumSize() { return new Dimension(super.getMinimumSize().width, UIScale.scale(stripHeight())); }

    /** The header's height in logical pixels: a flush strip adds its rule. */
    private int stripHeight() { return logicalHeight() + (flush ? kit.metrics().value(DesignMetrics.Role.DOCK_RULE) : 0); }
}
