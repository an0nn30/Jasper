package dev.jasper.vault.ui;

import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.api.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PanelsTest {
    @Test void createPanelRequiresMatchingNonEmptyPasswords() {
        List<String> created = new ArrayList<>();
        boolean[] cancelled = {false};
        PasswordPanel panel = PasswordPanel.create(true, (password, bind) -> created.add(new String(password) + ":" + bind), () -> cancelled[0] = true);
        assertThat(panel.bind.isSelected()).isTrue();
        panel.primary.doClick();
        assertThat(panel.message.getText()).contains("at least 8");
        panel.password.setText("hunter2hunter2"); panel.confirm.setText("different");
        panel.primary.doClick();
        assertThat(panel.message.getText()).contains("do not match");
        assertThat(created).isEmpty();
        panel.confirm.setText("hunter2hunter2"); panel.bind.setSelected(false);
        panel.primary.doClick();
        assertThat(created).containsExactly("hunter2hunter2:false");
        assertThat(panel.password.getPassword()).as("fields cleared after use").isEmpty();
        panel.cancel.doClick();
        assertThat(cancelled[0]).isTrue();
    }

    @Test void pickerListsChoicesAndReportsTheSelection() {
        var choices = List.of(new CredentialDescriptor(UUID.randomUUID(), "prod", "deploy", Kind.ACCOUNT_PASSWORD), new CredentialDescriptor(UUID.randomUUID(), "laptop", "SHA256:x", Kind.SSH_KEY));
        List<UUID> chosen = new ArrayList<>();
        boolean[] cancelled = {false};
        var panel = new PickerPanel(choices, chosen::add, () -> cancelled[0] = true);
        assertThat(panel.list.getModel().getSize()).isEqualTo(2);
        assertThat(panel.primary.isEnabled()).isFalse();
        panel.list.setSelectedIndex(1);
        assertThat(panel.primary.isEnabled()).isTrue();
        panel.primary.doClick();
        assertThat(chosen).containsExactly(choices.get(1).id());
        panel.cancel.doClick();
        assertThat(cancelled[0]).isTrue();
        assertThat(PickerPanel.label(choices.getFirst())).isEqualTo("prod  —  deploy");
    }
}
