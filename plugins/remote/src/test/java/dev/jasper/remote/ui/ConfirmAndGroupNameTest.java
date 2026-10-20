package dev.jasper.remote.ui;

import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.*;

/** The two small host dialogs: Enter and Escape as at baseline f16ae32f. */
class ConfirmAndGroupNameTest {
    @Test void confirmHasNoDefaultVerbAndEscapeCancels() throws Exception {
        edt(() -> {
            var events = new ArrayList<String>();
            var panel = new ConfirmPanel(new FakeKit("dev.jasper.remote"), "Delete the saved host <b>prod</b>?", "Delete", () -> events.add("confirm"), () -> events.add("cancel"));
            assertThat(primaries(panel.view())).as("the old dialog had no default button: Enter deletes nothing").isEmpty();
            assertThat(form(panel.view()).actions()).extracting(Nodes.Button::label).containsExactly("Cancel", "Delete");
            assertThat(panel.text()).isEqualTo("Delete the saved host <b>prod</b>?");
            press(panel.view(), form(panel.view()).cancelAction());
            click(panel.view(), ConfirmPanel.CONFIRM);
            assertThat(events).containsExactly("cancel", "confirm");
            panel.close();
            assertThat(closed(panel.view())).as("closing disposes the view, so no later click can reach the verb").isTrue();
            assertThat(events).hasSize(2);
        });
    }

    @Test void enterRenamesOnlyFromTheNameFieldAndErrorsStayUntilRenamed() throws Exception {
        edt(() -> {
            List<String> renamed = new ArrayList<>(); int[] cancelled = {0};
            var panel = new GroupNamePanel(new FakeKit("dev.jasper.remote"), "Other", renamed::add, () -> cancelled[0]++);
            var name = node(panel.view(), GroupNamePanel.NAME, Nodes.Field.class);
            assertThat(name.value()).isEqualTo("Other");
            assertThat(name.label()).isEqualTo("Default group name");
            assertThat(name.trailingAction()).as("Enter in the field renames, as its action listener did").isEqualTo(GroupNamePanel.RENAME);
            assertThat(primaries(panel.view())).as("no default button: Enter on Cancel renames nothing").isEmpty();
            assertThat(form(panel.view()).actions()).extracting(Nodes.Button::label).containsExactly("Cancel", "Rename");
            type(panel.view(), GroupNamePanel.NAME, "Servers");
            press(panel.view(), name.trailingAction());
            panel.showError("A group with that name already exists");
            assertThat(texts(panel.view())).contains("A group with that name already exists");
            assertThat(node(panel.view(), GroupNamePanel.NAME, Nodes.Field.class).value()).as("the typed name stays").isEqualTo("Servers");
            click(panel.view(), GroupNamePanel.RENAME_BUTTON);
            assertThat(renamed).containsExactly("Servers", "Servers");
            press(panel.view(), form(panel.view()).cancelAction());
            assertThat(cancelled[0]).isEqualTo(1);
        });
    }
}
