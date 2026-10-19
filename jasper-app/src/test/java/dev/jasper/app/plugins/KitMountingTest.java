package dev.jasper.app.plugins;

import dev.jasper.sdk.ui.*;
import java.awt.Dimension;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class KitMountingTest {
    private static ViewSpec toggle(boolean checked) {
        return new ViewSpec(new Nodes.Toggle("motion",ControlState.READY,"Motion",checked,"motion",false));
    }
    @Test void duplicateTypedOpenRecoversStrandedDocumentWithoutConsumingIncomingView() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var containment=new Containment(() -> true);var model=new dev.jasper.app.contributions.Contributions();
            var fixture=new TerminalFixture();var main=fixture.addWindow();var separate=fixture.addWindow();
            var failMain=new java.util.concurrent.atomic.AtomicBoolean();var holder=new JPanel();
            model.tabs().addHost(new dev.jasper.app.contributions.WorkspaceHost(main, e -> {if(failMain.get())throw new IllegalStateException("recovery failed");holder.add(e.component());},e -> holder.remove(e.component()),e -> {},() -> {}));
            model.tabs().addHost(new dev.jasper.app.contributions.WorkspaceHost(separate, e -> {throw new IllegalStateException("destination failed");},e -> {},e -> {},() -> {}));
            model.tabs().windows(new dev.jasper.app.contributions.TabRegistry.Windows(() -> main, main::equals, tab -> model.tabs().move(tab, separate)));
            var terminals=new HostedTerminals("test.kit",new CapabilityGate("test.kit",java.util.Set.of()),fixture.registry,Runnable::run,() -> true,() -> true,new HostedSessions("test.kit",containment,Runnable::run,id -> null, name -> new javax.swing.ImageIcon()));
            var ui=new HostedUi("test.kit",model,containment,Runnable::run,() -> true,() -> true,getClass().getClassLoader(),() -> dev.jasper.sdk.Variant.DARK,h -> () -> {},AppContractTest.headlessWindows(),terminals);
            var kit=new HostedKit("test.kit",() -> true,ui::kitThemes,ui.appearance(),containment);ui.kit(kit);
            var disposed=new AtomicInteger();var original=kit.create(HostedKitTest.custom(Nodes.CustomKind.EDITOR,"doc"),e -> {});
            var editor=new JTextArea("unsaved");var document=editor.getDocument();original.registerContent(new CustomContent("doc",Nodes.CustomKind.EDITOR,editor,disposed::incrementAndGet));
            var spec=new ContentSpec("doc","Document");var tab=ui.windows().openTab(spec,null,original);var entry=model.tabs().hosted().getFirst();
            failMain.set(true);assertThatThrownBy(() -> tab.moveToNewWindow()).isInstanceOf(IllegalStateException.class).hasMessage("destination failed");
            assertThat(entry.window()).isNull();assertThat(entry.closed()).isFalse();failMain.set(false);
            var incoming=kit.create(new ViewSpec(new Nodes.Text("body",ControlState.READY,"Unused",Nodes.TextRole.BODY)),e -> {});
            assertThat(ui.windows().openTab(spec,null,incoming)).isSameAs(tab);
            assertThat(entry.window()).isEqualTo(main);assertThat(holder.getComponent(0)).isSameAs(original.component());
            assertThat(editor.getDocument()).isSameAs(document);assertThat(disposed.get()).isZero();
            var window=ui.windows().create(new WindowSpec("test.kit.extra","Extra",new Dimension(400,300),false));window.setContent(incoming);
            tab.close();assertThat(disposed.get()).isEqualTo(1);window.close();ui.closeAll();kit.close();
        });
    }

    @Test void livePolicySettlesActiveNativeAndHostedEffectsAndDisablesFutureEffects() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try(var themes=dev.jasper.app.appearance.ThemeTestSupport.controller(true)) {
                var kit=new HostedKit("test.kit",() -> true,() -> themes.current().pair(),null,new Containment(() -> true),themes::animationsEnabled);
                var hosted=kit.create(toggle(false),e -> {});
                try(var nativeView=new dev.jasper.ui.swing.KitView(themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME),KitValues.toModule(toggle(false)),e -> {})) {
                    hosted.update(toggle(true));nativeView.update(KitValues.toModule(toggle(true)));
                    for(var view:java.util.List.of(hosted.component(),nativeView)) {
                        assertThat(dev.jasper.ui.swing.KitMotionProbe.running(view,"motion")).isTrue();
                        assertThat(dev.jasper.ui.swing.KitMotionProbe.position(view,"motion")).isLessThan(1);
                    }
                    themes.configureAnimations(false);
                    for(var view:java.util.List.of(hosted.component(),nativeView)) {
                        dev.jasper.ui.swing.KitMotionProbe.frame(view,"motion");
                        assertThat(dev.jasper.ui.swing.KitMotionProbe.running(view,"motion")).isFalse();
                        assertThat(dev.jasper.ui.swing.KitMotionProbe.position(view,"motion")).isEqualTo(1);
                    }
                    hosted.update(toggle(false));nativeView.update(KitValues.toModule(toggle(false)));
                    for(var view:java.util.List.of(hosted.component(),nativeView)) {
                        assertThat(dev.jasper.ui.swing.KitMotionProbe.running(view,"motion")).isFalse();
                        assertThat(dev.jasper.ui.swing.KitMotionProbe.position(view,"motion")).isZero();
                    }
                    themes.configureAnimations(true);hosted.update(toggle(true));
                    assertThat(dev.jasper.ui.swing.KitMotionProbe.running(hosted.component(),"motion")).isTrue();
                }finally{kit.close();}
            }
        });
    }
    @Test void customDocumentReparentResamplesOpacityWithoutRecreation() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var containment=new Containment(() -> true);var current=dev.jasper.app.appearance.ThemeTestSupport.controller(true);
            var left=new JPanel();var right=new JPanel();
            var ui=new HostedUi("test.kit",new dev.jasper.app.contributions.Contributions(),containment,Runnable::run,() -> true,() -> true,getClass().getClassLoader(),() -> dev.jasper.sdk.Variant.DARK,h -> () -> {},AppContractTest.headlessWindows(),null,current::current,h -> current.subscribe((value,changed) -> h.accept(value)),new GlassSource(c -> SwingUtilities.isDescendingFrom(c,right) ? .4 : 1,h -> new dev.jasper.app.lifecycle.Subscription(() -> {})));
            var kit=new HostedKit("test.kit",() -> true,ui::kitThemes,ui.appearance(),containment);ui.kit(kit);
            var editor=new JTextArea("same document");editor.setCaretPosition(4);var document=editor.getDocument();var scroll=new JScrollPane(editor);var disposed=new AtomicInteger();
            var view=kit.create(HostedKitTest.custom(Nodes.CustomKind.EDITOR,"doc"),e -> {});
            view.registerContent(new CustomContent("doc",Nodes.CustomKind.EDITOR,scroll,disposed::incrementAndGet));
            left.add(view.component());assertThat(scroll.getBackground().getAlpha()).isEqualTo(255);
            right.add(view.component());assertThat(scroll.getBackground().getAlpha()).isEqualTo(102);
            assertThat(scroll.getForeground()).isEqualTo(ui.appearance().color(SemanticColor.TEXT_PRIMARY));
            assertThat(ui.appearance().editorPalette()).isEqualTo(HostedPalettes.editor(current.current().workspace()));
            assertThat(editor.getDocument()).isSameAs(document);assertThat(editor.getCaretPosition()).isEqualTo(4);assertThat(disposed.get()).isZero();
            left.add(view.component());assertThat(scroll.getBackground().getAlpha()).isEqualTo(255);
            view.close();kit.close();ui.closeAll();current.close();assertThat(disposed.get()).isEqualTo(1);
        });
    }

    @Test void panelSiteClosureDisposesOnceAndSemanticIconRetainsPlacement() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var failures=new java.util.ArrayList<PluginRuntime.Failure>();
            var containment=new Containment(java.awt.EventQueue::isDispatchThread,failures::add);
            var model=new dev.jasper.app.contributions.Contributions();
            var fixture=new TerminalFixture();
            var terminals=new HostedTerminals("test.kit",new CapabilityGate("test.kit",java.util.Set.of()),fixture.registry,Runnable::run,() -> true,() -> true,new HostedSessions("test.kit",containment,Runnable::run,id -> null, name -> new javax.swing.ImageIcon()));
            var ui=new HostedUi("test.kit",model,containment,Runnable::run,() -> true,() -> true,getClass().getClassLoader(),() -> dev.jasper.sdk.Variant.DARK,h -> () -> {},AppContractTest.headlessWindows(),terminals);
            var kit=new HostedKit("test.kit",() -> true,ui::kitThemes,ui.appearance(),containment);ui.kit(kit);
            var disposed=new AtomicInteger();
            var spec=new PanelSpec("test.kit.files","Files","folder",Anchor.LEFT).withSplit();
            var registration=ui.panels().register(spec,h -> {
                var view=kit.create(HostedKitTest.custom(Nodes.CustomKind.EDITOR,"doc"),e -> {});
                view.registerContent(new CustomContent("doc",Nodes.CustomKind.EDITOR,new JTextArea(),disposed::incrementAndGet));return view;
            });
            var entry=model.panels().getFirst();assertThat(entry.defaultSlot().split()).isTrue();assertThat(entry.icon()).isNotNull();
            var site=new dev.jasper.app.contributions.PanelSite(fixture.addWindow(),() -> {},() -> {},() -> true);
            assertThat(entry.factory().apply(site)).isInstanceOf(dev.jasper.ui.swing.KitView.class);
            site.notifyClosed();site.notifyClosed();registration.close();assertThat(disposed.get()).isEqualTo(1);
            ui.panels().register(new PanelSpec("test.kit.bad","Bad","folder",Anchor.LEFT),h -> {throw new IllegalStateException("failure");});
            assertThat(model.panels().getFirst().factory().apply(site)).isNull();assertThat(failures).hasSize(1);
            ui.closeAll();kit.close();
        });
    }

    /**
     * A kit (ViewSpec) panel's PanelHost.setHeader reaches its window as application header metadata, with the panel's
     * own kit header available to borrow; actions are contained and become inert when the instance closes.
     */
    @Test void aViewPanelsHeaderReachesTheWindowContainedAndInertAfterClose() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var failures=new java.util.ArrayList<PluginRuntime.Failure>();
            var containment=new Containment(java.awt.EventQueue::isDispatchThread,failures::add);
            var model=new dev.jasper.app.contributions.Contributions();
            var fixture=new TerminalFixture();
            var terminals=new HostedTerminals("test.kit",new CapabilityGate("test.kit",java.util.Set.of()),fixture.registry,Runnable::run,() -> true,() -> true,new HostedSessions("test.kit",containment,Runnable::run,id -> null, name -> new javax.swing.ImageIcon()));
            var ui=new HostedUi("test.kit",model,containment,Runnable::run,() -> true,() -> true,getClass().getClassLoader(),() -> dev.jasper.sdk.Variant.DARK,h -> () -> {},AppContractTest.headlessWindows(),terminals);
            var kit=new HostedKit("test.kit",() -> true,ui::kitThemes,ui.appearance(),containment);ui.kit(kit);
            var calls=new java.util.ArrayList<String>();
            var icon="file";
            ui.panels().register(new PanelSpec("test.kit.hosts","Hosts","folder",Anchor.LEFT),host -> {
                host.setHeader(new PanelHeader("3",true,java.util.List.of(
                    new PanelHeader.Action("New session",icon,() -> calls.add("new")),
                    new PanelHeader.Action("Broken",icon,() -> {throw new IllegalStateException("header failed");}))));
                return kit.create(new ViewSpec(new Nodes.Panel("hosts",ControlState.READY,
                    new Nodes.Header("hosts-header",ControlState.READY,"Hosts",java.util.OptionalInt.empty(),java.util.Optional.empty(),java.util.Optional.empty(),java.util.List.of()),
                    java.util.Optional.empty(),new Nodes.Text("body",ControlState.READY,"Body",Nodes.TextRole.BODY),java.util.Optional.empty())),e -> {});
            });
            var headers=new java.util.ArrayList<dev.jasper.app.contributions.PanelHeaderState>();
            var site=new dev.jasper.app.contributions.PanelSite(fixture.addWindow(),() -> {},() -> {},() -> true,headers::add);
            var component=model.panels().getFirst().factory().apply(site);
            assertThat(component).isInstanceOf(dev.jasper.ui.swing.KitView.class);
            var view=(dev.jasper.ui.swing.KitView)component;
            assertThat(view.spec().root()).as("a kit Panel root: the window borrows its header").isInstanceOf(dev.jasper.ui.model.Nodes.Panel.class);
            assertThat(view.panelHeader("hosts")).isNotNull();
            var header=headers.getLast();
            assertThat(header.detail()).isEqualTo("3");
            assertThat(header.active()).isTrue();
            assertThat(header.actions()).extracting(dev.jasper.app.contributions.PanelHeaderState.Action::label).containsExactly("New session","Broken");
            assertThat(header.actions().getFirst().icon().getIconWidth()).isEqualTo(16);
            header.actions().forEach(action -> action.invoke().run());
            assertThat(calls).containsExactly("new");
            assertThat(failures).as("a failing header action is contained").hasSize(1);
            site.notifyClosed();
            assertThat(headers.getLast()).isEqualTo(dev.jasper.app.contributions.PanelHeaderState.EMPTY);
            assertThat(view.isClosed()).isTrue();
            header.actions().forEach(action -> action.invoke().run());
            assertThat(calls).containsExactly("new");
            assertThat(failures).hasSize(1);
            ui.closeAll();kit.close();
        });
    }

    @Test void singletonReplacementRejectsForeignAndClosesOwnedContentExactlyOnce() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var containment = new Containment(java.awt.EventQueue::isDispatchThread);
            var windows = AppContractTest.headlessWindows();
            var ui = new HostedUi("test.kit", new dev.jasper.app.contributions.Contributions(), containment,
                Runnable::run, java.awt.EventQueue::isDispatchThread, () -> true, getClass().getClassLoader(),
                () -> dev.jasper.sdk.Variant.DARK, h -> () -> {}, windows, null);
            var kit = new HostedKit("test.kit", () -> true, ui::kitThemes, ui.appearance(), containment);
            ui.kit(kit);
            var foreign = new HostedKit("other.kit", () -> true, ui::kitThemes, null, containment);
            var spec = new WindowSpec("test.kit.window", "Document", new Dimension(400, 300), true);
            var window = ui.windows().create(spec);
            var disposed = new AtomicInteger();
            var first = kit.create(HostedKitTest.custom(Nodes.CustomKind.EDITOR, "document"), e -> {});
            first.registerContent(new CustomContent("document", Nodes.CustomKind.EDITOR, new JTextArea("stable"), disposed::incrementAndGet));
            window.setContent(first);
            assertThat(ui.windows().create(spec)).isSameAs(window);
            var alien = foreign.create(first.spec(), e -> {});
            assertThatThrownBy(() -> window.setContent(alien)).isInstanceOf(IllegalArgumentException.class);
            assertThat(disposed.get()).isZero();
            window.setContent(first);
            assertThat(disposed.get()).isZero();
            var second = kit.create(new ViewSpec(new Nodes.Text("body", ControlState.READY, "Next", Nodes.TextRole.BODY)), e -> {});
            window.setContent(second);
            assertThat(disposed.get()).isEqualTo(1);
            assertThatThrownBy(first::spec).isInstanceOf(IllegalStateException.class);
            window.close(); window.close(); ui.closeAll(); kit.close(); foreign.close();
            assertThat(disposed.get()).isEqualTo(1);
            assertThat(kit.viewCount()).isZero();
        });
    }
    /** Plan 2 Task 7 host contract: closing a dialog that holds a Vault-style password prompt. */
    @Test void closingAVaultPromptDialogDisposesItsViewAndErasesTheTypedSecretBeforeOwnerCleanup() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var containment = new Containment(java.awt.EventQueue::isDispatchThread);
            var windows = AppContractTest.headlessWindows();
            var ui = new HostedUi("test.kit", new dev.jasper.app.contributions.Contributions(), containment,
                Runnable::run, java.awt.EventQueue::isDispatchThread, () -> true, getClass().getClassLoader(),
                () -> dev.jasper.sdk.Variant.DARK, h -> () -> {}, windows, null);
            var kit = new HostedKit("test.kit", () -> true, ui::kitThemes, ui.appearance(), containment);
            ui.kit(kit);
            var owner = ui.windows().create(new WindowSpec("test.kit.owner", "Owner", new Dimension(400, 300), true));
            owner.show();
            var dialog = ui.windows().dialog(new DialogSpec("Unlock Vault", owner, true));
            var events = new java.util.ArrayList<UiEvent>();
            var prompt = new ViewSpec(new Nodes.Form("form", ControlState.READY, Nodes.FormKind.FULL, "Unlock", java.util.Optional.empty(),
                java.util.List.of(new Nodes.FormRow("password.row", ControlState.READY, "Master password:", "",
                    new Nodes.Secret("password", ControlState.READY, "", "Master password", "master"), java.util.Optional.empty())),
                java.util.Optional.empty(), java.util.List.of(
                    new Nodes.Button("cancel", ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, "cancel", ""),
                    new Nodes.Button("submit", ControlState.READY, "Unlock", "", Nodes.ButtonKind.PRIMARY, "submit", "")), "cancel"));
            var view = kit.create(prompt, events::add);
            dialog.setContent(view);
            dialog.show();
            var field = HostedKitContractTest.input(((HostedKitView) view).component(), "password.input");
            field.setText("synthetic-prompt-secret");
            var segment = new javax.swing.text.Segment();
            try { field.getDocument().getText(0, field.getDocument().getLength(), segment); }
            catch (javax.swing.text.BadLocationException impossible) { throw new AssertionError(impossible); }
            char[] storage = segment.array;
            assertThat(events).containsExactly(new UiEvent.SecretChanged("password"));
            assertThat(view.spec().toString() + events).doesNotContain("synthetic-prompt-secret");
            boolean[] disposedFirst = {false};
            dialog.onClosed(() -> {
                // The owner's cleanup runs after the surface disposed the view: nothing secret is left to reach.
                try { view.spec(); } catch (IllegalStateException closed) { disposedFirst[0] = true; }
            });
            var surface = windows.open().stream().filter(s -> s.title().equals("Unlock Vault")).findFirst().orElseThrow();
            assertThat(surface.requestClose()).isTrue();
            assertThat(disposedFirst[0]).as("the view is disposed before owner close handlers run").isTrue();
            assertThat(storage).as("the typed secret is erased with the view").containsOnly((char) 0);
            assertThat(field.getDocument().getLength()).isZero();
            assertThatThrownBy(() -> view.withSecret("password", chars -> {})).isInstanceOf(IllegalStateException.class);
            assertThat(kit.viewCount()).isZero();
            owner.close(); ui.closeAll(); kit.close();
        });
    }
    @Test void nativeAndHostedContextsUseLiveMotionPolicy() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var themes = dev.jasper.app.appearance.ThemeTestSupport.controller(true)) {
                var nativeContext = themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME);
                var kit = new HostedKit("test.kit", () -> true, () -> themes.current().pair(), null,
                    new Containment(java.awt.EventQueue::isDispatchThread), themes::animationsEnabled);
                var hosted = kit.create(new ViewSpec(new Nodes.Progress("busy", ControlState.READY, -1, "Busy")), e -> {});
                assertThat(nativeContext.animationsEnabled().getAsBoolean()).isTrue();
                themes.configureAnimations(false);
                assertThat(nativeContext.animationsEnabled().getAsBoolean()).isFalse();
                assertThat(kit.animationsEnabled()).isFalse();
                themes.configureAnimations(true);
                assertThat(nativeContext.animationsEnabled().getAsBoolean()).isTrue();
                assertThat(kit.animationsEnabled()).isTrue();
                assertThat(hosted.spec().root().id()).isEqualTo("busy");
                kit.close();
            }
        });
    }
}
