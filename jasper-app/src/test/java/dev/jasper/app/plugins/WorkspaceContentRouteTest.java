package dev.jasper.app.plugins;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.appearance.ResolvedTheme;

import dev.jasper.app.appearance.ThemeManager;
import dev.jasper.sdk.ui.*;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.theme.ThemeRoute;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Plan 2 Task 9-kit ruling G1/G1b: a plugin's kit view mounted as a workspace content tab takes the workspace theme slot
 * on an opaque workspace surface, and its specialized content follows that slot, under a light chrome and a dark
 * workspace (the mixed chrome/workspace regression); dialogs keep the chrome slot; a move keeps all of it
 * and resamples opacity.
 */
class WorkspaceContentRouteTest {
    static <T extends Component> T named(Component root, String name, Class<T> type) {
        if (type.isInstance(root) && name.equals(root.getName())) return type.cast(root);
        if (root instanceof Container parent) for (Component child : parent.getComponents()) {
            T found = named(child, name, type); if (found != null) return found;
        }
        return null;
    }

    /** The plugin's view of {@code fixture}'s windows, so a dialog can name one as its owner. */
    static HostedTerminals terminals(TerminalFixture fixture, Containment containment) {
        return new HostedTerminals("test.kit", new CapabilityGate("test.kit", java.util.Set.of()), fixture.registry, Runnable::run, () -> true, () -> true,
            new HostedSessions("test.kit", containment, Runnable::run, id -> null, name -> new javax.swing.ImageIcon()));
    }

    static ViewSpec document() {
        return new ViewSpec(new Nodes.Split("tab", ControlState.READY, Nodes.SplitKind.DOCUMENT, List.of(
            new Nodes.Text("banner", ControlState.READY, "notes.txt changed on disk.", Nodes.TextRole.BODY),
            new Nodes.Custom("document", ControlState.READY, "text", Nodes.CustomKind.EDITOR, "Document"))));
    }

