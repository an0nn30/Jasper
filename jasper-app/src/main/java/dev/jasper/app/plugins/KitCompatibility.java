package dev.jasper.app.plugins;

import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.NameAndTypeEntry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Admission across SDK breaks: exact removed JVM members and types, never plugin execution or reflective linking.
 * Each entry names the SDK version that removed it. A plugin reads an enum constant with {@code getstatic}, as a classic
 * or arrow {@code switch} does through its switch map. Generic erasure and names held as strings (an enum constant
 * looked up by {@code valueOf}, or a qualified constant label in a pattern {@code switch}) are not provable here;
 * normal linkage containment still applies.
 */
final class KitCompatibility {
    private KitCompatibility() {}
    private static final Map<String, String> TYPES = removed(Map.of("0.15", List.of(
        "dev/jasper/sdk/settings/SettingsPanel$View",
        "dev/jasper/sdk/ui/BundledIcon",
        "dev/jasper/sdk/ui/IconName",
        "dev/jasper/sdk/ui/PanelFactory$View")));
    private static final Map<String, String> MEMBERS = removed(Map.of("0.15", List.of(
        "dev/jasper/sdk/notifications/NotificationSpec#withIcon(Ljavax/swing/Icon;)Ldev/jasper/sdk/notifications/NotificationSpec;",
        "dev/jasper/sdk/palette/PaletteRow#withIcon(Ljavax/swing/Icon;)Ldev/jasper/sdk/palette/PaletteRow;",
        "dev/jasper/sdk/palette/ScopeSpec#withIcon(Ljavax/swing/Icon;)Ldev/jasper/sdk/palette/ScopeSpec;",
        "dev/jasper/sdk/settings/SettingsPanel#create(Ldev/jasper/sdk/settings/SettingsPanelHost;)Ljavax/swing/JComponent;",
        "dev/jasper/sdk/settings/SettingsPanelHost#row(Ldev/jasper/sdk/settings/Setting;)Ljavax/swing/JComponent;",
        "dev/jasper/sdk/settings/SettingsPanelHost#rowNode(Ldev/jasper/sdk/settings/Setting;)Ldev/jasper/sdk/ui/Nodes$Node;",
        "dev/jasper/sdk/ui/ActionSpec#withIcon(Ljavax/swing/Icon;)Ldev/jasper/sdk/ui/ActionSpec;",
        "dev/jasper/sdk/ui/Appearance#icon(Ldev/jasper/sdk/ui/BundledIcon;)Ljavax/swing/Icon;",
        "dev/jasper/sdk/ui/Appearance#icon(Ldev/jasper/sdk/ui/IconName;)Ljavax/swing/Icon;",
        "dev/jasper/sdk/ui/Appearance#icon(Ljava/lang/String;)Ljavax/swing/Icon;",
        "dev/jasper/sdk/ui/ContentPresentation#<init>(Ljava/lang/String;Ljava/lang/String;Ljavax/swing/Icon;ZLjava/lang/String;Ljava/lang/String;)V",
        "dev/jasper/sdk/ui/ContentPresentation#icon()Ljavax/swing/Icon;",
        "dev/jasper/sdk/ui/EditorPalette#current()Ldev/jasper/sdk/ui/EditorPalette;",
        "dev/jasper/sdk/ui/PanelFactory#create(Ldev/jasper/sdk/ui/PanelHost;)Ljavax/swing/JComponent;",
        "dev/jasper/sdk/ui/PanelHeader$Action#<init>(Ljava/lang/String;Ljavax/swing/Icon;Ljava/lang/Runnable;)V",
        "dev/jasper/sdk/ui/PanelHeader$Action#icon()Ljavax/swing/Icon;",
        "dev/jasper/sdk/ui/PanelSpec#<init>(Ljava/lang/String;Ljava/lang/String;Ljavax/swing/Icon;Ldev/jasper/sdk/ui/Anchor;)V",
        "dev/jasper/sdk/ui/PanelSpec#<init>(Ljava/lang/String;Ljava/lang/String;Ljavax/swing/Icon;Ldev/jasper/sdk/ui/Anchor;Ljava/util/Optional;)V",
        "dev/jasper/sdk/ui/PanelSpec#<init>(Ljava/lang/String;Ljava/lang/String;Ljavax/swing/Icon;Ldev/jasper/sdk/ui/Anchor;Ljava/util/Optional;Z)V",
        "dev/jasper/sdk/ui/PanelSpec#<init>(Ljava/lang/String;Ljava/lang/String;Ljavax/swing/Icon;Ldev/jasper/sdk/ui/Anchor;Ljava/util/Optional;ZLjava/lang/String;)V",
        "dev/jasper/sdk/ui/PanelSpec#icon()Ljavax/swing/Icon;",
        "dev/jasper/sdk/ui/PanelSpec#iconName()Ljava/lang/String;",
        "dev/jasper/sdk/ui/Panels#registerView(Ldev/jasper/sdk/ui/PanelSpec;Ldev/jasper/sdk/ui/PanelFactory$View;)Ldev/jasper/sdk/Subscription;",
        "dev/jasper/sdk/ui/PluginDialog#setContent(Ljavax/swing/JComponent;)V",
        "dev/jasper/sdk/ui/PluginWindow#setContent(Ljavax/swing/JComponent;)V",
        "dev/jasper/sdk/ui/StatusItem#setIcon(Ljavax/swing/Icon;)V",
        "dev/jasper/sdk/ui/SyntaxPalette#current()Ldev/jasper/sdk/ui/SyntaxPalette;",
        "dev/jasper/sdk/ui/ToolbarItem#menu(Ljavax/swing/Icon;Ljava/lang/String;Ljava/util/List;)Ldev/jasper/sdk/ui/ToolbarItem;",
        "dev/jasper/sdk/ui/ToolbarItem$Dropdown#<init>(Ljavax/swing/Icon;Ljava/lang/String;Ljava/util/List;)V",
        "dev/jasper/sdk/ui/ToolbarItem$Dropdown#icon()Ljavax/swing/Icon;",
        "dev/jasper/sdk/ui/WindowSurface#setContent(Ljavax/swing/JComponent;)V",
        "dev/jasper/sdk/ui/Windows#openTab(Ldev/jasper/sdk/ui/ContentSpec;Ldev/jasper/sdk/WindowOwner;Ljavax/swing/JComponent;)Ldev/jasper/sdk/ui/ContentTab;",
        "dev/jasper/sdk/ui/WorkspacePalette#current()Ldev/jasper/sdk/ui/WorkspacePalette;",
        "dev/jasper/sdk/ui/WorkspacePalette#current(Ljava/awt/Component;)Ldev/jasper/sdk/ui/WorkspacePalette;"),
        // The constants SDK 0.18 renamed.
        "0.18", List.of(
        "dev/jasper/sdk/ui/Nodes$ChoiceKind#SEGMENTEDLdev/jasper/sdk/ui/Nodes$ChoiceKind;",
        "dev/jasper/sdk/ui/Nodes$ButtonPresentation#CIRCULARLdev/jasper/sdk/ui/Nodes$ButtonPresentation;",
        "dev/jasper/sdk/ui/Nodes$FormKind#HEROLdev/jasper/sdk/ui/Nodes$FormKind;",
        "dev/jasper/sdk/ui/Nodes$FormKind#HERO_COMPACTLdev/jasper/sdk/ui/Nodes$FormKind;",
        "dev/jasper/sdk/ui/Nodes$LayoutRole#HEROLdev/jasper/sdk/ui/Nodes$LayoutRole;",
        "dev/jasper/sdk/ui/Nodes$TextRole#HERO_TITLELdev/jasper/sdk/ui/Nodes$TextRole;",
        "dev/jasper/sdk/ui/Nodes$IconSize#HEROLdev/jasper/sdk/ui/Nodes$IconSize;"),
        // The members SDK 0.19 removed with workspace tabs.
        "0.19", List.of(
        "dev/jasper/sdk/terminal/TabHandle#activePane()Ljava/util/Optional;",
        "dev/jasper/sdk/terminal/TabHandle#panes()Ljava/util/List;")));

