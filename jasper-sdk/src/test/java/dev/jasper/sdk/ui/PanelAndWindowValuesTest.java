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
        assertThatNullPointerException().isThrownBy(() -> new PanelSpec("dev.x.tool.hosts", "Hosts", null, Anchor.LEFT));
        assertThatNullPointerException().isThrownBy(() -> new PanelSpec("dev.x.tool.hosts", "Hosts", new ImageIcon(), null));
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
