package dev.jasper.app.settingsui;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Font;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

/** A category page, as IntelliJ shows one: its title and a link to each child page. */
public final class GroupPage implements SettingsPage {
    private final String id;
    private final String title;
    private final Optional<String> parent;
    private final boolean hiddenWhenChildless;
    private final Supplier<List<SettingsPage>> children;
    private final PageContext context;
    private JPanel panel;

    public GroupPage(String id, String title, Optional<String> parent, boolean hiddenWhenChildless,
                     Supplier<List<SettingsPage>> children, PageContext context) {
        this.id = id; this.title = title; this.parent = parent; this.hiddenWhenChildless = hiddenWhenChildless;
        this.children = children; this.context = context;
    }

    @Override public String id() { return id; }
    @Override public String title() { return title; }
    @Override public Optional<String> parent() { return parent; }
    @Override public List<String> searchTerms() { return List.of(title); }
    @Override public boolean hiddenWhenChildless() { return hiddenWhenChildless; }

    @Override public JComponent component() {
        if (panel != null) return panel;
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        var heading = new JLabel(title);
        heading.putClientProperty("html.disable", true);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        panel.add(heading);
        for (SettingsPage child : children.get()) {
            var link = new JButton(child.title());
            link.putClientProperty("html.disable", true);
            link.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
            var color = UIManager.getColor("Component.linkColor");
            if (color != null) link.setForeground(color);
            link.setAlignmentX(0);
            link.addActionListener(event -> context.navigate().accept(child.id()));
            panel.add(link);
        }
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    @Override public boolean modified() { return false; }
    @Override public void reset() { }
    @Override public CompletableFuture<Void> apply() { return CompletableFuture.completedFuture(null); }
    @Override public void highlight(String query) { }
    @Override public void dispose() { }
}
