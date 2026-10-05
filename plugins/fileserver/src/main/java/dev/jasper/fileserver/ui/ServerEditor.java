package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.auth.AuthorizedKeys;
import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.Login;
import dev.jasper.fileserver.definition.PortRange;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.ServerAddresses;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Add a server, edit one, or save a temporary one. Validation is inline; Save hands back a definition. */
public final class ServerEditor extends JPanel {
    /** A Vault account as the editor shows it; the UI never sees Vault types. */
    public record Account(UUID id, String name) { }

    public record BindChoice(Bind bind, String label) {
        @Override public String toString() { return label; }
    }

    /** Where "Add from File…" and "Paste" get authorized-key text; each returns empty when there is nothing. */
    public record KeySources(Supplier<Optional<String>> fromFile, Supplier<Optional<String>> fromClipboard) {
        public static final KeySources NONE = new KeySources(Optional::empty, Optional::empty);
    }

    final JTextField name = new JTextField(24), folder = new JTextField(24), port = new JTextField(6);
    final JComboBox<Protocol> protocol = new JComboBox<>();
    final JButton chooseFolder = new JButton("Choose…"), chooseAccount = new JButton("Choose…");
    final JCheckBox autostart = new JCheckBox("Start when Jasper starts");
    final JComboBox<BindChoice> reachable = new JComboBox<>();
    final JCheckBox writable = new JCheckBox("Allow changes (upload, rename, delete)");
    final JRadioButton anonymous = new JRadioButton("Anonymous"), vault = new JRadioButton("Vault account"), session = new JRadioButton("Session password");
    final JCheckBox useTls = new JCheckBox("Use TLS"), requireTls = new JCheckBox("Require TLS (refuse logins and transfers without it)");
    final JTextField passive = new JTextField(12);
    final DefaultListModel<String> keyLines = new DefaultListModel<>();
    final JList<String> keys = new JList<>(keyLines);
    final JButton addKeyFile = new JButton("Add from File…"), pasteKeys = new JButton("Paste"), removeKey = new JButton("Remove");
    final JLabel accountLabel = label("none chosen"), portHint = label(" "), warnings = label(" "), message = label(" ");
    final JButton save, cancel = new JButton("Cancel");
    private final List<ServerDefinition> others;
    private final Optional<ServerDefinition> initial;
    private final boolean existing, vaultPresent, linux;
    private Protocol previous;
    private UUID accountId;

