package dev.jasper.sdk.ui;

import dev.jasper.sdk.WindowOwner;
import java.awt.Dimension;
import javax.swing.ImageIcon;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PanelAndWindowValuesTest {
    @Test void panelSpecsValidate() {
        var spec = new PanelSpec("dev.x.tool.hosts", "Hosts", new ImageIcon(), Anchor.LEFT);
        assertThat(spec.defaultAnchor()).isEqualTo(Anchor.LEFT);
        assertThatIllegalArgumentException().isThrownBy(() -> new PanelSpec("nodot", "Hosts", new ImageIcon(), Anchor.LEFT));
        assertThatIllegalArgumentException().isThrownBy(() -> new PanelSpec("dev.x.tool.hosts", " ", new ImageIcon(), Anchor.LEFT));
        assertThatIllegalArgumentException().as("Task 4 dual-source rule: exactly one icon source")
            .isThrownBy(() -> new PanelSpec("dev.x.tool.hosts", "Hosts", (javax.swing.Icon) null, Anchor.LEFT))
            .withMessage("Exactly one icon source is required");
        assertThatNullPointerException().isThrownBy(() -> new PanelSpec("dev.x.tool.hosts", "Hosts", new ImageIcon(), null));
        assertThat(spec.toggleActionId()).as("the application registers <id>.toggle").isEmpty();
        var named = spec.withToggleActionId("dev.x.tool.show");
        assertThat(named.toggleActionId()).contains("dev.x.tool.show");
        assertThat(named.id()).isEqualTo(spec.id());
        assertThat(named.withToggleActionId(null).toggleActionId()).isEmpty();
        assertThatNullPointerException().isThrownBy(() -> new PanelSpec("dev.x.tool.hosts", "Hosts", new ImageIcon(), Anchor.LEFT, null));
    }

    @Test void aSplitSurvivesTheToggleActionAndOlderConstructorsStillLink() throws Exception {
        var spec = new PanelSpec("dev.x.tool.files", "Files", new ImageIcon(), Anchor.LEFT);
        assertThat(spec.split()).as("the first half unless asked").isFalse();
        assertThat(new PanelSpec("dev.x.tool.files", "Files", new ImageIcon(), Anchor.LEFT,
            java.util.Optional.of("dev.x.tool.show")).split()).isFalse();
        var split = spec.withSplit();
        assertThat(split.split()).isTrue();
        assertThat(split.defaultAnchor()).isEqualTo(Anchor.LEFT);
        assertThat(split.withToggleActionId("dev.x.tool.show")).satisfies(value -> {
            assertThat(value.split()).as("withToggleActionId keeps the flag").isTrue();
            assertThat(value.toggleActionId()).hasValue("dev.x.tool.show");
        });
        assertThat(spec.withToggleActionId("dev.x.tool.show").withSplit().toggleActionId()).hasValue("dev.x.tool.show");
        // Plugins built against 0.12 and earlier call these two constructors.
        assertThat(PanelSpec.class.getConstructor(String.class, String.class, javax.swing.Icon.class, Anchor.class)).isNotNull();
        assertThat(PanelSpec.class.getConstructor(String.class, String.class, javax.swing.Icon.class, Anchor.class,
            java.util.Optional.class)).isNotNull();
    }

    @Test void windowAndDialogSpecsValidateAndCopyTheSize() {
        var size = new Dimension(640, 480);
        var spec = new WindowSpec("dev.x.tool.manager", "Manager", size, true);
        size.width = 1;
        assertThat(spec.preferredSize()).isEqualTo(new Dimension(640, 480));
        assertThatIllegalArgumentException().isThrownBy(() -> new WindowSpec("nodot", "Manager", new Dimension(1, 1), false));
        assertThatIllegalArgumentException().isThrownBy(() -> new WindowSpec("dev.x.tool.manager", "", new Dimension(1, 1), false));
        assertThatIllegalArgumentException().isThrownBy(() -> new WindowSpec("dev.x.tool.manager", "Manager", new Dimension(0, 10), false));
        WindowOwner owner = new WindowOwner() { };
        assertThat(new DialogSpec("Trust host?", owner, true).owner()).isSameAs(owner);
        assertThatNullPointerException().isThrownBy(() -> new DialogSpec("Trust host?", null, true));
        assertThatIllegalArgumentException().isThrownBy(() -> new DialogSpec(" ", owner, true));
    }
}
