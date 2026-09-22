package dev.jasper.vault.ui;

import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.service.PickPrompt;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

/** The account picker: a list of descriptors, Choose and Cancel. */
public final class PickerPanel extends JPanel {
    final JList<CredentialDescriptor> list;
    final JButton primary = new JButton("Choose");
    final JButton cancel = new JButton("Cancel");

    public PickerPanel(PickPrompt prompt) { this(prompt.choices(), prompt::choose, prompt::cancel); }

    PickerPanel(List<CredentialDescriptor> choices, Consumer<UUID> onChoose, Runnable onCancel) {
        super(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        var model = new DefaultListModel<CredentialDescriptor>();
        choices.forEach(model::addElement);
        list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer((view, value, index, selected, focused) -> {
            var label = new javax.swing.JLabel(label(value));
            label.setOpaque(true);
            label.setBackground(selected ? view.getSelectionBackground() : view.getBackground());
            label.setForeground(selected ? view.getSelectionForeground() : view.getForeground());
            label.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            return label;
        });
        var scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(360, 240));
        add(scroll, BorderLayout.CENTER);
        var buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(Box.createHorizontalGlue()); buttons.add(cancel); buttons.add(Box.createHorizontalStrut(8)); buttons.add(primary);
        add(buttons, BorderLayout.SOUTH);
        primary.setEnabled(false);
        list.addListSelectionListener(event -> primary.setEnabled(list.getSelectedValue() != null));
        primary.addActionListener(event -> { CredentialDescriptor chosen = list.getSelectedValue(); if (chosen != null) onChoose.accept(chosen.id()); });
        cancel.addActionListener(event -> onCancel.run());
    }

    static String label(CredentialDescriptor descriptor) {
        return descriptor.subtitle().isBlank() ? descriptor.name() : descriptor.name() + "  —  " + descriptor.subtitle();
    }
}
