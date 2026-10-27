package dev.jasper.app.workspace;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.NativeStyles;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * What a window's workspace shows once its last tab has closed (workspace tabs spec 1 decision 9, spec 10): New
 * Terminal Tab and Open File, each with its current shortcut, as VS Code's and IntelliJ's empty editor areas do. A click
 * runs the window's action. EDT only.
 */
final class EmptyWorkspace extends JPanel {
    /** One command: the button that runs it and the caption that shows its shortcut. */
    private record Row(ActionId id, JButton button, JLabel shortcut) { }

    private final WindowContent owner;
    private final List<Row> rows = new ArrayList<>();

    EmptyWorkspace(WindowContent owner) {
        super(new GridBagLayout());
        this.owner = Objects.requireNonNull(owner, "owner");
        setName("workspace.empty");
        setOpaque(false);
        addRow(ActionId.NEW_TAB, "New Terminal Tab");
        addRow(ActionId.OPEN_FILE, "Open File…");
        refresh();
    }

    private void addRow(ActionId id, String label) {
        var button = new JButton(label);
        button.setName("workspace.empty." + id.id());
        button.setFocusable(false);
        button.getAccessibleContext().setAccessibleName(label);
        NativeStyles.installed().button(button, () -> ThemeZone.theme(button));
        button.addActionListener(event -> owner.invoke(id));
        var shortcut = new JLabel();
        shortcut.setName("workspace.empty." + id.id() + ".shortcut");
        NativeStyles.installed().label(shortcut, Typography.Role.CAPTION, SemanticColor.TEXT_PLACEHOLDER, () -> ThemeZone.theme(shortcut));
        int gap = NativeStyles.installed().metric(DesignMetrics.Role.NATIVE_ACTION_PADDING);
        var place = new GridBagConstraints();
        place.gridy = rows.size();
        place.anchor = GridBagConstraints.LINE_END;
        place.insets = new Insets(gap / 2, 0, gap / 2, gap);
        add(button, place);
        place = new GridBagConstraints();
        place.gridy = rows.size();
        place.anchor = GridBagConstraints.LINE_START;
        place.insets = new Insets(gap / 2, 0, gap / 2, 0);
        add(shortcut, place);
        rows.add(new Row(id, button, shortcut));
    }

    /** Shows each command's shortcut as the keymap binds it now; a command with none shows no caption. */
    void refresh() {
        for (Row row : rows) {
            String text = owner.shortcutText(row.id());
            row.shortcut().setText(text);
            row.shortcut().setVisible(!text.isEmpty());
            row.button().setEnabled(owner.action(row.id()).isEnabled());
        }
    }

    /** The button that runs {@code id}, for tests. */
    JButton button(ActionId id) { return rows.stream().filter(row -> row.id() == id).findFirst().orElseThrow().button(); }

    /** The caption showing {@code id}'s shortcut, for tests. */
    JLabel shortcut(ActionId id) { return rows.stream().filter(row -> row.id() == id).findFirst().orElseThrow().shortcut(); }
}
