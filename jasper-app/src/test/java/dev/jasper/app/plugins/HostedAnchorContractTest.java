package dev.jasper.app.plugins;
import dev.jasper.sdk.testing.AnchorContractTest;
import java.util.*;
class HostedAnchorContractTest extends AnchorContractTest {
 protected Fixture fixture(){return new Fixture(){
  final dev.jasper.app.windows.AuxiliaryWindows windows=AppContractTest.headlessWindows();final List<Runnable> closers=new ArrayList<>();
  public Endpoint endpoint(String id){var containment=new Containment(()->true);var ui=new HostedUi(id,new dev.jasper.app.contributions.Contributions(),containment,Runnable::run,()->true,()->true,getClass().getClassLoader(),()->dev.jasper.sdk.Variant.DARK,h->()->{},windows,null);var kit=new HostedKit(id,()->true,ui::kitThemes,null,containment);ui.kit(kit);Runnable stop=()->{ui.closeAll();kit.close();};closers.add(stop);return new Endpoint(kit,ui.windows(),stop);}
  public void close(){closers.forEach(Runnable::run);windows.close();}
 };}
}
