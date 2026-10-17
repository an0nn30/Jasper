package dev.jasper.app.windows;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.persistence.UiState;
import dev.jasper.app.presentation.DialogMapping;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.app.testsupport.MockProfile;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.ClassicDialog;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.theme.ThemeRoute;
import java.awt.Dimension;
import java.util.List;
import java.util.UUID;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Spec 10.8 and plan 6b Rulings 11: which dialogs a classic look draws in their window. */
@ExtendWith(EdtTestExtension.class)
class ClassicPlacementTest {
    static AuxiliaryWindows windows(DialogMapping.Kind kind, boolean windowTitled) {
        return new AuxiliaryWindows(UiState.inMemory(), surface -> { throw new AssertionError("never shown"); }, (choice, cancel) -> List.of(),
            new DialogMapping(kind, windowTitled));
    }

    @Test void aClassicOverlayIsCentredAndASheetJoinsTheTop() {
        var windows = windows(DialogMapping.Kind.CLASSIC, true);
        var unlock = windows.modalOverlay("Unlock Vault", UUID.randomUUID());
        var add = windows.dialog("Add Login", true, UUID.randomUUID());
        add.attachedSheet(true);
        assertThat(unlock.classic()).isTrue();
        assertThat(NativeShells.classicPlacement(unlock, true)).contains(ClassicDialog.Placement.CENTERED);
        assertThat(NativeShells.classicPlacement(add, true)).contains(ClassicDialog.Placement.SHEET);
        assertThat(NativeShells.classicPlacement(windows.dialog("Details", true, UUID.randomUUID()), true))
            .as("a centred dialog stays native").isEmpty();
        assertThat(NativeShells.classicPlacement(windows.overlay("Connecting", UUID.randomUUID()), true))
            .as("a progress overlay stays as it is").isEmpty();
        assertThat(NativeShells.classicPlacement(windows.window("vault", "Credential Vault", new Dimension(800, 600), true), true)).isEmpty();
    }

    @Test void aDialogWithoutAShowingWindowIsNative() {
        var windows = windows(DialogMapping.Kind.CLASSIC, true);
        var context = KitTestSupport.context();
        var spec = new ViewSpec(new Nodes.Text("locked", ControlState.READY, "Locked", Nodes.TextRole.LABEL));
        var unlock = windows.modalOverlay("Unlock Vault", UUID.randomUUID());
        assertThat(NativeShells.classicPlacement(unlock, false)).isEmpty();
        var add = windows.dialog("Add Login", true, UUID.randomUUID());
        add.attachedSheet(true);
        assertThat(NativeShells.classicPlacement(add, false)).isEmpty();
        try (var sheetView = new KitView(context, spec, event -> { }); var overlayView = new KitView(context, spec, event -> { })) {
            add.setContent(sheetView);
            unlock.setContent(overlayView);
            assertThat(sheetView.isOpaque()).as("an attached sheet's content paints no surface").isFalse();
            NativeShells.nativeFallback(add);
            assertThat(add.attachedSheet()).as("no showing window to hang from: a centred native dialog, not a refused sheet").isFalse();
            assertThat(sheetView.isOpaque()).as("the centred dialog's content paints its surface again").isTrue();
            NativeShells.nativeFallback(unlock);
            assertThat(unlock.attachedSheet() || overlayView.isOpaque()).as("an overlay opens as a modal dialog; nothing to clear").isFalse();
        }
    }

