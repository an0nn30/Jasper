package dev.jasper.app;

import dev.jasper.app.vault.VaultSnapshot;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.*;

/** Body of the Add credential dialog: the supplied mock's form with Import at bottom left. */
final class VaultAddLoginDialog extends JPanel {
    final VaultLoginForm form = new VaultLoginForm(false);
    final JButton imported = new JButton("Import SSH key…");
    final JButton cancel = new JButton("Cancel");
    final JButton add = new JButton("Add login");
    final JLabel error = new JLabel(" ");
    final JPanel footer = new JPanel(new BorderLayout());
    final JPanel actions = VaultUi.row(cancel, add);

    VaultAddLoginDialog(List<VaultSnapshot.KeyInfo> keys) {
        super(new BorderLayout(0, 18));
        VaultUi.pad(this, 20);
        form.load(null, keys);
        add(form, BorderLayout.CENTER);
        footer.add(error, BorderLayout.NORTH);
        footer.add(imported, BorderLayout.WEST);
        footer.add(actions, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
    }
}
