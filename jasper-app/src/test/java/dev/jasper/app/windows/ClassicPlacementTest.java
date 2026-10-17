package dev.jasper.app.windows;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.persistence.UiState;
import dev.jasper.app.presentation.DialogMapping;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.ClassicDialog;
import dev.jasper.ui.swing.KitView;
import java.awt.Dimension;
import java.util.List;
import java.util.UUID;
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
        var unlock = windows.modalOverlay("Unlock Vault", UUID.randomUUID());
        assertThat(NativeShells.classicPlacement(unlock, false)).isEmpty();
        var add = windows.dialog("Add Login", true, UUID.randomUUID());
        add.attachedSheet(true);
        assertThat(NativeShells.classicPlacement(add, false)).isEmpty();
        NativeShells.nativeFallback(add);
        assertThat(add.attachedSheet()).as("no showing window to hang from: a centred native dialog, not a refused sheet").isFalse();
        NativeShells.nativeFallback(unlock);
        assertThat(unlock.kind()).as("an overlay opens as a modal dialog; nothing to clear").isEqualTo(AuxiliarySurface.Kind.OVERLAY);
    }

    /** A classic dialog's content replaced after its shell exists takes the same host flags (spec 10.8). */
    @Test void contentSetAfterTheShellTakesItsFlags() {
        var windows = windows(DialogMapping.Kind.CLASSIC, true);
        var unlock = windows.modalOverlay("Unlock Vault", UUID.randomUUID());
        unlock.laterContent(NativeShells.IN_WINDOW);
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
    }
}
