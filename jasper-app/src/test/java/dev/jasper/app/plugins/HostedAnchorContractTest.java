package dev.jasper.app.plugins;
import dev.jasper.sdk.testing.AnchorContractTest;
import java.util.*;
class HostedAnchorContractTest extends AnchorContractTest {
 protected Fixture fixture(){return new Fixture(){
  final dev.jasper.app.windows.AuxiliaryWindows windows=AppContractTest.headlessWindows();final List<Runnable> closers=new ArrayList<>();
  public Endpoint endpoint(String id){var containment=new Containment(()->true);
   // A main window holds the plugin's content tabs, so a dialog a content tab owns resolves to that window.
   var fixture=new TerminalFixture();var main=fixture.addWindow();
   var terminals=new HostedTerminals(id,new CapabilityGate(id,java.util.Set.of()),fixture.registry,Runnable::run,()->true,()->true,new HostedSessions(id,containment,Runnable::run,x->null,name->new javax.swing.ImageIcon()));
   var model=new dev.jasper.app.contributions.Contributions();model.tabs().addHost(new dev.jasper.app.contributions.WorkspaceHost(main,tab->{},tab->{},tab->{},()->{}));
   var ui=new HostedUi(id,model,containment,Runnable::run,()->true,()->true,getClass().getClassLoader(),()->dev.jasper.sdk.Variant.DARK,h->()->{},windows,terminals);var kit=new HostedKit(id,()->true,ui::kitThemes,null,containment);ui.kit(kit);Runnable stop=()->{ui.closeAll();kit.close();};closers.add(stop);return new Endpoint(kit,ui.windows(),stop);}
  public void close(){closers.forEach(Runnable::run);windows.close();}
 };}
}
