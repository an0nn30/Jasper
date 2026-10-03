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
 * an options button (the ⋮ menu: Move to, Hide) and a hide button (—). It has no bar colour of its own: a plain
 * panel inside the frame, it takes the tool window background. Plan 2 makes it a drag handle. EDT only.
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
    private final JButton options = button("general/moreVertical", "Options");
    private final JButton hide = button("general/hide", "Hide");
    private final Supplier<JPopupMenu> menu;

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
        buttons.add(options);
        buttons.add(Box.createHorizontalStrut(2));
        buttons.add(hide);
        // An app-owned compound border keeps the padding through look-and-feel switches: the same pattern ToolWindowArea.Host.gutters uses.
        setBorder(BorderFactory.createCompoundBorder(new FlatEmptyBorder(0, 10, 0, 6), null));
        add(title, BorderLayout.CENTER);
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

    JLabel titleLabel() { return title; }

    JButton optionsButton() { return options; }

    JButton hideButton() { return hide; }

    /** The options menu as it would open now. */
    JPopupMenu menu() { return menu.get(); }

    @Override public Dimension getPreferredSize() { return new Dimension(super.getPreferredSize().width, UIScale.scale(HEIGHT)); }

    @Override public Dimension getMinimumSize() { return new Dimension(super.getMinimumSize().width, UIScale.scale(HEIGHT)); }
}
