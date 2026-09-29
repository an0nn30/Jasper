package dev.jasper.app.settingsui;

import com.formdev.flatlaf.FlatClientProperties;
import dev.jasper.app.platform.AppIcons;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import javax.swing.*;

/** The Settings dialog's panel, laid out as IntelliJ's: search and tree at left; header, banner and page at right; buttons below. */
final class SettingsView extends JPanel {
    final JTextField search = new JTextField();
    final PageTree tree;
    final JLabel breadcrumb = new JLabel();
    final JButton back = iconButton("general/chevronLeft", "Back");
    final JButton forward = iconButton("general/chevronRight", "Forward");
    final JPanel banner = new JPanel(new BorderLayout(8, 0));
    final JLabel bannerText = new JLabel();
    final JButton bannerButton = new JButton();
    final JScrollPane body = new JScrollPane();
    final JButton help = iconButton("general/questionDialog", "Help");
    final JLabel message = new JLabel();
    final JButton cancel = new JButton("Cancel");
    final JButton apply = new JButton("Apply");
    final JButton ok = new JButton("OK");

    SettingsView(java.util.function.Consumer<String> selected, Runnable cancelled) {
        super(new BorderLayout());
        tree = new PageTree(selected);
        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search settings");
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, AppIcons.bundled("general/search"));
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        search.getAccessibleContext().setAccessibleName("Search settings");

        var left = new JPanel(new BorderLayout(0, 6));
        left.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 6));
        left.add(search, BorderLayout.NORTH);
        var treeScroll = new JScrollPane(tree, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        treeScroll.setBorder(BorderFactory.createEmptyBorder());
        left.add(treeScroll, BorderLayout.CENTER);

        breadcrumb.putClientProperty("html.disable", true);
        breadcrumb.setFont(breadcrumb.getFont().deriveFont(java.awt.Font.BOLD));
        var arrows = new JPanel(new FlowLayout(FlowLayout.TRAILING, 2, 0));
        arrows.setOpaque(false);
        arrows.add(back);
        arrows.add(forward);
        var header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(12, 20, 8, 12));
        header.add(breadcrumb, BorderLayout.WEST);
        header.add(arrows, BorderLayout.EAST);

        bannerText.putClientProperty("html.disable", true);
        banner.setBorder(BorderFactory.createEmptyBorder(6, 20, 6, 12));
        Color bannerBackground = UIManager.getColor("Banner.warningBackground");
        if (bannerBackground != null) banner.setBackground(bannerBackground);
        banner.add(bannerText, BorderLayout.CENTER);
        banner.add(bannerButton, BorderLayout.EAST);
        banner.setVisible(false);

        body.setBorder(BorderFactory.createEmptyBorder());
        // A generated page scrolls sideways only when the dialog is narrower than its editors need.
        body.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        body.setOpaque(false);
        body.getViewport().setOpaque(false);
        body.getVerticalScrollBar().setUnitIncrement(16);
        var page = new JPanel(new BorderLayout());
        page.add(banner, BorderLayout.NORTH);
        page.add(body, BorderLayout.CENTER);
        var right = new JPanel(new BorderLayout());
        right.add(header, BorderLayout.NORTH);
        right.add(page, BorderLayout.CENTER);

        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setDividerLocation(260);
        split.setDividerSize(1);
        split.setBorder(BorderFactory.createEmptyBorder());
        add(split, BorderLayout.CENTER);

        message.putClientProperty("html.disable", true);
        Color red = UIManager.getColor("Jasper.configErrorForeground");
        message.setForeground(red != null ? red : Color.RED);
        var buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(apply);
        buttons.add(ok);
        var footer = new JPanel(new BorderLayout(8, 0));
        Color line = UIManager.getColor("Separator.foreground");
        footer.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, line != null ? line : Color.GRAY),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        footer.add(help, BorderLayout.WEST);
        footer.add(message, BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelSettings");
        getActionMap().put("cancelSettings", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { cancelled.run(); }
        });
    }

    @Override public void addNotify() {
        super.addNotify();
        var root = SwingUtilities.getRootPane(this);
        if (root != null) root.setDefaultButton(ok);
    }

    void banner(String text, String button, Runnable action) {
        for (var listener : bannerButton.getActionListeners()) bannerButton.removeActionListener(listener);
        if (text == null) { banner.setVisible(false); return; }
        bannerText.setText(text);
        bannerButton.setText(button);
        bannerButton.addActionListener(event -> action.run());
        banner.setVisible(true);
    }

    void page(JComponent content) {
        body.setViewportView(content);
        body.getViewport().setOpaque(false);
    }

    private static JButton iconButton(String icon, String name) {
        var button = new JButton(AppIcons.bundled(icon));
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        button.setToolTipText(name);
        button.getAccessibleContext().setAccessibleName(name);
        return button;
    }
}
