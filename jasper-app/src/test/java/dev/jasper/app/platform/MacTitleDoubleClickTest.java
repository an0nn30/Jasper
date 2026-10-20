package dev.jasper.app.platform;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MacTitleDoubleClickTest {
    @Test void theSystemSettingNamesTheAction() {
        assertThat(MacTitleDoubleClick.parse("Minimize\n")).isEqualTo(MacTitleDoubleClick.Action.MINIMIZE);
        assertThat(MacTitleDoubleClick.parse("None")).isEqualTo(MacTitleDoubleClick.Action.NONE);
        for (String zoom : new String[] {"Maximize", "Fill", "", "something new", null})
            assertThat(MacTitleDoubleClick.parse(zoom)).as("%s", zoom).isEqualTo(MacTitleDoubleClick.Action.ZOOM);
    }
}
