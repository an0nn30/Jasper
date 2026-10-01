package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Forward a Port: a remote port reached on this computer's loopback, through the SSH pane's host or a chosen one. The
 * local port follows the remote one (or the next free port) until edited. It starts a quick forward, or a saved tunnel
 * when "Keep as a saved tunnel" is ticked.
 */
public final class ForwardPanel extends JPanel {
    final JLabel heading = new JLabel();
    final JComboBox<RemoteHost> host = new JComboBox<>();
    final JPanel hostRow = new JPanel(new BorderLayout(6, 0)), moreRow = new JPanel(new BorderLayout(6, 0));
    final JTextField remotePort = new JTextField(6), localPort = new JTextField(6), targetHost = new JTextField("localhost", 14);
    final JTextField saveName = new JTextField(16);
    final JButton more = new JButton("More ▸");
    final JCheckBox keep = new JCheckBox("Keep as a saved tunnel");
    final JLabel message = new JLabel(" ");
    final JButton forward = new JButton("Forward"), cancel = new JButton("Cancel");
    private final Optional<RemoteHost> paneHost;
    private final IntPredicate free;
    private boolean localEdited, filling;

    /**
     * @param paneHost the host of the SSH pane it was opened from; empty asks for one
     * @param free whether a port on this computer's loopback is free
     * @param onForward receives the quick forward, or the named tunnel to save and start
     */
    public ForwardPanel(List<RemoteHost> hosts, Optional<RemoteHost> paneHost, IntPredicate free, Consumer<Tunnel> onForward, Runnable onCancel) {
        super(new BorderLayout(0, 8));
        this.paneHost = paneHost;
        this.free = free;
        setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        heading.setText(paneHost.map(via -> "Forward a port on " + via.name() + " to this computer").orElse("Forward a port on a host to this computer"));
        heading.putClientProperty("html.disable", true);
        hosts.forEach(host::addItem);
        host.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                putClientProperty("html.disable", true);
                super.getListCellRendererComponent(list, value instanceof RemoteHost via ? via.name() : "", index, selected, focus);
                return this;
            }
        });
        hostRow.add(new JLabel("Host"), BorderLayout.WEST);
        hostRow.add(host, BorderLayout.CENTER);
        hostRow.setVisible(paneHost.isEmpty());
        moreRow.add(new JLabel("Forward to host"), BorderLayout.WEST);
        moreRow.add(targetHost, BorderLayout.CENTER);
        moreRow.setVisible(false);
        saveName.setEnabled(false);

        var form = new JPanel(new GridBagLayout());
        var at = new GridBagConstraints();
        at.insets = new Insets(3, 3, 3, 3);
        at.anchor = GridBagConstraints.WEST;
        at.gridx = 0;
        at.gridwidth = 2;
        at.gridy = 0;
        form.add(heading, at); at.gridy++;
        form.add(hostRow, at); at.gridy++;
        at.gridwidth = 1;
        row(form, at, "Remote port", remotePort);
        row(form, at, "Local port", localPort);
        at.gridwidth = 2;
        form.add(more, at); at.gridy++;
        form.add(moreRow, at); at.gridy++;
        form.add(keep, at); at.gridy++;
        at.gridwidth = 1;
        row(form, at, "Name", saveName);
        add(form, BorderLayout.CENTER);
        var south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        message.setAlignmentX(Component.LEFT_ALIGNMENT);
        message.putClientProperty("html.disable", true);
        south.add(message);
        var buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.add(Box.createHorizontalGlue()); buttons.add(cancel); buttons.add(Box.createHorizontalStrut(8)); buttons.add(forward);
        south.add(buttons);
        add(south, BorderLayout.SOUTH);

        remotePort.getDocument().addDocumentListener(listener(this::suggest));
        localPort.getDocument().addDocumentListener(listener(() -> { if (!filling) localEdited = true; }));
        more.addActionListener(event -> {
            moreRow.setVisible(!moreRow.isVisible());
            more.setText(moreRow.isVisible() ? "Less ▾" : "More ▸");
            revalidate();
            Window window = SwingUtilities.getWindowAncestor(this);
            if (window != null) window.pack();
        });
        keep.addActionListener(event -> {
            saveName.setEnabled(keep.isSelected());
            if (keep.isSelected() && saveName.getText().isBlank()) {
                String remotePortText = remotePort.getText().strip();
                if (!remotePortText.isEmpty()) saveName.setText("Port " + remotePortText + " on " + via().map(RemoteHost::name).orElse("the host"));
            }
        });
        cancel.addActionListener(event -> onCancel.run());
        forward.addActionListener(event -> {
            try {
                int remote = port(remotePort, 1, "The remote port must be a number from 1 to 65535");
                int local = port(localPort, 0, "The local port must be a number from 0 to 65535");
                RemoteHost through = via().orElseThrow(() -> new IllegalArgumentException("Choose a host"));
                String name = keep.isSelected() ? saveName.getText().strip() : "";
                if (keep.isSelected() && name.isEmpty()) throw new IllegalArgumentException("Name the tunnel to keep it");
                Tunnel tunnel = Tunnel.create(name, through.id(), TunnelKind.LOCAL, Tunnel.LOCALHOST, local, targetHost.getText(), remote);
                onForward.accept(tunnel);
            } catch (IllegalArgumentException invalid) { message.setText(invalid.getMessage()); }
        });
    }

    public void showError(String error) { message.setText(error); }

    private Optional<RemoteHost> via() { return paneHost.or(() -> Optional.ofNullable((RemoteHost) host.getSelectedItem())); }

    /** The local port follows the remote one until someone edits it: the same number, else the next free one. */
    private void suggest() {
        if (localEdited) return;
        filling = true;
        try { localPort.setText(suggestion(remotePort.getText().strip())); }
        finally { filling = false; }
    }

    private String suggestion(String remote) {
        int port;
        try { port = Integer.parseInt(remote); } catch (NumberFormatException none) { return ""; }
        if (port < 1 || port > 65535) return "";
        for (int candidate = port; candidate <= Math.min(65535, port + 100); candidate++) if (free.test(candidate)) return String.valueOf(candidate);
        return String.valueOf(port);
    }

    private static int port(JTextField field, int minimum, String problem) {
        try {
            int value = Integer.parseInt(field.getText().strip());
            if (value < minimum || value > 65535) throw new IllegalArgumentException(problem);
            return value;
        } catch (NumberFormatException bad) { throw new IllegalArgumentException(problem); }
    }

    private static DocumentListener listener(Runnable action) {
        return new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { action.run(); }
            @Override public void removeUpdate(DocumentEvent e) { action.run(); }
            @Override public void changedUpdate(DocumentEvent e) { action.run(); }
        };
    }

    private static void row(JPanel form, GridBagConstraints at, String label, Component field) {
        at.gridx = 0; form.add(new JLabel(label), at);
        at.gridx = 1; form.add(field, at);
        at.gridx = 0; at.gridy++;
    }
}
