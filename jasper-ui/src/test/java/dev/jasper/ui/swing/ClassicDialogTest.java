package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.release;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.Nodes.ButtonKind;
import dev.jasper.ui.model.Nodes.FormKind;
import dev.jasper.ui.model.Nodes.FormSubmission;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Spec 10.8 and the Credential Vault mocks: the classic in-window dialog's chrome. */
class ClassicDialogTest {
    static final ControlState READY = ControlState.READY;

    static Nodes.Form form(FormKind kind, String title, String footnote) {
        return new Nodes.Form("form", READY, kind, title, Optional.empty(), List.of(new Nodes.Secret("password", READY, "", "Master password", "")),
            Optional.empty(), List.of(new Nodes.Button("cancel", READY, "Cancel", "", ButtonKind.SECONDARY, "cancel", ""),
                new Nodes.Button("unlock", READY, "Unlock", "", ButtonKind.PRIMARY, "unlock", "")), "cancel", FormSubmission.PRIMARY, footnote);
    }

    static void layout(java.awt.Container container) {
        container.doLayout();
        for (var child : container.getComponents()) if (child instanceof java.awt.Container nested) layout(nested);
    }

    static BufferedImage print(ClassicDialog dialog, int width, int height) {
        dialog.setSize(width, height);
        for (int i = 0; i < 3; i++) layout(dialog);
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try { g.setColor(Color.WHITE); g.fillRect(0, 0, width, height); dialog.printAll(g); } finally { g.dispose(); }
        return image;
    }

    static Color at(BufferedImage image, int x, int y) { return new Color(image.getRGB(x, y) & 0xFFFFFF); }

    @Test void theCentredDialogIsTheMocksChrome() {
        Edt.run(() -> {
            var theme = KitFixtures.enterpriseLight();
            var context = KitFixtures.context(theme, KitFixtures.enterprise());
            try (var view = new KitView(context, new ViewSpec(form(FormKind.PROMPT_COMPACT, "Unlock Vault", "Auto-locks after 15 min idle")), e -> { })) {
                view.hostTitled(true); view.dialogBand(true); view.dialogCard(true);
                try (var dialog = new ClassicDialog(context, view, "Unlock Vault", Nodes.Hue.NONE, ClassicDialog.Placement.CENTERED)) {
                    dialog.windowTitleHeight(29);
                    dialog.open();
                    var image = print(dialog, 1440, 871);
                    var card = dialog.cardBounds();
                    assertThat(card.width).as("the content and the frame").isEqualTo(view.getPreferredSize().width + 2);
                    assertThat(card.x).isEqualTo((1440 - card.width) / 2);
                    assertThat(card.y).as("centred on the whole window, its title bar included").isEqualTo((871 - 29 - card.height) / 2);
                    var strip = dialog.card().getComponent(0);
                    assertThat(strip.getHeight()).as("26 and the rule").isEqualTo(27);
                    // The still scrim over white; EnterpriseDialogMockTest compares it with Credential Vault 1's.
                    var scrim = theme.semantic().color(SemanticColor.SURFACE_SCRIM);
                    int white = (int) Math.round(255 + (scrim.getRed() - 255) * scrim.getAlpha() / 255.0);
                    assertThat(at(image, 5, 5).getRed()).as("the scrim over white").isBetween(white - 1, white + 1);
                    assertThat(at(image, card.x, card.y + card.height / 2)).as("the frame").isEqualTo(DerivedColor.BORDER_HOVER.color(theme));
                    assertThat(at(image, card.x + 40, card.y + 6)).as("the strip").isEqualTo(DerivedColor.SURFACE_HEADER_ACTIVE.color(theme));
                    assertThat(at(image, card.x + 40, card.y + 27)).as("its rule").isEqualTo(theme.semantic().color(SemanticColor.BORDER_WINDOW));
                    assertThat(at(image, card.x + 3, card.y + card.height - 5)).as("the footer band").isEqualTo(DerivedColor.SURFACE_FOOTER.color(theme));
                    assertThat(KitViewTest.find(view, "form.footnote", javax.swing.JLabel.class)).as("the footnote stays in the band").isNotNull();
                }
            } finally { release(context); }
        });
    }