    @Test void aContentTabTakesTheWorkspaceSlotOverAnOpaqueSurfaceWhileDialogsKeepChrome() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var chrome = ThemeTestSupport.generated(false); var workspace = ThemeTestSupport.generated(true);
            var mixed = ThemeTestSupport.mixed(chrome, workspace);
            var containment = new Containment(() -> true); var model = new dev.jasper.app.contributions.Contributions();
            var fixture = new TerminalFixture(); var main = fixture.addWindow(); var separate = fixture.addWindow();
            var mainHolder = new JPanel(); var separateHolder = new JPanel();
            model.tabs().addHost(new dev.jasper.app.contributions.WorkspaceHost(main, e -> mainHolder.add(e.component()), e -> mainHolder.remove(e.component()), e -> {}, () -> {}));
            model.tabs().addHost(new dev.jasper.app.contributions.WorkspaceHost(separate, e -> separateHolder.add(e.component()), e -> separateHolder.remove(e.component()), e -> {}, () -> {}));
            model.tabs().windows(new dev.jasper.app.contributions.TabRegistry.Windows(() -> main, tab -> model.tabs().move(tab, separate)));
            var ui = new HostedUi("test.kit", model, containment, Runnable::run, () -> true, () -> true, getClass().getClassLoader(), () -> dev.jasper.sdk.Variant.LIGHT,
                h -> () -> {}, AppContractTest.headlessWindows(), terminals(fixture, containment), () -> mixed, h -> new dev.jasper.app.lifecycle.Subscription(() -> {}),
                new GlassSource(c -> SwingUtilities.isDescendingFrom(c, separateHolder) ? .4 : 1, h -> new dev.jasper.app.lifecycle.Subscription(() -> {})));
            var kit = new HostedKit("test.kit", () -> true, ui::kitThemes, ui.appearance(), containment); ui.kit(kit);
            var disposed = new AtomicInteger();
            var view = kit.create(document(), e -> {});
            var text = new JTextArea("unsaved"); text.setCaretPosition(3); var scroll = new JScrollPane(text);
            view.registerContent(new CustomContent("text", Nodes.CustomKind.EDITOR, scroll, disposed::incrementAndGet));
            KitView component = ((HostedKitView) view).component();
            var banner = named(component, "banner", JLabel.class);
            var chromeText = chrome.semantic().color(dev.jasper.ui.theme.SemanticColor.TEXT_PRIMARY);
            var workspaceText = workspace.semantic().color(dev.jasper.ui.theme.SemanticColor.TEXT_PRIMARY);
            assertThat(chromeText).isNotEqualTo(workspaceText);
            assertThat(component.route()).isEqualTo(ThemeRoute.CHROME);
            assertThat(banner.getForeground()).isEqualTo(chromeText);
            assertThat(scroll.getBackground().getRGB()).as("before mounting, chrome").isEqualTo(chrome.semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_INPUT).getRGB());

            var tab = ui.windows().openTab(new ContentSpec("doc", "Document"), null, view);
            assertThat(component.route()).isEqualTo(ThemeRoute.WORKSPACE);
            assertThat(component.isOpaque()).as("an opaque workspace surface").isTrue();
            assertThat(named(component, "banner", JLabel.class)).as("same control").isSameAs(banner);
            assertThat(banner.getForeground()).isEqualTo(workspaceText);
            assertThat(scroll.getBackground().getRGB()).as("its specialized content follows the slot")
                .isEqualTo(workspace.semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_INPUT).getRGB());
            assertThat(scroll.getForeground()).isEqualTo(workspaceText);
            var image = new java.awt.image.BufferedImage(60, 60, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            component.setSize(60, 60); var g = image.createGraphics(); try { component.paint(g); } finally { g.dispose(); }
            assertThat(image.getRGB(59, 0) >>> 24).as("the surface paints opaque").isEqualTo(255);

            tab.moveToNewWindow();
            assertThat(component.route()).as("a move keeps the slot").isEqualTo(ThemeRoute.WORKSPACE);
            assertThat(scroll.getBackground().getAlpha()).as("and resamples the destination's opacity").isEqualTo(102);
            assertThat(scroll.getBackground().getRGB() & 0xffffff).isEqualTo(workspace.semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_INPUT).getRGB() & 0xffffff);
            assertThat(text.getText()).isEqualTo("unsaved"); assertThat(text.getCaretPosition()).isEqualTo(3); assertThat(disposed.get()).isZero();

            var dialog = ui.windows().dialog(new DialogSpec("Form", tab, false));
            var form = kit.create(new ViewSpec(new Nodes.Text("label", ControlState.READY, "Name", Nodes.TextRole.BODY)), e -> {});
            dialog.setContent(form);
            KitView formComponent = ((HostedKitView) form).component();
            assertThat(formComponent.route()).as("dialogs keep chrome").isEqualTo(ThemeRoute.CHROME);
            assertThat(formComponent.isOpaque()).isFalse();
            assertThat(named(formComponent, "label", JLabel.class).getForeground()).isEqualTo(chromeText);

            tab.close(); assertThat(disposed.get()).isEqualTo(1);
            dialog.close(); ui.closeAll(); kit.close();
        });
    }

    /** A mixed light-chrome, dark-workspace host whose main content host adds the component and then fails. */
    @Test void aFailedPublicationReturnsTheViewAndItsContentToChromeAndTheCaller() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var chrome = ThemeTestSupport.generated(false); var workspace = ThemeTestSupport.generated(true);
            var mixed = ThemeTestSupport.mixed(chrome, workspace);
            var containment = new Containment(() -> true); var model = new dev.jasper.app.contributions.Contributions();
            var fixture = new TerminalFixture(); var main = fixture.addWindow(); var holder = new JPanel();
            var terminals = terminals(fixture, containment);
            model.tabs().addHost(new dev.jasper.app.contributions.WorkspaceHost(main,
                e -> { holder.add(e.component()); holder.remove(e.component()); throw new IllegalStateException("mount failed"); }, e -> {}, e -> {}, () -> {}));
            model.tabs().windows(new dev.jasper.app.contributions.TabRegistry.Windows(() -> main, tab -> { }));
            var ui = new HostedUi("test.kit", model, containment, Runnable::run, () -> true, () -> true, getClass().getClassLoader(), () -> dev.jasper.sdk.Variant.LIGHT,
                h -> () -> {}, AppContractTest.headlessWindows(), terminals, () -> mixed, h -> new dev.jasper.app.lifecycle.Subscription(() -> {}), GlassSource.NONE);
            var kit = new HostedKit("test.kit", () -> true, ui::kitThemes, ui.appearance(), containment); ui.kit(kit);
            var view = kit.create(document(), e -> {});
            var scroll = new JScrollPane(new JTextArea("kept"));
            view.registerContent(new CustomContent("text", Nodes.CustomKind.EDITOR, scroll, () -> {}));
            var chromeInput = chrome.semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_INPUT).getRGB();
            assertThatThrownBy(() -> ui.windows().openTab(new ContentSpec("doc", "Document"), null, view)).isInstanceOf(IllegalStateException.class);
            KitView component = ((HostedKitView) view).component();
            assertThat(component.route()).isEqualTo(ThemeRoute.CHROME); assertThat(component.isOpaque()).isFalse();
            assertThat(scroll.getBackground().getRGB()).as("its content is back on chrome without waiting for a reparent").isEqualTo(chromeInput);
            // The caller can still mount the refused view elsewhere.
            var dialog = ui.windows().dialog(new DialogSpec("Form", terminals.window(main).orElseThrow(), false));
            dialog.setContent(view);
            assertThat(component.route()).as("on a dialog, still chrome").isEqualTo(ThemeRoute.CHROME);
            dialog.close(); ui.closeAll(); kit.close();
        });
    }

    @Test void aRouteChangeAloneRefreshesRegisteredContent() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var chrome = ThemeTestSupport.generated(false); var workspace = ThemeTestSupport.generated(true);
            var mixed = ThemeTestSupport.mixed(chrome, workspace);
            var containment = new Containment(() -> true);
            var ui = new HostedUi("test.kit", new dev.jasper.app.contributions.Contributions(), containment, Runnable::run, () -> true, () -> true, getClass().getClassLoader(),
                () -> dev.jasper.sdk.Variant.LIGHT, h -> () -> {}, AppContractTest.headlessWindows(), null, () -> mixed, h -> new dev.jasper.app.lifecycle.Subscription(() -> {}), GlassSource.NONE);
            var kit = new HostedKit("test.kit", () -> true, ui::kitThemes, ui.appearance(), containment); ui.kit(kit);
            var view = (HostedKitView) kit.create(document(), e -> {});
            var scroll = new JScrollPane(new JTextArea("kept"));
            view.registerContent(new CustomContent("text", Nodes.CustomKind.EDITOR, scroll, () -> {}));
            var parent = new JPanel(); parent.add(view.component());
            view.route(ThemeRoute.WORKSPACE, true);
            assertThat(scroll.getBackground().getRGB()).isEqualTo(workspace.semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_INPUT).getRGB());
            assertThat(scroll.getForeground()).isEqualTo(workspace.semantic().color(dev.jasper.ui.theme.SemanticColor.TEXT_PRIMARY));
            view.route(ThemeRoute.CHROME, false);
            assertThat(scroll.getBackground().getRGB()).isEqualTo(chrome.semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_INPUT).getRGB());
            view.close(); ui.closeAll(); kit.close();
        });
    }
}