    /** Which shell {@code NativeShells.create} builds for each classic surface (spec 10.8). */
    @Test void eachClassicDialogTakesItsRoute() {
        var windows = windows(DialogMapping.Kind.CLASSIC, true);
        var unlock = windows.modalOverlay("Unlock Vault", UUID.randomUUID());
        var add = windows.dialog("Add Login", true, UUID.randomUUID());
        add.attachedSheet(true);
        var details = windows.dialog("Details", true, UUID.randomUUID());
        var progress = windows.overlay("Connecting", UUID.randomUUID());
        assertThat(NativeShells.classicRoute(unlock, true)).isEqualTo(NativeShells.ClassicRoute.IN_WINDOW);
        assertThat(NativeShells.classicRoute(add, true)).isEqualTo(NativeShells.ClassicRoute.IN_WINDOW);
        assertThat(NativeShells.classicRoute(unlock, false)).as("a modal overlay with no showing window").isEqualTo(NativeShells.ClassicRoute.MODAL_DIALOG);
        assertThat(NativeShells.classicRoute(add, false)).as("a sheet with no showing window falls back").isEqualTo(NativeShells.ClassicRoute.AS_REQUESTED);
        assertThat(NativeShells.classicRoute(details, true)).isEqualTo(NativeShells.ClassicRoute.AS_REQUESTED);
        assertThat(NativeShells.classicRoute(progress, true)).isEqualTo(NativeShells.ClassicRoute.AS_REQUESTED);
        var plain = windows(DialogMapping.Kind.AS_REQUESTED, false).modalOverlay("Unlock Vault", UUID.randomUUID());
        assertThat(NativeShells.classicRoute(plain, false)).as("an unmarked overlay keeps its own fallback").isEqualTo(NativeShells.ClassicRoute.AS_REQUESTED);
    }

    /** A dialog owned by a classic dialog drawn in its window hangs from that window, as one owned by an overlay does. */
    @Test void aDialogOwnedByAClassicSheetHangsFromItsWindow() {
        var windows = windows(DialogMapping.Kind.CLASSIC, true);
        var add = windows.dialog("Add Login", true, UUID.randomUUID());
        add.attachedSheet(true);
        var unlock = windows.dialog("Unlock Vault", true, add);
        var grant = windows.modalOverlay("Grant", add);
        var confirm = windows.dialog("Confirm", true, grant);
        assertThat(NativeShells.windowed(unlock, surface -> surface == add)).as("the sheet has no window: its owner's").isSameAs(add);
        assertThat(NativeShells.windowed(confirm, surface -> surface == add)).as("through an overlay and the sheet").isSameAs(add);
        assertThat(NativeShells.windowed(unlock, surface -> false)).as("a native sheet's window is its own").isSameAs(unlock);
        assertThat(NativeShells.windowed(confirm, surface -> false)).as("an overlay hangs from its owner's window").isSameAs(grant);
        var nested = windows.modalOverlay("Approve", unlock);
        assertThat(NativeShells.windowed(nested, surface -> surface == add)).as("a native dialog under the sheet has its own window").isSameAs(nested);
        NativeShells.nativeFallback(grant);
        assertThat(NativeShells.windowed(confirm, surface -> false)).as("an overlay opened as a native dialog has its own window").isSameAs(confirm);
    }

    /** A classic dialog's content replaced after its shell exists takes the same host flags (spec 10.8). */
    @Test void contentSetAfterTheShellTakesItsFlags() {
        var windows = windows(DialogMapping.Kind.CLASSIC, true);
        var unlock = windows.modalOverlay("Unlock Vault", UUID.randomUUID());
        unlock.laterContent(NativeShells.CLASSIC_FLAGS);
        var context = KitTestSupport.context();
        var spec = new ViewSpec(new Nodes.Text("locked", ControlState.READY, "Locked", Nodes.TextRole.LABEL));
        try (var later = new KitView(context, spec, event -> { })) {
            unlock.setContent(later);
            assertThat(later.hostTitled() && later.dialogBand()).as("the strip and the band, as for the first content").isTrue();
        }
        var plain = windows(DialogMapping.Kind.AS_REQUESTED, false).modalOverlay("Unlock Vault", UUID.randomUUID());
        try (var content = new KitView(context, spec, event -> { })) {
            plain.setContent(content);
            assertThat(content.hostTitled() || content.dialogBand()).as("no shell flags, no marks").isFalse();
        }
    }