    private static Map<String, String> removed(Map<String, List<String>> byVersion) {
        var removed = new HashMap<String, String>();
        byVersion.forEach((version, names) -> names.forEach(name -> removed.put(name, version)));
        return Map.copyOf(removed);
    }

    static void check(byte[] bytes, String source) throws PluginLoader.LoadFailure {
        try {
            var model=ClassFile.of().parse(bytes);
            for(var entry:model.constantPool()) {
                if(entry instanceof ClassEntry type) {
                    if(TYPES.containsKey(type.asInternalName())) fail(source,"type",type.asInternalName(),TYPES);
                    descriptor(source,type.asInternalName()); // Array class constants carry descriptors.
                }
                if(entry instanceof NameAndTypeEntry value) descriptor(source,value.type().stringValue());
                if(entry instanceof MemberRefEntry member) {
                    String reference=member.owner().asInternalName()+"#"+member.name().stringValue()+member.type().stringValue();
                    if(MEMBERS.containsKey(reference))fail(source,"member",reference,MEMBERS);
                }
            }
            for(var field:model.fields())descriptor(source,field.fieldType().stringValue());
            for(var method:model.methods()) {
                String signature=method.methodName().stringValue()+method.methodType().stringValue();
                descriptor(source,method.methodType().stringValue());
                // An old factory implementation may never reference its interface's removed member.
                for(var implemented:model.interfaces()) {
                    String reference=implemented.asInternalName()+"#"+signature;
                    if(MEMBERS.containsKey(reference))fail(source,"member",reference,MEMBERS);
                }
            }
        } catch(IllegalArgumentException failure) {
            throw new PluginLoader.LoadFailure("Cannot inspect " + source + ": invalid class file",failure);
        }
    }
    private static void descriptor(String source,String descriptor) throws PluginLoader.LoadFailure {
        for(var type:TYPES.keySet())if(descriptor.contains("L"+type+";"))fail(source,"type",type,TYPES);
    }
    private static void fail(String source,String kind,String name,Map<String, String> removedIn) throws PluginLoader.LoadFailure {
        throw new PluginLoader.LoadFailure(source+" references removed SDK "+kind+" "+name+"; rebuild it for SDK "+removedIn.get(name),null);
    }
}
