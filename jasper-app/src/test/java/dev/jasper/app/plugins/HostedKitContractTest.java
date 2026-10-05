package dev.jasper.app.plugins;
import dev.jasper.sdk.testing.KitContractTest;
import dev.jasper.sdk.ui.*;
import java.awt.*;
import javax.swing.*;
class HostedKitContractTest extends KitContractTest {
 protected UiKit kit(String owner){return new HostedKit(owner,() -> true,() -> KitThemeBridge.fallback(false),null,new Containment(EventQueue::isDispatchThread));}
 protected void emit(UiView view,UiEvent event){((HostedKitView)view).dispatch(event);}
 protected void secret(UiView view,String id,char[] value){ password(((HostedKitView)view).component()).setText(new String(value)); }
 static JPasswordField password(Component c){if(c instanceof JPasswordField p)return p;if(c instanceof Container p)for(var child:p.getComponents()){var result=password(child);if(result!=null)return result;}return null;}
 protected void owns(UiKit kit,UiView view){((HostedKit)kit).requireOwned(view);}
 protected void stop(UiKit kit){((HostedKit)kit).close();}
}