    @Test void theDefaultMappingKeepsEveryDialogAsRequested() {
        var windows = windows(DialogMapping.Kind.AS_REQUESTED, false);
        var unlock = windows.modalOverlay("Unlock Vault", UUID.randomUUID());
        var add = windows.dialog("Add Login", true, UUID.randomUUID());
        add.attachedSheet(true);
        assertThat(unlock.classic() || add.classic()).isFalse();
        assertThat(NativeShells.classicPlacement(unlock, true)).isEmpty();
        assertThat(NativeShells.classicPlacement(add, true)).isEmpty();
        assertThat(add.hostTitled()).isFalse();
        var three = new AuxiliaryWindows(UiState.inMemory(), surface -> { throw new AssertionError(); });
        assertThat(three.modalOverlay("Unlock Vault", UUID.randomUUID()).classic()).as("today's constructors are the default look").isFalse();
    }

    @Test void aNativeTitleTitlesTheDialogsItDraws() {
        var windows = windows(DialogMapping.Kind.CLASSIC, true);
        assertThat(windows.dialog("Settings", true, UUID.randomUUID()).hostTitled()).isTrue();
        assertThat(windows.modalOverlay("Unlock Vault", UUID.randomUUID()).hostTitled()).as("an overlay's title is its strip").isFalse();
        var unlock = windows.modalOverlay("Unlock Vault", UUID.randomUUID());
        NativeShells.nativeFallback(unlock);
        assertThat(unlock.hostTitled()).as("opened as a native modal dialog, its system bar titles it").isTrue();
        var progress = windows.overlay("Connecting", UUID.randomUUID());
        NativeShells.nativeFallback(progress);
        assertThat(progress.hostTitled()).as("a progress overlay stays in its window").isFalse();
        var untitled = windows(DialogMapping.Kind.CLASSIC, false).modalOverlay("Unlock Vault", UUID.randomUUID());
        NativeShells.nativeFallback(untitled);
        assertThat(untitled.hostTitled()).as("no native title: the form keeps its own").isFalse();
        var sheet = windows.dialog("Add Login", true, UUID.randomUUID());
        sheet.attachedSheet(true);
        assertThat(sheet.hostTitled()).as("an attached sheet has no title bar to title it").isFalse();
        NativeShells.nativeFallback(sheet);
        assertThat(sheet.hostTitled()).as("its centred native fallback has one").isTrue();
    }

    /**
     * The production path of a classic dialog's body (spec 10.8): the surface's holder, an opaque panel on
     * {@code surface.panel}, wraps the content, so {@code NativeShells.classicContent} makes it transparent and the card's
     * dialog surface shows, through later look-and-feel updates too.
     */
    @Test void aClassicDialogsBodyIsTheKitsDialogSurface() {
        var themes = EnterpriseLook.controller(false);
        try {
            var context = themes.appearance().context(ThemeRoute.CHROME);
            var windows = windows(DialogMapping.Kind.CLASSIC, true);
            var unlock = windows.modalOverlay("Unlock Vault", UUID.randomUUID());
            var form = EnterpriseDialogMockTest.form(Nodes.FormKind.PROMPT_COMPACT, "Unlock Vault", "");
            try (var view = new KitView(context, new ViewSpec(form), event -> { })) {
                unlock.setContent(view);
                NativeShells.classicContent(unlock);
                assertThat(view.hostTitled() && view.dialogBand() && view.dialogCard()).as("the strip, the band and the card").isTrue();
                SwingUtilities.updateComponentTreeUI(unlock.holder());
                assertThat(unlock.holder().isOpaque()).as("the holder stays transparent through a look-and-feel update").isFalse();
                java.awt.Color dialogSurface = context.theme().semantic().color(context.kit().colors().dialogSurface());
                assertThat(UIManager.getColor("Panel.background").getRGB()).as("the holder's own fill differs, so the probe means something")
                    .isNotEqualTo(dialogSurface.getRGB());
                var root = new JRootPane();
                root.setSize(900, 600);
                dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(root);
                var dialog = new ClassicDialog(context, unlock.holder(), "Unlock Vault", Nodes.Hue.NONE, ClassicDialog.Placement.CENTERED);
                try (var overlay = WindowOverlay.classic(root, dialog)) {
                    overlay.show();
                    var card = overlay.cardBounds();
                    var image = MockProfile.print(root, 900, 600);
                    // In the body's side padding, halfway down the card.
                    assertThat(MockProfile.at(image, 2 * (card.x + 6), 2 * (card.y + card.height / 2)))
                        .as("the body is the kit's dialog surface").isEqualTo(new java.awt.Color(dialogSurface.getRGB() & 0xFFFFFF));
                }
            }
        } finally { themes.close(); EnterpriseLook.restoreDefault(); }
    }

