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
 protected void stop(UiKit kit){((HostedKit)kit).close();}
}
