package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Plan 3 Task 10: the app-only compact hint face (the palette footer) and its retention across same-id updates. */
class HintPresentationTest {
    private static ViewSpec hint(String detail, boolean secondVerb) {
        var children = new java.util.ArrayList<Nodes.Node>(List.of(new Nodes.Text("hint.text", ControlState.READY, detail, Nodes.TextRole.CAPTION),
            new Nodes.Button("verb.1", ControlState.READY, "Paste and run", "", Nodes.ButtonKind.LINK, "verb.1", "", Nodes.ButtonPresentation.EMBEDDED)));
        if (secondVerb) children.add(new Nodes.Button("verb.2", ControlState.READY, "Edit file", "", Nodes.ButtonKind.LINK, "verb.2", "", Nodes.ButtonPresentation.EMBEDDED));
        return new ViewSpec(new Nodes.Stack("hint", ControlState.READY, Nodes.LayoutRole.NAVIGATION, children));
    }

    @Test void hintStackTextAndEmbeddedLinksUseTheHintRoleAndKeepItAcrossUpdates() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, hint("cd ~/src", false), event -> { })) {
                var ordinaryText = ThemePainting.font(context, Typography.Role.CAPTION);
                var ordinaryLink = ThemePainting.font(context, Typography.Role.LABEL);
                var hintFont = ThemePainting.font(context, Typography.Role.PALETTE_HINT);
                assertThat(KitViewTest.find(view, "hint.text", JLabel.class).getFont()).isEqualTo(ordinaryText);
                assertThat(KitViewTest.find(view, "verb.1", AbstractButton.class).getFont()).isEqualTo(ordinaryLink);
                view.setHintPresentation("hint", true);
                assertThat(KitViewTest.find(view, "hint.text", JLabel.class).getFont()).isEqualTo(hintFont);
                assertThat(KitViewTest.find(view, "verb.1", AbstractButton.class).getFont()).isEqualTo(hintFont);
                view.update(hint("cd ~/projects", true));
                assertThat(KitViewTest.find(view, "verb.2", AbstractButton.class).getFont()).as("a new verb joins the face").isEqualTo(hintFont);
                assertThat(KitViewTest.find(view, "hint.text", JLabel.class).getText()).isEqualTo("cd ~/projects");
                view.setHintPresentation("hint", false);
                assertThat(KitViewTest.find(view, "hint.text", JLabel.class).getFont()).isEqualTo(ordinaryText);
                assertThatIllegalArgumentException().isThrownBy(() -> view.setHintPresentation("hint.text", true));
            } finally { KitViewTest.release(context); }
        });
    }
}
