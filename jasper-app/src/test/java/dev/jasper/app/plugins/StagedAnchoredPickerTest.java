package dev.jasper.app.plugins;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.ui.swing.KitView;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.*;
import static dev.jasper.app.plugins.StagedVaultHost.*;
import static org.assertj.core.api.Assertions.*;

/** Actual staged Vault -> owned popup -> editor -> Remote import, through the real host, with synthetic sshDir. */
class StagedAnchoredPickerTest {
 @TempDir Path root;
 @Test void sixCompactStatesAndRealCreationRenderFromTheMountedImportRoute()throws Exception{
  var theme=onEdtValue(() -> ThemeTestSupport.controller(true));
  // Slate Cobalt Dark; the menu switches to Light and back while it keeps its query and selection.
  var pair=ThemeTestSupport.resolved(true);
  var appearance=onEdtValue(()->new WorkspaceAppearanceFixture(pair));
  try(var vault=new StagedVaultHost(root.resolve("home"),true,appearance)){
   vault.createVault("synthetic-preview-password");onEdt(()->vault.surface("Credential Vault").close());
   var ssh=Files.createDirectories(root.resolve("ssh"));Files.writeString(ssh.resolve("config"),"Host picker-demo\n HostName picker-demo.invalid\n User fixture\n");
   var staged=Path.of(System.getProperty("jasper.stagedPlugins")).resolve("dev.jasper.remote");
   try(var loader=new PluginClassLoader("dev.jasper.remote",ManagedVaultLoadingTest.jars(staged),getClass().getClassLoader(),Map.of("dev.jasper.vault.api",vault.loader))){
    var constructor=loader.loadClass("dev.jasper.remote.RemotePlugin").getDeclaredConstructor(Executor.class,Function.class,BiFunction.class,Path.class);constructor.setAccessible(true);
    var remote=(Plugin)constructor.newInstance((Executor)SwingUtilities::invokeLater,(Function<Object,Optional<Object>>)c->Optional.empty(),(BiFunction<Duration,Runnable,Runnable>)(delay,task)->()->{},ssh);
    onEdt(()->vault.host.start(new HostedPlugin(StagedRemoteHost.REMOTE,Set.of(),Set.of("dev.jasper.vault","dev.jasper.editor"),Set.of("dev.jasper.remote.api"),loader,()->remote)));
    vault.invoke("dev.jasper.remote.import");var form=vault.awaitSurface("Import from ~/.ssh/config");await(()->form.shown()&&form.holder().getComponentCount()>0,"import preview mounted");
    onEdt(()->{
     capture("empty",kitView(form));click(form,"import.choose.0.input");
     var menu=menu(vault);capture("open-empty",menu);search(menu,"staging");capture("no-matches",menu);enter(named(menu,"credential.menu.search"));
     click(vault.surface("New Credential"),"create.login");var editor=vault.surface("Add Login");
     assertThat(editor.ownerSurface()).contains(form);
     var closingView=kitView(editor);var closingForm=closingView.getComponent(0);closingView.addContainerListener(new java.awt.event.ContainerAdapter(){@Override public void componentRemoved(java.awt.event.ContainerEvent event){closingForm.getPreferredSize();}});
     type(editor,"name.input","fixture login");type(editor,"username.input","fixture-with-a-deliberately-long-synthetic-username");type(editor,"password.input","synthetic-secret");click(editor,"save");
    });
    await(()->menus(vault).size()==1,"picker returns after saving Login");
    onEdt(()->{
     var menu=menu(vault);assertThat(((dev.jasper.ui.model.Nodes.PickerMenu)menu.spec().root()).query()).isEqualTo("staging");
     search(menu,"");key(menu,"down");
     var field=(JTextField)named(menu,"credential.menu.search");var document=field.getDocument();
     appearance.setPair(ThemeTestSupport.resolved(false));appearance.setPair(pair);
     var defaults=new HashMap<Object,Object>();UIManager.getDefaults().forEach(defaults::put);try{UIManager.put("defaultFont",new Font("Dialog",Font.PLAIN,20));}finally{UIManager.put("defaultFont",defaults.get("defaultFont"));}
     assertThat(field.getDocument()).isSameAs(document);assertThat(((dev.jasper.ui.model.Nodes.PickerItem)((JList<?>)named(menu,"credential.menu.rows")).getSelectedValue()).label()).isEqualTo("fixture login");
     enter(field);
    });
    await(()->!((dev.jasper.ui.model.Nodes.Choice)node(form,"import.choose.0")).face().orElseThrow().primary().isEmpty(),"chosen metadata projected");
    onEdt(()->{capture("selected",kitView(form));KitView menu;
     click(form,"import.choose.0.input");menu=menu(vault);capture("open",menu);capture("open-face",kitView(form));
     var field=(JTextField)named(menu,"credential.menu.search");var document=field.getDocument();field.setText("fixt");field.setCaretPosition(2);capture("filtering",menu);
     appearance.setPair(dev.jasper.app.appearance.ThemeTestSupport.resolved(false));capture("filtering-light",menu);
     appearance.setPair(theme.current());capture("filtering-default-theme",menu);appearance.setPair(pair);
     var raw=new HashMap<Object,Object>();UIManager.getDefaults().forEach(raw::put);var font=raw.get("defaultFont");try{UIManager.put("defaultFont",new Font("Dialog",Font.PLAIN,20));capture("filtering-enlarged",menu);capture("selected-enlarged",kitView(form));assertFooterLabelsFit(kitView(form));}finally{UIManager.put("defaultFont",font);}
     menu.setAvailableHeight(90);capture("filtering-short",menu);assertThat(menu.getPreferredSize().height).isLessThanOrEqualTo(90);menu.setAvailableHeight(Integer.MAX_VALUE);
     capture("selected-narrow",kitView(form),330);
     assertThat(field.getDocument()).isSameAs(document);assertThat(field.getCaretPosition()).isEqualTo(2);
     field.setText("staging");capture("no-matches-selected",menu);
     field.getActionMap().get("picker.escape").actionPerformed(null);
    });
    for(String kind:List.of("login","key")) {
     onEdt(()->{click(form,"import.choose.0.input");var menu=menu(vault);search(menu,"cancel-fixture");enter(named(menu,"credential.menu.search"));click(vault.surface("New Credential"),"create."+kind);});
     var editor=vault.awaitSurface(kind.equals("login")?"Add Login":"Add SSH Key");
     onEdt(()->{teardownProbe(editor);click(editor,"cancel");});
     await(()->menus(vault).size()==1,"picker returns after "+kind+" Cancel");
     onEdt(()->{assertThat(((dev.jasper.ui.model.Nodes.PickerMenu)menu(vault).spec().root()).query()).isEqualTo("cancel-fixture");assertThat(vault.problems).isEmpty();key(menu(vault),"escape");});
    }
    settle();onEdt(()->click(form,"import.import"));
    var review=vault.awaitSurface("Import SSH keys");onEdt(()->{capture("disabled-review",kitView(form));
     var defaults=new HashMap<Object,Object>();UIManager.getDefaults().forEach(defaults::put);try{UIManager.put("defaultFont",new Font("Dialog",0,1).deriveFont(32.5f));assertThat(com.formdev.flatlaf.util.UIScale.getUserScaleFactor()).isEqualTo(2.5f);capture("disabled-review-250",kitView(form));}finally{UIManager.put("defaultFont",defaults.get("defaultFont"));}
     click(review,"commit");});
    await(()->!node(form,"import.import").state().enabled()&&((dev.jasper.ui.model.Nodes.Text)node(form,"import.status")).text().contains("saved"),"import completion");
    onEdt(()->{capture("disabled",kitView(form));assertThat(vault.problems).isEmpty();teardownProbe(form);click(form,"import.cancel");assertThat(form.closed()).isTrue();assertThat(vault.problems).isEmpty();});
   }
  }finally{onEdt(theme::close);}
 }
 static void teardownProbe(dev.jasper.app.windows.AuxiliarySurface surface) {
  var view=kitView(surface);var root=(Container)view.getComponent(0);var policy=root.getFocusTraversalPolicy();
  view.addContainerListener(new java.awt.event.ContainerAdapter(){@Override public void componentRemoved(java.awt.event.ContainerEvent event){
   root.getPreferredSize();assertThat(policy.getDefaultComponent(root)).isNull();assertThat(policy.getFirstComponent(root)).isNull();assertThat(policy.getLastComponent(root)).isNull();
  }});
 }
 static List<HostedKitView> menus(StagedVaultHost vault){try{
  var contextsField=PluginHost.class.getDeclaredField("contexts");contextsField.setAccessible(true);var context=(HostedContext)((Map<?,?>)contextsField.get(vault.host)).get("dev.jasper.vault");
  var kit=(HostedKit)context.kit();var viewsField=HostedKit.class.getDeclaredField("views");viewsField.setAccessible(true);
  @SuppressWarnings("unchecked")var views=(Set<HostedKitView>)viewsField.get(kit);
  return views.stream().filter(v->v.spec().root() instanceof dev.jasper.sdk.ui.Nodes.PickerMenu).toList();
 }catch(ReflectiveOperationException e){throw new AssertionError(e);}}
 static KitView menu(StagedVaultHost vault){return menus(vault).getFirst().component();}
 static void search(KitView menu,String text){((JTextField)named(menu,"credential.menu.search")).setText(text);}
 static void key(KitView menu,String key){((JTextField)named(menu,"credential.menu.search")).getActionMap().get("picker."+key).actionPerformed(null);}
 static void select(KitView menu,int index){((JList<?>)named(menu,"credential.menu.rows")).setSelectedIndex(index);}
 static void capture(String state,KitView view){capture(state,view,0);}
 static void capture(String state,KitView view,int width){
  try{
   Path output=Path.of("build/theme-migration-evidence/anchored-picker/actual");Files.createDirectories(output);
   var size=view.getPreferredSize();if(width>0)size.width=width;
   var popup=(JPopupMenu)SwingUtilities.getAncestorOfClass(JPopupMenu.class,view);
   JComponent painted=popup==null?view:popup;
   if(popup!=null)SwingUtilities.updateComponentTreeUI(popup);
   painted.setSize(size);layout(painted);
   if(popup!=null){assertThat(popup.getInsets()).isEqualTo(new Insets(0,0,0,0));var rows=(JList<?>)named(view,"credential.menu.rows");var scroll=(JScrollPane)SwingUtilities.getAncestorOfClass(JScrollPane.class,rows);if(!state.equals("filtering-short"))assertThat(scroll.getVerticalScrollBar().isVisible()).isFalse();}
   var image=new BufferedImage(size.width*2,size.height*2,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();g.scale(2,2);painted.printAll(g);g.dispose();ImageIO.write(image,"png",output.resolve(state+".png").toFile());
   var input=named(view,"import.choose.0.input");var bounds=input==null?new Rectangle():SwingUtilities.convertRectangle(input.getParent(),input.getBounds(),view);
   var field=named(view,"credential.menu.search");
   var defaults=new HashMap<Object,Object>();UIManager.getDefaults().forEach(defaults::put);
   String typography="\"hostWrapper\":"+(popup!=null)+",\"searchFontAttributes\":"+json(field==null?null:field.getFont().getAttributes())+",\"searchFont\":"+json(field==null?null:field.getFont())+",\"searchFontSize2D\":"+(field==null?"null":field.getFont().getSize2D())+",\"uiScale\":"+com.formdev.flatlaf.util.UIScale.getUserScaleFactor()+",\"defaultFontOverride\":"+json(defaults.get("defaultFont"))+",\"labelFontOverride\":"+json(defaults.get("Label.font"))+",\"familyOverride\":"+json(UIManager.get("Jasper.uiFontFamilyOverride"))+",";
   Files.writeString(output.resolve(state+".json"),"{"+typography+"\"state\":\""+state+"\",\"owner\":\""+view.spec().root().getClass().getSimpleName()+"\",\"logicalWidth\":"+size.width+",\"logicalHeight\":"+size.height+",\"deviceScale\":2,\"faceBounds\": ["+bounds.x+","+bounds.y+","+bounds.width+","+bounds.height+"],\"native\":false}");
  }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
 }
 static String json(Object value){return value==null?"null":"\""+value.toString().replace("\\","\\\\").replace("\"","\\\"")+"\"";}
 static void layout(Container c){for(int n=0;n<3;n++)arrange(c);}
 static void arrange(Container c){c.doLayout();for(var child:c.getComponents())if(child instanceof Container nested)arrange(nested);}
 static void assertFooterLabelsFit(java.awt.Container root) {
  for(var child:root.getComponents()) {
   if(child instanceof javax.swing.AbstractButton button && java.util.Set.of("Refresh","Cancel","Close","Import").contains(button.getText())) {
    var insets=button.getInsets();var image=new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
    try {g.setFont(button.getFont());String text=javax.swing.SwingUtilities.layoutCompoundLabel(button,g.getFontMetrics(),button.getText(),button.getIcon(),javax.swing.SwingConstants.CENTER,button.getHorizontalAlignment(),javax.swing.SwingConstants.CENTER,javax.swing.SwingConstants.RIGHT,new java.awt.Rectangle(insets.left,insets.top,button.getWidth()-insets.left-insets.right,button.getHeight()-insets.top-insets.bottom),new java.awt.Rectangle(),new java.awt.Rectangle(),button.getIconTextGap());assertThat(text).as("enlarged footer %s",button.getText()).isEqualTo(button.getText());}finally{g.dispose();}
   }
   if(child instanceof java.awt.Container container)assertFooterLabelsFit(container);
  }
 }

}
