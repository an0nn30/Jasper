package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.ShortcutContext;
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
            context.toolbar().add(ToolbarItem.menu("file", "Tool", List.of("dev.x.tool.run", "dev.x.tool.stop")));
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

    @Test void theHostKeepsEachActionsSpecAndChecksItsShortcutContext() {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> { });
            context.actions().register(ActionSpec.of("dev.x.tool.new", "New").withDefaultBindings("cmd+n", "ctrl+n")
                .withContext(ShortcutContext.panel("dev.x.tool.hosts")), invoked -> { });
            assertThat(host.actionSpec("dev.x.tool.new")).hasValueSatisfying(spec -> {
                assertThat(spec.defaultBinding()).hasValue("cmd+n");
                assertThat(spec.otherDefaultBinding()).hasValue("ctrl+n");
                assertThat(spec.context()).isEqualTo(ShortcutContext.panel("dev.x.tool.hosts"));
            });
            assertThat(host.actionSpec("dev.x.tool.absent")).isEmpty();
            assertThatIllegalArgumentException().isThrownBy(() -> context.actions().register(ActionSpec.of("dev.x.tool.other", "Other")
                .withContext(ShortcutContext.window("dev.y.manager")), invoked -> { }));
        }
    }

    @Test void queuedOppositeVariantsExposeTheirOwnCompleteWorkspaceSnapshot() {
        queuedWorkspaceSnapshots(Variant.LIGHT, Variant.DARK);
    }

    @Test void queuedSameVariantChangesExposeTheirOwnCompleteWorkspaceSnapshot() {
        queuedWorkspaceSnapshots(Variant.DARK, Variant.DARK);
    }

    private static void queuedWorkspaceSnapshots(Variant firstVariant, Variant secondVariant) {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> {});
            var appearance = context.appearance();
            var seen = new ArrayList<List<Object>>();
            appearance.onWorkspaceChanged(event -> seen.add(List.of(event, appearance.workspaceVariant(), appearance.variant(),
                appearance.workspacePalette(), appearance.editorPalette(), appearance.syntaxPalette())));
            var disposed = new java.util.concurrent.atomic.AtomicInteger();
            var closed = appearance.onWorkspaceChanged(event -> disposed.incrementAndGet());
            var initial = appearance.workspacePalette();
            var first = workspaceColors(java.awt.Color.RED);
            var second = workspaceColors(java.awt.Color.BLUE);
            host.setWorkspace(firstVariant, first.palette(), first.editor(), first.syntax());
            host.setWorkspace(secondVariant, second.palette(), second.editor(), second.syntax());
            // Duplicate pending requests are quiet, even though the dispatcher has not published them yet.
            host.setWorkspace(secondVariant, second.palette(), second.editor(), second.syntax());
            var late = new ArrayList<List<Object>>();
            appearance.onWorkspaceChanged(event -> late.add(List.of(event, appearance.workspaceVariant(),
                appearance.workspacePalette(), appearance.editorPalette(), appearance.syntaxPalette())));
            assertThat(appearance.workspacePalette()).isEqualTo(initial);
            java.util.concurrent.CompletableFuture.runAsync(closed::close).join();
            assertThat(seen).isEmpty();
            host.flush();
            assertThat(seen).containsExactly(
                List.of(firstVariant, firstVariant, Variant.DARK, first.palette(), first.editor(), first.syntax()),
                List.of(secondVariant, secondVariant, Variant.DARK, second.palette(), second.editor(), second.syntax()));
            assertThat(late).containsExactly(
                List.of(firstVariant, firstVariant, first.palette(), first.editor(), first.syntax()),
                List.of(secondVariant, secondVariant, second.palette(), second.editor(), second.syntax()));
            assertThat(disposed).hasValue(0);
            assertThat(appearance.workspaceVariant()).isEqualTo(secondVariant);
            assertThat(appearance.workspacePalette()).isEqualTo(second.palette());
            assertThat(appearance.editorPalette()).isEqualTo(second.editor());
            assertThat(appearance.syntaxPalette()).isEqualTo(second.syntax());
        }
    }

    private record WorkspaceColors(dev.jasper.sdk.ui.WorkspacePalette palette,
                                   dev.jasper.sdk.ui.EditorPalette editor, dev.jasper.sdk.ui.SyntaxPalette syntax) {}

    private static WorkspaceColors workspaceColors(java.awt.Color color) {
        var palette = new dev.jasper.sdk.ui.WorkspacePalette(color, color, color, color, color, color, color,
            color, color, color, color, color, color);
        var editor = new dev.jasper.sdk.ui.EditorPalette(color, color, color, color, color, color, color);
        var styles = new java.util.EnumMap<dev.jasper.sdk.ui.SyntaxRole, dev.jasper.sdk.ui.SyntaxStyle>(dev.jasper.sdk.ui.SyntaxRole.class);
        for (var role : dev.jasper.sdk.ui.SyntaxRole.values())
            styles.put(role, new dev.jasper.sdk.ui.SyntaxStyle(color, color.equals(java.awt.Color.RED), color.equals(java.awt.Color.BLUE)));
        return new WorkspaceColors(palette, editor, new dev.jasper.sdk.ui.SyntaxPalette(styles));
    }

    @Test void workspaceSubscriptionsClosedOnWorkersSuppressAlreadyQueuedCallbacks() {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> {});
            var seen = new java.util.concurrent.atomic.AtomicInteger();
            var subscription = context.appearance().onWorkspaceChanged(value -> seen.incrementAndGet());
            host.setVariant(Variant.LIGHT);
            java.util.concurrent.CompletableFuture.runAsync(subscription::close).join();
            host.flush();
            host.setVariant(Variant.DARK); host.flush();
            assertThat(seen).hasValue(0);
        }
    }

    @Test void appearanceAcceptsRealEdtAndCreatingTestThreadButNotWorkers() throws Exception {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> {});
            var appearance = context.appearance();
            var palette = appearance.editorPalette();
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                assertThat(appearance.editorPalette()).isEqualTo(palette);
                assertThat(appearance.workspacePalette()).isNotNull();
                assertThat(appearance.syntaxPalette().styles()).hasSize(16);
            });
            assertThatThrownBy(() -> java.util.concurrent.CompletableFuture.supplyAsync(appearance::editorPalette).join())
                .hasCauseInstanceOf(IllegalStateException.class);
            var seen = new ArrayList<Variant>();
            appearance.onWorkspaceChanged(seen::add);
            host.setVariant(Variant.LIGHT); host.flush();
            assertThat(appearance.workspaceVariant()).isEqualTo(Variant.LIGHT);
            assertThat(seen).containsExactly(Variant.LIGHT);
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
            assertThatIllegalArgumentException().isThrownBy(() -> context.actions().register(ActionSpec.of("dev.x.tool.bad", "Bad").withIcon("missing"),e -> {}));
        }
    }
    @Test void retainsRealDialogContentUntilClose() throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), c -> {});
            var owner = context.terminals().window(host.addTerminalWindow()).orElseThrow();
            var dialog = context.windows().dialog(new dev.jasper.sdk.ui.DialogSpec("Inspect", owner, true));
            var panel = KitFixtures.view(context); dialog.setContent(panel); dialog.show();
            assertThat(host.windowView(INFO.id(), "Inspect")).containsSame((FakeKitView)panel);
            dialog.close(); assertThat(host.windowContent(INFO.id(), "Inspect")).isEmpty();
        }
    }); }

    @Test void exposesTheOwnedViewMountedOnAnOpenSurfaceUntilCloseOrReplacement() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try (var host = new FakePluginHost()) {
                var context = host.start(INFO, Set.of(), Set.of(), c -> {});
                var owner = context.terminals().window(host.addTerminalWindow()).orElseThrow();
                var dialog = context.windows().dialog(new dev.jasper.sdk.ui.DialogSpec("Unlock", owner, true));
                assertThat(host.windowView(INFO.id(), "Unlock")).isEmpty();
                var spec = new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Secret("password", dev.jasper.sdk.ui.ControlState.READY, "", "Password", ""));
                var view = context.kit().create(spec, e -> {});
                dialog.setContent(view); dialog.show();
                assertThat(host.windowView(INFO.id(), "Unlock")).containsSame((FakeKitView) view);
                assertThat(host.windowView("other.plugin", "Unlock")).isEmpty();
                var replacement = KitFixtures.view(context); dialog.setContent(replacement);
                assertThat(host.windowView(INFO.id(), "Unlock")).containsSame((FakeKitView)replacement);
                assertThatThrownBy(view::spec).isInstanceOf(IllegalStateException.class);
                var next = context.kit().create(spec, e -> {}); dialog.setContent(next);
                ((FakeKitView) next).setSecret("password", "fixture".toCharArray());
                dialog.close();
                assertThat(host.windowView(INFO.id(), "Unlock")).isEmpty();
                assertThatThrownBy(next::spec).as("closing the surface disposes its view").isInstanceOf(IllegalStateException.class);
            }
        });
    }
}
