package dev.jasper.vault.ui;

import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import org.junit.jupiter.api.Test;
import static dev.jasper.vault.testsupport.KitViews.*;
import static org.assertj.core.api.Assertions.*;

class GrantPanelTest {
    @Test void enterNeverGrantsEscapeDeniesAndNamesStayLiteral() throws Exception {
        edt(() -> {
            var decisions = new java.util.ArrayList<dev.jasper.vault.service.GrantPrompt.Decision>();
            var prompt = dev.jasper.vault.service.GrantCaptureSupport.prompt("<b>SSH</b>", "prod", "deploy", decisions::add);
            var panel = new GrantPanel(new FakeKit("dev.jasper.vault"), prompt);
            var view = panel.view();
            var form = node(view, GrantPanel.FORM, Nodes.Form.class);
            assertThat(form.actions()).as("no default action: Enter grants nothing").noneMatch(b -> b.kind() == Nodes.ButtonKind.PRIMARY);
            assertThat(form.actions()).extracting(Nodes.Button::label).containsExactly("Deny", "Always allow for <b>SSH</b>", "Allow once");
            assertThat(form.cancelAction()).isEqualTo(GrantPanel.DENY);
            assertThat(node(view, GrantPanel.QUESTION, Nodes.Text.class).text()).isEqualTo("<b>SSH</b> wants to use deploy@prod from the vault.");
            press(view, form.cancelAction());
            assertThat(decisions).containsExactly(dev.jasper.vault.service.GrantPrompt.Decision.DENY);
            panel.close();
            assertThat(closed(view)).isTrue();
        });
    }
}
