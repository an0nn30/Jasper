package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SemanticColor;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 8.2's {@code Form.footnote}: one muted line at the footer's leading edge, beside the actions. */
@SuppressWarnings("try")
class FootnotesTest {
    static final ControlState READY = ControlState.READY;
    static final String NOTE = "Auto-locks after 15 min idle";
    static Form form(FormKind kind, String footnote) {
        return new Form("form", READY, kind, "Unlock Vault", Optional.empty(), List.of(new Secret("password", READY, "", "Master password", "")), Optional.empty(),
            List.of(new Nodes.Button("cancel", READY, "Cancel", "", ButtonKind.SECONDARY, "cancel", ""), new Nodes.Button("unlock", READY, "Unlock", "", ButtonKind.PRIMARY, "unlock", "")),
            "cancel", FormSubmission.PRIMARY, footnote);
    }

    @Test void aFootnoteIsOneMutedLineAtTheFootersLeadingEdgeBesideTheActions() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(form(FormKind.FULL, NOTE)), e -> {})) {
                KitFixtures.render(view, 540, 260, 1);
                var note = find(view, "form.footnote", JLabel.class);
                var cancel = find(view, "cancel", JComponent.class);
                assertThat(note).isNotNull();
                assertThat(note.isShowing() || note.isVisible()).isTrue();
                assertThat(note.getText()).isEqualTo(NOTE);
                assertThat(note.getFont()).isEqualTo(ThemePainting.font(context, Typography.Role.CAPTION));
                assertThat(note.getForeground()).isEqualTo(ThemePainting.color(context, SemanticColor.TEXT_TERTIARY));
                var noteAt = SwingUtilities.convertPoint(note.getParent(), note.getLocation(), view);
                var cancelAt = SwingUtilities.convertPoint(cancel.getParent(), cancel.getLocation(), view);
                assertThat(noteAt.x + note.getWidth()).as("before the actions").isLessThanOrEqualTo(cancelAt.x);
                assertThat(noteAt.y).as("on the actions' line").isLessThan(cancelAt.y + cancel.getHeight());
                assertThat(noteAt.y + note.getHeight()).isGreaterThan(cancelAt.y);
            } finally { release(context); }
        });
    }

    @Test void aPromptFormsFullWidthActionsPutTheFootnoteOnTheLineAboveThem() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(form(FormKind.PROMPT_COMPACT, NOTE)), e -> {})) {
                KitFixtures.render(view, 380, 240, 1);
                var note = find(view, "form.footnote", JLabel.class);
                var cancel = find(view, "cancel", JComponent.class);
                var noteAt = SwingUtilities.convertPoint(note.getParent(), note.getLocation(), view);
                var cancelAt = SwingUtilities.convertPoint(cancel.getParent(), cancel.getLocation(), view);
                assertThat(noteAt.y + note.getHeight()).isLessThanOrEqualTo(cancelAt.y);
            } finally { release(context); }
        });
    }

    @Test void aBlankFootnoteMountsNothing() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(form(FormKind.FULL, "")), e -> {})) {
                assertThat(find(view, "form.footnote", JLabel.class)).isNull();
                view.update(new ViewSpec(form(FormKind.FULL, NOTE)));
                assertThat(find(view, "form.footnote", JLabel.class)).isNotNull();
                view.update(new ViewSpec(form(FormKind.FULL, " ")));
                assertThat(find(view, "form.footnote", JLabel.class)).isNull();
            } finally { release(context); }
        });
    }
}
