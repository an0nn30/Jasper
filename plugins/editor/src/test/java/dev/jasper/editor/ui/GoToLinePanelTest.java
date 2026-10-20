package dev.jasper.editor.ui;

import dev.jasper.editor.testsupport.Kits.RecordingKit;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static dev.jasper.editor.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Go to Line on kit descriptors (Plan 2 Task 9a). Baseline f16ae32f: Enter in the field submitted (its action
 * listener) and Go was the default button elsewhere; an invalid value outlined the field and kept the dialog open.
 */
class GoToLinePanelTest {
    @Test void goesToAValidLineAndMarksAnInvalidOne() throws Exception {
        edt(() -> {
            List<Integer> lines = new ArrayList<>();
            int[] cancelled = {0};
            var panel = new GoToLinePanel(new FakeKit("dev.jasper.editor"), 2, 10, lines::add, () -> cancelled[0]++);
            assertThat(field(panel).value()).isEqualTo("2");
            type(panel.view(), GoToLinePanel.LINE, "11");
            panel.submit();
            assertThat(lines).isEmpty();
            assertThat(field(panel).state().error()).as("the field carries the error, which disables Go until the next edit")
                .isEqualTo("Enter a line from 1 to 10");
            type(panel.view(), GoToLinePanel.LINE, " 7 ");
            assertThat(field(panel).state().error()).as("the next edit clears it").isEmpty();
            panel.submit();
            assertThat(lines).containsExactly(7);
            assertThat(field(panel).state().error()).isEmpty();
            assertThat(cancelled[0]).isZero();
        });
    }

    @Test void enterInTheFieldAndGoSubmitAndNonNumbersAreMarked() throws Exception {
        edt(() -> {
            List<Integer> lines = new ArrayList<>();
            var panel = new GoToLinePanel(new FakeKit("dev.jasper.editor"), 1, 40, lines::add, () -> { });
            assertThat(field(panel).trailingAction()).as("Enter in the field submits, as its action listener did").isEqualTo(GoToLinePanel.GO_ACTION);
            assertThat(primaries(panel.view())).as("Go is the default: Enter elsewhere submits").extracting(Nodes.Button::label).containsExactly("Go");
            type(panel.view(), GoToLinePanel.LINE, "x");
            press(panel.view(), field(panel).trailingAction());
            assertThat(field(panel).state().error()).isEqualTo("Enter a line from 1 to 40");
            press(panel.view(), field(panel).trailingAction());
            assertThat(lines).as("Enter again on an invalid value keeps the dialog open").isEmpty();
            type(panel.view(), GoToLinePanel.LINE, "0");
            click(panel.view(), GoToLinePanel.GO);
            assertThat(lines).isEmpty();
            type(panel.view(), GoToLinePanel.LINE, "40");
            click(panel.view(), GoToLinePanel.GO);
            type(panel.view(), GoToLinePanel.LINE, "1");
            press(panel.view(), field(panel).trailingAction());
            assertThat(lines).containsExactly(40, 1);
        });
    }

    @Test void cancelAndEscapeCancelAndTheLabelNamesTheRange() throws Exception {
        edt(() -> {
            int[] cancelled = {0};
            var panel = new GoToLinePanel(new FakeKit("dev.jasper.editor"), 3, 1200, line -> { }, () -> cancelled[0]++);
            assertThat(field(panel).label()).as("the accessible name keeps the range").isEqualTo("Line (1–1200)");
            assertThat(form(panel.view()).actions()).extracting(Nodes.Button::label).containsExactly("Cancel", "Go");
            click(panel.view(), GoToLinePanel.CANCEL);
            escape(panel.view());
            assertThat(cancelled[0]).isEqualTo(2);
        });
    }

    /** Plan 3 Task 8: the canonical sheet: a form card with a "Line" row (no colon) captioned with the range, then the field. */
    @Test void composesACanonicalSheetWithOneFormRow() throws Exception {
        edt(() -> {
            var panel = new GoToLinePanel(new FakeKit("dev.jasper.editor"), 3, 1200, line -> { }, () -> { });
            var form = form(panel.view());
            assertThat(form.kind()).isEqualTo(Nodes.FormKind.SHEET);
            assertThat(form.title()).isEqualTo("Go to Line");
            assertThat(form.sections()).singleElement().isInstanceOfSatisfying(Nodes.Card.class, card -> {
                assertThat(card.kind()).isEqualTo(Nodes.CardKind.FORM);
                assertThat(card.children()).singleElement().isInstanceOfSatisfying(Nodes.FormRow.class, row -> {
                    assertThat(row.label()).isEqualTo("Line").doesNotContain(":");
                    assertThat(row.control()).isInstanceOf(Nodes.Field.class).extracting(Nodes.Node::id).isEqualTo(GoToLinePanel.LINE);
                    assertThat(row.caption()).as("the range under the label").isEqualTo("1–1200");
                    assertThat(row.trailing()).isEmpty();
                });
            });
            assertThat(form.actions().getLast().kind()).as("the one primary is right-most").isEqualTo(Nodes.ButtonKind.PRIMARY);
            assertThat(form.actions()).filteredOn(button -> button.kind() == Nodes.ButtonKind.PRIMARY).hasSize(1);
        });
    }

    @Test void aClosedPanelIgnoresLateSubmitsAndNeverTouchesItsView() throws Exception {
        edt(() -> {
            List<Integer> lines = new ArrayList<>();
            var kit = new RecordingKit("dev.jasper.editor");
            var panel = new GoToLinePanel(kit, 1, 10, lines::add, () -> { });
            panel.close();
            panel.submit();
            assertThat(lines).isEmpty();
            assertThat(kit.lateCalls).isEmpty();
        });
    }

    private static Nodes.Field field(GoToLinePanel panel) { return node(panel.view(), GoToLinePanel.LINE, Nodes.Field.class); }
}
