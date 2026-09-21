package dev.jasper.app.pluginmanager;

import dev.jasper.app.plugins.PluginRuntime;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;

/** The manager's passive view: it renders a snapshot and reports clicks. It decides nothing. */
final class PluginManagerPanel extends JPanel {
    /** What the user can ask for. */
    record Handlers(Consumer<PluginRuntime.Row> toggle, Consumer<PluginRuntime.Row> review, Consumer<PluginRuntime.Row> remove,
                    Consumer<PluginRuntime.Row> discard, Runnable install) { }

    /** One button of the banner. */
    record BannerAction(String label, Runnable run) { }

    final JButton toggle = new JButton("Disable");
    final JButton review = new JButton("Review…");
    final JButton remove = new JButton("Remove");
    final JButton discard = new JButton("Discard Install");
    final JButton install = new JButton("Install from Zip…");
    private final DefaultListModel<PluginRuntime.Row> model = new DefaultListModel<>();
    private final JList<PluginRuntime.Row> list = new JList<>(model);
    private final JLabel title = plain(new JLabel(" "));
    private final JTextArea body = new JTextArea();
    private final JPanel banner = new JPanel(new BorderLayout(12, 0));
    private final JLabel bannerLabel = plain(new JLabel());
    private final JPanel bannerActions = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
    private final JLabel notice = plain(new JLabel());
    private final JLabel message = plain(new JLabel(" "));
    private boolean busy;
    private boolean refreshing;

    /** Swing renders label text that starts with an html tag; plugin-supplied text must never be markup. */
    private static <T extends javax.swing.JComponent> T plain(T component) {
        component.putClientProperty("html.disable", Boolean.TRUE);
        return component;
    }

    static String stateLabel(String state) {
        return switch (state) {
            case "ACTIVE" -> "Active"; case "DISABLED" -> "Disabled"; case "NEEDS_CONSENT" -> "Needs review";
            case "SKIPPED" -> "Skipped"; case "FAILED" -> "Failed"; case "NOT_LOADED" -> "Not loaded yet";
            default -> state;
        };
    }

    static String label(PluginRuntime.Row row) {
        return row.name() + "  " + row.version() + " — " + stateLabel(row.state()) + (row.pending().isEmpty() ? "" : " (restart to apply)");
    }