    public ServerEditor(List<ServerDefinition> others, Optional<ServerDefinition> initial, boolean existing, Set<Protocol> protocols,
                        List<ServerAddresses.Nic> interfaces, boolean vaultPresent, Function<UUID, Optional<String>> accountName,
                        Supplier<CompletableFuture<Optional<Account>>> accountPicker, Supplier<Optional<Path>> folderChooser,
                        KeySources keySources, boolean linux, Consumer<ServerDefinition> onSave, Runnable onCancel) {
        super(new BorderLayout(0, 8));
        this.others = List.copyOf(others);
        this.initial = initial;
        this.existing = existing;
        this.vaultPresent = vaultPresent;
        this.linux = linux;
        setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        save = new JButton(existing ? "Save" : "Add Server");
        for (Protocol each : Protocol.values())
            if (protocols.contains(each) || initial.map(d -> d.protocol() == each).orElse(false)) protocol.addItem(each);
        protocol.setEnabled(!existing && protocol.getItemCount() > 1);
        reachable.addItem(new BindChoice(Bind.LOOPBACK, Bind.LOOPBACK.label()));
        reachable.addItem(new BindChoice(Bind.ALL, Bind.ALL.label()));
        for (ServerAddresses.Nic nic : interfaces)
            if (nic.up() && !nic.loopback() && !nic.addresses().isEmpty()) reachable.addItem(new BindChoice(new Bind.Interface(nic.name()), nic.label()));
        var logins = new ButtonGroup();
        logins.add(anonymous);
        logins.add(vault);
        logins.add(session);
        if (!vaultPresent) vault.setText("Vault account (install or enable the Credential Vault)");
        if (initial.isPresent()) {
            ServerDefinition definition = initial.get();
            name.setText(definition.name());
            protocol.setSelectedItem(definition.protocol());
            folder.setText(definition.folder().toString());
            port.setText(String.valueOf(definition.port()));
            autostart.setSelected(definition.autostart());
            writable.setSelected(definition.writable());
            selectBind(definition.bind());
            useTls.setSelected(definition.tls());
            requireTls.setSelected(definition.requireTls());
            passive.setText(definition.passiveRange().map(PortRange::token).orElse(""));
            definition.authorizedKeys().forEach(keyLines::addElement);
            switch (definition.login()) {
                case Login.Anonymous none -> anonymous.setSelected(true);
                case Login.Session password -> session.setSelected(true);
                case Login.Vault account -> {
                    vault.setSelected(true);
                    accountId = account.credentialId();
                    accountLabel.setText(accountName.apply(accountId).orElse(vaultPresent ? "account missing" : "needs the Credential Vault"));
                }
            }
        } else {
            port.setText(String.valueOf(selectedProtocol().defaultPort()));
            defaultLogin(selectedProtocol());
        }
        previous = selectedProtocol();

        var form = new JPanel(new GridBagLayout());
        var at = new GridBagConstraints();
        at.insets = new Insets(3, 3, 3, 3);
        at.anchor = GridBagConstraints.WEST;
        at.gridy = 0;
        row(form, at, "Name", name);
        row(form, at, "Protocol", protocol);
        row(form, at, "Folder", line(folder, chooseFolder));
        row(form, at, "", autostart);
        row(form, at, "Reachable from", reachable);
        row(form, at, "Port", port);
        row(form, at, "", portHint);
        row(form, at, "Passive ports", line(passive, label("FTP only; empty uses the range in Settings")));
        row(form, at, "", writable);
        var login = new JPanel();
        login.setLayout(new BoxLayout(login, BoxLayout.Y_AXIS));
        login.add(anonymous);
        login.add(line(vault, chooseAccount, accountLabel));
        login.add(session);
        row(form, at, "Login", login);
        keys.setVisibleRowCount(3);
        keys.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        keys.setCellRenderer(new DefaultListCellRenderer() {
            { putClientProperty("html.disable", Boolean.TRUE); }
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused) {
                return super.getListCellRendererComponent(list, keySummary(String.valueOf(value)), index, selected, focused);
            }
        });
        var keyPanel = new JPanel();
        keyPanel.setLayout(new BoxLayout(keyPanel, BoxLayout.Y_AXIS));
        var keyScroll = new JScrollPane(keys);
        keyScroll.setAlignmentX(LEFT_ALIGNMENT);
        keyPanel.add(keyScroll);
        keyPanel.add(line(addKeyFile, pasteKeys, removeKey));
        row(form, at, "Authorized keys", keyPanel);
        row(form, at, "Security", useTls);
        row(form, at, "", requireTls);
        row(form, at, "", warnings);
        add(form, BorderLayout.CENTER);
        var south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        message.setAlignmentX(LEFT_ALIGNMENT);
        south.add(message);
        var buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.add(Box.createHorizontalGlue());
        buttons.add(cancel);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(save);
        south.add(buttons);
        add(south, BorderLayout.SOUTH);

