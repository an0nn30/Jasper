package dev.jasper.remote.ui.transfers;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.*;
import javax.swing.*;

/** The SFTP sidebar's transfers: two-line rows, at most three visible before scrolling, hidden when empty. */
public final class TransferStrip extends JPanel {
    static final int VISIBLE_ROWS = 3;
    public record Actions(BiConsumer<UUID, TransferRows.Action> action, Consumer<UUID> cancel, Consumer<UUID> dismiss) { }
    private final Actions actions;
    private final JPanel list = new JPanel();
    private final JScrollPane scroll = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    private final JLabel unavailable = label("");
    private final Map<UUID, RowView> views = new LinkedHashMap<>();

    public TransferStrip(Actions actions) {
        super(new BorderLayout(0, 4));
        this.actions = actions;
        Color rule = UIManager.getColor("Separator.foreground");
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, rule == null ? Color.GRAY : rule),
            BorderFactory.createEmptyBorder(6, 0, 0, 0)));
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(label("Transfers"), BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(unavailable, BorderLayout.SOUTH);
        unavailable.setVisible(false);
        setVisible(false);
    }

    /** Updates rows in place; components are only detached when the set or order of transfers changes, so focus and presses survive a refresh. */
    public void rows(List<TransferRows.Row> rows) {
        boolean relayout = unavailable.isVisible();
        unavailable.setVisible(false);
        int height = visibleHeight();
        if (!rows.stream().map(TransferRows.Row::id).toList().equals(List.copyOf(views.keySet()))) {
            relayout = true;
            list.removeAll();
            var oldViews = new HashMap<>(views);
            views.clear();
            for (var row : rows) {
                var view = oldViews.computeIfAbsent(row.id(), id -> new RowView(id, actions));
                views.put(row.id(), view);
                list.add(view);
            }
        }
        for (var row : rows) views.get(row.id()).show(row);
        if (relayout || visibleHeight() != height) resize();
        setVisible(!rows.isEmpty());
    }

    public void unavailable(String message) {
        views.clear();
        list.removeAll();
        unavailable.setText(message);
        unavailable.setToolTipText(message);
        unavailable.setVisible(true);
        resize();
        setVisible(true);
    }

    private int visibleHeight() {
        int height = 0, shown = 0;
        for (var view : views.values()) { if (shown++ == VISIBLE_ROWS) break; height += view.getPreferredSize().height; }
        return height;
    }

    private void resize() {
        scroll.setPreferredSize(new Dimension(10, visibleHeight()));
        scroll.setVisible(!views.isEmpty());
        revalidate();
        repaint();
    }

    int visibleRows() { return Math.min(views.size(), VISIBLE_ROWS); }
    Optional<RowView> view(UUID id) { return Optional.ofNullable(views.get(id)); }
    String unavailableText() { return unavailable.isVisible() ? unavailable.getText() : ""; }
    List<UUID> order() { return List.copyOf(views.keySet()); }

    static JLabel label(String text) {
        var label = new JLabel(text);
        label.putClientProperty("html.disable", true);
        return label;
    }

    static String elideMiddle(String text, FontMetrics metrics, int width) {
        if (metrics.stringWidth(text) <= width) return text;
        for (int keep = text.length() - 1; keep > 1; keep--) {
            String candidate = text.substring(0, (keep + 1) / 2) + "…" + text.substring(text.length() - keep / 2);
            if (metrics.stringWidth(candidate) <= width) return candidate;
        }
        return "…";
    }

    static final class RowView extends JPanel {
        private final JLabel title = label(""), status = label("");
        private final JProgressBar progress = new JProgressBar(0, 1000);
        private final JButton action = new JButton(), close = new JButton("×");
        private String fullTitle = "", tooltip = "";
        private TransferRows.Row row;

        RowView(UUID id, Actions actions) {
            super(new BorderLayout(4, 2));
            setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
            progress.setPreferredSize(new Dimension(60, 6));
            progress.setBorderPainted(false);
            close.setMargin(new Insets(0, 4, 0, 4));
            close.getAccessibleContext().setAccessibleName("Cancel or dismiss transfer");
            action.setMargin(new Insets(0, 6, 0, 6));
            var top = new JPanel(new BorderLayout(4, 0));
            top.setOpaque(false);
            top.add(title, BorderLayout.CENTER);
            top.add(close, BorderLayout.EAST);
            var middle = new JPanel(new BorderLayout(0, 2));
            middle.setOpaque(false);
            middle.add(progress, BorderLayout.NORTH);
            middle.add(status, BorderLayout.CENTER);
            var bottom = new JPanel(new BorderLayout(4, 0));
            bottom.setOpaque(false);
            bottom.add(middle, BorderLayout.CENTER);
            bottom.add(action, BorderLayout.EAST);
            add(top, BorderLayout.NORTH);
            add(bottom, BorderLayout.CENTER);
            action.addActionListener(event -> { if (row != null) row.action().ifPresent(chosen -> actions.action().accept(id, chosen)); });
            close.addActionListener(event -> {
                if (row == null) return;
                if (row.finished()) actions.dismiss().accept(id); else actions.cancel().accept(id);
            });
        }

        void show(TransferRows.Row next) {
            row = next;
            fullTitle = next.arrow() + " " + next.title();
            tooltip = next.tooltip();
            title.setToolTipText(tooltip);
            title.getAccessibleContext().setAccessibleDescription(tooltip);
            fitTitle();
            status.setText(next.status());
            status.setToolTipText(next.status());
            progress.setIndeterminate(next.indeterminate());
            progress.setValue((int) Math.round(next.fraction().orElse(0) * 1000));
            progress.setVisible(next.indeterminate() || next.fraction().isPresent());
            action.setVisible(next.action().isPresent());
            next.action().ifPresent(chosen -> action.setText(chosen.label()));
            close.setEnabled(!next.cancelling());
            close.setToolTipText(next.finished() ? "Dismiss" : "Cancel transfer");
        }

        @Override public void doLayout() { fitTitle(); super.doLayout(); }

        private void fitTitle() {
            int width = getWidth() - close.getPreferredSize().width - 8;
            title.setText(width <= 0 ? fullTitle : elideMiddle(fullTitle, title.getFontMetrics(title.getFont()), width));
        }

        String fullTitle() { return fullTitle; }
        String titleText() { return title.getText(); }
        String tooltip() { return tooltip; }
        String statusText() { return status.getText(); }
        JButton actionButton() { return action; }
        JButton closeButton() { return close; }
        JProgressBar progress() { return progress; }
    }
}
