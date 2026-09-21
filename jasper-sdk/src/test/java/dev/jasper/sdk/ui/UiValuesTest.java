package dev.jasper.sdk.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UiValuesTest {
    @Test void actionSpecValidatesAndCopies() {
        var keywords = new ArrayList<>(List.of("connect"));
        ActionSpec spec = ActionSpec.of("dev.x.tool.run", "Run Tool").withKeywords(keywords).withDefaultBinding("cmd+alt+j");
        keywords.add("later");
        assertThat(spec.keywords()).containsExactly("connect");
        assertThat(spec.defaultBinding()).hasValue("cmd+alt+j");
        assertThat(spec.icon()).isEmpty();
        assertThat(ActionSpec.of("dev.x.tool.run", "Run").withDefaultBinding(null).defaultBinding()).isEmpty();
        for (String id : new String[]{"", "Upper.case", "nodot", "sp ace.x"})
            assertThatIllegalArgumentException().as(id).isThrownBy(() -> ActionSpec.of(id, "Title"));
        assertThatIllegalArgumentException().isThrownBy(() -> ActionSpec.of("dev.x.run", " "));
        assertThatNullPointerException().isThrownBy(() ->
            new ActionSpec("dev.x.run", "Run", null, List.of(), Optional.empty()));
    }

    @Test void toolbarItemsAndStatusSpecsValidate() {
        assertThat(ToolbarItem.action("dev.x.run")).isEqualTo(new ToolbarItem.Button("dev.x.run"));
        var ids = new ArrayList<>(List.of("dev.x.a", "dev.x.b"));
        var dropdown = (ToolbarItem.Dropdown) ToolbarItem.menu(new javax.swing.ImageIcon(), "Hosts", ids);
        ids.clear();
        assertThat(dropdown.actionIds()).containsExactly("dev.x.a", "dev.x.b");
        assertThatIllegalArgumentException().isThrownBy(() -> ToolbarItem.menu(new javax.swing.ImageIcon(), " ", List.of("dev.x.a")));
        assertThatIllegalArgumentException().isThrownBy(() -> ToolbarItem.menu(new javax.swing.ImageIcon(), "Hosts", List.of()));
        assertThatNullPointerException().isThrownBy(() -> ToolbarItem.menu(null, "Hosts", List.of("dev.x.a")));
        assertThatNullPointerException().isThrownBy(() -> ToolbarItem.action(null));

        assertThat(new StatusItemSpec("dev.x.lock", Side.RIGHT, 10).side()).isEqualTo(Side.RIGHT);
        assertThatIllegalArgumentException().isThrownBy(() -> new StatusItemSpec("nodot", Side.LEFT, 0));
        assertThatNullPointerException().isThrownBy(() -> new StatusItemSpec("dev.x.lock", null, 0));
    }
}
