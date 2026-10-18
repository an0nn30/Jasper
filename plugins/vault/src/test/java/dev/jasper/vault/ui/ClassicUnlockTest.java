package dev.jasper.vault.ui;

import static dev.jasper.vault.testsupport.KitViews.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.vault.service.UnlockCaptureSupport;
import dev.jasper.vault.workstation.Layout;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Plan 10b Rulings 11: the classic Unlock Vault of the 07 mocks. */
class ClassicUnlockTest {
    @TempDir Path directory;
    static final String FOOTNOTE = "Auto-locks after 15 min idle";

    @Test void theTileSitsBesideTheIntroAndThePasswordInTheLabelColumn() throws Exception {
        edt(() -> {
            try (var panel = PasswordPanel.unlock(new FakeKit("dev.jasper.vault"), UnlockCaptureSupport.prompt(directory), Layout.CLASSIC, FOOTNOTE)) {
                var view = panel.view();
                var root = (Nodes.Form) view.spec().root();
                assertThat(root.kind()).isEqualTo(Nodes.FormKind.PROMPT_COMPACT);
                var top = (Nodes.Stack) root.hero().orElseThrow();
                assertThat(top.role()).as("the classic prompt face puts the tile beside the intro").isEqualTo(Nodes.LayoutRole.PROMPT_HEADER);
                assertThat(top.children()).extracting(Nodes.Node::id).containsExactly("form.icon", "intro");
                assertThat(find(view, "form.heading")).as("the header strip titles the overlay").isEmpty();
                assertThat(node(view, "form.row", Nodes.FormRow.class).label()).isEqualTo("Master password");
                assertThat(node(view, "password", Nodes.Secret.class).kind()).isEqualTo(Nodes.SecretKind.INPUT);
                assertThat(node(view, "password", Nodes.Secret.class).submitAction()).isEqualTo("submit");
                assertThat(node(view, "password", Nodes.Secret.class).hint()).as("the label column names the field").isEmpty();
                assertThat(node(view, "intro", Nodes.Text.class).role()).isEqualTo(Nodes.TextRole.BODY);
                assertThat(root.footnote()).isEqualTo(FOOTNOTE);
                assertThat(root.actions()).extracting(Nodes.Button::label).containsExactly("Cancel", "Unlock");
                assertThat(root.cancelAction()).isEqualTo("cancel");
            }
        });
    }

    @Test void theFootnoteFollowsTheAutoLockAndUnlockNeedsAPassword() throws Exception {
        edt(() -> {
            try (var off = PasswordPanel.unlock(new FakeKit("dev.jasper.vault"), UnlockCaptureSupport.prompt(directory), Layout.CLASSIC, "")) {
                var root = (Nodes.Form) off.view().spec().root();
                assertThat(root.footnote()).as("auto-lock off: no footnote").isEmpty();
                assertThat(root.preview()).isEmpty();
            }
            try (var panel = PasswordPanel.unlock(new FakeKit("dev.jasper.vault"), UnlockCaptureSupport.prompt(directory), Layout.CLASSIC, FOOTNOTE)) {
                var view = panel.view();
                assertThat(button(view, "submit").state().enabled()).as("an empty field").isFalse();
                type(view, "password", "synthetic-wrong");
                assertThat(button(view, "submit").state().enabled()).isTrue();
                press(view, "submit");
                assertThat(secret(view, "password")).as("a submit clears the field").isEmpty();
                assertThat(button(view, "submit").state().enabled()).as("after a wrong password").isFalse();
                assertThat(view.spec().toString()).doesNotContain("synthetic-wrong");
            }
        });
    }

    @Test void theRowsUnlockKeepsItsPromptHeaderAndHasNoFootnote() throws Exception {
        edt(() -> {
            try (var panel = PasswordPanel.unlock(new FakeKit("dev.jasper.vault"), UnlockCaptureSupport.prompt(directory))) {
                var root = (Nodes.Form) panel.view().spec().root();
                assertThat(((Nodes.Stack) root.hero().orElseThrow()).role()).isEqualTo(Nodes.LayoutRole.PROMPT_HEADER);
                assertThat(node(panel.view(), "form.heading", Nodes.Text.class).text()).isEqualTo("Unlock Vault");
                assertThat(root.footnote()).isEmpty();
            }
        });
    }
}
