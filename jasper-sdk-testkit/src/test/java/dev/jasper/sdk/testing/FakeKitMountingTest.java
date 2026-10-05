package dev.jasper.sdk.testing;

import dev.jasper.sdk.*;
import dev.jasper.sdk.settings.*;
import dev.jasper.sdk.ui.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FakeKitMountingTest {
    @Test void ownedTabPinsDuringChooserAndOwnerClosureDiscardsSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try(var host=new FakePluginHost()) {
                var context=host.start(new PluginInfo("test.chooser","Chooser","1.0.0",Set.of()),Set.of(),Set.of(),c -> {});
                var view=context.kit().create(new ViewSpec(new Nodes.Text("body",ControlState.READY,"Document",Nodes.TextRole.BODY)),e -> {});
                var tab=context.windows().openTab(new ContentSpec("doc","Document"),null,view);
                var selected=java.nio.file.Path.of("selected").toAbsolutePath();host.queuePathSelection(List.of(selected));
                host.duringPathSelection(() -> {tab.move(true);assertThat(tab.isSeparate()).isFalse();assertThat(tab.requestClose().join()).isFalse();});
                assertThat(context.windows().chooseFiles(tab,"Choose",Optional.empty())).containsExactly(selected);
                tab.move(true);assertThat(tab.isSeparate()).isTrue();
                host.queuePathSelection(List.of(selected));host.duringPathSelection(tab::close);
                assertThat(context.windows().chooseFiles(tab,"Choose",Optional.empty())).isEmpty();
                assertThatThrownBy(view::spec).isInstanceOf(IllegalStateException.class);
            }
        });
    }
    @Test void settingsFactoryFailureIsContainedAndKitRouteNeverCallsLegacyFactory() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try(var host=new FakePluginHost()) {
                host.start(new PluginInfo("test.failure","Failure","1.0.0",Set.of()),Set.of(),Set.of(),context -> context.settings().page(new SettingsPanel.View(){
                    public JComponent create(SettingsPanelHost h){throw new AssertionError("Legacy factory must not run");}
                    public UiView createView(SettingsPanelHost h){throw new IllegalStateException("Factory failure");}
                }));
                assertThat(host.settingsPanel("test.failure")).isPresent();
                assertThat(host.settingsPanel("test.failure")).isPresent();assertThat(host.failures()).hasSize(1);
                host.closeSettingsPanel("test.failure");
            }
        });
    }
    @Test void settingsDraftEventsApplyCancelAndOwnerClose() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try(var host=new FakePluginHost()){
                var view=new AtomicReference<FakeKitView>();var rows=new AtomicReference<SettingsPanelHost>();
                var size=new AtomicReference<Setting<Integer>>();var calls=new ArrayList<String>();
                host.start(new PluginInfo("test.page","Page","1.0.0",Set.of()),Set.of(),Set.of(),context -> {
                    size.set(context.settings().declare(SettingSpec.of("size",SettingKind.integer(1,10),4).label("Size")));
                    context.settings().page(new SettingsPanel.View(){
                        public UiView createView(SettingsPanelHost h){rows.set(h);var result=(FakeKitView)context.kit().create(new ViewSpec(h.rowNode(size.get())),e -> calls.add(e.id()));view.set(result);return result;}
                        public void apply(){calls.add("apply");}
                        public void reset(){calls.add("reset");}
                        public void dispose(){calls.add("dispose");}
                    });
                });
                host.settingsPanel("test.page");
                view.get().emit(new UiEvent.Edited("settings:size","99"));
                assertThatThrownBy(() -> host.applySettingsPanel("test.page")).isInstanceOf(IllegalStateException.class);
                view.get().emit(new UiEvent.Edited("settings:size","7"));
                assertThat(host.pendingSettings("test.page")).containsEntry("size",7);assertThat(size.get().get()).isEqualTo(4);
                view.get().emit(new UiEvent.Action("plugin"));assertThat(calls).containsExactly("plugin");
                host.applySettingsPanel("test.page");assertThat(size.get().get()).isEqualTo(7);
                rows.get().stage(size.get(),9);
                var row=(Nodes.SettingRow)view.get().spec().root();
                assertThat(((Nodes.Field)((Nodes.Stack)row.control()).children().getFirst()).value()).isEqualTo("9");
                view.get().emit(new UiEvent.Action("settings:size:reset"));assertThat(host.pendingSettings("test.page")).containsEntry("size",4);
                host.closeSettingsPanel("test.page");host.closeSettingsPanel("test.page");
                assertThat(calls).containsExactly("plugin","apply","reset","dispose");assertThat(size.get().get()).isEqualTo(7);
                assertThatThrownBy(() -> rows.get().stage(size.get(),8)).isInstanceOf(IllegalStateException.class);
                host.settingsPanel("test.page");host.stopAll();
                assertThatThrownBy(view.get()::spec).isInstanceOf(IllegalStateException.class);
                assertThat(calls).containsExactly("plugin","apply","reset","dispose","reset","dispose");
            }
        });
    }
    @Test void panelRegistrationOwnsEachViewAndContainsFactoryFailure() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try(var host=new FakePluginHost()){
                var context=host.start(new PluginInfo("test.panel","Panel","1.0.0",Set.of()),Set.of(),Set.of(),c -> {});
                var views=new ArrayList<UiView>();var disposed=new AtomicInteger();
                var registration=context.panels().registerView(new PanelSpec("test.panel.files","Files","folder",Anchor.LEFT).withSplit(),h -> {
                    var view=context.kit().create(new ViewSpec(new Nodes.Custom("node",ControlState.READY,"document",Nodes.CustomKind.EDITOR,"Document")),e -> {});
                    view.registerContent(new CustomContent("document",Nodes.CustomKind.EDITOR,new JTextArea(),disposed::incrementAndGet));views.add(view);return view;
                });
                UUID one=UUID.randomUUID();host.openPanel("test.panel.files",one);host.openPanel("test.panel.files",one);host.openPanel("test.panel.files",UUID.randomUUID());
                assertThat(views).hasSize(2);registration.close();registration.close();assertThat(disposed.get()).isEqualTo(2);
                context.panels().registerView(new PanelSpec("test.panel.broken","Broken","folder",Anchor.LEFT),h -> {throw new IllegalStateException("failure");});
                assertThat(host.openPanel("test.panel.broken",one)).isNull();assertThat(host.failures()).hasSize(1);
            }
        });
    }
}
