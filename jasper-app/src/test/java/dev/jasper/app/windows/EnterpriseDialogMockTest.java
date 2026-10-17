package dev.jasper.app.windows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.app.testsupport.MockProfile;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.Nodes.ButtonKind;
import dev.jasper.ui.model.Nodes.FormKind;
import dev.jasper.ui.model.Nodes.FormSubmission;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.ClassicDialog;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.ThemeRoute;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Spec 10.8 against Credential Vault 1, 4 and 5 (Light, as the mocks are): the classic dialog's chrome over Enterprise
 * Light, around fixture forms (plan 6b Rulings 13 and 14). Unlock Vault's window in its 3012 × 1932 mock has its outer
 * edge at (64, 64) and its content at (66, 124); the Credential Vault window of the 1644 × 1204 sheet mocks has its
 * content from x 2 and its title bar's bottom at y 58.
 */
@ExtendWith(EdtTestExtension.class)
class EnterpriseDialogMockTest {
    static final String UNLOCK = "07 Credential Vault/Credential Vault 1 - Unlock Vault (over Workstation).png";
    /** Unlock Vault's frame in its mock, x 1104-1907 and y 782-1149, and the content's origin. */
    static final Rectangle UNLOCK_FRAME = new Rectangle(1104, 782, 804, 368);
    static final int UNLOCK_CONTENT_X = 66, UNLOCK_CONTENT_Y = 124;
    /** The sheets' frame in their mocks: x 380-1263 from y 58 (its height is each mock's), and the content's left edge. */
    static final int SHEET_X = 380, SHEET_WIDTH = 884, SHEET_TOP = 58, SHEET_CONTENT_X = 2;

    static Nodes.Form form(FormKind kind, String title, String footnote) {
        var ready = ControlState.READY;
        return new Nodes.Form("form", ready, kind, title, Optional.empty(), List.of(new Nodes.Secret("password", ready, "", "Master password", "")),
            Optional.empty(), List.of(new Nodes.Button("cancel", ready, "Cancel", "", ButtonKind.SECONDARY, "cancel", ""),
                new Nodes.Button("ok", ready, "Unlock", "", ButtonKind.PRIMARY, "ok", "")), "cancel", FormSubmission.PRIMARY, footnote);
    }

    /**
     * A classic dialog over a white window, printed at 2×.
     *
     * @param image the print
     * @param card where the card is, in logical pixels
     * @param swatch the theme's colour for the dialog's hue
     */
    record Rendered(BufferedImage image, Rectangle card, Color swatch, Color dialogSurface) { }

    static Rendered render(ClassicDialog.Placement placement, String title, Nodes.Hue hue, FormKind kind, String footnote,
                           int width, int height, String name) {
        var themes = EnterpriseLook.controller(false);
        try {
            var context = themes.appearance().context(ThemeRoute.CHROME);
            var palette = themes.current().chrome().semantic();
            // The swatch as its declared pair paints it: the hue held to the indicator guard on the strip (spec 7.3).
            Color swatch = hue == Nodes.Hue.NONE ? palette.color(SemanticColor.TEXT_PRIMARY)
                : dev.jasper.ui.theme.SurfaceContrast.painted(themes.current().chrome(), ClassicDialog.swatch(hue));
            var root = new JRootPane();
            var behind = new JPanel();
            behind.setBackground(Color.WHITE);
            root.setContentPane(behind);
            root.setSize(width, height);
            LayoutTestSupport.layoutTree(root);
            try (var view = new KitView(context, new ViewSpec(form(kind, title, footnote)), event -> { })) {
                // The production path: the surface's holder around the view, prepared as NativeShells.inWindow prepares it.
                var windows = ClassicPlacementTest.windows(dev.jasper.app.presentation.DialogMapping.Kind.CLASSIC, true);
                var surface = placement == ClassicDialog.Placement.SHEET ? windows.dialog(title, true, java.util.UUID.randomUUID())
                    : windows.modalOverlay(title, java.util.UUID.randomUUID());
                if (placement == ClassicDialog.Placement.SHEET) surface.attachedSheet(true);
                surface.setContent(view);
                NativeShells.classicContent(surface);
                var dialog = new ClassicDialog(context, surface.holder(), title, hue, placement);
                // Credential Vault 1's title bar and its rule above the content: 29 px (Task 6's centring).
                dialog.windowTitleHeight(29);
                try (var overlay = WindowOverlay.classic(root, dialog)) {
                    overlay.show();
                    var image = MockProfile.print(root, width, height);
                    MockProfile.write(image, "enterprise-" + name);
                    return new Rendered(image, overlay.cardBounds(), swatch, palette.color(context.kit().colors().dialogSurface()));
                }
            }
        } finally { themes.close(); EnterpriseLook.restoreDefault(); }
    }