    /** A modal overlay opened as a native dialog closes from its title bar as a dialog does, through its guards. */
    @Test void aNativeFallbackOverlayClosesThroughItsGuards() {
        var windows = windows(DialogMapping.Kind.CLASSIC, true);
        var inWindow = windows.modalOverlay("Unlock Vault", UUID.randomUUID());
        assertThat(inWindow.requestClose()).as("an in-window modal overlay refuses a window close").isFalse();
        assertThat(inWindow.closed()).isFalse();
        var unlock = windows.modalOverlay("Unlock Vault", UUID.randomUUID());
        NativeShells.nativeFallback(unlock);
        boolean[] allow = {false};
        int[] asked = {0};
        unlock.onClosing(() -> { asked[0]++; return allow[0]; });
        assertThat(unlock.requestClose()).as("a guard that refuses keeps it open").isFalse();
        assertThat(asked[0]).isEqualTo(1);
        allow[0] = true;
        assertThat(unlock.requestClose()).as("its title bar's close button closes it").isTrue();
        assertThat(unlock.closed()).isTrue();
    }

    /** A native dialog under the classic mapping takes the footer band, but not the card (spec 10.8's last sentence). */
    @Test void aNativeClassicDialogTakesTheBand() {
        var context = KitTestSupport.context();
        var spec = new ViewSpec(EnterpriseDialogMockTest.form(Nodes.FormKind.FULL, "Add Host", ""));
        var windows = windows(DialogMapping.Kind.CLASSIC, true);
        var details = windows.dialog("Add Host", true, UUID.randomUUID());
        var unlock = windows.modalOverlay("Unlock Vault", UUID.randomUUID());
        NativeShells.nativeFallback(unlock);
        var add = windows.dialog("Add Login", true, UUID.randomUUID());
        add.attachedSheet(true);
        NativeShells.nativeFallback(add);
        for (var surface : List.of(details, unlock, add)) {
            try (var view = new KitView(context, spec, event -> { }); var later = new KitView(context, spec, event -> { })) {
                surface.setContent(view);
                var marks = NativeShells.nativeMarks(surface, true);
                marks.accept(view);
                surface.laterContent(marks);
                assertThat(view.dialogBand()).as(surface.title() + ": the band").isTrue();
                assertThat(view.dialogCard()).as(surface.title() + ": the form paints its own fill").isFalse();
                assertThat(view.hostTitled()).as(surface.title() + ": the system bar titles a FULL form").isTrue();
                surface.setContent(later);
                assertThat(later.dialogBand() && later.hostTitled()).as(surface.title() + ": content set later is marked too").isTrue();
            }
        }
        var plain = windows(DialogMapping.Kind.AS_REQUESTED, false).dialog("Add Host", true, UUID.randomUUID());
        try (var view = new KitView(context, spec, event -> { })) {
            NativeShells.nativeMarks(plain, true).accept(view);
            assertThat(view.dialogBand() || view.hostTitled()).as("the default mapping marks nothing").isFalse();
        }
        try (var view = new KitView(context, spec, event -> { })) {
            NativeShells.nativeMarks(details, false).accept(view);
            assertThat(view.hostTitled()).as("a Jasper-drawn title bar leaves the form its title").isFalse();
            assertThat(view.dialogBand()).isTrue();
        }
    }
}
