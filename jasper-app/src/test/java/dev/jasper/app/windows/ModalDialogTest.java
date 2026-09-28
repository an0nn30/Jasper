package dev.jasper.app.windows;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.persistence.UiState;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModalDialogTest {
    private final UiState state = UiState.inMemory();
    private final AuxiliaryWindows windows = new AuxiliaryWindows(state, surface -> new AuxiliarySurface.Shell(
        () -> { }, () -> { }, () -> { }, title -> { }, () -> new Rectangle(10, 20, 900, 600), (title, initial) -> Optional.empty()));

    @Test void aNamedModalDialogRemembersItsBoundsWithOrWithoutAnOwner() {
        var dialog = windows.modalDialog("app.settings", "Settings", new Dimension(1000, 700), UUID.randomUUID());
        assertThat(dialog.kind()).isEqualTo(AuxiliarySurface.Kind.DIALOG);
        assertThat(dialog.modal()).isTrue();
        assertThat(dialog.preferredSize()).isEqualTo(new Dimension(1000, 700));
        dialog.show();
        dialog.close();
        assertThat(state.window("app.settings")).hasValue(new UiState.Bounds(10, 20, 900, 600));
        var ownerless = windows.modalDialog("app.other", "Other", new Dimension(400, 300), null);
        assertThat(ownerless.ownerWindow()).isEmpty();
        ownerless.show();
        ownerless.close();
        assertThat(state.window("app.other")).isPresent();
    }

    @Test void plainDialogsStillForgetTheirBounds() {
        var plain = windows.dialog("Confirm", true, UUID.randomUUID());
        plain.show();
        plain.close();
        assertThat(state.window("")).isEmpty();
    }
}
