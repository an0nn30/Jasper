package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.SemanticColor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Plan 10b Rulings 1: the classic reveal is "Show", "Show password" in a prompt, and "Show" and "Copy" on a stored value. */
class ClassicRevealTest {
    static final ControlState READY = ControlState.READY;

    static ViewSpec row(Nodes.SecretKind kind) {
        return new ViewSpec(new Nodes.Card("card", READY, Nodes.CardKind.FORM, List.of(new Nodes.FormRow("row", READY, "Password", "",
            new Nodes.Secret("pw", READY, "", "Password", "", kind, ""), Optional.empty(), false))));
    }

    static ViewSpec prompt() {
        return new ViewSpec(new Nodes.Form("unlock", READY, Nodes.FormKind.PROMPT_COMPACT, "Unlock Vault", Optional.empty(), List.of(
            new Nodes.FormRow("row", READY, "Master password", "", new Nodes.Secret("pw", READY, "", "Master password", "", Nodes.SecretKind.INPUT, ""),
                Optional.empty(), false)), Optional.empty(),
            List.of(new Nodes.Button("submit", READY, "Unlock", "", Nodes.ButtonKind.PRIMARY, "submit", "")), ""));
    }

    static ViewSpec stored(String id) {
        return new ViewSpec(new Nodes.Card("card", READY, Nodes.CardKind.FORM, List.of(new Nodes.FormRow("row", READY, "Password", "",
            new Nodes.Secret(id, READY, "", "Password", "", Nodes.SecretKind.DETAIL, "copy"), Optional.empty(), false))));
    }

    static KitContext classic() { return KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise()); }

    @Test void anInputsRevealIsAShowCheckboxAfterTheField() {
        Edt.run(() -> {
            var context = classic();
            try (var view = new KitView(context, row(Nodes.SecretKind.INPUT), event -> { })) {
                KitFixtures.render(view, 440, 60, 1);
                var control = (SecretFields.SecretControl) view.control("pw");
                var box = KitViewTest.find(view, "pw.show", Switches.SwitchButton.class);
                assertThat(KitViewTest.find(view, "pw.show.label", javax.swing.JLabel.class).getText()).isEqualTo("Show");
                assertThat(control.reveal.getParent()).as("no glyph inside the field").isNull();
                var input = control.input;
                int fieldEnd = SwingUtilities.convertPoint(control.surface.getParent(), control.surface.getLocation(), view).x + control.surface.getWidth();
                assertThat(SwingUtilities.convertPoint(box.getParent(), box.getLocation(), view).x).as("after the field").isGreaterThanOrEqualTo(fieldEnd);
                char masked = input.getEchoChar();
                box.doClick(0);
                assertThat(input.getEchoChar()).as("Show reveals").isEqualTo((char) 0);
                assertThat(box.isSelected()).isTrue();
                box.doClick(0);
                assertThat(input.getEchoChar()).isEqualTo(masked);
                KitFixtures.capture(context, view.spec(), 440, 60, "reveal-checkbox");
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void aPromptsRevealIsShowPasswordUnderTheField() {
        Edt.run(() -> {
            var context = classic();
            try (var view = new KitView(context, prompt(), event -> { })) {
                KitFixtures.render(view, 400, 160, 1);
                var control = (SecretFields.SecretControl) view.control("pw");
                assertThat(control.boxLabel.getText()).isEqualTo("Show password");
                var boxAt = SwingUtilities.convertPoint(control.box.getParent(), control.box.getLocation(), view);
                var fieldAt = SwingUtilities.convertPoint(control.surface.getParent(), control.surface.getLocation(), view);
                assertThat(boxAt.y).as("under the field").isGreaterThanOrEqualTo(fieldAt.y + control.surface.getHeight());
                assertThat(boxAt.x).as("on the field's leading edge").isEqualTo(fieldAt.x);
                KitFixtures.capture(context, view.spec(), 400, 160, "reveal-prompt");
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void aDiscreetInputsCheckboxAlwaysShows() {
        Edt.run(() -> {
            var context = classic();
            try (var view = new KitView(context, row(Nodes.SecretKind.DISCREET_INPUT), event -> { })) {
                KitFixtures.render(view, 440, 60, 1);
                var control = (SecretFields.SecretControl) view.control("pw");
                assertThat(control.boxRow.isVisible()).as("empty and unfocused, the checkbox still shows").isTrue();
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void aStoredValueHasShowAndCopyButtons() {
        Edt.run(() -> {
            var context = classic(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, stored("detail.secret.a"), events::add)) {
                view.replaceSecret("detail.secret.a", "synthetic-a".toCharArray());
                KitFixtures.render(view, 300, 60, 1);
                var reveal = KitViewTest.find(view, "detail.secret.a.reveal", Buttons.KitButton.class);
                var copy = KitViewTest.find(view, "detail.secret.a.copy", Buttons.KitButton.class);
                assertThat(reveal.getText()).isEqualTo("Show");
                assertThat(reveal.getIcon()).isNull();
                assertThat(reveal.kind).isEqualTo(Nodes.ButtonKind.SECONDARY);
                assertThat(copy.getText()).isEqualTo("Copy");
                assertThat(copy.kind).isEqualTo(Nodes.ButtonKind.SECONDARY);
                assertThat(SwingUtilities.convertPoint(copy.getParent(), copy.getLocation(), view).x).as("Copy follows Show")
                    .isGreaterThan(SwingUtilities.convertPoint(reveal.getParent(), reveal.getLocation(), view).x + reveal.getWidth() - 1);
                reveal.doClick(0);
                assertThat(reveal.getText()).isEqualTo("Hide");
                copy.doClick(0);
                assertThat(events).contains(new UiEvent.Action("copy"));
                KitFixtures.capture(context, view.spec(), 300, 60, "reveal-stored");
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void aStoredValuesShowIsPerEntry() {
        Edt.run(() -> {
            var context = classic();
            try (var view = new KitView(context, stored("detail.secret.a"), event -> { })) {
                view.replaceSecret("detail.secret.a", "synthetic-a".toCharArray());
                KitFixtures.render(view, 300, 60, 1);
                KitViewTest.find(view, "detail.secret.a.reveal", Buttons.KitButton.class).doClick(0);
                assertThat(((SecretFields.SecretControl) view.control("detail.secret.a")).revealed).isTrue();
                view.update(stored("detail.secret.b"));
                view.replaceSecret("detail.secret.b", "synthetic-b".toCharArray());
                KitFixtures.render(view, 300, 60, 1);
                var next = (SecretFields.SecretControl) view.control("detail.secret.b");
                assertThat(next.revealed).as("another entry's value shows masked").isFalse();
                assertThat(next.reveal.getText()).isEqualTo("Show");
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void theDefaultKitKeepsTheGlyphInTheField() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), Kit.DEFAULT);
            try (var view = new KitView(context, row(Nodes.SecretKind.INPUT), event -> { })) {
                KitFixtures.render(view, 440, 60, 1);
                var control = (SecretFields.SecretControl) view.control("pw");
                assertThat(control.box).isNull();
                assertThat(control.reveal.getParent()).as("the glyph sits in the field").isSameAs(control.surface);
                assertThat(control.reveal.getIcon()).isNotNull();
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void theFacesDeclareTheirPairs() {
        assertThat(new ClassicRevealFace(SemanticColor.SURFACE_DIALOG).pairs()).extracting(pair -> pair.slot().key() + " on " + pair.against().key())
            .containsExactly("text.primary on surface.dialog");
        assertThat(new DefaultRevealFace().pairs()).isEmpty();
    }
}
