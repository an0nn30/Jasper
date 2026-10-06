package dev.jasper.vault.ui;

import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.api.Kind;
import dev.jasper.vault.service.UnlockCaptureSupport;
import dev.jasper.vault.testsupport.KitViews;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.vault.testsupport.KitViews.*;
import static org.assertj.core.api.Assertions.*;

class PanelsTest {
    @TempDir Path directory;

    @Test void createPanelRequiresMatchingNonEmptyPasswords() throws Exception {
        edt(() -> {
            List<String> created = new ArrayList<>();
            boolean[] cancelled = {false};
            var kit = new FakeKit("dev.jasper.vault");
            PasswordPanel panel = PasswordPanel.create(kit, true, (password, bind) -> created.add(new String(password) + ":" + bind), () -> cancelled[0] = true);
            var view = panel.view();
            assertThat(node(view, panel.bind, Nodes.Toggle.class).checked()).isTrue();
            assertThat(node(view, panel.password, Nodes.Secret.class).revealGroup())
                .as("password and confirmation reveal together").isEqualTo(node(view, panel.confirm, Nodes.Secret.class).revealGroup()).isNotBlank();
            assertThat(node(view, panel.password, Nodes.Secret.class).submitAction()).as("Enter in either field submits, as their action listeners did")
                .isEqualTo(panel.submit);
            assertThat(node(view, panel.confirm, Nodes.Secret.class).submitAction()).isEqualTo(panel.submit);
            press(view, panel.submit);
            assertThat(error(view, panel.password)).contains("at least 8");
            assertThat(button(view, panel.submit).kind()).isEqualTo(Nodes.ButtonKind.PRIMARY);
            type(view, panel.password, "hunter2hunter2");
            assertThat(error(view, panel.password)).as("an edit ends the previous validation").isEmpty();
            type(view, panel.confirm, "different");
            press(view, panel.submit);
            assertThat(error(view, panel.confirm)).contains("do not match");
            assertThat(created).isEmpty();
            assertThat(secret(view, panel.password)).as("a rejected submit keeps both fields").isEqualTo("hunter2hunter2");
            assertThat(secret(view, panel.confirm)).isEqualTo("different");
            type(view, panel.confirm, "hunter2hunter2"); emit(view, new UiEvent.Toggled(panel.bind, false));
            press(view, panel.submit);
            assertThat(created).containsExactly("hunter2hunter2:false");
            assertThat(secret(view, panel.password)).as("fields cleared after use").isEmpty();
            assertThat(secret(view, panel.confirm)).isEmpty();
            assertThat(view.spec().toString()).doesNotContain("hunter2");
            assertThat(node(view, panel.form, Nodes.Form.class).cancelAction()).as("Escape cancels").isEqualTo(panel.cancel);
            press(view, panel.cancel);
            assertThat(cancelled[0]).isTrue();
            panel.close();
            assertThat(closed(view)).as("closing disposes the view and its secret storage").isTrue();
            assertThat(kit.failures()).isEmpty();
        });
    }

    @Test void busyCreateBlocksDuplicateSubmitsAndFailedCreationReenablesWithoutRetainingThePassword() throws Exception {
        edt(() -> {
            List<char[]> received = new ArrayList<>();
            var kit = new FakeKit("dev.jasper.vault");
            PasswordPanel[] holder = {null};
            holder[0] = PasswordPanel.create(kit, false, (password, bind) -> { received.add(password); holder[0].setBusy(true); }, () -> { });
            var panel = holder[0]; var view = panel.view();
            type(view, panel.password, "synthetic-pass"); type(view, panel.confirm, "synthetic-pass");
            press(view, panel.submit);
            assertThat(received).hasSize(1);
            assertThat(button(view, panel.submit).state().busy()).isTrue();
            assertThat(node(view, panel.password, Nodes.Secret.class).state().enabled()).isFalse();
            press(view, panel.submit);
            assertThat(received).as("busy form ignores a second submit").hasSize(1);
            panel.setBusy(false); // the create failed: the owner reports it and the form is usable again
            assertThat(button(view, panel.submit).state().busy()).isFalse();
            assertThat(secret(view, panel.password)).as("the failed attempt left no password in the form").isEmpty();
            panel.close();
        });
    }

    @Test void unlockSubmitsACopyAndReportsErrorsButIgnoresThemOnceClosed() throws Exception {
        edt(() -> {
            var kit = new RecordingKit("dev.jasper.vault");
            var prompt = UnlockCaptureSupport.prompt(directory);
            var panel = PasswordPanel.unlock(kit, prompt); var view = panel.view();
            press(view, panel.submit);
            assertThat(panel.busy()).as("an empty password does nothing").isFalse();
            type(view, panel.password, "wrong-password");
            press(view, panel.submit);
            assertThat(secret(view, panel.password)).as("the field is erased on submit").isEmpty();
            assertThat(panel.busy()).isFalse();
            assertThat(panel.status()).isNotEmpty().doesNotContain("wrong-password");
            assertThat(view.spec().toString() + kit.events).doesNotContain("wrong-password");
            assertThat(node(view, panel.form, Nodes.Form.class).actions()).filteredOn(b -> b.kind() == Nodes.ButtonKind.PRIMARY)
                .singleElement().satisfies(b -> assertThat(b.label()).isEqualTo("Unlock"));
            type(view, panel.password, "late-password");
            panel.close();
            assertThat(closed(view)).isTrue();
            prompt.submit("another".toCharArray());
            assertThat(kit.lateCalls).as("a result after closure never reaches the disposed view").isEmpty();
        });
    }

    @Test void pickerListsChoicesAndReportsTheSelection() throws Exception {
        edt(() -> {
            var choices = List.of(new CredentialDescriptor(UUID.randomUUID(), "prod", "deploy", Kind.ACCOUNT_PASSWORD), new CredentialDescriptor(UUID.randomUUID(), "laptop", "SHA256:x", Kind.SSH_KEY));
            List<UUID> chosen = new ArrayList<>();
            boolean[] cancelled = {false};
            var panel = new PickerPanel(new FakeKit("dev.jasper.vault"), choices, chosen::add, () -> cancelled[0] = true);
            var view = panel.view();
            var list = node(view, PickerPanel.LIST, Nodes.ItemList.class);
            assertThat(list.items()).extracting(item -> item.id()).containsExactly(choices.get(0).id().toString(), choices.get(1).id().toString());
            assertThat(list.activateAction()).as("double click only selects; Enter reaches Choose").isEmpty();
            assertThat(list.visibleRows()).hasValue(PickerPanel.VISIBLE_ROWS);
            assertThat(button(view, PickerPanel.CHOOSE).state().enabled()).isFalse();
            press(view, PickerPanel.CHOOSE);
            assertThat(chosen).isEmpty();
            emit(view, new UiEvent.Selected(PickerPanel.LIST, java.util.Set.of(choices.get(1).id().toString())));
            assertThat(button(view, PickerPanel.CHOOSE).state().enabled()).isTrue();
            assertThat(button(view, PickerPanel.CHOOSE).kind()).isEqualTo(Nodes.ButtonKind.PRIMARY);
            press(view, PickerPanel.CHOOSE);
            assertThat(chosen).containsExactly(choices.get(1).id());
            assertThat(node(view, PickerPanel.FORM, Nodes.Form.class).cancelAction()).isEqualTo(PickerPanel.CANCEL);
            press(view, PickerPanel.CANCEL);
            assertThat(cancelled[0]).isTrue();
            assertThat(PickerPanel.label(choices.getFirst())).isEqualTo("prod  —  deploy");
            panel.close();
        });
    }
}
