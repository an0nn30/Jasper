package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.find;
import static dev.jasper.ui.swing.KitViewTest.release;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.Nodes.FormKind;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.DerivedColor;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Spec 10.8: a host that titles the dialog takes the form's title; a classic dialog's footer is a band. */
class HostedTitleTest {
    static Nodes.Form sheet(Optional<Nodes.Node> hero) {
        return new Nodes.Form("form", ControlState.READY, FormKind.SHEET, "Add Login", hero,
            List.of(new Nodes.Secret("password", ControlState.READY, "", "Password", "")), Optional.empty(),
            List.of(new Nodes.Button("save", ControlState.READY, "Save", "", Nodes.ButtonKind.PRIMARY, "save", "")), "", Nodes.FormSubmission.PRIMARY, "");
    }

    @Test void aHostTitledFormDrawsNeitherItsTitleNorAHeadingThatRepeatsIt() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var hero = new Nodes.Text("heading", ControlState.READY, "Add Login", Nodes.TextRole.LABEL);
            try (var view = new KitView(context, new ViewSpec(sheet(Optional.of(hero))), e -> { })) {
                KitFixtures.render(view, 440, 300, 1);
                assertThat(find(view, "heading", javax.swing.JComponent.class).isVisible()).isTrue();
                view.hostTitled(true);
                KitFixtures.render(view, 440, 300, 1);
                assertThat(find(view, "heading", javax.swing.JComponent.class).isVisible()).as("the host shows the title").isFalse();
                view.update(new ViewSpec(sheet(Optional.of(hero))));
                KitFixtures.render(view, 440, 300, 1);
                assertThat(find(view, "heading", javax.swing.JComponent.class).isVisible()).as("and after an update and a refresh").isFalse();
                view.hostTitled(false);
                KitFixtures.render(view, 440, 300, 1);
                assertThat(find(view, "heading", javax.swing.JComponent.class).isVisible()).as("back when the host stops titling").isTrue();
            } finally { release(context); }
            context = KitFixtures.context();
            var intro = new Nodes.Text("intro", ControlState.READY, "Store a password for a host", Nodes.TextRole.DESCRIPTION);
            try (var view = new KitView(context, new ViewSpec(sheet(Optional.of(intro))), e -> { })) {
                view.hostTitled(true);
                KitFixtures.render(view, 440, 300, 1);
                assertThat(find(view, "intro", javax.swing.JComponent.class).isVisible()).as("a hero that says more than the title stays").isTrue();
            } finally { release(context); }
            context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(sheet(Optional.empty())), e -> { })) {
                view.hostTitled(true);
                KitFixtures.render(view, 440, 300, 1);
                for (var label : descendants(view)) if (label instanceof javax.swing.JLabel text && "Add Login".equals(text.getText()))
                    assertThat(text.isVisible()).as("no title label").isFalse();
            } finally { release(context); }
        });
    }

    @Test void aDialogBandIsTheFooterOnSurfaceFooterUnderARule() {
        Edt.run(() -> {
            var theme = KitFixtures.enterpriseLight();
            var context = KitFixtures.context(theme, KitFixtures.enterprise());
            try (var view = new KitView(context, new ViewSpec(sheet(Optional.empty())), e -> { })) {
                var before = KitFixtures.render(view, 440, 300, 1);
                view.dialogBand(true);
                var image = KitFixtures.render(view, 440, 300, 1);
                var save = find(view, "save", javax.swing.JComponent.class);
                var at = javax.swing.SwingUtilities.convertPoint(save.getParent(), save.getLocation(), view);
                assertThat(new java.awt.Color(image.getRGB(4, at.y + save.getHeight() / 2) & 0xFFFFFF)).isEqualTo(DerivedColor.SURFACE_FOOTER.color(theme));
                assertThat(image.getRGB(4, at.y + save.getHeight() / 2)).as("the band is new").isNotEqualTo(before.getRGB(4, at.y + save.getHeight() / 2));
                // The card owns the frame: the SHEET form's surface has no outline and no inset of its own.
                var surface = find(view, "form", Forms.Surface.class);
                assertThat(surface.sheet).as("no sheet outline inside a classic dialog").isFalse();
                assertThat(surface.getInsets()).isEqualTo(new java.awt.Insets(0, 0, 0, 0));
                var origin = javax.swing.SwingUtilities.convertPoint(surface.getParent(), surface.getLocation(), view);
                int bottom = origin.y + surface.getHeight() - surface.getInsets().bottom;
                assertThat(bottom - (at.y + save.getHeight())).as("8 below the buttons, to the surface's inner edge").isEqualTo(8);
            } finally { release(context); }
        });
    }

    static List<java.awt.Component> descendants(java.awt.Container root) {
        var all = new java.util.ArrayList<java.awt.Component>();
        for (var child : root.getComponents()) { all.add(child); if (child instanceof java.awt.Container nested) all.addAll(descendants(nested)); }
        return all;
    }
}
