package dev.jasper.app.plugins;
import dev.jasper.sdk.testing.KitContractTest;
import dev.jasper.sdk.ui.*;
import java.awt.*;
import javax.swing.*;
class HostedKitContractTest extends KitContractTest {
 protected UiKit kit(String owner){return new HostedKit(owner,() -> true,() -> dev.jasper.app.appearance.ThemeTestSupport.pair(false),null,new Containment(EventQueue::isDispatchThread));}
 protected void emit(UiView view,UiEvent event){((HostedKitView)view).dispatch(event);}
 protected void secret(UiView view,String id,char[] value){ input(((HostedKitView)view).component(),id+".input").setText(new String(value)); }
 /** The kit's input for a secret node: a password field, or the text area of a multiline secret. */
 static javax.swing.text.JTextComponent input(Component c,String name){if(c instanceof javax.swing.text.JTextComponent t&&name.equals(t.getName()))return t;if(c instanceof Container p)for(var child:p.getComponents()){var result=input(child,name);if(result!=null)return result;}return null;}
 static JPasswordField password(Component c){if(c instanceof JPasswordField p)return p;if(c instanceof Container p)for(var child:p.getComponents()){var result=password(child);if(result!=null)return result;}return null;}
 protected void owns(UiKit kit,UiView view){((HostedKit)kit).requireOwned(view);}
 protected void fieldText(UiView view,String id,String text){input(((HostedKitView)view).component(),id+".input").setText(text);}
 protected int[] fieldSelection(UiView view,String id){var field=input(((HostedKitView)view).component(),id+".input");return new int[]{field.getSelectionStart(),field.getSelectionEnd()};}
 /** As a person types: the text component's key-typed action, which a read-only or disabled field ignores; a hidden field (or one under a hidden container) is not there to type into. */
 protected void type(UiView view,String id,String text){var root=((HostedKitView)view).component();var field=input(root,id+".input");if(!shown(field,root))return;field.selectAll();new javax.swing.text.DefaultEditorKit.DefaultKeyTypedAction().actionPerformed(new java.awt.event.ActionEvent(field,java.awt.event.ActionEvent.ACTION_PERFORMED,text));}
 /** As a person clicks: the kit's shown button named {@code id}. */
 protected void click(UiView view,String id){var root=((HostedKitView)view).component();var button=named(root,id);if(!(button instanceof AbstractButton b)||!shown(b,root))throw new AssertionError("No shown button "+id);b.doClick(0);}
 static Component named(Component c,String name){if(name.equals(c.getName()))return c;if(c instanceof Container p)for(var child:p.getComponents()){var result=named(child,name);if(result!=null)return result;}return null;}
 /** Whether {@code c} and every container above it, up to {@code root}, is visible. */
 static boolean shown(Component c,Component root){for(var at=c;at!=null;at=at.getParent()){if(!at.isVisible())return false;if(at==root)return true;}return false;}
 /** As a person clicks into a field whose window has the focus elsewhere: a left press and release on its input. */
 protected void clickInto(UiView view,String id){var field=input(((HostedKitView)view).component(),id+".input");long when=System.currentTimeMillis();
  for(int type:new int[]{java.awt.event.MouseEvent.MOUSE_PRESSED,java.awt.event.MouseEvent.MOUSE_RELEASED,java.awt.event.MouseEvent.MOUSE_CLICKED})
   field.dispatchEvent(new java.awt.event.MouseEvent(field,type,when,type==java.awt.event.MouseEvent.MOUSE_PRESSED?java.awt.event.InputEvent.BUTTON1_DOWN_MASK:0,2,2,1,false,java.awt.event.MouseEvent.BUTTON1));}
 /** The titles of the suggestions the kit's list shows under field {@code id} (published on its input while it shows). */
 protected java.util.List<String> suggestions(UiView view,String id){var list=(JList<?>)input(((HostedKitView)view).component(),id+".input").getClientProperty(dev.jasper.ui.swing.KitOwnership.SUGGESTIONS);
  var titles=new java.util.ArrayList<String>();if(list!=null)for(int row=0;row<list.getModel().getSize();row++)if(list.getModel().getElementAt(row) instanceof dev.jasper.ui.model.Nodes.Suggestion suggestion)titles.add(suggestion.title());return titles;}
 /** As a person presses a key with the focus in field {@code id}: its key listeners, then, unless one consumed the key, its key bindings. */
 protected void press(UiView view,String id,String key){var field=input(((HostedKitView)view).component(),id+".input");
  var event=new java.awt.event.KeyEvent(field,java.awt.event.KeyEvent.KEY_PRESSED,System.currentTimeMillis(),0,KeyStroke.getKeyStroke(key).getKeyCode(),java.awt.event.KeyEvent.CHAR_UNDEFINED);
  for(var listener:field.getKeyListeners())listener.keyPressed(event);if(!event.isConsumed())SwingUtilities.processKeyBindings(event);}
 /**
  * As a person chooses from node {@code id}'s menu: a menu button's own popup, or a button's hold menu (its context
  * menu), each step a row whose item has that id, through a submenu's children; a disabled submenu does not open.
  */
 protected void choose(UiView view,String id,String... path){var node=(JComponent)named(((HostedKitView)view).component(),id);
  var menu=node.getComponentPopupMenu()!=null?node.getComponentPopupMenu():(JPopupMenu)node.getClientProperty(dev.jasper.ui.swing.KitOwnership.CONTEXT_MENU);
  if(menu==null)throw new AssertionError("No menu "+id);
  Component[] rows=menu.getComponents();JMenuItem row=null;
  for(String step:path){
   if(row instanceof JMenu submenu){if(!submenu.isEnabled())return;rows=submenu.getMenuComponents();}
   row=null;for(var candidate:rows)if(candidate instanceof JMenuItem item&&item.getClientProperty("Jasper.menu.item") instanceof dev.jasper.ui.model.MenuItem shown&&shown.id().equals(step))row=item;
   if(row==null)return;
  }
  if(!(row instanceof JMenu))row.doClick(0);}
 /** The tokens the kit's chips show in token field {@code id}: the accessible names of its components named {@code id.token.N}, in order. */
 protected java.util.List<String> tokens(UiView view,String id){var shown=new java.util.ArrayList<String>();chips(((HostedKitView)view).component(),id+".token.",shown);return shown;}
 static void chips(Component c,String prefix,java.util.List<String> into){if(c instanceof JComponent chip&&chip.getName()!=null&&chip.getName().startsWith(prefix))into.add(chip.getAccessibleContext().getAccessibleName());
  if(c instanceof Container p)for(var child:p.getComponents())chips(child,prefix,into);}
 /** As a person clicks a token's remove button: the kit's shown button in token field {@code id} named "Remove" and the token. */
 protected void removeToken(UiView view,String id,String token){var root=((HostedKitView)view).component();var button=removal(root,id+".remove.","Remove "+token);
  if(button==null||!shown(button,root))throw new AssertionError("No shown remove button for "+token);button.doClick(0);}
 static AbstractButton removal(Component c,String prefix,String name){if(c instanceof AbstractButton b&&b.getName()!=null&&b.getName().startsWith(prefix)&&name.equals(b.getAccessibleContext().getAccessibleName()))return b;
  if(c instanceof Container p)for(var child:p.getComponents()){var found=removal(child,prefix,name);if(found!=null)return found;}return null;}
 /** As the focus leaves field {@code id} for node {@code to} for good: the input's focus listeners hear a permanent loss to it. */
 protected void leave(UiView view,String id,String to){var root=((HostedKitView)view).component();var field=input(root,id+".input");var opposite=named(root,to);
  for(var listener:field.getFocusListeners())listener.focusLost(new java.awt.event.FocusEvent(field,java.awt.event.FocusEvent.FOCUS_LOST,false,opposite));}
 protected void stop(UiKit kit){((HostedKit)kit).close();}
}