    @Test void aSheetJoinsTheTopWithNoTopFrameAndASwatchLeadsTheTitle() {
        Edt.run(() -> {
            var theme = KitFixtures.enterpriseLight();
            var context = KitFixtures.context(theme, KitFixtures.enterprise());
            try (var view = new KitView(context, new ViewSpec(form(FormKind.SHEET, "Add Login", "")), e -> { })) {
                view.hostTitled(true); view.dialogBand(true); view.dialogCard(true);
                try (var dialog = new ClassicDialog(context, view, "Add Login", Nodes.Hue.BLUE, ClassicDialog.Placement.SHEET)) {
                    dialog.open();
                    var image = print(dialog, 822, 573);
                    var card = dialog.cardBounds();
                    assertThat(card.y).as("joined under the title bar").isZero();
                    assertThat(card.x).isEqualTo((822 - card.width) / 2);
                    assertThat(at(image, card.x + 60, 0)).as("no top frame: the strip reaches the top").isEqualTo(DerivedColor.SURFACE_HEADER_ACTIVE.color(theme));
                    assertThat(at(image, card.x + 1 + 10 + 4, 13)).as("the swatch, 8 square at 10").isEqualTo(theme.semantic().color(SemanticColor.HUE_BLUE));
                    // The card owns the frame: a SHEET form inside it draws no outline of its own (Credential Vault 4:
                    // band 526-605, frame 606-607 at 2x), so the frame sits directly under the band.
                    int bottom = card.y + card.height - 1;
                    assertThat(at(image, card.x + 60, bottom)).as("the card's frame closes the bottom").isEqualTo(DerivedColor.BORDER_HOVER.color(theme));
                    assertThat(at(image, card.x + 60, bottom - 1)).as("the band directly above the frame").isEqualTo(DerivedColor.SURFACE_FOOTER.color(theme));
                    assertThat(at(image, card.x + 1, bottom - 10)).as("no form outline inside the frame's side").isEqualTo(DerivedColor.SURFACE_FOOTER.color(theme));
                }
            } finally { release(context); }
        });
    }

    /** Credential Vault 1: the strip titles Unlock Vault; the prompt's hero keeps its lock tile and its message. */
    @Test void aPromptHeadingKeepsItsTileAndMessageUnderTheStrip() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, KitFixtures.form("unlock"), e -> { })) {
                view.hostTitled(true); view.dialogBand(true); view.dialogCard(true);
                try (var dialog = new ClassicDialog(context, view, "Unlock Vault", Nodes.Hue.NONE, ClassicDialog.Placement.CENTERED)) {
                    dialog.open();
                    print(dialog, 1440, 871);
                    assertThat(KitViewTest.find(view, "unlock-hero.tile", Stacks.Tile.class).isVisible()).as("the lock tile").isTrue();
                    assertThat(KitViewTest.find(view, "unlock-description", javax.swing.JComponent.class).isVisible()).as("the intro").isTrue();
                    assertThat(KitViewTest.find(view, "unlock-title", javax.swing.JComponent.class).isVisible())
                        .as("the heading is the strip's, hidden by the classic prompt face").isFalse();
                    view.update(KitFixtures.form("unlock"));
                    print(dialog, 1440, 871);
                    assertThat(KitViewTest.find(view, "unlock-hero.tile", Stacks.Tile.class).isVisible()).as("after an update").isTrue();
                    assertThat(KitViewTest.find(view, "unlock-description", javax.swing.JComponent.class).isVisible()).isTrue();
                }
            } finally { release(context); }
        });
    }

    /** The strip is named from the start, and a long title ends in an ellipsis inside its trailing inset. */
    @Test void aLongTitleIsShortenedAndTheStripIsNamed() {
        Edt.run(() -> {
            var theme = KitFixtures.enterpriseLight();
            var context = KitFixtures.context(theme, KitFixtures.enterprise());
            var body = new javax.swing.JPanel();
            body.setOpaque(false);
            body.setPreferredSize(new java.awt.Dimension(200, 80));
            String title = "Grant access to the deployment credentials for every production host in the fleet";
            try (var dialog = new ClassicDialog(context, body, title, Nodes.Hue.NONE, ClassicDialog.Placement.CENTERED)) {
                var strip = dialog.card().getComponent(0);
                assertThat(strip.getAccessibleContext().getAccessibleName()).as("named before any setTitle").isEqualTo(title);
                dialog.open();
                var image = print(dialog, 600, 400);
                var card = dialog.cardBounds();
                var ground = DerivedColor.SURFACE_HEADER_ACTIVE.color(theme);
                int inset = context.metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.DIALOG_HEADER_INSET);
                boolean inked = false;
                for (int y = card.y + 2; y < card.y + strip.getHeight() - 2; y++) {
                    for (int x = card.x + card.width - inset; x < card.x + card.width - 2; x++)
                        assertThat(at(image, x, y)).as("nothing drawn in the trailing inset at " + x + ", " + y).isEqualTo(ground);
                    for (int x = card.x + inset; x < card.x + card.width / 2; x++) inked |= !at(image, x, y).equals(ground);
                }
                assertThat(inked).as("the title is drawn").isTrue();
            } finally { release(context); }
        });
    }

    @Test void itNeverMoves() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, new ViewSpec(form(FormKind.SHEET, "Add Login", "")), e -> { })) {
                var dialog = new ClassicDialog(context, view, "Add Login", Nodes.Hue.NONE, ClassicDialog.Placement.CENTERED);
                dialog.setSize(800, 600); dialog.open();
                assertThat(dialog.cardBounds().isEmpty()).as("in place at once").isFalse();
                var removed = new AtomicInteger();
                dialog.dismiss(removed::incrementAndGet);
                assertThat(removed).as("gone at once").hasValue(1);
                dialog.close();
            } finally { release(context); }
        });
    }
}
