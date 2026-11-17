package dev.jasper.ui.swing;

import java.awt.font.FontRenderContext;
import javax.swing.JComponent;
import static org.assertj.core.api.Assertions.assertThat;

/** Independent constraints for compact controls: catalog floor, a padded rendered font line and no spare height. */
final class CompactTextSizing {
    private CompactTextSizing() { }

    static int assertHeight(JComponent owner, JComponent text, int floor, int padding) {
        int height = owner.getHeight();
        int line = (int) Math.ceil(text.getFont().getLineMetrics("Ag", new FontRenderContext(null, true, true)).getHeight());
        assertThat(height).as("%s keeps its catalog height floor", owner.getName()).isGreaterThanOrEqualTo(floor);
        assertThat(height - padding).as("%s fits its rendered font line plus padding", owner.getName()).isGreaterThanOrEqualTo(line);
        assertThat(height - 1 < floor || height - 1 - padding < line)
            .as("%s has no unnecessary height: actual=%s floor=%s line=%s padding=%s font=%s", text.getName(), height, floor, line, padding, text.getFont()).isTrue();
        return height;
    }
}