    PluginManagerPanel(Handlers handlers) {
        super(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        bannerLabel.setFont(bannerLabel.getFont().deriveFont(Font.BOLD));
        banner.add(bannerLabel, BorderLayout.CENTER);
        banner.add(bannerActions, BorderLayout.EAST);
        banner.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        banner.setVisible(false);
        notice.setVisible(false);
        var top = new JPanel(new BorderLayout());
        top.add(banner, BorderLayout.NORTH);
        top.add(notice, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> owner, Object value, int index, boolean selected, boolean focused) {
                var cell = (JLabel) super.getListCellRendererComponent(owner, value, index, selected, focused);
                plain(cell).setText(label((PluginRuntime.Row) value));
                cell.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                return cell;
            }
        });
        list.addListSelectionListener(event -> { if (!refreshing && !event.getValueIsAdjusting()) render(); });

        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 3f));
        body.setEditable(false); body.setLineWrap(true); body.setWrapStyleWord(true); body.setOpaque(false);
        body.setFont(UIManager.getFont("Label.font"));
        var actions = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
        for (JButton button : List.of(toggle, review, remove, discard)) actions.add(button);
        var details = new JPanel(new BorderLayout(0, 8));
        details.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 4));
        details.add(title, BorderLayout.NORTH);
        details.add(new JScrollPane(body, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
        details.add(actions, BorderLayout.SOUTH);

        var listScroll = new JScrollPane(list);
        listScroll.setMinimumSize(new Dimension(200, 100));
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, details);
        split.setDividerLocation(280);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        var bottom = new JPanel(new BorderLayout(12, 0));
        bottom.add(install, BorderLayout.WEST);
        bottom.add(message, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        toggle.addActionListener(event -> { if (current() != null) handlers.toggle().accept(current()); });
        review.addActionListener(event -> { if (current() != null) handlers.review().accept(current()); });
        remove.addActionListener(event -> { if (current() != null) handlers.remove().accept(current()); });
        discard.addActionListener(event -> { if (current() != null) handlers.discard().accept(current()); });
        install.addActionListener(event -> handlers.install().run());
        render();
    }

    private PluginRuntime.Row current() { return list.getSelectedValue(); }

    /** Replaces the rows, keeping the selection by id; the first row is selected when the old one is gone. */
    void show(PluginRuntime.Snapshot snapshot) {
        String keep = selected();
        refreshing = true;
        try {
            model.clear();
            snapshot.rows().forEach(model::addElement);
            int index = -1;
            for (int i = 0; i < model.size(); i++) if (model.get(i).id().equals(keep)) index = i;
            if (index < 0 && !model.isEmpty()) index = 0;
            if (index >= 0) list.setSelectedIndex(index); else list.clearSelection();
        } finally { refreshing = false; }
        render();
    }

    private void render() {
        PluginRuntime.Row row = current();
        toggle.setVisible(row != null && row.canToggle());
        review.setVisible(row != null && row.needsConsent());
        discard.setVisible(row != null && row.pendingInstall());
        remove.setVisible(row != null && row.canRemove() && !row.pendingInstall());
        if (row != null) {
            toggle.setText(row.enabled() ? "Disable" : "Enable");
            remove.setText(row.pendingRemoval() ? "Keep" : "Remove");
        }
        title.setText(row == null ? " " : row.name() + " " + row.version());
        body.setText(row == null ? "No plugins are installed." : describe(row));
        body.setCaretPosition(0);
        applyBusy();
    }

    private static String describe(PluginRuntime.Row row) {
        var text = new StringBuilder(row.id());
        text.append('\n').append(row.vendor().isBlank() ? row.origin() : row.vendor() + " · " + row.origin());
        text.append("\n\n").append(stateLabel(row.state()));
        if (!row.reason().isEmpty()) text.append(": ").append(row.reason());
        if (!row.pending().isEmpty()) text.append('\n').append(row.pending());
        if (row.errors() > 0) text.append('\n').append(row.errors()).append(row.errors() == 1 ? " error" : " errors").append(" since Jasper started; see the log");
        if (!row.description().isBlank()) text.append("\n\n").append(row.description());
        text.append("\n\nCapabilities");
        if (row.capabilities().isEmpty()) text.append("\n  None");
        for (String capability : row.capabilities())
            text.append("\n  • ").append(Capabilities.describe(capability)).append(row.unconsented().contains(capability) ? " — not reviewed" : "");
        if (!row.requires().isEmpty()) {
            text.append("\n\nRequires");
            for (String requirement : row.requires()) text.append("\n  • ").append(requirement);
        }
        return text.toString();
    }

    void busy(boolean value) { busy = value; applyBusy(); }

    private void applyBusy() {
        for (JButton button : List.of(toggle, review, remove, discard, install)) button.setEnabled(!busy);
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
        banner.revalidate(); banner.repaint();
    }

    void notice(String text) { notice.setText(text); notice.setVisible(!text.isEmpty()); }

    void message(String text, boolean error) {
        message.setText(text.isEmpty() ? " " : text);
        message.setForeground(UIManager.getColor(error ? "Actions.Red" : "Label.foreground"));
    }

    List<String> listed() {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) labels.add(label(model.get(i)));
        return labels;
    }

    String selected() { return current() == null ? null : current().id(); }

    void select(String id) {
        for (int i = 0; i < model.size(); i++) if (model.get(i).id().equals(id)) list.setSelectedIndex(i);
    }

    String details() { return title.getText() + "\n" + body.getText(); }
    String bannerText() { return banner.isVisible() ? bannerLabel.getText() : ""; }
    String noticeText() { return notice.isVisible() ? notice.getText() : ""; }
    String messageText() { return message.getText().strip(); }

    List<JButton> bannerButtons() {
        List<JButton> buttons = new ArrayList<>();
        if (banner.isVisible()) for (Component component : bannerActions.getComponents()) buttons.add((JButton) component);
        return buttons;
    }
}
