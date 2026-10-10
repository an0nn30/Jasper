package dev.jasper.sdk.ui;

import dev.jasper.sdk.WindowOwner;
import java.awt.Dimension;
import javax.swing.ImageIcon;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PanelAndWindowValuesTest {
    @Test void panelSpecsValidate() {
        var spec = new PanelSpec("dev.x.tool.hosts", "Hosts", "file", Anchor.LEFT);
        assertThat(spec.defaultAnchor()).isEqualTo(Anchor.LEFT);
        assertThatIllegalArgumentException().isThrownBy(() -> new PanelSpec("nodot", "Hosts", "file", Anchor.LEFT));
        assertThatIllegalArgumentException().isThrownBy(() -> new PanelSpec("dev.x.tool.hosts", " ", "file", Anchor.LEFT));
        assertThatIllegalArgumentException().as("A semantic icon is required")
            .isThrownBy(() -> new PanelSpec("dev.x.tool.hosts", "Hosts", (String) null, Anchor.LEFT))
            .withMessage("Invalid semantic icon name");
        assertThatNullPointerException().isThrownBy(() -> new PanelSpec("dev.x.tool.hosts", "Hosts", "file", null));
        assertThat(spec.toggleActionId()).as("the application registers <id>.toggle").isEmpty();
        var named = spec.withToggleActionId("dev.x.tool.show");
        assertThat(named.toggleActionId()).contains("dev.x.tool.show");
        assertThat(named.id()).isEqualTo(spec.id());
        assertThat(named.withToggleActionId(null).toggleActionId()).isEmpty();
        assertThatNullPointerException().isThrownBy(() -> new PanelSpec("dev.x.tool.hosts", "Hosts", "file", Anchor.LEFT, null, false));
    }

    @Test void aSplitSurvivesTheToggleActionAndSemanticConstructorsLink() throws Exception {
        var spec = new PanelSpec("dev.x.tool.files", "Files", "file", Anchor.LEFT);
        assertThat(spec.split()).as("the first half unless asked").isFalse();
        assertThat(new PanelSpec("dev.x.tool.files", "Files", "file", Anchor.LEFT,
            java.util.Optional.of("dev.x.tool.show"), false).split()).isFalse();
        var split = spec.withSplit();
        assertThat(split.split()).isTrue();
        assertThat(split.defaultAnchor()).isEqualTo(Anchor.LEFT);
        assertThat(split.withToggleActionId("dev.x.tool.show")).satisfies(value -> {
            assertThat(value.split()).as("withToggleActionId keeps the flag").isTrue();
            assertThat(value.toggleActionId()).hasValue("dev.x.tool.show");
        });
        assertThat(spec.withToggleActionId("dev.x.tool.show").withSplit().toggleActionId()).hasValue("dev.x.tool.show");
        // Final semantic constructors.
        assertThat(PanelSpec.class.getConstructor(String.class, String.class, String.class, Anchor.class)).isNotNull();
        assertThat(PanelSpec.class.getConstructor(String.class, String.class, String.class, Anchor.class,
            java.util.Optional.class, boolean.class)).isNotNull();
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