        protocol.addActionListener(event -> protocolChanged());
        reachable.addActionListener(event -> refresh());
        writable.addActionListener(event -> refresh());
        anonymous.addActionListener(event -> refresh());
        vault.addActionListener(event -> refresh());
        session.addActionListener(event -> refresh());
        useTls.addActionListener(event -> refresh());
        requireTls.addActionListener(event -> refresh());
        port.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { refresh(); }
            @Override public void removeUpdate(DocumentEvent event) { refresh(); }
            @Override public void changedUpdate(DocumentEvent event) { refresh(); }
        });
        addKeyFile.addActionListener(event -> addKeys(keySources.fromFile().get()));
        pasteKeys.addActionListener(event -> addKeys(keySources.fromClipboard().get()));
        removeKey.addActionListener(event -> {
            int[] chosen = keys.getSelectedIndices();
            for (int i = chosen.length - 1; i >= 0; i--) keyLines.remove(chosen[i]);
            refresh();
        });
        keys.addListSelectionListener(event -> refresh());
        chooseFolder.addActionListener(event -> folderChooser.get().ifPresent(path -> folder.setText(path.toString())));
        chooseAccount.addActionListener(event -> accountPicker.get().thenAccept(chosen -> chosen.ifPresent(account -> {
            accountId = account.id();
            accountLabel.setText(account.name());
            vault.setSelected(true);
            refresh();
        })));
        cancel.addActionListener(event -> onCancel.run());
        save.addActionListener(event -> {
            try { onSave.accept(build()); }
            catch (IllegalArgumentException invalid) { message.setText(invalid.getMessage()); }
        });
        refresh();
    }

    public void showError(String error) { message.setText(error); }

    ServerDefinition build() {
        String nameText = name.getText().strip();
        if (nameText.isEmpty()) throw new IllegalArgumentException("A server needs a name");
        for (ServerDefinition other : others)
            if (other.name().equalsIgnoreCase(nameText) && !(existing && initial.orElseThrow().id().equals(other.id())))
                throw new IllegalArgumentException("A server named " + other.name() + " exists");
        String folderText = folder.getText().strip();
        if (folderText.isEmpty()) throw new IllegalArgumentException("Choose a folder to share");
        Path folderPath;
        try { folderPath = Path.of(folderText).toAbsolutePath().normalize(); }
        catch (InvalidPathException bad) { throw new IllegalArgumentException("The folder is not a valid path"); }
        if (!Files.isDirectory(folderPath)) throw new IllegalArgumentException("The folder " + folderText + " does not exist");
        int portValue = parsedPort();
        if (portValue < 1) throw new IllegalArgumentException("The port must be a number from 1 to 65535");
        Protocol chosen = selectedProtocol();
        Login login;
        if (!chosen.hasLogin() || anonymous.isSelected()) login = Login.ANONYMOUS;
        else if (vault.isSelected()) {
            if (accountId == null) throw new IllegalArgumentException("Choose a Vault account, or use a session password");
            login = new Login.Vault(accountId);
        } else login = Login.SESSION;
        ServerDefinition base = initial.orElse(null);
        boolean tlsOn = chosen.allowsTls() && useTls.isSelected();
        boolean requireOn = chosen == Protocol.FTP && tlsOn && requireTls.isSelected();
        String range = passive.getText().strip();
        Optional<PortRange> passiveRange = chosen == Protocol.FTP && !range.isEmpty() ? Optional.of(PortRange.parse(range)) : Optional.empty();
        return new ServerDefinition(existing ? base.id() : UUID.randomUUID(), nameText, chosen, folderPath, selectedBind(), portValue,
            passiveRange, writable.isSelected(), login, chosen == Protocol.SFTP ? Collections.list(keyLines.elements()) : List.of(), tlsOn, requireOn, autostart.isSelected());
    }

    /** Adds every key in {@code text} that is not listed yet; bad text is explained, and nothing is added from it. */
    private void addKeys(Optional<String> text) {
        if (text.isEmpty()) return;
        try {
            for (AuthorizedKeys.Key key : AuthorizedKeys.parseAll(text.get()))
                if (!keyLines.contains(key.line())) keyLines.addElement(key.line());
            message.setText(" ");
        } catch (IllegalArgumentException bad) {
            message.setText(bad.getMessage());
        }
        refresh();
    }

    private static String keySummary(String line) {
        try { return AuthorizedKeys.parse(line).summary(); }
        catch (IllegalArgumentException unreadable) { return line; }
    }

    private void protocolChanged() {
        Protocol chosen = selectedProtocol();
        if (port.getText().strip().equals(String.valueOf(previous.defaultPort()))) port.setText(String.valueOf(chosen.defaultPort()));
        defaultLogin(chosen);
        previous = chosen;
        refresh();
    }

    private void refresh() {
        Protocol chosen = selectedProtocol();
        boolean ftp = chosen == Protocol.FTP, sftp = chosen == Protocol.SFTP;
        passive.setEnabled(ftp);
        useTls.setEnabled(chosen.allowsTls());
        if (!chosen.allowsTls()) useTls.setSelected(false);
        requireTls.setEnabled(ftp && useTls.isSelected());
        if (!requireTls.isEnabled()) requireTls.setSelected(false);
        boolean login = chosen.hasLogin();
        boolean noPassword = chosen.allowsAnonymous() || sftp; // SFTP's "anonymous" means keys only
        anonymous.setText(!login ? "No login (TFTP has none)" : sftp ? "Keys only (no password)" : "Anonymous");
        anonymous.setEnabled(login && noPassword);
        vault.setEnabled(login && vaultPresent);
        chooseAccount.setEnabled(login && vaultPresent);
        session.setEnabled(login);
        if (!login) anonymous.setSelected(true);
        else if (anonymous.isSelected() && !noPassword) (vaultPresent ? vault : session).setSelected(true);
        keys.setEnabled(sftp);
        addKeyFile.setEnabled(sftp);
        pasteKeys.setEnabled(sftp);
        removeKey.setEnabled(sftp && !keys.isSelectionEmpty());
        int value = parsedPort();
        portHint.setText(linux && value > 0 && value < 1024
            ? "Ports below 1024 need root or CAP_NET_BIND_SERVICE on Linux — try " + chosen.suggestedPort(value) : " ");
        Login shown = anonymous.isSelected() ? Login.ANONYMOUS : vault.isSelected() ? new Login.Vault(new UUID(0, 0)) : Login.SESSION;
        boolean tls = chosen.allowsTls() && useTls.isSelected();
        List<String> list = ServerRows.warnings(chosen, tls, shown, writable.isSelected(), selectedBind());
        warnings.setText(list.isEmpty() ? " " : "⚠ " + String.join(" · ", list));
    }

    private void defaultLogin(Protocol chosen) {
        if (chosen == Protocol.HTTP || !chosen.hasLogin()) anonymous.setSelected(true);
        else (vaultPresent ? vault : session).setSelected(true);
    }

    private void selectBind(Bind bind) {
        for (int i = 0; i < reachable.getItemCount(); i++)
            if (reachable.getItemAt(i).bind().equals(bind)) { reachable.setSelectedIndex(i); return; }
        String label = bind instanceof Bind.Interface named ? named.name() + " (not available)" : bind.label();
        reachable.addItem(new BindChoice(bind, label));
        reachable.setSelectedIndex(reachable.getItemCount() - 1);
    }

    private Protocol selectedProtocol() { return (Protocol) protocol.getSelectedItem(); }
    private Bind selectedBind() { return ((BindChoice) reachable.getSelectedItem()).bind(); }

    private int parsedPort() {
        try {
            int value = Integer.parseInt(port.getText().strip());
            return value >= 1 && value <= 65535 ? value : -1;
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    private static JPanel line(Component... parts) {
        var line = new JPanel();
        line.setLayout(new BoxLayout(line, BoxLayout.X_AXIS));
        line.setAlignmentX(LEFT_ALIGNMENT);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) line.add(Box.createHorizontalStrut(6));
            line.add(parts[i]);
        }
        return line;
    }

    private static void row(JPanel form, GridBagConstraints at, String label, Component field) {
        at.gridx = 0;
        form.add(label(label), at);
        at.gridx = 1;
        form.add(field, at);
        at.gridy++;
    }

    private static JLabel label(String text) {
        var label = new JLabel();
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setText(text);
        return label;
    }
}
