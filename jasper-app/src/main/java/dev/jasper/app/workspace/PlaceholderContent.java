package dev.jasper.app.workspace;

import dev.jasper.app.contributions.TabContent;
import dev.jasper.app.persistence.WorkspaceFile;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.NativeStyles;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.text.View;

/**
 * A plugin's tab that could not come back when the windows were restored (workspace tabs spec 9.2 and 13): it says why,
 * with Close and, while its plugin runs, Retry. It keeps what was saved, so it is saved again as it was and comes back as
 * the plugin's tab once the plugin can restore it. Whenever it shows, it asks again how its plugin stands. EDT only.
 */
final class PlaceholderContent extends JPanel implements TabContent {
    private final WorkspaceFile.TabRecord.Content saved;
    /**
     * Why it did not come back, as plain text that wraps (6b Task 2's review): one line as wide as its text while that fits
     * the pane less its margins, else wrapped at that width, as tall as its lines.
     */
    private final JTextArea reason = new JTextArea() {
        @Override public Dimension getPreferredSize() {
            if (getUI() == null || getFont() == null) return super.getPreferredSize();
            Insets insets = getInsets();
            int line = getFontMetrics(getFont()).stringWidth(getText()) + insets.left + insets.right + 1;
            int room = PlaceholderContent.this.getWidth() - 2 * NativeStyles.installed().metric(DesignMetrics.Role.NOTICE_HORIZONTAL_PADDING);
            int width = room > 0 ? Math.min(line, room) : line;
            View lines = getUI().getRootView(this);
            lines.setSize(Math.max(1, width - insets.left - insets.right), Integer.MAX_VALUE);
            return new Dimension(width, (int) Math.ceil(lines.getPreferredSpan(View.Y_AXIS)) + insets.top + insets.bottom);
        }
    };
    private final JButton retry = new JButton("Retry");
    private final JButton close = new JButton("Close");
    private final Runnable onShown;

    /**
     * A placeholder for {@code saved}.
     *
     * @param saved what was saved for the tab
     * @param why why it did not come back, a sentence
     * @param retryable whether its plugin runs, so that Retry can rebuild it
     * @param onRetry runs the plugin's restorer again
     * @param onClose closes the tab
     * @param onShown asks how its plugin stands, whenever it shows
     */
    PlaceholderContent(WorkspaceFile.TabRecord.Content saved, String why, boolean retryable, Runnable onRetry, Runnable onClose,
                       Runnable onShown) {
        super(new GridBagLayout());
        this.saved = Objects.requireNonNull(saved, "saved");
        this.onShown = Objects.requireNonNull(onShown, "onShown");
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) shown();
        });
        setName("workspace.placeholder");
        setOpaque(false);
        reason.setName("workspace.placeholder.reason");
        reason.putClientProperty("html.disable", Boolean.TRUE);
        // Read, not edited: the keyboard goes from Retry to Close, never into the text.
        reason.setEditable(false);
        reason.setFocusable(false);
        reason.setOpaque(false);
        reason.setLineWrap(true);
        reason.setWrapStyleWord(true);
        reason.setBorder(BorderFactory.createEmptyBorder());
        NativeStyles.installed().textArea(reason, () -> ThemeZone.theme(reason));
        for (JButton button : new JButton[]{retry, close}) {
            button.setName("workspace.placeholder." + button.getText().toLowerCase(java.util.Locale.ROOT));
            NativeStyles.installed().button(button, () -> ThemeZone.theme(button));
        }
        retry.addActionListener(event -> onRetry.run());
        close.addActionListener(event -> onClose.run());
        int gap = NativeStyles.installed().metric(DesignMetrics.Role.NATIVE_ACTION_PADDING);
        var actions = new JPanel(new FlowLayout(FlowLayout.CENTER, gap, 0));
        actions.setOpaque(false);
        actions.add(retry);
        actions.add(close);
        var place = new GridBagConstraints();
        place.insets = new Insets(0, 0, gap, 0);
        add(reason, place);
        place = new GridBagConstraints();
        place.gridy = 1;
        add(actions, place);
        failed(why, retryable);
    }

    /** What was saved for the tab: it is saved again unchanged. */
    WorkspaceFile.TabRecord.Content saved() { return saved; }

    /** Why the tab did not come back. */
    String reason() { return reason.getText(); }

    /** Whether Retry is offered: its plugin runs. */
    boolean retryable() { return retry.isVisible(); }

    /** A Retry that failed again, or the first failure: says why, and offers Retry while the plugin runs. */
    void failed(String why, boolean retryable) {
        reason.setText(Objects.requireNonNull(why, "why"));
        retry.setVisible(retryable);
        revalidate();
        repaint();
    }

    /** The button the keyboard reaches first, the tab's focus target: Retry while it shows, otherwise Close. */
    JButton action() { return retry.isVisible() ? retry : close; }

    /** Focuses {@link #action()}, as the tab's focus target does when the tab is focused. */
    void focusAction() { action().requestFocusInWindow(); }

    /** It shows: what it says follows how its plugin stands now. */
    void shown() { onShown.run(); }

    /** The Retry button, for tests. */
    JButton retryButton() { return retry; }

    /** The Close button, for tests. */
    JButton closeButton() { return close; }

    @Override public JComponent component() { return this; }

    @Override public void dispose() { }
}
