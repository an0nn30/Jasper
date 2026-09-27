package dev.jasper.editor.ui;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GoToLinePanelTest {
    @Test void goesToAValidLineAndMarksAnInvalidOne() {
        List<Integer> lines = new ArrayList<>();
        int[] cancelled = {0};
        var panel = new GoToLinePanel(2, 10, lines::add, () -> cancelled[0]++);
        assertThat(panel.field().getText()).isEqualTo("2");
        panel.field().setText("11");
        panel.submit();
        assertThat(lines).isEmpty();
        assertThat(panel.field().getClientProperty("JComponent.outline")).isEqualTo("error");
        panel.field().setText(" 7 ");
        panel.submit();
        assertThat(lines).containsExactly(7);
        assertThat(panel.field().getClientProperty("JComponent.outline")).isNull();
        assertThat(cancelled[0]).isZero();
    }
}
