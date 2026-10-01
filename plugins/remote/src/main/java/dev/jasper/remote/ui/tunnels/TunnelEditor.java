package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Add or edit one tunnel, or name a quick forward to keep it. Validation is inline; Save hands back the tunnel. */
public final class TunnelEditor extends JPanel {
    final JTextField name = new JTextField(20);
    final JComboBox<RemoteHost> host = new JComboBox<>();
    final JRadioButton local = new JRadioButton("Local"), remote = new JRadioButton("Remote"), dynamic = new JRadioButton("Dynamic (SOCKS)");
    final JLabel explanation = new JLabel(), listenLabel = new JLabel(), forwardLabel = new JLabel();
    final JTextField listenAddress = new JTextField(Tunnel.LOCALHOST, 12), listenPort = new JTextField(6);
    final JTextField forwardHost = new JTextField("localhost", 14), forwardPort = new JTextField(6);
    final JCheckBox allowOthers = new JCheckBox("Allow other computers to connect");
    final JLabel exposure = new JLabel(" "), preview = new JLabel(" "), warning = new JLabel(" "), message = new JLabel(" ");
    final JButton save, cancel = new JButton("Cancel");
    private final JPanel forwardFields;
    private final List<Tunnel> others;
    private final UUID editingId;

    public TunnelEditor(List<RemoteHost> hosts, List<Tunnel> others, Optional<Tunnel> editing, Optional<UUID> preferredHost,
                        Consumer<Tunnel> onSave, Runnable onCancel) {
        super(new BorderLayout(0, 8));
        // These show host and tunnel names and what was typed; none of it is markup, and the property must precede the text.
        for (JLabel label : List.of(explanation, listenLabel, forwardLabel, exposure, preview, warning, message)) label.putClientProperty("html.disable", true);
        this.others = List.copyOf(others);
        this.editingId = editing.map(Tunnel::id).orElse(null);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        save = new JButton(editing.isEmpty() ? "Add Tunnel" : "Save");
        hosts.forEach(host::addItem);
        host.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                putClientProperty("html.disable", true);
                super.getListCellRendererComponent(list, value instanceof RemoteHost via ? via.name() + " — " + via.label() : "", index, selected, focus);
                return this;
            }
        });
        UUID wanted = editing.map(Tunnel::hostId).or(() -> preferredHost).orElse(null);
        host.setSelectedIndex(-1);
        for (int i = 0; i < host.getItemCount(); i++) if (host.getItemAt(i).id().equals(wanted)) host.setSelectedIndex(i);
        if (host.getSelectedIndex() < 0 && editing.isEmpty() && host.getItemCount() > 0) host.setSelectedIndex(0);
        var types = new ButtonGroup();
        types.add(local); types.add(remote); types.add(dynamic);
        editing.ifPresentOrElse(tunnel -> {
            name.setText(tunnel.name());
            (tunnel.kind() == TunnelKind.REMOTE ? remote : tunnel.kind() == TunnelKind.DYNAMIC ? dynamic : local).setSelected(true);
            listenAddress.setText(tunnel.bindAddress());
            listenPort.setText(String.valueOf(tunnel.bindPort()));
            allowOthers.setSelected(tunnel.bindAddress().equals(Tunnel.ALL));
            if (tunnel.kind() != TunnelKind.DYNAMIC) {
                forwardHost.setText(tunnel.targetHost());
                forwardPort.setText(String.valueOf(tunnel.targetPort()));
            }
        }, () -> local.setSelected(true));
        listenAddress.setEnabled(!allowOthers.isSelected());

        var form = new JPanel(new GridBagLayout());
        var at = new GridBagConstraints();
        at.insets = new Insets(3, 3, 3, 3);
        at.anchor = GridBagConstraints.WEST;
        at.gridy = 0;
        row(form, at, new JLabel("Name"), name);
        row(form, at, new JLabel("Via host"), host);
        var typeRow = new JPanel();
        typeRow.setLayout(new BoxLayout(typeRow, BoxLayout.X_AXIS));
        typeRow.add(local); typeRow.add(Box.createHorizontalStrut(8)); typeRow.add(remote); typeRow.add(Box.createHorizontalStrut(8)); typeRow.add(dynamic);
        row(form, at, new JLabel("Type"), typeRow);
        row(form, at, new JLabel(""), explanation);
        row(form, at, listenLabel, pair(listenAddress, listenPort));
        row(form, at, new JLabel(""), allowOthers);
        row(form, at, new JLabel(""), exposure);
        forwardFields = pair(forwardHost, forwardPort);
        row(form, at, forwardLabel, forwardFields);
        row(form, at, new JLabel(""), preview);
        add(form, BorderLayout.CENTER);
        var south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        warning.setAlignmentX(Component.LEFT_ALIGNMENT);
        message.setAlignmentX(Component.LEFT_ALIGNMENT);
        south.add(warning);
        south.add(message);
        var buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.add(Box.createHorizontalGlue()); buttons.add(cancel); buttons.add(Box.createHorizontalStrut(8)); buttons.add(save);
        south.add(buttons);
        add(south, BorderLayout.SOUTH);

        DocumentListener changed = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refresh(); }
            @Override public void removeUpdate(DocumentEvent e) { refresh(); }
            @Override public void changedUpdate(DocumentEvent e) { refresh(); }
        };
        for (JTextField field : List.of(listenAddress, listenPort, forwardHost, forwardPort)) field.getDocument().addDocumentListener(changed);
        for (JRadioButton type : List.of(local, remote, dynamic)) type.addActionListener(event -> refresh());
        host.addActionListener(event -> refresh());
        allowOthers.addActionListener(event -> {
            listenAddress.setText(allowOthers.isSelected() ? Tunnel.ALL : Tunnel.LOCALHOST);
            listenAddress.setEnabled(!allowOthers.isSelected());
            refresh();
        });
        cancel.addActionListener(event -> onCancel.run());
        save.addActionListener(event -> {
            Tunnel tunnel;
            try { tunnel = tunnel(editing); }
            catch (IllegalArgumentException invalid) { message.setText(invalid.getMessage()); return; }
            onSave.accept(tunnel);
        });
        refresh();
    }

    public void showError(String error) { message.setText(error); }

    private Tunnel tunnel(Optional<Tunnel> editing) {
        String nameValue = name.getText().strip();
        if (nameValue.isEmpty()) throw new IllegalArgumentException("A tunnel needs a name");
        for (Tunnel other : others)
            if (!other.id().equals(editingId) && other.name().equalsIgnoreCase(nameValue)) throw new IllegalArgumentException("A tunnel named " + other.name() + " exists");
        RemoteHost via = selectedHost().orElseThrow(() -> new IllegalArgumentException("Choose a host to go through"));
        TunnelKind kind = kind();
        int bindPort = port(listenPort, 0, "The listen port must be a number from 0 to 65535");
        int targetPort = kind == TunnelKind.DYNAMIC ? 0 : port(forwardPort, 1, "The target port must be a number from 1 to 65535");
        return editing.map(existing -> existing.withEdited(nameValue, via.id(), kind, listenAddress.getText(), bindPort, forwardHost.getText(), targetPort))
            .orElseGet(() -> Tunnel.create(nameValue, via.id(), kind, listenAddress.getText(), bindPort, forwardHost.getText(), targetPort));
    }

    private void refresh() {
        TunnelKind kind = kind();
        boolean server = kind == TunnelKind.REMOTE;
        String via = selectedHost().map(RemoteHost::name).orElse("the host");
        listenLabel.setText(server ? "Listen on (the server)" : "Listen on (this computer)");
        forwardLabel.setText(server ? "Forward to (as seen from this computer)" : "Forward to (as seen from " + via + ")");
        forwardLabel.setVisible(kind != TunnelKind.DYNAMIC);
        forwardFields.setVisible(kind != TunnelKind.DYNAMIC);
        explanation.setText(switch (kind) {
            case LOCAL -> "Connections to a port on this computer go through " + via + " to the target.";
            case REMOTE -> "Connections to a port on the server come back through this computer to the target.";
            case DYNAMIC -> "A SOCKS5 proxy on this computer; each connection goes out through " + via + ".";
        });
        exposure.setText(allowOthers.isSelected()
            ? "Other computers on the network can connect to this port." + (server ? " The server's GatewayPorts setting must allow it." : "") : " ");
        preview.setText(preview(kind, via));
        warning.setText(sharedPort(kind).map(other -> other.name() + " listens on the same port; they cannot run at once.").orElse(" "));
        revalidate();
    }

    private String preview(TunnelKind kind, String via) {
        String port = listenPort.getText().strip();
        String listen = listenAddress.getText().strip() + ":" + (port.isEmpty() ? "?" : port.equals("0") ? "any port" : port);
        String target = forwardHost.getText().strip() + ":" + (forwardPort.getText().strip().isEmpty() ? "?" : forwardPort.getText().strip());
        return switch (kind) {
            case LOCAL -> listen + " on this computer → " + target + " as seen from " + via;
            case REMOTE -> listen + " on " + via + " → " + target + " as seen from this computer";
            case DYNAMIC -> "A SOCKS5 proxy on " + listen + "; connections leave from " + via;
        };
    }

    /** Another saved tunnel on the same side, address and port: both may exist, but not run at once. */
    private Optional<Tunnel> sharedPort(TunnelKind kind) {
        int port;
        try { port = Integer.parseInt(listenPort.getText().strip()); } catch (NumberFormatException none) { return Optional.empty(); }
        if (port <= 0) return Optional.empty();
        String address = listenAddress.getText().strip();
        UUID via = selectedHost().map(RemoteHost::id).orElse(null);
        return others.stream()
            .filter(other -> !other.id().equals(editingId) && other.bindPort() == port && other.bindAddress().equals(address))
            .filter(other -> (other.kind() == TunnelKind.REMOTE) == (kind == TunnelKind.REMOTE) && (kind != TunnelKind.REMOTE || other.hostId().equals(via)))
            .findFirst();
    }

    private TunnelKind kind() { return remote.isSelected() ? TunnelKind.REMOTE : dynamic.isSelected() ? TunnelKind.DYNAMIC : TunnelKind.LOCAL; }

    private Optional<RemoteHost> selectedHost() { return Optional.ofNullable((RemoteHost) host.getSelectedItem()); }

    private static int port(JTextField field, int minimum, String problem) {
        try {
            int value = Integer.parseInt(field.getText().strip());
            if (value < minimum || value > 65535) throw new IllegalArgumentException(problem);
            return value;
        } catch (NumberFormatException bad) { throw new IllegalArgumentException(problem); }
    }

    private static JPanel pair(JTextField address, JTextField port) {
        var pair = new JPanel();
        pair.setLayout(new BoxLayout(pair, BoxLayout.X_AXIS));
        pair.add(address); pair.add(new JLabel(" : ")); pair.add(port);
        return pair;
    }

    private static void row(JPanel form, GridBagConstraints at, JLabel label, Component field) {
        at.gridx = 0; form.add(label, at);
        at.gridx = 1; form.add(field, at);
        at.gridy++;
    }
}