    /** The body, 12 px (2×) inside the frame's left edge and 64 below its top (under the strip): the kit's dialog surface, the mock's #ECEDEF. */
    static void body(String what, Rendered rendered, int x, int y, BufferedImage mock, int mockX, int mockY) {
        var at = MockProfile.at(rendered.image(), x, y);
        assertThat(at).as(what + " body is the kit's dialog surface").isEqualTo(new Color(rendered.dialogSurface().getRGB() & 0xFFFFFF));
        assertThat(MockProfile.deltaE(at, MockProfile.at(mock, mockX, mockY))).as(what + " body against the mock").isLessThanOrEqualTo(2.5);
    }

    static void same(String what, MockProfile.Run actual, MockProfile.Run expected, int tolerance) {
        assertThat(actual.length()).as(what + " length").isCloseTo(expected.length(), within(tolerance));
        assertThat(MockProfile.deltaE(actual.color(), expected.color())).as(what + " colour").isLessThanOrEqualTo(2.5);
    }

    @Test void unlockVaultIsCredentialVaultOnesChrome() {
        var mock = MockProfile.mock(UNLOCK);
        var rendered = render(ClassicDialog.Placement.CENTERED, "Unlock Vault", Nodes.Hue.NONE, FormKind.PROMPT_COMPACT,
            "Auto-locks after 15 min idle", 1440, 871, "unlock");
        var card = rendered.card();
        var image = rendered.image();
        // Centred on the whole window, its title bar included: the mock's card centre is (720, 421) in the content.
        assertThat(card.x + card.width / 2.0).as("centred across").isCloseTo((UNLOCK_FRAME.getCenterX() - UNLOCK_CONTENT_X) / 2, within(1.0));
        assertThat(card.y + card.height / 2.0).as("centred on the whole window").isCloseTo((UNLOCK_FRAME.getCenterY() - UNLOCK_CONTENT_Y) / 2, within(1.0));
        // Down a column 16 px (2×) inside the frame's right edge, in the content's side padding and so clear of the title,
        // the footnote and the buttons whatever the fixture form's width: the frame, the
        // 26 px strip and its rule from the top; the band's rule, the 40 px band and the frame from the bottom.
        var expected = MockProfile.solid(MockProfile.column(mock, UNLOCK_FRAME.x + UNLOCK_FRAME.width - 16, UNLOCK_FRAME.y, UNLOCK_FRAME.y + UNLOCK_FRAME.height), 2);
        var actual = MockProfile.solid(MockProfile.column(image, 2 * (card.x + card.width) - 16, 2 * card.y, 2 * (card.y + card.height)), 2);
        String[] top = {"frame", "header strip", "strip rule"};
        for (int i = 0; i < 3; i++) same("unlock " + top[i], actual.get(i), expected.get(i), 2);
        String[] bottom = {"frame", "footer band", "band rule"};
        for (int i = 1; i <= 3; i++) same("unlock bottom " + bottom[i - 1], actual.get(actual.size() - i), expected.get(expected.size() - i), 2);
        body("unlock", rendered, 2 * card.x + 12, 2 * card.y + 64, mock, UNLOCK_FRAME.x + 12, UNLOCK_FRAME.y + 64);
        // The title, 10 in and 8 down in the strip, in the semibold UI face (mock ink 1128-1265, from y 800).
        var mockTitle = MockProfile.inks(mock, new Rectangle(UNLOCK_FRAME.x + 8, UNLOCK_FRAME.y + 4, 560, 46), 12, 25).getFirst();
        var title = MockProfile.inks(image, new Rectangle(2 * card.x + 8, 2 * card.y + 4, 560, 46), 12, 25).getFirst();
        assertThat(title.start() - 2 * card.x).as("the title's inset").isCloseTo(mockTitle.start() - UNLOCK_FRAME.x, within(3));
        assertThat(title.top() - 2 * card.y).as("the title's top").isCloseTo(mockTitle.top() - UNLOCK_FRAME.y, within(3));
        MockProfile.fonts("Unlock Vault's title width", title.width(), mockTitle.width(), 6);
        // The still scrim: white reads #C9CACC.
        assertThat(MockProfile.deltaE(MockProfile.at(image, 200, 1600), MockProfile.at(mock, 1500, 1400))).as("the scrim").isLessThanOrEqualTo(2.5);
        MockProfile.write(image.getSubimage(2 * card.x, 2 * card.y, 560, 56), "enterprise-unlock-strip");
        MockProfile.write(image.getSubimage(2 * card.x, 2 * (card.y + card.height) - 86, 560, 86), "enterprise-unlock-footer");
    }

