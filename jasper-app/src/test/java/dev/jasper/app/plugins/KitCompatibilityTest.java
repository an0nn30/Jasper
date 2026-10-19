package dev.jasper.app.plugins;

import dev.jasper.app.testsupport.PluginJars;
import dev.jasper.sdk.JasperSdk;
import dev.jasper.sdk.plugin.Plugin;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class KitCompatibilityTest {
    @TempDir Path root;
    private PluginCandidate candidate(String range, Map<String,byte[]> entries, boolean multi) throws Exception {
        var manifest=new Manifest();manifest.getMainAttributes().putValue("Manifest-Version","1.0");
        if(multi)manifest.getMainAttributes().putValue("Multi-Release","true");
        var directory=Files.createDirectories(root.resolve(UUID.randomUUID().toString()));
        try(var out=new JarOutputStream(Files.newOutputStream(directory.resolve("plugin.jar")),manifest)) {
            out.putNextEntry(new JarEntry("plugin.toml"));out.write(PluginJars.descriptor("test.kit","1.0.0","test.kit.Main").replace(">=0.1",range).getBytes(java.nio.charset.StandardCharsets.UTF_8));out.closeEntry();
            for(var entry:entries.entrySet()){out.putNextEntry(new JarEntry(entry.getKey()));out.write(entry.getValue());out.closeEntry();}
        }
        return PluginDiscovery.single(directory,PluginCandidate.Origin.DEV,new ArrayList<>()).orElseThrow();
    }
    private byte[] reference(String owner,String name,String descriptor) {
        return ClassFile.of().build(ClassDesc.of("test.kit.Probe"),builder -> {
            builder.constantPool().interfaceMethodRefEntry(ClassDesc.of(owner),name,MethodTypeDesc.ofDescriptor(descriptor));
        });
    }
    /** A class whose constant pool reads one enum constant, as {@code getstatic} does. */
    private byte[] constant(String owner,String name) {
        return ClassFile.of().build(ClassDesc.of("test.kit.Probe"),builder -> builder.constantPool().fieldRefEntry(ClassDesc.of(owner),name,ClassDesc.of(owner)));
    }
    private void rejects(byte[] bytes,String expected) throws Exception { rejects(bytes,expected,"0.15"); }
    private void rejects(byte[] bytes,String expected,String removedIn) throws Exception {
        var candidate=candidate(">=0.1",Map.of("test/kit/Probe.class",bytes),false);
        assertThat(PluginResolver.resolve(List.of(candidate),Map.of(),Version.parse(JasperSdk.VERSION),false).load()).containsExactly(candidate);
        assertThatThrownBy(() -> PluginLoader.load(candidate,Map.of(),Plugin.class.getClassLoader()))
            .isInstanceOf(PluginLoader.LoadFailure.class).hasMessageContaining("; rebuild it for SDK "+removedIn).hasMessageContaining(expected).hasMessageContaining("Probe.class");
    }
    private static final List<List<String>> RENAMED=List.of(List.of("ChoiceKind","SEGMENTED"),List.of("ButtonPresentation","CIRCULAR"),
        List.of("FormKind","HERO"),List.of("FormKind","HERO_COMPACT"),List.of("LayoutRole","HERO"),List.of("TextRole","HERO_TITLE"),List.of("IconSize","HERO"));
    private static final List<List<String>> RENAMED_TO=List.of(List.of("ChoiceKind","EXCLUSIVE"),List.of("ButtonPresentation","HELP"),
        List.of("FormKind","PROMPT"),List.of("FormKind","PROMPT_COMPACT"),List.of("LayoutRole","PROMPT_HEADER"),List.of("TextRole","PROMPT_TITLE"),List.of("IconSize","PROMPT"));
    /** Spec 8.1 and 15: a plugin built on 0.17 that claims a 0.18 range and still reads a renamed constant is refused at load. */
    @Test void aConstantSdkZeroEighteenRenamedIsRefusedWithTheVersionThatRemovedIt() throws Exception {
        for(var renamed:RENAMED)
            rejects(constant("dev.jasper.sdk.ui.Nodes$"+renamed.get(0),renamed.get(1)),"dev/jasper/sdk/ui/Nodes$"+renamed.get(0)+"#"+renamed.get(1)+"L","0.18");
        var candidate=candidate(">=0.18.0, <0.19",Map.of("test/kit/Probe.class",constant("dev.jasper.sdk.ui.Nodes$ChoiceKind","SEGMENTED")),false);
        assertThatThrownBy(() -> PluginLoader.load(candidate,Map.of(),Plugin.class.getClassLoader())).hasMessage("plugin.jar!test/kit/Probe.class references removed SDK member "
            +"dev/jasper/sdk/ui/Nodes$ChoiceKind#SEGMENTEDLdev/jasper/sdk/ui/Nodes$ChoiceKind;; rebuild it for SDK 0.18");
    }
    @Test void theNamesSdkZeroEighteenGaveThemAreAdmitted() throws Exception {
        for(var renamed:RENAMED_TO) {
            var candidate=candidate(">=0.18.0, <0.19",Map.of("test/kit/Probe.class",constant("dev.jasper.sdk.ui.Nodes$"+renamed.get(0),renamed.get(1))),false);
            try(var loader=PluginLoader.load(candidate,Map.of(),Plugin.class.getClassLoader()).loader()){assertThat(loader).as(renamed.toString()).isNotNull();}
        }
        var own=candidate(">=0.18.0, <0.19",Map.of("test/kit/Probe.class",constant("test.kit.Mode","HERO")),false);
        try(var loader=PluginLoader.load(own,Map.of(),Plugin.class.getClassLoader()).loader()){assertThat(loader).as("the plugin's own constant").isNotNull();}
    }
    /** Workspace tabs plan 3b: a manifest of the previous minor, 0.18, is skipped by its range before any class is read. */
    @Test void aZeroEighteenRangeIsSkippedBeforeItLoads() throws Exception {
        var candidate=candidate(">=0.18.0, <0.19",Map.of("test/kit/Probe.class",constant("dev.jasper.sdk.ui.Nodes$ChoiceKind","SEGMENTED")),false);
        var result=PluginResolver.resolve(List.of(candidate),Map.of(),Version.parse(JasperSdk.VERSION),false);
        assertThat(result.load()).isEmpty();
        assertThat(result.rejected()).singleElement().satisfies(value -> assertThat(value.reason()).isEqualTo("needs SDK >=0.18.0, <0.19.0; this is SDK 0.19.0"));
    }
    @Test void truthfulOldRangeIsRejectedByPureResolver() throws Exception {
        var candidate=candidate(">=0.14, <0.15",Map.of(),false);
        var result=PluginResolver.resolve(List.of(candidate),Map.of(),Version.parse("0.15.0"),false);
        assertThat(result.load()).isEmpty();assertThat(result.rejected()).singleElement().satisfies(value -> assertThat(value.reason()).contains("needs SDK", "0.15"));
    }
    @Test void falselyBroadRangeWithRawMountReferenceIsRejectedBeforeInitialization() throws Exception {
        rejects(reference("dev.jasper.sdk.ui.WindowSurface","setContent","(Ljavax/swing/JComponent;)V"),"setContent");
    }
    @Test void methodHandleAndRemovedBridgeTypesAreDetected() throws Exception {
        rejects(ClassFile.of().build(ClassDesc.of("test.kit.Probe"),b -> b.constantPool().classEntry(ClassDesc.of("dev.jasper.sdk.ui.PanelFactory$View"))),"PanelFactory$View");
        rejects(ClassFile.of().build(ClassDesc.of("test.kit.Probe"),b -> b.constantPool().methodHandleEntry(9,b.constantPool().interfaceMethodRefEntry(ClassDesc.of("dev.jasper.sdk.settings.SettingsPanelHost"),"rowNode",MethodTypeDesc.ofDescriptor("(Ldev/jasper/sdk/settings/Setting;)Ldev/jasper/sdk/ui/Nodes$Node;")))),"rowNode");
    }
    @Test void removedArrayClassEntryIsDetected() throws Exception {
        rejects(ClassFile.of().build(ClassDesc.of("test.kit.Probe"), b -> b.constantPool().classEntry(ClassDesc.of("dev.jasper.sdk.ui.PanelFactory$View").arrayType())), "PanelFactory$View");
    }
    @Test void obsoleteFactoryImplementationIsDetectedWithoutCallsite() throws Exception {
        rejects(ClassFile.of().build(ClassDesc.of("test.kit.Probe"),b -> {
            b.withInterfaceSymbols(ClassDesc.of("dev.jasper.sdk.ui.PanelFactory"));
            b.withMethodBody("create",MethodTypeDesc.ofDescriptor("(Ldev/jasper/sdk/ui/PanelHost;)Ljavax/swing/JComponent;"),ClassFile.ACC_PUBLIC,c -> c.aconst_null().areturn());
        }),"create");
        rejects(ClassFile.of().build(ClassDesc.of("test.kit.Probe"),b -> {
            b.withInterfaceSymbols(ClassDesc.of("dev.jasper.sdk.settings.SettingsPanel"));
            b.withMethodBody("create",MethodTypeDesc.ofDescriptor("(Ldev/jasper/sdk/settings/SettingsPanelHost;)Ljavax/swing/JComponent;"),ClassFile.ACC_PUBLIC,c -> c.aconst_null().areturn());
        }),"create");
    }
    @Test void defaultPackageClassesAreInspectedToo() throws Exception {
        var bytes=reference("dev.jasper.sdk.ui.WindowSurface","setContent","(Ljavax/swing/JComponent;)V");
        var candidate=candidate(">=0.1",Map.of("Probe.class",bytes),false);
        assertThatThrownBy(() -> PluginLoader.load(candidate,Map.of(),Plugin.class.getClassLoader())).hasMessageContaining("setContent");
    }
    @Test void sameNamesWithFinalDescriptorsAndOrdinaryAwtValuesAreAdmitted() throws Exception {
        var bytes=reference("dev.jasper.sdk.ui.WindowSurface","setContent","(Ldev/jasper/sdk/ui/UiView;)V");
        var candidate=candidate(">=0.15, <0.16",Map.of("test/kit/Probe.class",bytes),false);
        try(var loader=PluginLoader.load(candidate,Map.of(),Plugin.class.getClassLoader()).loader()){assertThat(loader).isNotNull();}
    }
    @Test void effectiveMultiReleaseEntryWinsAndFutureOrNonMultiVariantsAreIgnored() throws Exception {
        var old=reference("dev.jasper.sdk.ui.WindowSurface","setContent","(Ljavax/swing/JComponent;)V");
        var current=reference("dev.jasper.sdk.ui.WindowSurface","setContent","(Ldev/jasper/sdk/ui/UiView;)V");
        String base="test/kit/Probe.class";
        for(var entries:List.of(Map.of(base,old,"META-INF/versions/25/"+base,current),Map.of(base,current,"META-INF/versions/26/"+base,old))) {
            try(var loader=PluginLoader.load(candidate(">=0.1",entries,true),Map.of(),Plugin.class.getClassLoader()).loader()){assertThat(loader).isNotNull();}
        }
        try(var loader=PluginLoader.load(candidate(">=0.1",Map.of(base,current,"META-INF/versions/25/"+base,old),false),Map.of(),Plugin.class.getClassLoader()).loader()){assertThat(loader).isNotNull();}
        var rejected=candidate(">=0.1",Map.of(base,current,"META-INF/versions/25/"+base,old),true);
        assertThatThrownBy(() -> PluginLoader.load(rejected,Map.of(),Plugin.class.getClassLoader())).hasMessageContaining("setContent");
    }
    @Test void supportedPluginInstantiatesAndUsesTheFinalKitContract() throws Exception {
        var dir=root.resolve("supported");
        PluginJars.build(dir,"plugin.jar",PluginJars.descriptor("test.kit","1.0.0","test.kit.Main").replace(">=0.1",">=0.15, <0.16"),Map.of("test.kit.Main","""
            package test.kit;
            public final class Main implements dev.jasper.sdk.plugin.Plugin {
                public void start(dev.jasper.sdk.plugin.PluginContext context){
                    context.panels().register(new dev.jasper.sdk.ui.PanelSpec("test.kit.panel","Panel","file",dev.jasper.sdk.ui.Anchor.LEFT), host -> context.kit().create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Text("text",dev.jasper.sdk.ui.ControlState.READY,"Text",dev.jasper.sdk.ui.Nodes.TextRole.BODY)),e -> {}));
                }
                public java.awt.Point point(){return new java.awt.Point(1,2);}
            }
            """),List.of());
        var candidate=PluginDiscovery.single(dir,PluginCandidate.Origin.DEV,new ArrayList<>()).orElseThrow();
        var loaded=PluginLoader.load(candidate,Map.of(),Plugin.class.getClassLoader());
        try(var loader=loaded.loader()) {
            var plugin=loaded.instantiate();
            assertThat(plugin.getClass().getClassLoader()).isSameAs(loader);
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                var host=new dev.jasper.sdk.testing.FakePluginHost();
                host.start(new dev.jasper.sdk.PluginInfo("test.kit","Kit","1.0.0",Set.of()),Set.of(),Set.of(),plugin);
                assertThat(host.panels()).contains("test.kit.panel|Panel|LEFT");
                var window=host.addTerminalWindow();
                host.openPanel("test.kit.panel",window);
                assertThat(host.panelView("test.kit.panel",window)).isPresent();
                host.close();
            });
        }
    }
}
