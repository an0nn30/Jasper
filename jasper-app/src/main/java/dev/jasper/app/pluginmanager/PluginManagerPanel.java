package dev.jasper.app.pluginmanager;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import dev.jasper.app.plugins.PluginRuntime;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * The Plugins page's passive view: a search bar and gear menu, the restart banner, the plugin cards and the selected
 * plugin's details. It renders a snapshot and reports clicks; it decides nothing. EDT only.
 */
final class PluginManagerPanel extends JPanel {
    /** What the user can ask for. */
    record Handlers(Consumer<PluginRuntime.Row> toggle, Consumer<PluginRuntime.Row> review, Consumer<PluginRuntime.Row> remove,
                    Consumer<PluginRuntime.Row> discard, Runnable install, Consumer<PluginRuntime.Row> openSettings,
                    Consumer<PluginRuntime.Row> openFolder, Consumer<PluginRuntime.Row> openData, Consumer<URI> browse) { }

    /** One button of the banner. */
    record BannerAction(String label, Runnable run) { }

    final JTextField search = new JTextField(18);
    final JButton gear = new JButton();
    private final Handlers handlers;
    private final PluginDetails details;
    private final PluginCardList list;
    private final JPanel banner = new JPanel(new BorderLayout(12, 0));
    private final JLabel bannerLabel = plain(new JLabel());
    private final JPanel bannerActions = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
    private final JLabel notice = plain(new JLabel());
    private final JLabel message = plain(new JLabel(" "));
    private boolean busy;

    /** Swing renders label text that starts with an html tag; plugin-supplied text must never be markup. */
    static <T extends JComponent> T plain(T component) {
        component.putClientProperty("html.disable", Boolean.TRUE);
        return component;
    }

    PluginManagerPanel(Handlers handlers) {
        super(new BorderLayout(0, 8));
        this.handlers = handlers;
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        details = new PluginDetails(handlers);
        // The list reports its first selection while it is being built, before this field is set.
        list = new PluginCardList(row -> (row.needsConsent() ? handlers.review() : handlers.toggle()).accept(row),
            () -> { PluginCardList built = cardList(); if (built != null) details.show(built.selectedRow(), busy); },
            (at, point) -> { if (!busy) details.menu().show(at, point.x, point.y); });

        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search plugins");
        search.getAccessibleContext().setAccessibleName("Search plugins");
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { list.search(search.getText()); }
            @Override public void removeUpdate(DocumentEvent event) { list.search(search.getText()); }
            @Override public void changedUpdate(DocumentEvent event) { list.search(search.getText()); }
        });
        gear.setIcon(new FlatSVGIcon("dev/jasper/app/icons/intellij/general/settings.svg", 16, 16, PluginManagerPanel.class.getClassLoader()));
        gear.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        gear.setToolTipText("Plugin options");
        gear.getAccessibleContext().setAccessibleName("Plugin options");
        gear.addActionListener(event -> gearMenu().show(gear, 0, gear.getHeight()));
        var bar = new JPanel(new BorderLayout(8, 0));
        bar.add(search, BorderLayout.CENTER);
        bar.add(gear, BorderLayout.EAST);

        banner.add(bannerLabel, BorderLayout.CENTER);
        banner.add(bannerActions, BorderLayout.EAST);
        banner.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        banner.setVisible(false);
        notice.setVisible(false);
        var top = new JPanel(new BorderLayout());
        top.add(bar, BorderLayout.NORTH);
        top.add(banner, BorderLayout.CENTER);
        top.add(notice, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        var listScroll = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        listScroll.setMinimumSize(new Dimension(240, 100));
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, details);
        split.setDividerLocation(340);
        split.setResizeWeight(0.4);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);
        add(message, BorderLayout.SOUTH);
        restyle();
        details.show(list.selectedRow(), false);
    }

    private PluginCardList cardList() { return list; }

    @Override public void updateUI() {
        super.updateUI();
        // Called by JPanel's constructor, before the labels exist.
        if (message != null) restyle();
    }

    private void restyle() {
        Font base = UIManager.getFont("Label.font");
        if (base != null) bannerLabel.setFont(base.deriveFont(Font.BOLD));
    }

    private JPopupMenu gearMenu() {
        var menu = new JPopupMenu();
        var install = new JMenuItem("Install from Zip…");
        install.setEnabled(!busy);
        install.addActionListener(event -> handlers.install().run());
        menu.add(install);
        return menu;
    }

    List<String> gearLabels() {
        List<String> labels = new ArrayList<>();
        for (Component component : gearMenu().getComponents()) if (component instanceof JMenuItem item) labels.add(item.getText());
        return labels;
    }

    /** Test seam: clicks the gear menu's item; a disabled item does nothing. */
    void clickGear(String label) {
        for (Component component : gearMenu().getComponents())
            if (component instanceof JMenuItem item && item.getText().equals(label)) { item.doClick(); return; }
        throw new IllegalArgumentException("No gear menu item " + label);
    }

    /** Replaces the plugins, keeping the selection and the search. */
    void show(PluginRuntime.Snapshot snapshot) { list.show(snapshot.rows()); }

    void busy(boolean value) {
        busy = value;
        list.busy(value);
        gear.setEnabled(!value);
        details.show(list.selectedRow(), value);
    }

    /** An empty text hides the banner. */
    void banner(String text, List<BannerAction> actions) {
        bannerLabel.setText(text);
        bannerActions.removeAll();
        for (BannerAction action : actions) {
            var button = new JButton(action.label());
            button.addActionListener(event -> action.run().run());
            bannerActions.add(button);
        }
        banner.setVisible(!text.isEmpty());
        banner.revalidate();
        banner.repaint();
    }

    void notice(String text) { notice.setText(text); notice.setVisible(!text.isEmpty()); }

    void message(String text, boolean error) {
        message.setText(text.isEmpty() ? " " : text);
        message.setForeground(UIManager.getColor(error ? "Actions.Red" : "Label.foreground"));
    }

    List<String> listed() { return list.listed(); }
    List<String> groupTitles() { return list.groupTitles(); }
    String selected() { return list.selected(); }
    void select(String id) { list.select(id); }
    PluginCard card(String id) { return list.card(id); }
    void search(String text) { search.setText(text); }
    JButton primary() { return details.primary; }
    JTabbedPane tabs() { return details.tabs; }
    List<String> menuLabels() { return details.menuLabels(); }
    void clickMenu(String label) { details.clickMenu(label); }
    boolean homepageShown() { return details.homepage.isVisible(); }
    void clickHomepage() { details.homepage.doClick(); }

    /** The selected plugin's details as text, or the list's empty text when nothing is selected. */
    String details() { return list.selectedRow() == null ? list.emptyText() : details.text(); }

    String bannerText() { return banner.isVisible() ? bannerLabel.getText() : ""; }
    String noticeText() { return notice.isVisible() ? notice.getText() : ""; }
    String messageText() { return message.getText().strip(); }

    List<JButton> bannerButtons() {
        List<JButton> buttons = new ArrayList<>();
        if (banner.isVisible()) for (Component component : bannerActions.getComponents()) buttons.add((JButton) component);
        return buttons;
    }
}
