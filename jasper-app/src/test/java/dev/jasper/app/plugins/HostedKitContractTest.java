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
 protected void stop(UiKit kit){((HostedKit)kit).close();}
}
