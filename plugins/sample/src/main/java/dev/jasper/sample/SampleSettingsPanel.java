package dev.jasper.sample;

import dev.jasper.sdk.settings.SettingsPanel;
import dev.jasper.sdk.settings.SettingsPanelHost;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;

/** The sample's own settings page: standard rows for the demos, and a slider of its own for the step delay. */
final class SampleSettingsPanel implements SettingsPanel {
    private final SampleSettings settings;
    private JLabel delayLabel;

    SampleSettingsPanel(SampleSettings settings) { this.settings = settings; }

    @Override public JComponent create(SettingsPanelHost host) {
        var page = new JPanel();
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setOpaque(false);
        for (var demo : List.of(settings.activity, settings.ui, settings.terminal, settings.session, settings.scope)) {
            JComponent row = host.row(demo);
            row.setAlignmentX(0);
            page.add(row);
        }
        delayLabel = new JLabel("Step delay");
        delayLabel.putClientProperty("html.disable", true);
        var delay = new JSlider(0, 5000, host.pending(settings.stepMillis));
        delay.setMajorTickSpacing(1000);
        delay.setPaintTicks(true);
        delay.getAccessibleContext().setAccessibleName("Step delay in milliseconds");
        delay.addChangeListener(event -> {
            host.stage(settings.stepMillis, delay.getValue());
            host.changed();
        });
        var row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
        row.setAlignmentX(0);
        row.add(delayLabel, BorderLayout.WEST);
        row.add(delay, BorderLayout.CENTER);
        page.add(row);
        return page;
    }

    @Override public List<String> keywords() { return List.of("sample", "demo", "step delay", "milliseconds"); }

    @Override public void highlight(String query) {
        if (delayLabel == null) return;
        String words = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        boolean match = !words.isEmpty() && "step delay milliseconds".contains(words);
        delayLabel.setFont(delayLabel.getFont().deriveFont(match ? Font.BOLD : Font.PLAIN));
    }
}
