package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.Side;
import dev.jasper.sdk.ui.StandardMenu;
import dev.jasper.sdk.ui.StatusItemSpec;
import dev.jasper.sdk.ui.ToolbarItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FakeUiTest {
    private static final PluginInfo INFO = new PluginInfo("dev.x.tool", "Tool", "1.0.0", Set.of());

    @Test void recordsAndRendersEveryContribution() {
        try (var host = new FakePluginHost()) {
            List<String> ran = new ArrayList<>();
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> { });
            var run = context.actions().register(ActionSpec.of("dev.x.tool.run", "Run"),
                invoked -> ran.add(invoked.window().id() + "/" + invoked.pane().isPresent()));
            context.actions().register(ActionSpec.of("dev.x.tool.stop", "Stop"), invoked -> { });
            context.toolbar().add(ToolbarItem.action("dev.x.tool.run"));
            context.toolbar().add(ToolbarItem.menu(new javax.swing.ImageIcon(), "Tool", List.of("dev.x.tool.run", "dev.x.tool.stop")));
            var view = context.menus().standard(StandardMenu.VIEW);
            view.add("dev.x.tool.run");
            view.addSeparator();
            view.submenu("More").add("dev.x.tool.stop");
            context.menus().standard(StandardMenu.VIEW).add("dev.x.tool.stop");
            context.menus().create("dev.x.tool.menu", "Tool").add("dev.x.tool.run");
            context.menus().terminalContext().add("dev.x.tool.stop");
            var late = context.statusBar().add(new StatusItemSpec("dev.x.tool.late", Side.RIGHT, 20));
            var early = context.statusBar().add(new StatusItemSpec("dev.x.tool.early", Side.RIGHT, 10));
            late.setText("Late");
            early.setText("Early");
            early.setTooltip("tip");
            early.setAction("dev.x.tool.run");

            assertThat(host.actions()).containsExactly("dev.x.tool.run|Run|true", "dev.x.tool.stop|Stop|true");
            assertThat(host.toolbar()).containsExactly("button:dev.x.tool.run", "menu:Tool:dev.x.tool.run,dev.x.tool.stop");
            assertThat(host.menu("VIEW")).containsExactly("item:dev.x.tool.run", "---", "submenu:More", "  item:dev.x.tool.stop",
                "===", "item:dev.x.tool.stop");
            assertThat(host.menu("top:dev.x.tool.menu")).containsExactly("item:dev.x.tool.run");
            assertThat(host.menu("context")).containsExactly("item:dev.x.tool.stop");
            assertThat(host.menu("FILE")).isEmpty();
            assertThat(host.status()).containsExactly("dev.x.tool.early|RIGHT|Early|tip|dev.x.tool.run", "dev.x.tool.late|RIGHT|Late||");

            UUID window = UUID.randomUUID();
            assertThat(host.invoke("dev.x.tool.run", window, UUID.randomUUID())).isTrue();
            run.setEnabled(false);
            assertThat(host.invoke("dev.x.tool.run", window, null)).isFalse();
            assertThat(host.invoke("dev.x.tool.absent", window, null)).isFalse();
            assertThat(ran).containsExactly(window + "/true");

            run.close();
            assertThat(host.toolbar()).containsExactly("menu:Tool:dev.x.tool.stop");
            assertThat(host.menu("VIEW")).containsExactly("---", "submenu:More", "  item:dev.x.tool.stop", "===", "item:dev.x.tool.stop");
            assertThat(host.menu("top:dev.x.tool.menu")).isEmpty();
        }
    }

    @Test void appearanceFollowsTheHostAndIconsComeFromThePluginsLoader() {
        try (var host = new FakePluginHost()) {
            List<Variant> seen = new ArrayList<>();
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> plugin.appearance().onChanged(seen::add));
            assertThat(context.appearance().variant()).isEqualTo(Variant.DARK);
            host.setVariant(Variant.LIGHT);
            host.flush();
            assertThat(context.appearance().variant()).isEqualTo(Variant.LIGHT);
            assertThat(seen).containsExactly(Variant.LIGHT);
            assertThatIllegalArgumentException().isThrownBy(() -> context.appearance().icon("no/such/icon.svg"));
        }
    }
}