    @ParameterizedTest
    @CsvSource({"07 Credential Vault/Credential Vault 4 - Add Login.png, Add Login, BLUE, add-login, 2F65CA, 550",
                "07 Credential Vault/Credential Vault 5 - Add SSH Key.png, Add SSH Key, GREEN, add-ssh-key, 2E9E4F, 486"})
    void aSheetIsCredentialVaultsChrome(String file, String title, Nodes.Hue hue, String name, String mockSwatch, int frameHeight) throws Exception {
        // EdtTestExtension intercepts test methods, not a parameterized test's invocations: the body runs on the EDT here.
        dev.jasper.app.workspace.DesktopTestSupport.edt(() -> sheet(file, title, hue, name, mockSwatch, frameHeight));
    }

    static void sheet(String file, String title, Nodes.Hue hue, String name, String mockSwatch, int frameHeight) {
        var mock = MockProfile.mock(file);
        // The frame's bottom edge: 606-607 in Credential Vault 4, 542-543 in 5.
        var frame = new Rectangle(SHEET_X, SHEET_TOP, SHEET_WIDTH, frameHeight);
        var rendered = render(ClassicDialog.Placement.SHEET, title, hue, FormKind.SHEET, "", 820, 572, name);
        var card = rendered.card();
        var image = rendered.image();
        assertThat(card.y).as("joined under the title bar").isZero();
        assertThat(card.x + card.width / 2.0).as("centred across").isCloseTo((frame.getCenterX() - SHEET_CONTENT_X) / 2, within(1.0));
        // Down a column 280 px (2×) inside the frame: no top frame, so the strip and its rule come first.
        var expected = MockProfile.solid(MockProfile.column(mock, frame.x + 280, frame.y, frame.y + frame.height), 2);
        var actual = MockProfile.solid(MockProfile.column(image, 2 * card.x + 280, 0, 2 * card.height), 2);
        same(name + " header strip", actual.get(0), expected.get(0), 2);
        same(name + " strip rule", actual.get(1), expected.get(1), 2);
        String[] bottom = {"frame", "footer band", "band rule"};
        for (int i = 1; i <= 3; i++) same(name + " bottom " + bottom[i - 1], actual.get(actual.size() - i), expected.get(expected.size() - i), 2);
        body(name, rendered, 2 * card.x + 12, 64, mock, frame.x + 12, frame.y + 64);
        // The swatch, 8 square at 10, 9 down (mock 402-417, y 76-91), then the title 7 after it.
        var mockInks = MockProfile.inks(mock, new Rectangle(frame.x + 4, frame.y + 2, 560, 46), 12, 25);
        var inks = MockProfile.inks(image, new Rectangle(2 * card.x + 4, 2, 560, 46), 12, 25);
        for (int i = 0; i < 2; i++) {
            assertThat(inks.get(i).start() - 2 * card.x).as(name + " ink " + i + " inset").isCloseTo(mockInks.get(i).start() - frame.x, within(3));
            assertThat(inks.get(i).top()).as(name + " ink " + i + " top").isCloseTo(mockInks.get(i).top() - frame.y, within(3));
        }
        assertThat(inks.get(0).width()).as(name + " swatch width").isEqualTo(mockInks.get(0).width());
        MockProfile.fonts(name + " title width", inks.get(1).width(), mockInks.get(1).width(), 6);
        var swatch = MockProfile.at(image, inks.get(0).start() + 8, inks.get(0).top() + 8);
        assertThat(swatch).as(name + " swatch: the theme's hue role through its guard (Rulings 14)").isEqualTo(rendered.swatch());
        System.out.printf("%s swatch: theme %06X, mock %s, delta E %.1f%n", name, swatch.getRGB() & 0xFFFFFF, mockSwatch,
            MockProfile.deltaE(swatch, new Color(Integer.parseInt(mockSwatch, 16))));
        MockProfile.write(image.getSubimage(2 * card.x, 0, 560, 54), "enterprise-" + name + "-strip");
        MockProfile.write(image.getSubimage(2 * card.x, 2 * card.height - 86, 560, 86), "enterprise-" + name + "-footer");
    }
}
