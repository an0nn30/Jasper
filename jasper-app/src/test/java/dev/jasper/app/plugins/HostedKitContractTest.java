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
 protected void stop(UiKit kit){((HostedKit)kit).close();}
}
