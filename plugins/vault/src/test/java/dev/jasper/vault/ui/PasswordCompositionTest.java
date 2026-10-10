package dev.jasper.vault.ui;

import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.vault.service.UnlockCaptureSupport;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.vault.testsupport.KitViews.*;
import static org.assertj.core.api.Assertions.*;

class PasswordCompositionTest {
    @TempDir Path directory;

    @Test void createUsesHeroAndPolicyReadinessWithoutRetainingSecrets() throws Exception {
        edt(() -> {
            try (var panel = PasswordPanel.create(new FakeKit("dev.jasper.vault"), true, (p,b) -> java.util.Arrays.fill(p, (char)0), () -> {})) {
                var view = panel.view();
                assertThat(((Nodes.Form)view.spec().root()).kind()).isEqualTo(Nodes.FormKind.HERO);
                assertThat(((Nodes.Form)view.spec().root()).hero()).isPresent();
                assertThat(node(view, "strength", Nodes.Strength.class).filled()).isZero();
                assertThat(node(view, "bind", Nodes.Toggle.class).checked()).isTrue();
                assertThat(button(view, "submit").state().enabled()).isFalse();
                type(view, "password", "1234567"); type(view, "confirm", "1234567");
                assertThat(button(view, "submit").state().enabled()).isFalse();
                type(view, "password", "12345678"); type(view, "confirm", "12345678");
                assertThat(button(view, "submit").state().enabled()).isTrue();
                type(view, "confirm", "mismatch");
                assertThat(button(view, "submit").state().enabled()).isFalse();
                assertThat(view.spec().toString()).doesNotContain("12345678", "mismatch");
                panel.clear();
                assertThat(button(view, "submit").state().enabled()).isFalse();
                assertThat(node(view, "strength", Nodes.Strength.class).filled()).isZero();
            }
        });
    }

    @Test void unlockUsesOneSecretAndBecomesReadyWithAnyNonemptyPassword() throws Exception {
        edt(() -> {
            try (var panel = PasswordPanel.unlock(new FakeKit("dev.jasper.vault"), UnlockCaptureSupport.prompt(directory))) {
                assertThat(((Nodes.Form)panel.view().spec().root()).kind()).isEqualTo(Nodes.FormKind.HERO_COMPACT);
                assertThat(all(panel.view()).stream().filter(Nodes.Secret.class::isInstance)).hasSize(1);
                assertThat(button(panel.view(), "submit").state().enabled()).isFalse();
                type(panel.view(), "password", "a");
                assertThat(button(panel.view(), "submit").state().enabled()).isTrue();
            }
        });
    }

    @Test void secretDescriptorCannotCaptureTypedPassword() {
        var node = new Nodes.Secret("password", ControlState.READY, "Master password", "Master Password", "master");
        assertThat(node.toString()).doesNotContain("synthetic-secret");
        assertThat(java.util.Arrays.stream(node.getClass().getRecordComponents()).map(java.lang.reflect.RecordComponent::getName))
            .doesNotContain("value");
    }
}
