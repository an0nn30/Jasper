package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.vault.api.CredentialDescriptor;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

/** Add or edit one host. Validation is inline; Save hands back a {@link RemoteHost} and the dialog closes it. */
public final class HostEditor extends JPanel {
    final JTextField name = new JTextField(24), hostname = new JTextField(24), port = new JTextField("22", 6), username = new JTextField(16);
    final JComboBox<String> group = new JComboBox<>();
    final JCheckBox favorite = new JCheckBox("Favorite");
    final JRadioButton vaultAuth = new JRadioButton("Vault credential"), agentAuth = new JRadioButton("SSH agent");
    final JButton choose = new JButton("Choose…");
    final JLabel credentialLabel = new JLabel("none chosen");
    final JComboBox<Object> jump = new JComboBox<>();
    final JButton save, cancel = new JButton("Cancel");
    final JLabel message = new JLabel(" ");
    private UUID credentialId;

    public HostEditor(List<RemoteHost> others, Optional<RemoteHost> editing, boolean vaultPresent, Function<UUID, Optional<String>> credentialName,
                      Supplier<CompletableFuture<Optional<CredentialDescriptor>>> pick, Consumer<RemoteHost> onSave, Runnable onCancel) {
        super(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        save = new JButton(editing.isPresent() ? "Save" : "Add Host");
        group.setEditable(true);
        group.addItem("");
        new TreeSet<>(others.stream().map(RemoteHost::group).filter(g -> !g.isEmpty()).toList()).forEach(group::addItem);
        jump.addItem("none");
        others.stream().filter(other -> editing.map(host -> !host.id().equals(other.id())).orElse(true)).forEach(jump::addItem);
        jump.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, value instanceof RemoteHost host ? host.name() : value, index, selected, focus);
            }
        });
        var authGroup = new ButtonGroup(); authGroup.add(vaultAuth); authGroup.add(agentAuth);
        vaultAuth.setEnabled(vaultPresent);
        if (!vaultPresent) { vaultAuth.setText("Vault credential (needs Credential Vault)"); }
        editing.ifPresentOrElse(host -> {
            name.setText(host.name()); hostname.setText(host.hostname()); port.setText(String.valueOf(host.port())); username.setText(host.username());
            group.setSelectedItem(host.group()); favorite.setSelected(host.favorite());
            switch (host.auth()) {
                case Auth.Vault vault -> { vaultAuth.setSelected(true); credentialId = vault.credentialId(); credentialLabel.setText(credentialName.apply(credentialId).orElse("credential missing")); }
                case Auth.Agent agent -> agentAuth.setSelected(true);
            }
            host.jump().ifPresent(id -> { for (int i = 1; i < jump.getItemCount(); i++) if (((RemoteHost) jump.getItemAt(i)).id().equals(id)) jump.setSelectedIndex(i); });
        }, () -> (vaultPresent ? vaultAuth : agentAuth).setSelected(true));

        var form = new JPanel(new GridBagLayout());
        var at = new GridBagConstraints();
        at.insets = new Insets(3, 3, 3, 3); at.anchor = GridBagConstraints.WEST; at.gridy = 0;
        row(form, at, "Name", name); row(form, at, "Hostname", hostname); row(form, at, "Port", port); row(form, at, "Username", username);
        row(form, at, "Group", group); row(form, at, "", favorite);
        var auth = new JPanel(); auth.setLayout(new BoxLayout(auth, BoxLayout.X_AXIS));
        auth.add(vaultAuth); auth.add(Box.createHorizontalStrut(6)); auth.add(choose); auth.add(Box.createHorizontalStrut(6)); auth.add(credentialLabel); auth.add(Box.createHorizontalStrut(12)); auth.add(agentAuth);
        row(form, at, "Authentication", auth); row(form, at, "Jump host", jump);
        add(form, BorderLayout.CENTER);
        var south = new JPanel(); south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.add(message);
        var buttons = new JPanel(); buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(Box.createHorizontalGlue()); buttons.add(cancel); buttons.add(Box.createHorizontalStrut(8)); buttons.add(save);
        south.add(buttons);
        add(south, BorderLayout.SOUTH);

        Runnable syncAuth = () -> choose.setEnabled(vaultAuth.isSelected());
        vaultAuth.addActionListener(e -> syncAuth.run()); agentAuth.addActionListener(e -> syncAuth.run()); syncAuth.run();
        choose.addActionListener(event -> pick.get().thenAccept(chosen -> chosen.ifPresent(descriptor -> { credentialId = descriptor.id(); credentialLabel.setText(descriptor.name()); })));
        cancel.addActionListener(event -> onCancel.run());
        save.addActionListener(event -> {
            try {
                if (name.getText().isBlank()) throw new IllegalArgumentException("A host needs a name");
                int portValue;
                try { portValue = Integer.parseInt(port.getText().strip()); } catch (NumberFormatException bad) { throw new IllegalArgumentException("The port must be a number from 1 to 65535"); }
                Auth chosen;
                if (vaultAuth.isSelected()) { if (credentialId == null) throw new IllegalArgumentException("Choose a credential, or use the SSH agent"); chosen = new Auth.Vault(credentialId); }
                else chosen = Auth.AGENT;
                String groupValue = group.getEditor().getItem() == null ? "" : group.getEditor().getItem().toString();
                Optional<UUID> jumpValue = jump.getSelectedItem() instanceof RemoteHost target ? Optional.of(target.id()) : Optional.empty();
                if (name.getText().isBlank()) throw new IllegalArgumentException("A host needs a name");
                for (RemoteHost other : others)
                    if (other.name().equalsIgnoreCase(name.getText().strip()) && editing.map(host -> !host.id().equals(other.id())).orElse(true)) throw new IllegalArgumentException("A host named " + other.name() + " exists");
                RemoteHost host = editing.map(existing -> existing.withEdited(name.getText(), hostname.getText(), portValue, username.getText(), chosen, groupValue, jumpValue))
                    .orElseGet(() -> RemoteHost.create(name.getText(), hostname.getText(), portValue, username.getText(), chosen, groupValue, jumpValue));
                onSave.accept(favorite.isSelected() ? host.withFavorite(true) : host.withFavorite(false));
            } catch (IllegalArgumentException invalid) { message.setText(invalid.getMessage()); }
        });
    }

    public void showError(String error) { message.setText(error); }

    private static void row(JPanel form, GridBagConstraints at, String label, java.awt.Component field) {
        at.gridx = 0; form.add(new JLabel(label), at); at.gridx = 1; form.add(field, at); at.gridy++;
    }
}
