package dev.jasper.app.workspace;

import dev.jasper.app.contributions.TabContent;
import dev.jasper.app.persistence.WorkspaceFile;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.NativeStyles;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * A plugin's tab that could not come back when the windows were restored (workspace tabs spec 9.2 and 13): it says why,
 * with Close and, while its plugin runs, Retry. It keeps what was saved, so it is saved again as it was and comes back as
 * the plugin's tab once the plugin can restore it. Whenever it shows, it asks again how its plugin stands. EDT only.
 */
final class PlaceholderContent extends JPanel implements TabContent {
    private final WorkspaceFile.TabRecord.Content saved;
    private final JLabel reason = new JLabel("", SwingConstants.CENTER);
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
        NativeStyles.installed().label(reason, Typography.Role.LABEL, SemanticColor.TEXT_PRIMARY, () -> ThemeZone.theme(reason));
        for (JButton button : new JButton[]{retry, close}) {
            button.setName("workspace.placeholder." + button.getText().toLowerCase(java.util.Locale.ROOT));
            NativeStyles.installed().button(button, () -> ThemeZone.theme(button));
        }
        retry.addActionListener(event -> onRetry.run());
        close.addActionListener(event -> onClose.run());
        int gap = NativeStyles.installed().metric(DesignMetrics.Role.NATIVE_ACTION_PADDING);
        var place = new GridBagConstraints();
        place.gridwidth = 2;
        place.insets = new Insets(0, 0, gap, 0);
        add(reason, place);
        place = new GridBagConstraints();
        place.gridy = 1;
        place.insets = new Insets(0, 0, 0, gap / 2);
        add(retry, place);
        place = new GridBagConstraints();
        place.gridy = 1;
        place.insets = new Insets(0, gap / 2, 0, 0);
        add(close, place);
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
