package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.contributions.ProgressState;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Function;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.UIManager;

/** Stable, bounded host chrome; progress ticks never replace this component or its listeners. */
final class StatusProgressView extends JPanel {
    private final JButton main = button();
    private final JButton secondary = button();
    private final JProgressBar bar = new JProgressBar(0, 1000);
    private Action primaryAction, secondaryAction;

    StatusProgressView() {
        super(null);
        setOpaque(false); bar.setFocusable(false);
        main.addActionListener(e -> invoke(primaryAction, e));
        secondary.addActionListener(e -> invoke(secondaryAction, e));
        bar.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                invoke(primaryAction, new ActionEvent(bar, ActionEvent.ACTION_PERFORMED, "progress"));
            }
        });
        add(main); add(bar); add(secondary);
    }

    private static JButton button() {
        var result = new JButton();
        result.putClientProperty("html.disable", true);
        result.setBorder(BorderFactory.createEmptyBorder());
        result.setOpaque(false); result.setContentAreaFilled(false);
        return result;
    }

    private static void invoke(Action action, ActionEvent event) {
        if (action != null && action.isEnabled()) action.actionPerformed(event);
    }

    void update(ProgressState state, Function<String, Action> actions) {
        primaryAction = state.actionId() == null ? null : actions.apply(state.actionId());
        secondaryAction = state.secondaryActionId() == null ? null : actions.apply(state.secondaryActionId());
        main.setText(state.text() + (state.detail().isEmpty() ? "" : " \u00b7 " + state.detail()));
        main.setEnabled(primaryAction != null && primaryAction.isEnabled());
        main.getAccessibleContext().setAccessibleName(state.accessibleDescription());
        main.setToolTipText(state.accessibleDescription());
        bar.setIndeterminate(state.fraction().isEmpty());
        bar.setValue((int) Math.round(state.fraction().orElse(0) * 1000));
        bar.getAccessibleContext().setAccessibleName(state.text());
        bar.getAccessibleContext().setAccessibleDescription(state.accessibleDescription());
        secondary.setVisible(state.secondaryActionId() != null);
        secondary.setText(secondaryAction == null ? "" : String.valueOf(secondaryAction.getValue(Action.NAME)));
        secondary.setEnabled(secondaryAction != null && secondaryAction.isEnabled());
        getAccessibleContext().setAccessibleDescription(state.accessibleDescription());
        refreshTheme(); revalidate(); repaint();
    }

    void refreshTheme() {
        for (var button : new JButton[]{main, secondary}) {
            button.setFont(UIManager.getFont("Label.font"));
            button.setForeground(UIManager.getColor("Jasper.mutedForeground"));
        }
    }

    @Override public Dimension getPreferredSize() {
        return new Dimension(Math.min(UIScale.scale(420), main.getPreferredSize().width + UIScale.scale(90)
            + (secondary.isVisible() ? secondary.getPreferredSize().width + UIScale.scale(8) : 0)),
            Math.max(UIScale.scale(24), Math.max(main.getPreferredSize().height, secondary.getPreferredSize().height)));
    }
    @Override public Dimension getMinimumSize() { return new Dimension(0, getPreferredSize().height); }
    @Override public void doLayout() {
        int width = Math.max(0, getWidth()), height = Math.max(0, getHeight());
        int cancel = secondary.isVisible() ? Math.min(width, secondary.getPreferredSize().width) : 0;
        secondary.setBounds(width - cancel, 0, cancel, height);
        int remaining = Math.max(0, width - cancel - (cancel > 0 ? UIScale.scale(8) : 0));
        int progress = Math.min(UIScale.scale(72), remaining / 3);
        int gap = progress > 0 ? Math.min(UIScale.scale(8), remaining - progress) : 0;
        main.setBounds(0, 0, Math.max(0, remaining - progress - gap), height);
        int barHeight = Math.min(height, UIScale.scale(8));
        bar.setBounds(remaining - progress, (height - barHeight) / 2, progress, barHeight);
    }
}
